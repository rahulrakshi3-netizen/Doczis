package com.doczis.app

import android.app.Application
import com.doczis.app.data.db.AppDatabase
import com.doczis.app.data.repository.FileRepository
import com.doczis.app.util.NotificationHelper
import com.doczis.app.util.SettingsManager

class DoczisApp : Application() {

    lateinit var settingsManager: SettingsManager
        private set

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val fileRepository: FileRepository by lazy { FileRepository(database.fileDao()) }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        settingsManager.isDarkMode = settingsManager.isDarkMode
        NotificationHelper.createChannel(this)
    }
}
