package com.aurora.downloader.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus
import com.aurora.downloader.download.engine.ProgressSnapshot
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModelFactory(private val app: AuroraApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DownloadsViewModel(app) as T
}

class DownloadsViewModel(private val app: AuroraApp) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> =
        app.downloadRepository.observeDownloads()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * Live speed/ETA straight from the engine, keyed by download id. The flow
     * is a stream of single snapshots, so we fold them into a map the UI can
     * look up per card.
     */
    val liveProgress: StateFlow<Map<Long, ProgressSnapshot>> =
        app.downloadEngine.progress
            .runningFold(
                initial = emptyMap<Long, ProgressSnapshot>(),
                operation = { acc, s -> acc + (s.downloadId to s) }
            )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    fun pause(id: Long) = viewModelScope.launch {
        app.downloadEngine.pause(id)
    }

    fun resume(id: Long) = viewModelScope.launch {
        app.downloadEngine.resume(id)
    }

    /** Pauses a running download or resumes a paused/failed/canceled one. */
    fun pauseResume(entity: DownloadEntity) = viewModelScope.launch {
        when (entity.status) {
            DownloadStatus.PAUSED, DownloadStatus.ERROR, DownloadStatus.CANCELED ->
                app.downloadEngine.resume(entity.id)
            else -> app.downloadEngine.pause(entity.id)
        }
    }

    /** Cancels and removes a download job, keeping any partial file. */
    fun cancel(id: Long) = viewModelScope.launch {
        app.downloadEngine.cancel(id)
        _events.tryEmit("Download canceled")
    }

    /** Deletes the download record and its file from disk. */
    fun delete(id: Long) = viewModelScope.launch {
        app.downloadEngine.delete(id)
        _events.tryEmit("Download deleted")
    }

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /**
     * Opens a finished download. Published files resolve via their MediaStore
     * content:// URI; anything still in staging falls back to the file path.
     */
    fun openFile(entity: DownloadEntity, showError: (String) -> Unit) {
        val uriString = entity.contentUri ?: entity.savePath
        if (uriString.isBlank()) {
            showError("No file to open")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = if (entity.contentUri != null) Uri.parse(entity.contentUri)
            else Uri.fromFile(java.io.File(entity.savePath))
            setDataAndType(uri, entity.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            app.startActivity(intent)
        } catch (t: Throwable) {
            val msg = "No app found to open this file"
            showError(msg)
            viewModelScope.launch { _events.tryEmit(msg) }
        }
    }
}
