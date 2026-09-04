package com.mreddy.liftz.data.sync

/**
 * Where a backup actually goes.
 *
 * Deliberately tiny and storage-agnostic. Everything hard about syncing — deciding what to send,
 * detecting that the local copy changed, resolving which side wins, reporting state to the UI —
 * lives in [SyncManager] and is fully testable without a network. This interface is the only part
 * that has to change to point at Firebase, S3, WebDAV or anything else, and it is four functions.
 *
 * That split is intentional: the Firebase project, its OAuth client and `google-services.json` all
 * require the account holder, so the parts that need a human are isolated behind here rather than
 * threaded through the app.
 */
interface SyncBackend {

    /** Human-readable name for the settings screen, e.g. "This device" or "Google Drive". */
    val displayName: String

    /** Whether this backend is usable right now (signed in, configured, reachable). */
    suspend fun isAvailable(): Boolean

    /** Push a full snapshot. [snapshot] is the same JSON the export screen produces. */
    suspend fun upload(snapshot: String, meta: SnapshotMeta): Result<Unit>

    /** Pull the most recent snapshot, or null if the remote has nothing yet. */
    suspend fun download(): Result<StoredSnapshot?>
}

/** Describes a snapshot without containing it, so the UI can show what is stored without a fetch. */
data class SnapshotMeta(
    /** Epoch millis the snapshot was taken on the device that made it. */
    val takenAtMs: Long,
    /** Stable per-install id, so a restore can tell you which phone the backup came from. */
    val deviceId: String,
    /** Portable JSON schema version, so a future app can refuse a snapshot it cannot read. */
    val schemaVersion: Int,
    /** Rough size indicator for the UI. */
    val sizeBytes: Int
)

data class StoredSnapshot(val json: String, val meta: SnapshotMeta)
