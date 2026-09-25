package com.aurora.downloader.ui.screens.browser

import android.content.Context
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * A WebView with the cookie plumbing pre-wired. AcceptThirdPartyCookies is on
 * because most CDN-fronted download portals set the session cookie on a
 * different host than the page; without it the download URL bounces to a
 * login redirect and the user gets an HTML file instead of the real one.
 */
class AuroraWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    init {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@AuroraWebView, true)
        }
    }
}
