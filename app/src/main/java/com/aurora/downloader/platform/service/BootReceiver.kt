package com.aurora.downloader.platform.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Hand off to WorkManager: a BroadcastReceiver must not do work,
            // and the engine's own queue picks up resumable downloads.
            DownloadService.start(context)
        }
    }
}
