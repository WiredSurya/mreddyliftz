package com.mreddy.liftz

import android.app.Application
import com.mreddy.liftz.data.auth.AuthManager
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.net.Connectivity
import com.mreddy.liftz.data.prefs.ProfilePrefs
import com.mreddy.liftz.data.prefs.SyncPrefs
import com.mreddy.liftz.data.prefs.UiPrefs
import com.mreddy.liftz.data.sync.SyncManager
import com.mreddy.liftz.data.update.UpdateChecker
import com.mreddy.liftz.data.repo.LiftzRepository

/**
 * No DI framework on purpose. One app, one database, one repository, constructed here and read
 * from the ViewModels. Easy to reason about for a first native Android project.
 */
class LiftzApp : Application() {

    val database: LiftzDatabase by lazy { LiftzDatabase.get(this) }
    val repository: LiftzRepository by lazy { LiftzRepository(database) }
    val uiPrefs: UiPrefs by lazy { UiPrefs(this) }
    val syncPrefs: SyncPrefs by lazy { SyncPrefs(this) }
    val profilePrefs: ProfilePrefs by lazy { ProfilePrefs(this) }
    val connectivity: Connectivity by lazy { Connectivity(this) }
    val auth: AuthManager by lazy { AuthManager(this) }
    val updateChecker: UpdateChecker by lazy { UpdateChecker(this) }

    /**
     * Backup/restore. Picks its own backend: the signed-in account's Firestore document when
     * cloud sync is on, the chosen folder or on-device storage otherwise.
     */
    val syncManager: SyncManager by lazy {
        SyncManager(this, database, syncPrefs, auth, connectivity)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LiftzApp
            private set

        fun repo(): LiftzRepository = instance.repository
        fun prefs(): UiPrefs = instance.uiPrefs
        fun syncPrefs(): SyncPrefs = instance.syncPrefs
        fun profilePrefs(): ProfilePrefs = instance.profilePrefs
        fun connectivity(): Connectivity = instance.connectivity
        fun auth(): AuthManager = instance.auth
        fun updates(): UpdateChecker = instance.updateChecker
        fun sync(): SyncManager = instance.syncManager
    }
}
