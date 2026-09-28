package com.aurora.downloader

import android.app.Application
import com.aurora.downloader.data.database.AuroraDatabase
import com.aurora.downloader.data.datastore.SettingsRepository
import com.aurora.downloader.download.engine.DownloadEngine
import com.aurora.downloader.download.transport.OkHttpFactory
import com.aurora.downloader.download.persistence.DownloadRepository
import com.aurora.downloader.platform.notification.NotificationController
import com.aurora.downloader.ui.screens.onboarding.NotificationPrefs

class AuroraApp : Application() {

    val database by lazy { AuroraDatabase.get(this) }
    val settings by lazy { SettingsRepository(this) }

    val okHttp by lazy { OkHttpFactory.create() }

    val notificationPrefs by lazy { NotificationPrefs(this) }

    val downloadRepository by lazy {
        DownloadRepository(database.downloadDao(), database.partDao())
    }

    val downloadEngine by lazy {
        DownloadEngine(
            app = this,
            repository = downloadRepository,
            httpClient = okHttp,
            settings = settings,
            scheduleDao = database.scheduleDao()
        )
    }

    private val notificationController by lazy { NotificationController(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Mirror engine state into the notification shade for the whole
        // process lifetime; it no-ops when there is nothing to show.
        notificationController.start()
        // Evaluate scheduled downloads (Download later / Wi-Fi / Charging).
        downloadEngine.scheduler?.start()
    }

    companion object {
        @Volatile
        private var instance: AuroraApp? = null

        fun get(): AuroraApp =
            instance ?: error("AuroraApp not initialized")
    }
}
