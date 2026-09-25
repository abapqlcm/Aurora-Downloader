package com.aurora.downloader

import android.app.Application
import com.aurora.downloader.data.database.AuroraDatabase
import com.aurora.downloader.data.datastore.SettingsRepository
import com.aurora.downloader.download.engine.DownloadEngine
import com.aurora.downloader.download.transport.OkHttpFactory
import com.aurora.downloader.download.persistence.DownloadRepository

class AuroraApp : Application() {

    val database by lazy { AuroraDatabase.get(this) }
    val settings by lazy { SettingsRepository(this) }

    val okHttp by lazy { OkHttpFactory.create() }

    val downloadRepository by lazy {
        DownloadRepository(database.downloadDao(), database.partDao())
    }

    val downloadEngine by lazy {
        DownloadEngine(
            app = this,
            repository = downloadRepository,
            httpClient = okHttp,
            settings = settings
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: AuroraApp? = null

        fun get(): AuroraApp =
            instance ?: error("AuroraApp not initialized")
    }
}
