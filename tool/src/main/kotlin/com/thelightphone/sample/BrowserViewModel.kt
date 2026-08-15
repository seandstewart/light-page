package com.thelightphone.sample

import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BrowserViewModel : LightViewModel<Unit>() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(requestedUrl = "https://example.com")
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    fun onWebState(update: WebStateUpdate) {
        _uiState.update { current ->
            current.copy(
                committedUrl = update.url ?: current.committedUrl,
                loading = update.loading,
                canGoBack = update.canGoBack,
                canGoForward = update.canGoForward,
                readerApplied = update.readerApplied,
                error = update.error
            )
        }
    }

    fun toggleReader() = _uiState.update {
        it.copy(readerRequested = !it.readerRequested)
    }

    fun showUrlEditor(visible: Boolean) = _uiState.update {
        it.copy(urlEditorVisible = visible)
    }

    fun submitUrl(raw: String) = _uiState.update {
        it.copy(requestedUrl = raw, urlEditorVisible = false)
    }

    fun requestExit() {
        // The screen owns the WebView reference and the LightScreen back path;
        // the ViewModel intentionally never holds the WebView.
    }
}
