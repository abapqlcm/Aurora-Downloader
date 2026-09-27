package com.aurora.downloader.ui.screens.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.download.engine.NewDownloadRequest
import com.aurora.downloader.download.transport.ProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * States of the Add-Download flow. [Idle] -> [Analyzing] -> ([Info] | [Error]).
 * The user can retry from Error, and "download anyway" enqueues a blind job.
 */
sealed interface AddState {
    data object Idle : AddState
    data object Analyzing : AddState
    data class Info(
        val url: String,
        val fileName: String,
        val size: Long?,
        val mimeType: String?,
        val server: String?,
        val resumable: Boolean
    ) : AddState
    data class Error(val reason: String, val url: String) : AddState
}

class AddDownloadViewModelFactory(private val app: AuroraApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddDownloadViewModel(app) as T
}

class AddDownloadViewModel(private val app: AuroraApp) : ViewModel() {

    private val _state = MutableStateFlow<AddState>(AddState.Idle)
    val state: StateFlow<AddState> = _state.asStateFlow()

    /** The filename the user is editing; defaults to the server's suggestion. */
    var fileName: String = ""
        get() = field
        set(value) { field = value }

    private var lastUrl: String = ""
    private var lastProbe: ProbeResult? = null

    fun analyze(url: String) {
        if (url.isBlank()) return
        lastUrl = url.trim()
        _state.value = AddState.Analyzing
        viewModelScope.launch {
            val probe = app.downloadEngine.probe(lastUrl)
            if (probe == null) {
                _state.value = AddState.Error(
                    reason = "The server did not respond or refused the request.",
                    url = lastUrl
                )
                return@launch
            }
            fileName = probe.fileName
            lastProbe = probe
            _state.value = AddState.Info(
                url = lastUrl,
                fileName = probe.fileName,
                size = probe.totalBytes.takeIf { it > 0 },
                mimeType = probe.mimeType,
                server = runCatching { Uri.parse(probe.finalUrl).host }.getOrNull(),
                resumable = probe.isResumable
            )
        }
    }

    fun updateFileName(value: String) {
        fileName = value
    }

    private fun enqueue(): Long? {
        val name = fileName.ifBlank {
            lastProbe?.fileName ?: lastUrl.substringAfterLast('/').ifBlank { "download.bin" }
        }
        val target = File(
            app.getExternalFilesDir(null) ?: app.filesDir,
            "staging"
        )
        val id = kotlinx.coroutines.runBlocking {
            app.downloadEngine.enqueue(
                NewDownloadRequest(
                    url = lastUrl,
                    fileName = name,
                    targetDirectory = target
                )
            )
        }
        return id
    }

    fun start() {
        val id = enqueue() ?: return
        _state.value = AddState.Idle
    }

    fun downloadLater() {
        enqueue()
        _state.value = AddState.Idle
    }
}
