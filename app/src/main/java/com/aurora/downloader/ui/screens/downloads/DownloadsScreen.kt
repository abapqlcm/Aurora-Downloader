package com.aurora.downloader.ui.screens.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus

private enum class DownloadTab(val label: String) {
    ALL("All"), DOWNLOADING("Downloading"), QUEUED("Queued"),
    PAUSED("Paused"), COMPLETED("Completed"), FAILED("Failed")
}

private enum class SortMode(val label: String) {
    NEWEST("Newest"), OLDEST("Oldest"), NAME("Name"), SIZE("Size"), PROGRESS("Progress")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    app: AuroraApp,
    onOpenBrowser: () -> Unit,
    onAddDownload: () -> Unit,
    onOpenDetails: (Long) -> Unit
) {
    val vm: DownloadsViewModel = viewModel(factory = DownloadsViewModelFactory(app))
    val downloads by vm.downloads.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var tab by remember { mutableStateOf(DownloadTab.ALL) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.events.collect { msg -> snackbar.showSnackbar(msg) }
    }

    // Tab + search filtering, applied to the engine's live state.
    val visible = remember(downloads, tab, query) {
        downloads.filter { dl ->
            val matchesTab = when (tab) {
                DownloadTab.ALL -> true
                DownloadTab.DOWNLOADING -> dl.status == DownloadStatus.DOWNLOADING ||
                    dl.status == DownloadStatus.PROBING ||
                    dl.status == DownloadStatus.VERIFYING ||
                    dl.status == DownloadStatus.RETRYING
                DownloadTab.QUEUED -> dl.status == DownloadStatus.QUEUED ||
                    dl.status == DownloadStatus.READY ||
                    dl.status == DownloadStatus.CREATED ||
                    dl.status == DownloadStatus.WAITING_NETWORK ||
                    dl.status == DownloadStatus.WAITING_SCHEDULE
                DownloadTab.PAUSED -> dl.status == DownloadStatus.PAUSED
                DownloadTab.COMPLETED -> dl.status == DownloadStatus.COMPLETED
                DownloadTab.FAILED -> dl.status == DownloadStatus.ERROR ||
                    dl.status == DownloadStatus.CANCELED
            }
            matchesTab && (query.isBlank() ||
                dl.fileName.contains(query, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aurora", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (downloads.isEmpty()) "Nothing downloading"
                            else "${downloads.count { it.status != DownloadStatus.COMPLETED }} active · ${downloads.size} total",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDownload,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add download")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search downloads") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp,
                divider = {}
            ) {
                DownloadTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) }
                    )
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    Modifier.fillMaxSize(),
                    onOpenBrowser = onOpenBrowser,
                    onAddDownload = onAddDownload
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { dl ->
                        DownloadCard(
                            dl = dl,
                            onPauseResume = { vm.pauseResume(dl) },
                            onCancel = { vm.cancel(dl.id) },
                            onOpen = { vm.openFile(dl) { } },
                            onDelete = { pendingDelete = dl },
                            onOpenDetails = { onOpenDetails(dl.id) }
                        )
                    }
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
private fun EmptyState(
    modifier: Modifier,
    onOpenBrowser: () -> Unit,
    onAddDownload: () -> Unit
) {
    Column(
        modifier = modifier.padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gold ring + arrow — the Aurora signature
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↓",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Nothing here yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a link, or open the Browser tab and let Aurora grab the file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        onClick = onAddDownload,
                        role = androidx.compose.ui.semantics.Role.Button
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Add a link",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        onClick = onOpenBrowser,
                        role = androidx.compose.ui.semantics.Role.Button
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Browse the web",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(
    dl: DownloadEntity,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetails: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val active = dl.status == DownloadStatus.DOWNLOADING ||
        dl.status == DownloadStatus.PROBING ||
        dl.status == DownloadStatus.VERIFYING ||
        dl.status == DownloadStatus.RETRYING ||
        dl.status == DownloadStatus.QUEUED

    val percent = if (dl.totalBytes > 0) {
        (dl.downloadedBytes.toFloat() / dl.totalBytes).coerceIn(0f, 1f)
    } else 0f
    val animatedPercent by animateFloatAsState(targetValue = percent, label = "progress")

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = dl.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
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
                        DropdownMenuItem(
                            text = { Text("Details") },
                            onClick = { menuOpen = false; onOpenDetails() }
                        )
                        if (dl.status == DownloadStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = { menuOpen = false; onOpen() }
                            )
                        }
                        if (dl.status == DownloadStatus.ERROR ||
                            dl.status == DownloadStatus.CANCELED) {
                            DropdownMenuItem(
                                text = { Text("Restart") },
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
            LinearProgressIndicator(
                progress = { animatedPercent },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
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
                    AnimatedVisibility(
                        visible = active || dl.status == DownloadStatus.PAUSED,
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        IconButton(onClick = onPauseResume, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (dl.status == DownloadStatus.PAUSED) Icons.Filled.PlayArrow
                                else Icons.Filled.Pause,
                                contentDescription = if (dl.status == DownloadStatus.PAUSED)
                                    "Resume" else "Pause",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // cancel while active
                    AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // open when done
                    AnimatedVisibility(
                        visible = dl.status == DownloadStatus.COMPLETED,
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        TextButton(
                            onClick = onOpen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Open",
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
