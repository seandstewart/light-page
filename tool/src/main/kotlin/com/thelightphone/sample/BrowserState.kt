package com.thelightphone.sample

data class BrowserUiState(
    val requestedUrl: String,
    val committedUrl: String? = null,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val readerRequested: Boolean = true,
    val readerApplied: Boolean = false,
    val urlEditorVisible: Boolean = false,
    val error: BrowserError? = null
)

data class WebStateUpdate(
    val url: String? = null,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val readerApplied: Boolean = false,
    val error: BrowserError? = null
)

sealed interface BrowserError {
    data object Tls : BrowserError
    data object Offline : BrowserError
    data object RendererGone : BrowserError
    data class Http(val code: Int) : BrowserError
    data class UnsupportedScheme(val scheme: String) : BrowserError
}

/**
 * Human-readable status text for a browser error.
 * Blocked schemes render the exact `Unsupported scheme (<scheme>)` message.
 */
fun BrowserError.message(): String = when (this) {
    is BrowserError.Tls -> "TLS error"
    is BrowserError.Offline -> "Offline"
    is BrowserError.RendererGone -> "Renderer gone"
    is BrowserError.Http -> "HTTP error $code"
    is BrowserError.UnsupportedScheme -> "Unsupported scheme ($scheme)"
}
