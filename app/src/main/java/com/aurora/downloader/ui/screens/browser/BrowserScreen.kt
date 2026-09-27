package com.aurora.downloader.ui.screens.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.downloader.AuroraApp

/**
 * The in-app browser.
 *
 * This is not a feature, it is the *reliability layer*: Chrome and most major
 * browsers do not hand downloads to external apps, so the only interception
 * path that works on 100% of sites is a WebView whose DownloadListener we own.
 * The session cookies carried out of it are what make real-world downloads
 * succeed — without them the overwhelming majority of protected downloads 403.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    app: AuroraApp,
    onOpenSettings: () -> Unit
) {
    val vm: BrowserViewModel = viewModel(factory = BrowserViewModelFactory(app))
    val state by vm.state.collectAsState()
    var address by remember { mutableStateOf("https://www.google.com") }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp, 6.dp, 12.dp, 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NavIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { webRef?.goBack() }
                    NavIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") { webRef?.goForward() }
                    NavIcon(Icons.Filled.Refresh, "Reload") { webRef?.reload() }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search or type a URL") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (address.isNotEmpty()) {
                                IconButton(onClick = { address = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.load(address) })
                    )
                }

                if (loading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                AuroraWebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            // Layer 1: direct file links navigate instead of
                            // firing the download listener — catch them here.
                            if (DownloadDetector.looksLikeFile(url)) {
                                val cookieHeader =
                                    CookieManager.getInstance().getCookie(url) ?: ""
                                vm.handleDownload(
                                    url = url,
                                    userAgent = view.settings.userAgentString,
                                    contentDisposition = null,
                                    mimeType = DownloadDetector.guessMime(url),
                                    cookieHeader = cookieHeader
                                )
                                return true
                            }
                            return false
                        }

                        // Layer 3: Content-Disposition headers only appear on
                        // the response, so sniff them during interception.
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): android.webkit.WebResourceResponse? {
                            val url = request.url.toString()
                            if (request.isForMainFrame &&
                                DownloadDetector.looksLikeFile(url)
                            ) {
                                view.post {
                                    val cookieHeader =
                                        CookieManager.getInstance().getCookie(url) ?: ""
                                    vm.handleDownload(
                                        url = url,
                                        userAgent = view.settings.userAgentString,
                                        contentDisposition = null,
                                        mimeType = DownloadDetector.guessMime(url),
                                        cookieHeader = cookieHeader
                                    )
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            url?.let {
                                address = it
                                vm.onUrlLoaded(it)
                                // Layer 4: the page *is* the file (PDF viewers,
                                // direct image/video links). Offer to save it.
                                if (DownloadDetector.looksLikeFile(it) &&
                                    vm.sheetState.value == null
                                ) {
                                    val cookieHeader =
                                        CookieManager.getInstance().getCookie(it) ?: ""
                                    vm.handleDownload(
                                        url = it,
                                        userAgent = view.settings.userAgentString,
                                        contentDisposition = null,
                                        mimeType = DownloadDetector.guessMime(it),
                                        cookieHeader = cookieHeader
                                    )
                                }
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            loading = newProgress < 100
                        }
                    }
                    // The single most important hook in the app.
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        val cookieHeader = CookieManager.getInstance().getCookie(url) ?: ""
                        vm.handleDownload(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            cookieHeader = cookieHeader
                        )
                    }
                    loadUrl(state.currentUrl)
                    webRef = this
                }
            },
            update = { web ->
                webRef = web
                if (state.currentUrl != web.url) {
                    web.loadUrl(state.currentUrl)
                }
            }
        )
    }

    // Download interception sheet — probes the URL, shows server metadata.
    val sheet by vm.sheetState.collectAsState()
    sheet?.let { s ->
        DownloadSheet(
            state = s,
            onDismiss = { vm.dismissSheet() },
            onStart = { vm.confirmDownload() }
        )
    }
}

@Composable
private fun NavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(20.dp))
    }
}
