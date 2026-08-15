package com.thelightphone.lightpage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakePreferences = FakeReaderPreferences()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state uses provided url and reader requested`() = runTest {
        val viewModel = BrowserViewModel(
            preferences = fakePreferences,
            initialUrl = "https://test.example.com"
        )
        val state = viewModel.uiState.first()
        assertEquals("https://test.example.com", state.requestedUrl)
        assertTrue(state.readerRequested)
    }

    @Test
    fun `initial state restores last url and reader preference from preferences`() = runTest {
        fakePreferences.setLastUrl("https://saved.example.com")
        fakePreferences.setReaderEnabled(false)

        val viewModel = BrowserViewModel(
            preferences = fakePreferences,
            initialUrl = "https://test.example.com"
        )
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://saved.example.com", state.requestedUrl)
        assertFalse(state.readerRequested)
    }

    @Test
    fun `defaultStartUrl is fixture index in debug`() {
        assertEquals("http://10.0.2.2:8000/", BrowserViewModel.defaultStartUrl(debug = true))
    }

    @Test
    fun `defaultStartUrl is example com in release`() {
        assertEquals("https://example.com", BrowserViewModel.defaultStartUrl(debug = false))
    }

    @Test
    fun `onWebState updates state fields`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.onWebState(
            WebStateUpdate(
                url = "https://example.com",
                loading = false,
                canGoBack = true,
                canGoForward = true,
                readerApplied = true
            )
        )
        val state = viewModel.uiState.first()
        assertEquals("https://example.com", state.committedUrl)
        assertTrue(state.canGoBack)
        assertTrue(state.canGoForward)
        assertTrue(state.readerApplied)
        assertFalse(state.loading)
    }

    @Test
    fun `toggleReader flips reader requested and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.toggleReader()
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.readerRequested)
        assertFalse(fakePreferences.readerEnabled.first())
    }

    @Test
    fun `submitUrl normalizes and persists valid url`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.showUrlEditor(true)
        viewModel.submitUrl("example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://example.com", state.requestedUrl)
        assertFalse(state.urlEditorVisible)
        assertEquals("https://example.com", fakePreferences.lastUrl.first())
    }

    @Test
    fun `submitUrl ignores invalid url`() = runTest {
        val viewModel = BrowserViewModel(
            preferences = fakePreferences,
            initialUrl = "https://initial.example.com"
        )
        viewModel.showUrlEditor(true)
        viewModel.submitUrl("not a valid url")
        val state = viewModel.uiState.first()
        assertEquals("https://initial.example.com", state.requestedUrl)
        assertTrue(state.urlEditorVisible)
    }

    private class FakeReaderPreferences : ReaderPreferences {
        private val _readerEnabled = MutableStateFlow(true)
        private val _lastUrl = MutableStateFlow<String?>(null)

        override val readerEnabled: Flow<Boolean> = _readerEnabled
        override val lastUrl: Flow<String?> = _lastUrl

        override suspend fun setReaderEnabled(enabled: Boolean) {
            _readerEnabled.value = enabled
        }

        override suspend fun setLastUrl(url: String) {
            _lastUrl.value = url
        }
    }
}
