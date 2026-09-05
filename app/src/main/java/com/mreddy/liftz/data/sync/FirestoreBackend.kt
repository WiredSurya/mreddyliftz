package com.mreddy.liftz.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.mreddy.liftz.data.auth.AuthManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Snapshot storage in the signed-in user's own Firestore document.
 *
 * Layout is one document per account, overwritten each backup:
 *
 *     users/{uid}/snapshots/latest
 *
 * There is no history of previous snapshots, and that is a decision rather than an omission.
 * [SyncManager] already documents why this app syncs whole snapshots instead of merging rows;
 * keeping N old copies would multiply storage and invite a "restore which one?" UI that a
 * single-user app does not need. The device-local folder backup is the answer for versioning —
 * point it at a Drive folder and Drive keeps the versions.
 *
 * SECURITY: the `users/{uid}` prefix is not cosmetic. The Firestore rules in
 * `firestore.rules` allow a signed-in account to touch only paths whose {uid} equals its own,
 * so one account physically cannot read another's data. Changing this path means changing those
 * rules to match.
 */
class FirestoreBackend(
    private val auth: AuthManager,
    private val isOnline: suspend () -> Boolean
) : SyncBackend {

    override val displayName: String = "your Google account"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private fun docOrNull() = auth.uid?.let {
        db.collection("users").document(it).collection("snapshots").document("latest")
    }

    override suspend fun isAvailable(): Boolean = auth.isSignedIn && isOnline()

    override suspend fun upload(snapshot: String, meta: SnapshotMeta): Result<Unit> = runCatching {
        val doc = docOrNull() ?: error("You are not signed in")

        // A Firestore document is capped at 1 MiB including field names and overhead. A snapshot
        // that large would fail with an opaque server error, so refuse it here with a reason.
        // At current export sizes (single-digit KB) this is a long way off; it exists so that if
        // it is ever hit, the message says what happened instead of "PERMISSION_DENIED"-adjacent
        // noise.
        require(snapshot.length < MAX_DOC_BYTES) {
            "This backup is too large for cloud sync (${snapshot.length / 1024} KB). " +
                "Use a backup folder instead."
        }

        val payload = mapOf(
            "json" to snapshot,
            "takenAtMs" to meta.takenAtMs,
            "deviceId" to meta.deviceId,
            "schemaVersion" to meta.schemaVersion,
            "sizeBytes" to meta.sizeBytes
        )

        // Firestore's write Task completes on SERVER acknowledgement, not on the local cache
        // write — so offline it would hang rather than fail. isAvailable() already checks
        // connectivity, but connectivity can drop between that check and this call, and a sync
        // button that spins forever is worse than one that reports a timeout.
        withTimeout(TIMEOUT_MS) { doc.set(payload).await() }
        Unit
    }.recoverCatching {
        if (it is TimeoutCancellationException) {
            error("Cloud sync timed out. Your data is safe on this phone — try again later.")
        }
        throw it
    }

    override suspend fun download(): Result<StoredSnapshot?> = runCatching {
        val doc = docOrNull() ?: error("You are not signed in")
        val snap = withTimeout(TIMEOUT_MS) { doc.get().await() }
        if (!snap.exists()) return@runCatching null

        val json = snap.getString("json") ?: return@runCatching null
        StoredSnapshot(
            json = json,
            meta = SnapshotMeta(
                takenAtMs = snap.getLong("takenAtMs") ?: 0L,
                deviceId = snap.getString("deviceId").orEmpty(),
                schemaVersion = (snap.getLong("schemaVersion") ?: 1L).toInt(),
                sizeBytes = (snap.getLong("sizeBytes") ?: json.length.toLong()).toInt()
            )
        )
    }.recoverCatching {
        if (it is TimeoutCancellationException) {
            error("Couldn't reach the cloud. Check your connection and try again.")
        }
        throw it
    }

    /**
     * Permanently remove this account's stored snapshot.
     *
     * Exists so the privacy policy can answer "how do I delete my cloud data" with a button
     * rather than an email address. Local data is untouched — this deletes the copy in the
     * cloud and nothing else.
     */
    suspend fun deleteStored(): Result<Unit> = runCatching {
        val doc = docOrNull() ?: error("You are not signed in")
        withTimeout(TIMEOUT_MS) { doc.delete().await() }
        Unit
    }

    private companion object {
        const val MAX_DOC_BYTES = 900_000     // 1 MiB hard limit, with headroom for overhead
        const val TIMEOUT_MS = 30_000L
    }
}
