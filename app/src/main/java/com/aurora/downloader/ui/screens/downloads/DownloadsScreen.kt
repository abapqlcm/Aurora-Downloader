package com.aurora.downloader.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    IconButton(onClick = onOpenBrowser) {
                        Icon(Icons.Filled.Language, contentDescription = "Open browser")
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { dl ->
                    DownloadRow(dl)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("No downloads yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Open the browser tab, navigate to a file, and Aurora will offer to download it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadRow(dl: DownloadEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = dl.fileName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val percent = if (dl.totalBytes > 0) {
            (dl.downloadedBytes.toFloat() / dl.totalBytes).coerceIn(0f, 1f)
        } else 0f

        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = describe(dl),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
