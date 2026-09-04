package com.mreddy.liftz.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Backs up into a folder the user picks, through Android's Storage Access Framework.
 *
 * WHY THIS RATHER THAN FIREBASE, AT LEAST FIRST:
 * SAF exposes whatever document providers are installed — Google Drive, Dropbox, OneDrive,
 * Nextcloud — as pickable folders. Point this at a folder inside one of those and the backup is
 * genuinely off-device and genuinely synced, using the provider's own account and its own
 * sync engine. That means:
 *
 *   - no Firebase project, OAuth client, SHA-1 registration or `google-services.json`
 *   - no server, no running cost, no security rules to get wrong
 *   - no credentials in this app, ever: it never sees a password or a token, only a folder
 *     handle the user granted it
 *   - the user picks their own provider instead of being forced onto ours
 *
 * The permission is taken as PERSISTABLE, so the grant survives reboots and app restarts and the
 * folder only has to be chosen once.
 *
 * Honest limits: it syncs when the provider's own app syncs, not instantly, and if the chosen
 * folder is local (an SD card, Downloads) then this is off-app but not off-device. Settings says
 * which of the two you picked rather than implying cloud either way.
 */
class FolderBackend(
    private val context: Context,
    private val treeUri: Uri
) : SyncBackend {

    override val displayName: String =
        DocumentFile.fromTreeUri(context, treeUri)?.name?.let { "folder \"$it\"" } ?: "chosen folder"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentFile.fromTreeUri(context, treeUri)?.canWrite() == true }
            .getOrDefault(false)
    }

    override suspend fun upload(snapshot: String, meta: SnapshotMeta): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = DocumentFile.fromTreeUri(context, treeUri)
                    ?: error("That folder is no longer reachable. Pick it again in Settings.")
                check(dir.canWrite()) {
                    "No write access to that folder any more. Pick it again in Settings."
                }
                val wrapper = JSONObject().apply {
                    put(KEY_TAKEN_AT, meta.takenAtMs)
                    put(KEY_DEVICE_ID, meta.deviceId)
                    put(KEY_SCHEMA, meta.schemaVersion)
                    put(KEY_PAYLOAD, snapshot)
                }.toString()

                // Write the new copy under a temp name first, and only delete the previous
                // backup once it has been written in full. A provider that dies mid-upload
                // therefore leaves the OLD good backup in place rather than a truncated one.
                dir.findFile(TMP_NAME)?.delete()
                val tmp = dir.createFile(MIME, TMP_NAME)
                    ?: error("Could not create a file in that folder")
                context.contentResolver.openOutputStream(tmp.uri, "wt")?.use {
                    it.write(wrapper.toByteArray())
                } ?: error("Could not open that folder for writing")

                dir.findFile(FILE_NAME)?.delete()
                check(tmp.renameTo(FILE_NAME)) { "Could not finalise the backup file" }
            }
        }

    override suspend fun download(): Result<StoredSnapshot?> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching null
            val file = dir.findFile(FILE_NAME) ?: return@runCatching null
            val text = context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@runCatching null
            val wrapper = JSONObject(text)
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
        const val FILE_NAME = "mreddyliftz_backup.json"
        const val TMP_NAME = "mreddyliftz_backup.tmp.json"
        const val MIME = "application/json"
        const val KEY_TAKEN_AT = "taken_at_ms"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SCHEMA = "schema_version"
        const val KEY_PAYLOAD = "payload"
    }
}
