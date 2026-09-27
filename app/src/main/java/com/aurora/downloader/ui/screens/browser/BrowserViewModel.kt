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

/**
 * The state of the browser's Download Sheet. The sheet appears whenever the
 * WebView intercepts a downloadable resource, with metadata the probe actually
 * fetched from the server — never a guess.
 */
sealed interface BrowserSheetState {
    data object Loading : BrowserSheetState
    data class Ready(
        val url: String,
        val fileName: String,
        val size: Long?,
        val mimeType: String?,
        val server: String?,
        val resumable: Boolean
    ) : BrowserSheetState
    data class Failed(val reason: String) : BrowserSheetState
}

class BrowserViewModelFactory(private val app: AuroraApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BrowserViewModel(app) as T
}

class BrowserViewModel(private val app: AuroraApp) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _sheetState = MutableStateFlow<BrowserSheetState?>(null)
    val sheetState: StateFlow<BrowserSheetState?> = _sheetState.asStateFlow()

    private var pendingUrl: String = ""
    private var pendingUserAgent: String? = null
    private var pendingReferer: String? = null
    private var pendingCookie: String = ""
    private var pendingName: String = ""

    fun load(url: String) {
        _state.value = _state.value.copy(currentUrl = normalizeUrl(url))
    }

    /** Keeps [BrowserState.currentUrl] in sync as the user navigates. */
    fun onUrlLoaded(url: String) {
        if (!url.startsWith("about:")) {
            _state.value = _state.value.copy(currentUrl = url)
        }
    }

    /**
     * Called from the WebView's DownloadListener — the interception point.
     * Cookies ride along in the probe request so protected downloads survive
     * the hop out of the browser.
     */
    fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookieHeader: String
    ) {
        pendingUrl = url
        pendingUserAgent = userAgent
        pendingReferer = _state.value.currentUrl
        pendingCookie = cookieHeader
        pendingName = URLUtil.guessFileName(url, contentDisposition, mimeType)

        _sheetState.value = BrowserSheetState.Loading
        viewModelScope.launch {
            val probe = app.downloadEngine.probe(
                url = url,
                cookieHeader = cookieHeader
            )
            _sheetState.value = if (probe == null) {
                BrowserSheetState.Failed(
                    "The server did not respond or refused the request."
                )
            } else {
                BrowserSheetState.Ready(
                    url = url,
                    fileName = probe.fileName.ifBlank { pendingName },
                    size = probe.totalBytes.takeIf { it > 0 },
                    mimeType = probe.mimeType,
                    server = runCatching {
                        android.net.Uri.parse(probe.finalUrl).host
                    }.getOrNull(),
                    resumable = probe.isResumable
                )
            }
        }
    }

    /** Confirmed from the Download Sheet: queue the job with its metadata. */
    fun confirmDownload() {
        val sheet = _sheetState.value
        _sheetState.value = null
        if (pendingUrl.isBlank()) return
        viewModelScope.launch {
            app.downloadEngine.enqueue(
                NewDownloadRequest(
                    url = pendingUrl,
                    fileName = (sheet as? BrowserSheetState.Ready)?.fileName
                        ?: pendingName,
                    userAgent = pendingUserAgent,
                    referer = pendingReferer,
                    cookieHeader = pendingCookie
                )
            )
        }
    }

    /** The user dismissed the sheet — drop the pending download. */
    fun dismissSheet() {
        _sheetState.value = null
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "https://$trimmed"
    }
}
