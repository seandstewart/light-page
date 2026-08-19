package com.thelightphone.lightpage

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.ui.LightThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        // Set the initial Light theme before the first composition so the top bar
        // and drawer backgrounds match the persisted/default state instead of
        // flashing the controller default.
        if (_uiState.value.pageTheme == PageTheme.LIGHT) LightThemeController.setLightTheme()
        else LightThemeController.setDarkTheme()

        // Async preference restoration: the initial state uses the default URL and
        // reader ON, then DataStore values overwrite it once available. This avoids
        // blocking the main thread during ViewModel construction.
        viewModelScope.launch {
            combine(
                preferences.lastUrl,
                preferences.readerEnabled,
                preferences.readerForced,
                preferences.pageTheme,
                preferences.cssInjectionEnabled,
                preferences.recentUrls,
            ) { values ->
                val lastUrl = values[0] as String?
                val readerEnabled = values[1] as Boolean
                val readerForced = values[2] as Boolean
                val pageTheme = values[3] as PageTheme
                val cssInjectionEnabled = values[4] as Boolean

                @Suppress("UNCHECKED_CAST")
                val recentUrls = values[5] as List<String>
                _uiState.update { current ->
                    current.copy(
                        requestedUrl = if (lastUrl != null && current.requestedUrl != lastUrl) lastUrl else current.requestedUrl,
                        readerRequested = readerEnabled,
                        readerForced = readerForced,
                        pageTheme = pageTheme,
                        cssInjectionEnabled = cssInjectionEnabled,
                        recentUrls = recentUrls,
                    )
                }
            }.collect { }
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
                error = when {
                    update.error != null -> update.error
                    update.clearError -> null
                    else -> current.error
                }
            )
        }
    }

    fun toggleReader() {
        val next = !_uiState.value.readerRequested
        _uiState.update { it.copy(readerRequested = next, readerForced = true) }
        viewModelScope.launch {
            preferences.setReaderEnabled(next)
            preferences.setReaderForced(true)
        }
    }

    fun toggleCssInjection() {
        val next = !_uiState.value.cssInjectionEnabled
        _uiState.update { it.copy(cssInjectionEnabled = next) }
        viewModelScope.launch { preferences.setCssInjectionEnabled(next) }
    }

    fun setPageTheme(theme: PageTheme) {
        _uiState.update { it.copy(pageTheme = theme) }
        viewModelScope.launch { preferences.setPageTheme(theme) }
    }

    fun showMenu(visible: Boolean) = _uiState.update { it.copy(menuVisible = visible) }

    fun showUrlDrawer(visible: Boolean) = _uiState.update { it.copy(urlDrawerVisible = visible) }

    fun showUrlEditor(mode: UrlEditorMode) = _uiState.update {
        it.copy(
            menuVisible = false,
            urlDrawerVisible = false,
            urlEditorVisible = true,
            urlEditorMode = mode,
            urlEditorInitialValue = when (mode) {
                is UrlEditorMode.Add -> ""
                is UrlEditorMode.Edit -> it.recentUrls.getOrNull(mode.index) ?: ""
            }
        )
    }

    fun closeUrlEditor() = _uiState.update {
        it.copy(
            urlEditorVisible = false,
            urlEditorMode = UrlEditorMode.Add,
            urlEditorInitialValue = ""
        )
    }

    fun editUrl(index: Int, raw: String) {
        val normalized = UrlNormalizer.validate(raw)
        if (normalized == null) {
            closeUrlEditor()
            return
        }
        var persisted: List<String> = emptyList()
        _uiState.update { current ->
            if (index !in current.recentUrls.indices) {
                current.copy(urlEditorVisible = false)
            } else {
                val others = current.recentUrls.filterIndexed { i, _ -> i != index }
                val updated = listOf(normalized) + others.filter { it != normalized }
                persisted = updated
                current.copy(
                    urlEditorVisible = false,
                    recentUrls = updated
                )
            }
        }
        viewModelScope.launch { if (persisted.isNotEmpty()) preferences.setRecentUrls(persisted) }
    }

    fun removeUrl(index: Int) {
        val updated = _uiState.value.recentUrls.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
        _uiState.update { current ->
            current.copy(recentUrls = updated)
        }
        viewModelScope.launch { preferences.setRecentUrls(updated) }
    }

    fun openWebInput(value: String, label: String) {
        _uiState.update { it.copy(webInputEditor = WebInputEditorState(value, label)) }
    }

    fun closeWebInput() {
        _uiState.update { it.copy(webInputEditor = null) }
    }

    fun submitUrl(raw: String) {
        val normalized = UrlNormalizer.validate(raw) ?: return
        val updated =
            listOf(normalized) + _uiState.value.recentUrls.filter { it != normalized }.take(MAX_RECENT_URLS - 1)
        _uiState.update { current ->
            current.copy(
                requestedUrl = normalized,
                urlEditorVisible = false,
                urlDrawerVisible = false,
                menuVisible = false,
                recentUrls = updated,
                error = null
            )
        }
        viewModelScope.launch {
            preferences.setLastUrl(normalized)
            preferences.setRecentUrls(updated)
        }
    }

    internal companion object {
        private const val MAX_RECENT_URLS = 20

        fun defaultStartUrl(debug: Boolean = BuildConfig.DEBUG): String =
            if (debug) "http://10.0.2.2:8000/" else "https://example.com"
    }
}
