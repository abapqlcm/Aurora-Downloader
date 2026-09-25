package com.aurora.downloader.ui.screens.browser

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.download.engine.NewDownloadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowserState(
    val currentUrl: String = "https://www.google.com"
)

class BrowserViewModelFactory(private val app: AuroraApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BrowserViewModel(app) as T
}

class BrowserViewModel(private val app: AuroraApp) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    fun load(url: String) {
        val normalized = normalizeUrl(url)
        _state.value = _state.value.copy(currentUrl = normalized)
    }

    /**
     * Called from the WebView's DownloadListener — the interception point.
     * Cookies are the whole game here; they ride along in the request.
     */
    fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookieHeader: String
    ) {
        val guessedName = URLUtil.guessFileName(
            url, contentDisposition, mimeType
        )
        viewModelScope.launch {
            app.downloadEngine.enqueue(
                NewDownloadRequest(
                    url = url,
                    fileName = guessedName,
                    userAgent = userAgent,
                    referer = _state.value.currentUrl,
                    cookieHeader = cookieHeader
                )
            )
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.isEmpty() -> "https://www.google.com"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
}
