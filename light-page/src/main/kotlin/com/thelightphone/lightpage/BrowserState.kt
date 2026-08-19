package com.thelightphone.lightpage

enum class PageTheme {
    LIGHT,
    DARK,
}

sealed interface UrlEditorMode {
    data object Add : UrlEditorMode
    data class Edit(val index: Int) : UrlEditorMode
}

data class WebInputEditorState(
    val value: String,
    val label: String,
)

data class BrowserUiState(
    val requestedUrl: String,
    val committedUrl: String? = null,
    val loading: Boolean = false,
    val statusScreenVisible: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val readerRequested: Boolean = true,
    val readerForced: Boolean = false,
    val readerApplied: Boolean = false,
    val cssInjectionEnabled: Boolean = true,
    val pageTheme: PageTheme = PageTheme.DARK,
    val menuVisible: Boolean = false,
    val urlDrawerVisible: Boolean = false,
    val urlEditorVisible: Boolean = false,
    val urlEditorMode: UrlEditorMode = UrlEditorMode.Add,
    val urlEditorInitialValue: String = "",
    val webInputEditor: WebInputEditorState? = null,
    val recentUrls: List<String> = emptyList(),
    val error: BrowserError? = null
)

data class WebStateUpdate(
    val url: String? = null,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val readerApplied: Boolean = false,
    val error: BrowserError? = null,
    val clearError: Boolean = false
)

enum class ReaderErrorCode {
    NOT_ELIGIBLE,
    PARSE_FAILED,
    TOO_SHORT,
    INTERACTIVE,
    LIBRARY_MISSING,
    EXCEPTION,
}

sealed interface BrowserError {
    data object Tls : BrowserError
    data object Offline : BrowserError
    data object RendererGone : BrowserError
    data class Http(val code: Int) : BrowserError
    data class UnsupportedScheme(val scheme: String) : BrowserError
    data class Reader(val code: ReaderErrorCode) : BrowserError
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
    is BrowserError.Reader -> "Reader: ${code.name}"
}
