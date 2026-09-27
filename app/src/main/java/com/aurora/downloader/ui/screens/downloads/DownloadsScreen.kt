package com.aurora.downloader.ui.screens.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    app: AuroraApp,
    onOpenBrowser: () -> Unit
) {
    val vm: DownloadsViewModel = viewModel(factory = DownloadsViewModelFactory(app))
    val downloads by vm.downloads.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { msg -> snackbar.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aurora", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (downloads.isEmpty()) "No downloads"
                            else "${downloads.size} download${if (downloads.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenBrowser) {
                        Icon(Icons.Outlined.Language, contentDescription = "Open browser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { dl ->
                    DownloadCard(
                        dl = dl,
                        onPauseResume = { vm.pauseResume(dl) },
                        onCancel = { vm.cancel(dl.id) },
                        onOpen = { vm.openFile(dl) { /* errors surface via events */ } },
                        onDelete = { pendingDelete = dl }
                    )
                }
            }
        }
    }

    pendingDelete?.let { dl ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = { Text("“${dl.fileName}” and its file will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(dl.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↓",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Nothing here yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Open the Browser tab, navigate to a file,\nand Aurora will grab it automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DownloadCard(
    dl: DownloadEntity,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val active = dl.status == DownloadStatus.DOWNLOADING ||
        dl.status == DownloadStatus.PROBING ||
        dl.status == DownloadStatus.VERIFYING ||
        dl.status == DownloadStatus.RETRYING ||
        dl.status == DownloadStatus.QUEUED

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = dl.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (dl.status == DownloadStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = { menuOpen = false; onOpen() }
                            )
                        }
                        if (dl.status == DownloadStatus.ERROR || dl.status == DownloadStatus.CANCELED) {
                            DropdownMenuItem(
                                text = { Text("Retry") },
                                onClick = { menuOpen = false; onPauseResume() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            // progress
            val percent = if (dl.totalBytes > 0) {
                (dl.downloadedBytes.toFloat() / dl.totalBytes).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = if (dl.status == DownloadStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = describe(dl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // pause / resume
                    AnimatedVisibility(visible = active || dl.status == DownloadStatus.PAUSED, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = onPauseResume, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (dl.status == DownloadStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (dl.status == DownloadStatus.PAUSED) "Resume" else "Pause",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // cancel while active
                    AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(onClick = onCancel, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // open when done
                    AnimatedVisibility(visible = dl.status == DownloadStatus.COMPLETED, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(
                                if (dl.published) "Open" else "Open (staged)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun describe(dl: DownloadEntity): String {
    val size = humanReadable(dl.totalBytes)
    val done = humanReadable(dl.downloadedBytes)
    return when (dl.status) {
        DownloadStatus.COMPLETED -> "Completed · $size"
        DownloadStatus.ERROR -> "Error: ${dl.lastError ?: "unknown"}"
        DownloadStatus.PAUSED -> "Paused · $done / $size"
        DownloadStatus.DOWNLOADING -> "$done / $size"
        DownloadStatus.WAITING_NETWORK -> "Waiting for network"
        DownloadStatus.WAITING_SCHEDULE -> "Scheduled"
        DownloadStatus.PROBING -> "Resolving link…"
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.RETRYING -> "Retrying (${dl.retryCount}/${dl.maxRetries})"
        DownloadStatus.VERIFYING -> "Verifying…"
        DownloadStatus.CANCELED -> "Canceled"
        DownloadStatus.CREATED, DownloadStatus.READY -> "Preparing…"
    }
}

private fun humanReadable(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}
