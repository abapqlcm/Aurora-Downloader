package com.aurora.downloader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.ui.navigation.AuroraNavHost
import com.aurora.downloader.ui.screens.onboarding.NotificationPermissionGate
import com.aurora.downloader.ui.theme.AuroraTheme

/**
 * The single activity. Everything else is Compose destinations.
 *
 * Notification taps deep-link here with a `download_id` extra, which
 * [AuroraNavHost] uses to open the download's Details screen directly.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuroraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as AuroraApp
                    NotificationPermissionGate(app = app) {
                        AuroraNavHost(
                            app = app,
                            initialDownloadId = intentDetailsId()
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Extracts the download id a notification tap asked us to open. */
    private fun intentDetailsId(): Long? =
        intent?.getLongExtra("download_id", -1L)?.takeIf { it != -1L }
}
