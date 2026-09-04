package com.mreddy.liftz

import android.app.Application
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.net.Connectivity
import com.mreddy.liftz.data.prefs.SyncPrefs
import com.mreddy.liftz.data.prefs.UiPrefs
import com.mreddy.liftz.data.sync.SyncManager
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
    val connectivity: Connectivity by lazy { Connectivity(this) }

    /**
     * Backup/restore. The backend is the only thing that changes when a real cloud target is
     * wired in — see docs/CLOUD_SYNC.md for exactly what that swap involves.
     */
    val syncManager: SyncManager by lazy {
        SyncManager(this, database, syncPrefs)
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
        fun connectivity(): Connectivity = instance.connectivity
        fun sync(): SyncManager = instance.syncManager
    }
}
