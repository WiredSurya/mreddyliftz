package com.mreddy.liftz.data.sync

import android.content.Context
import android.net.Uri
import com.mreddy.liftz.data.auth.AuthManager
import com.mreddy.liftz.data.auth.AuthState
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.net.Connectivity
import com.mreddy.liftz.data.json.JsonPort
import com.mreddy.liftz.data.json.LiftzExport
import com.mreddy.liftz.data.prefs.SyncPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

/** What the settings screen needs to describe sync without triggering it. */
data class SyncStatus(
    val backendName: String,
    val backendAvailable: Boolean,
    val lastBackupAtMs: Long?,
    val lastRestoreAtMs: Long?,
    val lastError: String?,
    /** SAF tree URI of the chosen folder, null when backing up on-device. */
    val folderUri: String?,
    val isCloud: Boolean,
    /** The signed-in Google account, or null when signed out. Display only. */
    val accountEmail: String? = null
)

/**
 * What happened during the automatic sync at app start.
 *
 * This is a return value rather than a silent side effect because two of these outcomes need to
 * be visible: [Adopted] changed the user's data, and [RemoteIsNewer] is a decision only they can
 * make.
 */
sealed interface LaunchSync {
    /** Signed out, cloud sync off, or nothing to do. */
    data object Skipped : LaunchSync
    /** Signed in but no usable connection. Everything still works locally. */
    data object Offline : LaunchSync
    /** Local work was pushed up. */
    data class BackedUp(val atMs: Long) : LaunchSync
    /** A blank install pulled down existing history — the "new phone" case. */
    data class Adopted(val fromDeviceId: String, val takenAtMs: Long) : LaunchSync
    /**
     * The cloud has newer data AND this phone already has its own. Deliberately NOT resolved
     * automatically: whichever side loses, real training history disappears, so the user is
     * asked instead. See the restore note on [SyncManager].
     */
    data class RemoteIsNewer(val takenAtMs: Long, val fromDeviceId: String) : LaunchSync
    data class Failed(val message: String) : LaunchSync
}

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
    private val auth: AuthManager,
    private val connectivity: Connectivity
) {

    /**
     * Resolved per call rather than held, so choosing a folder in Settings takes effect on the
     * very next backup without restarting anything.
     *
     * Falls back to on-device storage when no folder has been picked, so backup always works —
     * there is no state where the button is present but does nothing.
     */
    private suspend fun backend(): SyncBackend {
        // Signing in is the opt-in for cloud sync, so a signed-in account takes precedence over a
        // chosen folder. The folder is not forgotten — turning cloud sync off in Settings falls
        // straight back to it, which is why this is resolved per call rather than held.
        if (auth.isSignedIn && prefs.cloudSyncEnabledOnce()) {
            return FirestoreBackend(auth) { connectivity.isOnline().first() }
        }
        val folder = prefs.backupFolderOnce()
        return if (folder != null) {
            FolderBackend(context, Uri.parse(folder))
        } else {
            LocalFileBackend(context)
        }
    }

    val status: Flow<SyncStatus> =
        combine(prefs.statusFlow(), auth.state(), prefs.cloudSyncEnabled) { base, who, cloudOn ->
            if (who is AuthState.SignedIn && cloudOn) {
                base.copy(
                    backendName = "your Google account",
                    isCloud = true,
                    accountEmail = who.email
                )
            } else {
                base
            }
        }

    /** Take a snapshot of everything and hand it to the backend. */
    suspend fun backUpNow(): Result<SnapshotMeta> {
        val backend = backend()
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
        backend().download().getOrThrow()?.meta
    }

    /**
     * Replace local data with the stored snapshot.
     *
     * OVERWRITE, not merge: a restore is for "this phone is wrong, make it match the backup".
     * Merging is available separately through the normal JSON import if that is what is wanted.
     */
    suspend fun restoreNow(): Result<Unit> = runCatching {
        val stored = backend().download().getOrThrow()
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

    /**
     * Automatic sync at app start. This is what makes "sign in on a new phone and pick up where
     * you left off" actually happen.
     *
     * THE RULE THAT KEEPS THIS SAFE: an automatic restore only ever happens into a BLANK app.
     * If this device has training history of its own and the cloud copy is newer, the two cannot
     * both be kept — a snapshot restore is a wholesale replacement — so it stops and reports
     * [LaunchSync.RemoteIsNewer] for the user to decide. Silently overwriting a logged session
     * because a different phone synced later would be the single worst bug this app could have.
     *
     * The blank case has no such dilemma: there is nothing to lose, so it just works.
     */
    suspend fun syncOnLaunch(): LaunchSync {
        if (!auth.isSignedIn || !prefs.cloudSyncEnabledOnce()) return LaunchSync.Skipped
        if (!connectivity.isOnline().first()) return LaunchSync.Offline

        val backend = FirestoreBackend(auth) { true }   // connectivity already confirmed above

        val remote = backend.download().getOrElse {
            val msg = it.message ?: "Couldn't reach the cloud"
            prefs.recordError(msg)
            return LaunchSync.Failed(msg)
        }

        // Nothing up there yet: this account's first ever sync. Push what we have.
        if (remote == null) {
            return backUpNow().fold(
                onSuccess = { LaunchSync.BackedUp(it.takenAtMs) },
                onFailure = { LaunchSync.Failed(it.message ?: "Backup failed") }
            )
        }

        if (isDatabaseBlank()) {
            return runCatching {
                require(remote.meta.schemaVersion <= LiftzExport.SCHEMA_VERSION) {
                    "That backup was written by a newer version of the app. Update first."
                }
                JsonPort.import(db, JsonPort.parse(remote.json), JsonPort.ImportMode.OVERWRITE)
                prefs.recordRestore(System.currentTimeMillis())
                LaunchSync.Adopted(remote.meta.deviceId, remote.meta.takenAtMs)
            }.getOrElse {
                val msg = it.message ?: "Couldn't load your cloud backup"
                prefs.recordError(msg)
                LaunchSync.Failed(msg)
            }
        }

        // This device has data. Only push if our own last backup is at least as recent as the
        // stored one; otherwise another device has been busy and the user has to choose.
        val lastLocal = prefs.statusFlow().first().lastBackupAtMs ?: 0L
        return if (remote.meta.takenAtMs > lastLocal) {
            LaunchSync.RemoteIsNewer(remote.meta.takenAtMs, remote.meta.deviceId)
        } else {
            backUpNow().fold(
                onSuccess = { LaunchSync.BackedUp(it.takenAtMs) },
                onFailure = { LaunchSync.Failed(it.message ?: "Backup failed") }
            )
        }
    }

    /**
     * "Has this install got anything worth protecting yet?"
     *
     * No exercises and no sessions means a fresh install or a wiped one — the blank slate every
     * new install starts on. Goals and increments are excluded on purpose: they are seeded with
     * defaults on first run, so requiring them to be empty would mean nothing is ever blank.
     */
    private suspend fun isDatabaseBlank(): Boolean =
        db.exerciseDao().getAll().isEmpty() && db.sessionDao().countAll() == 0

    /**
     * Delete the cloud copy, leaving this phone alone.
     *
     * Separate from sign-out on purpose: signing out should not destroy a backup, and deleting a
     * backup should not require signing out. Conflating them would make one of the two
     * impossible to do without the other.
     */
    suspend fun deleteCloudCopy(): Result<Unit> {
        if (!auth.isSignedIn) return Result.failure(IllegalStateException("You are not signed in"))
        return FirestoreBackend(auth) { connectivity.isOnline().first() }
            .deleteStored()
            .onFailure { prefs.recordError(it.message ?: "Couldn't delete the cloud copy") }
    }

    /** Stable per-install id. Not a user identifier: it is random and dies with the install. */
    private suspend fun deviceId(): String =
        prefs.deviceId() ?: UUID.randomUUID().toString().also { prefs.setDeviceId(it) }
}
