package com.aurora.downloader.ui.screens.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.downloader.AuroraApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadDetailsViewModelFactory(
    private val app: AuroraApp,
    private val downloadId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DownloadDetailsViewModel(app, downloadId) as T
}

class DownloadDetailsViewModel(
    private val app: AuroraApp,
    private val downloadId: Long
) : ViewModel() {

    val download: StateFlow<com.aurora.downloader.domain.model.DownloadEntity?> =
        app.downloadRepository.observeDownload(downloadId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val parts: StateFlow<List<com.aurora.downloader.domain.model.PartEntity>> =
        app.downloadRepository.observeParts(downloadId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live speed/ETA for this download only. */
    val liveProgress: StateFlow<com.aurora.downloader.download.engine.ProgressSnapshot?> =
        app.downloadEngine.progress
            .filter { it.downloadId == downloadId }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pause() = viewModelScope.launch { app.downloadEngine.pause(downloadId) }
    fun resume() = viewModelScope.launch { app.downloadEngine.resume(downloadId) }
    fun cancel() = viewModelScope.launch { app.downloadEngine.cancel(downloadId) }
    fun restart() = viewModelScope.launch { app.downloadEngine.restart(downloadId) }
    fun open(context: Context) = app.downloadEngine.openDownload(downloadId, context)
    fun delete() = viewModelScope.launch { app.downloadEngine.delete(downloadId) }
}
