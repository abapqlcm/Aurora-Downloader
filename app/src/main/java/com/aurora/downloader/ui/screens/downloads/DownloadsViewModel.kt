package com.aurora.downloader.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class DownloadsViewModelFactory(private val app: AuroraApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DownloadsViewModel(app) as T
}

class DownloadsViewModel(app: AuroraApp) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> =
        app.downloadRepository.observeDownloads()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
}
