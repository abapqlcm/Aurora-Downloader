package com.aurora.downloader.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadStatus
import com.aurora.downloader.domain.model.PartEntity
import com.aurora.downloader.domain.model.PartStatus
import com.aurora.downloader.download.engine.SpeedTracker

/**
 * Everything the engine knows about one download: file facts, network facts,
 * performance, and a per-segment progress map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailsScreen(
    app: AuroraApp,
    downloadId: Long,
    onBack: () -> Unit
) {
    val vm: DownloadDetailsViewModel = viewModel(
        factory = DownloadDetailsViewModelFactory(app, downloadId)
    )
    val download by vm.download.collectAsState()
    val parts by vm.parts.collectAsState()
    val live by vm.liveProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        download?.fileName ?: "Download",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val dl = download ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: progress + status
            HeaderCard(dl)

            // Actions
            ActionRow(
                status = dl.status,
                onPause = { vm.pause() },
                onResume = { vm.resume() },
                onCancel = { vm.cancel() },
                onRestart = { vm.restart() },
                onOpen = { vm.open(app) },
                onDelete = { vm.delete() }
            )

            Section("FILE") {
                InfoRow("Filename", dl.fileName)
                InfoRow("Size", humanBytes(dl.totalBytes))
                dl.mimeType?.let { InfoRow("Type", it) }
                InfoRow("Extension", dl.fileName.substringAfterLast('.', "—"))
                InfoRow("Location", dl.contentUri ?: dl.savePath)
            }

            Section("NETWORK") {
                InfoRow("URL", dl.url)
                dl.finalUrl?.let { InfoRow("Final URL", it) }
                dl.etag?.let { InfoRow("ETag", it) }
                InfoRow(
                    "Range support",
                    if (dl.supportsRange) "✓ Supported" else "Not supported"
                )
                dl.lastError?.let { InfoRow("Last error", it) }
            }

            Section("PERFORMANCE") {
                InfoRow("Downloaded", SpeedTracker.formatBytes(dl.downloadedBytes))
                InfoRow("Connections", dl.partCount.toString())
                InfoRow("Retries", "${dl.retryCount} / ${dl.maxRetries}")
                // Live rate; only meaningful while bytes are actually moving.
                val snapshot = live
                val rate = snapshot?.speedBps?.takeIf { it > 0 }
                if (rate != null) {
                    InfoRow("Current speed", SpeedTracker.format(rate))
                }
                if (snapshot?.etaSeconds != null) {
                    InfoRow("ETA", SpeedTracker.formatEta(snapshot.etaSeconds))
                }
            }

            // Segment visualization
            if (parts.isNotEmpty()) {
                Section("SEGMENTS") {
                    val active = parts.count { it.status == PartStatus.RUNNING }
                    val done = parts.count { it.status == PartStatus.DONE }
                    val failed = parts.count { it.status == PartStatus.FAILED }
                    Text(
                        "$done completed · $active active · $failed failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    parts.forEach { part -> SegmentBar(part, dl.totalBytes) }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderCard(dl: com.aurora.downloader.domain.model.DownloadEntity) {
    val percent = if (dl.totalBytes > 0)
        (dl.downloadedBytes.toFloat() / dl.totalBytes).coerceIn(0f, 1f) else 0f
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                describeStatus(dl),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (dl.totalBytes > 0) {
                Text(
                    "${humanBytes(dl.downloadedBytes)} / ${humanBytes(dl.totalBytes)} " +
                        "· ${(percent * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${humanBytes(dl.downloadedBytes)} downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    status: DownloadStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        val active = status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.PROBING ||
            status == DownloadStatus.VERIFYING ||
            status == DownloadStatus.RETRYING ||
            status == DownloadStatus.QUEUED

        when {
            active -> {
                ChipButton("Pause", primary = true, weight = 1f, onClick = onPause)
                ChipButton("Cancel", primary = false, weight = 1f, onClick = onCancel)
            }
            status == DownloadStatus.PAUSED -> {
                ChipButton("Resume", primary = true, weight = 1f, onClick = onResume)
                ChipButton("Cancel", primary = false, weight = 1f, onClick = onCancel)
            }
            status == DownloadStatus.COMPLETED -> {
                ChipButton("Open", primary = true, weight = 1f, onClick = onOpen)
                ChipButton("Delete", primary = false, weight = 1f, onClick = onDelete)
            }
            status == DownloadStatus.ERROR -> {
                ChipButton("Retry", primary = true, weight = 1f, onClick = onRestart)
                ChipButton("Delete", primary = false, weight = 1f, onClick = onDelete)
            }
            status == DownloadStatus.CANCELED -> {
                ChipButton("Restart", primary = true, weight = 1f, onClick = onRestart)
                ChipButton("Delete", primary = false, weight = 1f, onClick = onDelete)
            }
            else -> Unit
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    primary: Boolean,
    weight: Float,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .then(Modifier)
            .fillMaxWidth(weight)
            .clip(shape)
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (primary) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Card { Column(modifier = Modifier.padding(16.dp)) { content() } }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentBar(part: PartEntity, totalBytes: Long) {
    val span = (part.end - part.start + 1).coerceAtLeast(1)
    val done = (part.written.coerceAtLeast(0).toFloat() / span).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            "Part ${part.partIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(done)
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        when (part.status) {
                            PartStatus.DONE -> MaterialTheme.colorScheme.primary
                            PartStatus.RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            PartStatus.FAILED -> MaterialTheme.colorScheme.error
                            PartStatus.PENDING -> MaterialTheme.colorScheme.outline
                        }
                    )
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${(done * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun describeStatus(dl: com.aurora.downloader.domain.model.DownloadEntity): String =
    when (dl.status) {
        DownloadStatus.CREATED, DownloadStatus.READY -> "Preparing"
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.PROBING -> "Resolving link"
        DownloadStatus.DOWNLOADING -> "Downloading"
        DownloadStatus.PAUSED -> "Paused"
        DownloadStatus.WAITING_NETWORK -> "Waiting for Wi-Fi"
        DownloadStatus.WAITING_SCHEDULE -> "Scheduled"
        DownloadStatus.RETRYING -> "Retrying (${dl.retryCount}/${dl.maxRetries})"
        DownloadStatus.VERIFYING -> "Verifying"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.ERROR -> "Failed"
        DownloadStatus.CANCELED -> "Cancelled"
    }

private fun humanBytes(bytes: Long): String = SpeedTracker.formatBytes(bytes)
