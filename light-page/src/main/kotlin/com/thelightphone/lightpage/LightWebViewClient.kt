package com.thelightphone.lightpage

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Hardened [WebViewClient] for the M1 browser shell.
 *
 * Scheme navigations are filtered through [BrowserPolicy]. TLS failures are
 * cancelled and surfaced as [BrowserError.Tls]. Blocked schemes report an
 * exact `Unsupported scheme (<scheme>)` status.
 */
class LightWebViewClient(
    private val onState: (WebStateUpdate) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onState(
            WebStateUpdate(
                url = url,
                loading = true,
                error = null,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        )
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onState(
            WebStateUpdate(
                url = url,
                loading = false,
                error = null,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        )
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onState(
            WebStateUpdate(
                url = url,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val allowed = BrowserPolicy.isAllowed(request.url)
        if (!allowed) {
            onState(
                WebStateUpdate(
                    loading = false,
                    error = BrowserError.UnsupportedScheme(
                        request.url.safeScheme()
                    )
                )
            )
        }
        return !allowed
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        handler.cancel()
        onState(
            WebStateUpdate(
                loading = false,
                error = BrowserError.Tls
            )
        )
    }

    private fun Uri?.safeScheme(): String = this?.scheme?.lowercase() ?: "unknown"
}
