package com.aurora.downloader.ui.screens.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browser") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AddressBar(
                text = address,
                onTextChange = { address = it },
                onSubmit = { vm.load(address) }
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    AuroraWebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView, request: WebResourceRequest
                            ): Boolean = false
                        }
                        webChromeClient = WebChromeClient()
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
                    }
                },
                update = { web ->
                    if (state.currentUrl != web.url) {
                        web.loadUrl(state.currentUrl)
                    }
                }
            )
        }
    }
}

@Composable
private fun AddressBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        label = { Text("Address") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Go
        ),
        keyboardActions = KeyboardActions(
            onGo = { onSubmit() }
        )
    )
}
