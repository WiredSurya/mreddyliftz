package com.mreddy.liftz.data.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * A [SyncBackend] that writes to the app's own private storage.
 *
 * This is NOT a placeholder that does nothing — it is a real, working backup target, and it is
 * what makes the whole sync pipeline testable and shippable before any cloud account exists. It
 * gives a genuine "restore my routine after I messed it up" safety net today, and it means the
 * upload/download/restore paths are exercised for real rather than waiting on Firebase to be
 * proven for the first time in production.
 *
 * It is explicitly NOT off-device: app-private storage is wiped with the app. Settings says so
 * plainly rather than implying a cloud that is not there.
 */
class LocalFileBackend(private val context: Context) : SyncBackend {

    override val displayName = "This device"

    override suspend fun isAvailable() = true

    private val file: File get() = File(context.filesDir, SNAPSHOT_FILE)

    override suspend fun upload(snapshot: String, meta: SnapshotMeta): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val wrapper = JSONObject().apply {
                    put(KEY_TAKEN_AT, meta.takenAtMs)
                    put(KEY_DEVICE_ID, meta.deviceId)
                    put(KEY_SCHEMA, meta.schemaVersion)
                    put(KEY_PAYLOAD, snapshot)
                }
                // Write to a temp file and rename, so an interrupted write cannot leave a
                // half-written backup that would fail to parse on restore.
                val tmp = File(context.filesDir, "$SNAPSHOT_FILE.tmp")
                tmp.writeText(wrapper.toString())
                check(tmp.renameTo(file)) { "Could not replace the existing backup" }
            }
        }

    override suspend fun download(): Result<StoredSnapshot?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val wrapper = JSONObject(file.readText())
            val payload = wrapper.getString(KEY_PAYLOAD)
            StoredSnapshot(
                json = payload,
                meta = SnapshotMeta(
                    takenAtMs = wrapper.optLong(KEY_TAKEN_AT),
                    deviceId = wrapper.optString(KEY_DEVICE_ID),
                    schemaVersion = wrapper.optInt(KEY_SCHEMA),
                    sizeBytes = payload.length
                )
            )
        }
    }

    private companion object {
        const val SNAPSHOT_FILE = "liftz_backup.json"
        const val KEY_TAKEN_AT = "taken_at_ms"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SCHEMA = "schema_version"
        const val KEY_PAYLOAD = "payload"
    }
}
