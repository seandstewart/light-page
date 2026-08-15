package com.thelightphone.lightpage

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

@InitialScreen
class BrowserScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, BrowserViewModel>(sealedActivity) {

    override val viewModelClass: Class<BrowserViewModel>
        get() = BrowserViewModel::class.java

    override fun createViewModel(): BrowserViewModel = BrowserViewModel(
        preferences = DataStoreReaderPreferences(lightContext)
    )

    private var webView: WebView? = null

    override fun goBack(result: Unit?) {
        val wv = webView
        if (wv?.canGoBack() == true) {
            wv.goBack()
        } else {
            super.goBack(result)
        }
    }

    override fun willShow() {
        super.willShow()
        webView?.onResume()
    }

    override fun onAppPause() {
        super.onAppPause()
        webView?.onPause()
    }

    override fun onScreenDestroy() {
        webView?.destroy()
        webView = null
        super.onScreenDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        // Tracks the last URL we explicitly loaded so normal link navigation
        // does not get clobbered by `requestedUrl` state changes.
        var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
            ) {
                // Read-only truncated URL status row. Errors take precedence.
                val statusText = state.error?.message()
                    ?: state.committedUrl?.take(38)
                    ?: state.requestedUrl.take(38)

                LightText(
                    text = statusText,
                    variant = LightTextVariant.Copy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // WebView host. The factory context is inferred; no Context import is used.
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            // Hardened browser settings. JS and DOM storage are required
                            // for the WebView shell; file/content access, mixed content,
                            // and popup windows are explicitly disabled.
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            setBackgroundColor(android.graphics.Color.WHITE)
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(
                                ReaderStateBridge { applied ->
                                    viewModel.onWebState(WebStateUpdate(readerApplied = applied))
                                },
                                "__lightReaderBridge"
                            )
                            webViewClient = LightWebViewClient(
                                injection = ScriptInjection(context.assets),
                                onState = viewModel::onWebState
                            )
                            loadUrl(state.requestedUrl)
                            lastLoadedUrl = state.requestedUrl
                            webView = this
                        }
                    },
                    update = { wv ->
                        // Drive the WebView only when `requestedUrl` was changed explicitly
                        // (URL editor, preference restore). Normal link clicks update
                        // `committedUrl` through the WebViewClient and must not clobber
                        // the displayed page.
                        if (state.requestedUrl != lastLoadedUrl && state.requestedUrl.isNotBlank()) {
                            wv.loadUrl(state.requestedUrl)
                            lastLoadedUrl = state.requestedUrl
                        }
                    }
                )

                NavRow(
                    state = state,
                    onBack = { goBack() },
                    onForward = { webView?.takeIf { it.canGoForward() }?.goForward() },
                    onReload = { webView?.reload() },
                    onToggleReader = {
                        viewModel.toggleReader()
                        val next = !state.readerRequested
                        webView?.evaluateJavascript(
                            "window.__lightSetReaderEnabled($next)", null
                        )
                    },
                    onOpenUrl = { viewModel.showUrlEditor(true) }
                )

                if (state.urlEditorVisible) {
                    UrlEntryModal(
                        current = state.committedUrl ?: state.requestedUrl,
                        onSubmit = { url ->
                            viewModel.submitUrl(url)
                            // Navigation is driven by the AndroidView update block
                            // watching requestedUrl, so no explicit loadUrl is needed here.
                        },
                        onDismiss = { viewModel.showUrlEditor(false) }
                    )
                }
            }
        }
    }
}

/**
 * JavaScript bridge that lets the injected page-hooks report whether the reader
 * transformation is currently applied. The WebView instance never enters the
 * ViewModel; commands flow one-way through the Compose layer.
 */
private class ReaderStateBridge(
    private val onReaderApplied: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun onReaderApplied(applied: Boolean) {
        onReaderApplied(applied)
    }
}

@Composable
private fun NavRow(
    state: BrowserUiState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleReader: () -> Unit,
    onOpenUrl: () -> Unit
) {
    LightBottomBar(
        items = listOf(
            LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back"
            ),
            LightBarButton.LightIcon(
                icon = LightIcons.ARROW_RIGHT,
                onClick = if (state.canGoForward) onForward else null,
                contentDescription = "Forward"
            ),
            LightBarButton.LightIcon(
                icon = if (state.readerRequested) LightIcons.ACCEPT else LightIcons.CLOSE,
                onClick = onToggleReader,
                contentDescription = "Toggle reader"
            ),
            LightBarButton.LightIcon(
                icon = LightIcons.REFRESH,
                onClick = onReload,
                contentDescription = "Reload"
            ),
            LightBarButton.LightIcon(
                icon = LightIcons.ADD,
                onClick = onOpenUrl,
                contentDescription = "Open URL"
            )
        )
    )
}
