package com.thelightphone.lightpage

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebViewClient

/**
 * Hardened [WebViewClient] for the M1 browser shell.
 *
 * Scheme navigations are filtered through [BrowserPolicy]. TLS failures are
 * cancelled and surfaced as [BrowserError.Tls]. Blocked schemes report an
 * exact `Unsupported scheme (<scheme>)` status.
 */
class LightWebViewClient(
    private val injection: ScriptInjection,
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

    override fun onPageCommitVisible(view: WebView, url: String?) {
        super.onPageCommitVisible(view, url)
        injection.injectBaseTheme(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        injection.injectBootScript(view)
        onState(
            WebStateUpdate(
                url = url,
                loading = false,
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

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            val browserError = when (error.errorCode) {
                WebViewClient.ERROR_HOST_LOOKUP,
                WebViewClient.ERROR_CONNECT,
                WebViewClient.ERROR_TIMEOUT,
                WebViewClient.ERROR_IO -> BrowserError.Offline

                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> BrowserError.Tls

                WebViewClient.ERROR_UNSUPPORTED_SCHEME,
                WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME -> BrowserError.UnsupportedScheme(
                    request.url.safeScheme()
                )

                else -> BrowserError.Offline
            }
            onState(
                WebStateUpdate(
                    loading = false,
                    error = browserError
                )
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (request.isForMainFrame) {
            onState(
                WebStateUpdate(
                    loading = false,
                    error = BrowserError.Http(errorResponse.statusCode)
                )
            )
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail
    ): Boolean {
        // Returning true means the app handles the crash. The WebView instance is
        // dead; the screen should recreate it when the user invokes the reload
        // affordance. We surface the error here so the UI can show the state.
        onState(
            WebStateUpdate(
                loading = false,
                error = BrowserError.RendererGone
            )
        )
        return true
    }

    private fun Uri?.safeScheme(): String = this?.scheme?.lowercase() ?: "unknown"
}
