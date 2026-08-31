package com.mreddy.liftz

import android.app.Application
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.prefs.UiPrefs
import com.mreddy.liftz.data.repo.LiftzRepository

/**
 * No DI framework on purpose. One app, one database, one repository, constructed here and read
 * from the ViewModels. Easy to reason about for a first native Android project.
 */
class LiftzApp : Application() {

    val database: LiftzDatabase by lazy { LiftzDatabase.get(this) }
    val repository: LiftzRepository by lazy { LiftzRepository(database) }
    val uiPrefs: UiPrefs by lazy { UiPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LiftzApp
            private set

        fun repo(): LiftzRepository = instance.repository
        fun prefs(): UiPrefs = instance.uiPrefs
    }
}
