package com.aurora.downloader.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadEntity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun pause(id: Long) = viewModelScope.launch {
        app.downloadEngine.pause(id)
    }

    fun resume(id: Long) = viewModelScope.launch {
        app.downloadEngine.resume(id)
    }

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
            showError("No app found to open this file")
        }
    }
}
