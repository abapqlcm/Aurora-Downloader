package com.aurora.downloader.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aurora.downloader.AuroraApp
import kotlinx.coroutines.delay
import com.aurora.downloader.ui.screens.browser.BrowserScreen
import com.aurora.downloader.ui.screens.downloads.DownloadsScreen
import com.aurora.downloader.ui.screens.settings.SettingsScreen

object Routes {
    const val SPLASH = "splash"
    const val DOWNLOADS = "downloads"
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DOWNLOADS, "Downloads", Icons.Outlined.Download),
    Tab(Routes.BROWSER, "Browser", Icons.Outlined.Language),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun AuroraNavHost(app: AuroraApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val showBars = current?.route != Routes.SPLASH

    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = showBars, enter = fadeIn(), exit = fadeOut()) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0F1013)
                ) {
                    tabs.forEach { tab ->
                        val selected = current?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.ui.graphics.Color(0xFFF5B800),
                                selectedTextColor = androidx.compose.ui.graphics.Color(0xFFF5B800),
                                indicatorColor = androidx.compose.ui.graphics.Color(0xFF1A1B20)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SPLASH) {
                com.aurora.downloader.ui.screens.splash.AuroraSplash()
                LaunchedEffect(Unit) {
                    delay(700)
                    nav.navigate(Routes.DOWNLOADS) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            }
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
}
