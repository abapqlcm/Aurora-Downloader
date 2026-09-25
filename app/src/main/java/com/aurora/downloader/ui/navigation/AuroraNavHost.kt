package com.aurora.downloader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.ui.screens.browser.BrowserScreen
import com.aurora.downloader.ui.screens.downloads.DownloadsScreen
import com.aurora.downloader.ui.screens.settings.SettingsScreen

object Routes {
    const val DOWNLOADS = "downloads"
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
}

@Composable
fun AuroraNavHost(app: AuroraApp) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DOWNLOADS) {
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(app = app, onOpenBrowser = { nav.navigate(Routes.BROWSER) })
        }
        composable(Routes.BROWSER) {
            BrowserScreen(app = app, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(app = app)
        }
    }
}
