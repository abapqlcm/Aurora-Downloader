package com.aurora.downloader.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("Downloads")
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Concurrent downloads", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${settings.maxConcurrentDownloads} at the same time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.maxConcurrentDownloads.toFloat(),
                        onValueChange = { scope.launch { vm.setConcurrent(it.toInt()) } },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Parts per download", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${settings.partsPerDownload} parallel connections per file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.partsPerDownload.toFloat(),
                        onValueChange = { scope.launch { vm.setParts(it.toInt()) } },
                        valueRange = 1f..16f,
                        steps = 14
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("Network")

            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Wi-Fi only", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pause downloads on metered networks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = settings.wifiOnly,
                        onCheckedChange = { scope.launch { vm.setWifiOnly(it) } }
                    )
                }
            }
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Speed limit", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.speedLimitEnabled)
                            "Capped at ${settings.speedLimitKBps} KB/s"
                        else "Unlimited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = settings.speedLimitEnabled,
                        onCheckedChange = { scope.launch { vm.setLimitEnabled(it) } }
                    )
                    if (settings.speedLimitEnabled) {
                        Slider(
                            value = settings.speedLimitKBps.toFloat(),
                            onValueChange = { scope.launch { vm.setLimitKbps(it.toInt()) } },
                            valueRange = 100f..20_000f
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("About")
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Aurora Downloader", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Free and open-source · No ads · No tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
