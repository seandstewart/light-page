package com.thelightphone.lightpage

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val preferences: ReaderPreferences,
    initialUrl: String = defaultStartUrl()
) : LightViewModel<Unit>() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            requestedUrl = initialUrl,
            readerRequested = true
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        // Async preference restoration: the initial state uses the default URL and
        // reader ON, then DataStore values overwrite it once available. This avoids
        // blocking the main thread during ViewModel construction.
        viewModelScope.launch {
            preferences.lastUrl.collect { saved ->
                if (saved != null && _uiState.value.requestedUrl != saved) {
                    _uiState.update { it.copy(requestedUrl = saved) }
                }
            }
        }
        viewModelScope.launch {
            preferences.readerEnabled.collect { enabled ->
                _uiState.update { it.copy(readerRequested = enabled) }
            }
        }
    }

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

    fun toggleReader() {
        val next = !_uiState.value.readerRequested
        _uiState.update { it.copy(readerRequested = next) }
        viewModelScope.launch { preferences.setReaderEnabled(next) }
    }

    fun showUrlEditor(visible: Boolean) = _uiState.update {
        it.copy(urlEditorVisible = visible)
    }

    fun submitUrl(raw: String) {
        val normalized = UrlNormalizer.validate(raw) ?: return
        _uiState.update {
            it.copy(
                requestedUrl = normalized,
                urlEditorVisible = false,
                error = null
            )
        }
        viewModelScope.launch { preferences.setLastUrl(normalized) }
    }

    fun requestExit() {
        // The screen owns the WebView reference and the LightScreen back path;
        // the ViewModel intentionally never holds the WebView.
    }

    internal companion object {
        fun defaultStartUrl(debug: Boolean = BuildConfig.DEBUG): String =
            if (debug) "http://10.0.2.2:8000/" else "https://example.com"
    }
}
