package com.mreddy.liftz.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where the update check lands. */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(
        val versionName: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val notes: String
    ) : UpdateStatus
    /** Network down, rate-limited, no release yet — all non-events. Never shown to the user. */
    data class Unknown(val reason: String) : UpdateStatus
}

/**
 * Self-updating, because there is no store to do it.
 *
 * The app is installed by sideloading from a link, which means nothing tells anyone a new build
 * exists. Without this, shipping a fix means personally chasing every friend, and six weeks later
 * half of them are reporting bugs that were fixed in a version they never installed.
 *
 * It asks GitHub for the latest release, compares versions, and if there is a newer one hands the
 * APK to Android's own package installer. It cannot install silently — that is a store-only
 * privilege and deliberately so — the user always sees and confirms Android's install screen.
 */
class UpdateChecker(private val context: Context) {

    /** The installed version, read from the package rather than BuildConfig. */
    fun installedVersion(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // GitHub rejects requests with no User-Agent outright.
                setRequestProperty("User-Agent", "mreddyLiftz")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            conn.inputStream.bufferedReader().use { it.readText() }.let { body ->
                conn.disconnect()
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v").trim()
                if (tag.isEmpty()) return@withContext UpdateStatus.Unknown("No release published yet")

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                var size = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                            size = a.optLong("size")
                            break
                        }
                    }
                }
                if (apkUrl.isNullOrBlank()) {
                    return@withContext UpdateStatus.Unknown("That release has no APK attached")
                }

                if (isNewerVersion(tag, installedVersion())) {
                    UpdateStatus.Available(tag, apkUrl, size, json.optString("body").trim())
                } else {
                    UpdateStatus.UpToDate
                }
            }
        }.getOrElse { UpdateStatus.Unknown(it.message ?: "Couldn't reach GitHub") }
    }

    /** Android 8+ gates sideloaded installs behind a per-app permission the user grants once. */
    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** Opens the system screen where that permission is granted. */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Download the APK and hand it to Android's installer.
     *
     * Uses DownloadManager rather than a raw stream: it survives the app being backgrounded,
     * retries on a flaky connection, and puts a progress entry in the notification shade — all
     * things a hand-rolled download on a phone in a gym would get wrong.
     */
    suspend fun downloadAndInstall(
        update: UpdateStatus.Available,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "mreddyLiftz-${update.versionName}.apk"

            // Clear any half-finished copy from a previous attempt, or the installer will happily
            // try to parse a truncated file and report a useless "package appears to be invalid".
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).delete()

            val id = dm.enqueue(
                DownloadManager.Request(Uri.parse(update.apkUrl))
                    .setTitle("mreddyLiftz ${update.versionName}")
                    .setDescription("Downloading update")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(
                        context, Environment.DIRECTORY_DOWNLOADS, fileName
                    )
            )

            var done = false
            var failure: String? = null
            while (!done) {
                delay(400)
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) { failure = "Download disappeared"; done = true; return@use }
                    when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> done = true
                        DownloadManager.STATUS_FAILED -> {
                            failure = "Download failed"; done = true
                        }
                        else -> {
                            val soFar = c.getLong(
                                c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            )
                            val total = c.getLong(
                                c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            )
                            if (total > 0) onProgress(((soFar * 100) / total).toInt())
                        }
                    }
                }
            }
            failure?.let { error(it) }

            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName
            )
            check(file.exists() && file.length() > 0) { "The downloaded file is empty" }

            // A raw file:// URI throws FileUriExposedException on API 24+; the installer needs a
            // content:// URI it has been granted read access to.
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.updates", file
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )
            Unit
        }
    }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/WiredSurya/mreddyliftz/releases/latest"
    }
}

/**
 * Numeric-segment version comparison.
 *
 * A string compare gets 1.10.0 vs 1.9.0 backwards — and 1.10 is exactly the version a project
 * reaches right before anyone notices. Pulled out of the class so it can be unit tested on the
 * JVM without an Android Context, which is the whole reason the bug would be caught at all.
 */
internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parts(v: String) = v.split('.', '-', '+')
        .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val a = parts(candidate)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
