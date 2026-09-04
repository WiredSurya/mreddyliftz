package com.mreddy.liftz.data.sync

import android.content.Context
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.json.JsonPort
import com.mreddy.liftz.data.json.LiftzExport
import com.mreddy.liftz.data.prefs.SyncPrefs
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** What the settings screen needs to describe sync without triggering it. */
data class SyncStatus(
    val backendName: String,
    val backendAvailable: Boolean,
    val lastBackupAtMs: Long?,
    val lastRestoreAtMs: Long?,
    val lastError: String?,
    val isCloud: Boolean
)

/**
 * Backup and restore, independent of where the bytes go.
 *
 * WHY SNAPSHOTS RATHER THAN PER-RECORD SYNC:
 * This is a single-person app. A whole-database snapshot with last-write-wins is the honest fit —
 * it is small (the export is a few KB), it reuses the JSON format that already exists and is
 * already tested, and it has no merge semantics to get subtly wrong. True multi-device merging
 * would need an `updatedAt` on every row, a change log, and a conflict policy per table; that is
 * a much larger commitment and would be the wrong thing to rush. If it is ever wanted, this class
 * is the seam to change and the [SyncBackend] contract does not move.
 *
 * RESTORE IS DELIBERATELY NOT AUTOMATIC. Nothing here silently overwrites local data. A restore
 * replaces the routine with the snapshot's, which is destructive if the phone has newer work on
 * it, so it is always something the user asks for explicitly after being told what will happen.
 */
class SyncManager(
    private val context: Context,
    private val db: LiftzDatabase,
    private val prefs: SyncPrefs,
    private val backend: SyncBackend
) {

    val status: Flow<SyncStatus> = prefs.status(backend.displayName, backend is LocalFileBackend)

    /** Take a snapshot of everything and hand it to the backend. */
    suspend fun backUpNow(): Result<SnapshotMeta> {
        if (!backend.isAvailable()) {
            val msg = "${backend.displayName} is not available right now"
            prefs.recordError(msg)
            return Result.failure(IllegalStateException(msg))
        }
        return runCatching {
            val json = JsonPort.exportToString(db, includeHistory = true)
            val meta = SnapshotMeta(
                takenAtMs = System.currentTimeMillis(),
                deviceId = deviceId(),
                schemaVersion = LiftzExport.SCHEMA_VERSION,
                sizeBytes = json.length
            )
            backend.upload(json, meta).getOrThrow()
            prefs.recordBackup(meta.takenAtMs)
            meta
        }.onFailure { prefs.recordError(it.message ?: "Backup failed") }
    }

    /** Peek at what is stored without applying it, so the UI can confirm before overwriting. */
    suspend fun peek(): Result<SnapshotMeta?> = runCatching {
        backend.download().getOrThrow()?.meta
    }

    /**
     * Replace local data with the stored snapshot.
     *
     * OVERWRITE, not merge: a restore is for "this phone is wrong, make it match the backup".
     * Merging is available separately through the normal JSON import if that is what is wanted.
     */
    suspend fun restoreNow(): Result<Unit> = runCatching {
        val stored = backend.download().getOrThrow()
            ?: error("There is no backup stored yet")
        require(stored.meta.schemaVersion <= LiftzExport.SCHEMA_VERSION) {
            "That backup was written by a newer version of the app (schema " +
                "${stored.meta.schemaVersion}). Update the app before restoring it."
        }
        val parsed = JsonPort.parse(stored.json)
        JsonPort.import(db, parsed, JsonPort.ImportMode.OVERWRITE)
        prefs.recordRestore(System.currentTimeMillis())
        Unit
    }.onFailure { prefs.recordError(it.message ?: "Restore failed") }

    /** Stable per-install id. Not a user identifier: it is random and dies with the install. */
    private suspend fun deviceId(): String =
        prefs.deviceId() ?: UUID.randomUUID().toString().also { prefs.setDeviceId(it) }
}
