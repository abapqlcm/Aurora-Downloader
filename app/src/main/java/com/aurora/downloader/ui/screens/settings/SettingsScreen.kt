package com.aurora.downloader.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.downloader.AuroraApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AuroraApp) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app))
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingRow(
                title = "Concurrent downloads",
                subtitle = "${settings.maxConcurrentDownloads} at the same time"
            ) {
                Slider(
                    value = settings.maxConcurrentDownloads.toFloat(),
                    onValueChange = { scope.launch { vm.setConcurrent(it.toInt()) } },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }

            SettingRow(
                title = "Parts per download",
                subtitle = "${settings.partsPerDownload} parallel connections per file"
            ) {
                Slider(
                    value = settings.partsPerDownload.toFloat(),
                    onValueChange = { scope.launch { vm.setParts(it.toInt()) } },
                    valueRange = 1f..16f,
                    steps = 14
                )
            }

            SettingRow(
                title = "Wi-Fi only",
                subtitle = "Pause downloads on metered networks"
            ) {
                Switch(
                    checked = settings.wifiOnly,
                    onCheckedChange = { scope.launch { vm.setWifiOnly(it) } }
                )
            }

            SettingRow(
                title = "Speed limit",
                subtitle = if (settings.speedLimitEnabled)
                    "Capped at ${settings.speedLimitKBps} KB/s"
                else "Unlimited"
            ) {
                Switch(
                    checked = settings.speedLimitEnabled,
                    onCheckedChange = { scope.launch { vm.setLimitEnabled(it) } }
                )
            }
            if (settings.speedLimitEnabled) {
                Slider(
                    value = settings.speedLimitKBps.toFloat(),
                    onValueChange = { scope.launch { vm.setLimitKbps(it.toInt()) } },
                    valueRange = 100f..20_000f
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    control: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        control()
    }
}
