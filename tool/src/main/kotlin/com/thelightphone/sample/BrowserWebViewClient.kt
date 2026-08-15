package com.thelightphone.sample

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Minimal state-reporting WebViewClient for the M1 shell.
 *
 * Security policy (scheme allowlist, TLS handling, etc.) is intentionally left to
 * the follow-up M1 security task so this shell can be wired up first.
 */
class BrowserWebViewClient(
    private val onState: (WebStateUpdate) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onState(
            WebStateUpdate(
                url = url,
                loading = true,
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
}
