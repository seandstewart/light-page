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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `initial state restores preferences`() = runTest {
        fakePreferences.setLastUrl("https://saved.example.com")
        fakePreferences.setReaderEnabled(false)
        fakePreferences.setThemeInverted(true)
        fakePreferences.setCssInjectionEnabled(false)
        fakePreferences.setRecentUrls(listOf("https://one.example.com", "https://two.example.com"))

        val viewModel = BrowserViewModel(
            preferences = fakePreferences,
            initialUrl = "https://test.example.com"
        )
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://saved.example.com", state.requestedUrl)
        assertFalse(state.readerRequested)
        assertTrue(state.themeInverted)
        assertFalse(state.cssInjectionEnabled)
        assertEquals(listOf("https://one.example.com", "https://two.example.com"), state.recentUrls)
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
    fun `onWebState clears error only when clearError is true`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.onWebState(WebStateUpdate(error = BrowserError.Offline))
        assertNotNull(viewModel.uiState.first().error)

        viewModel.onWebState(WebStateUpdate(loading = true))
        assertNotNull(viewModel.uiState.first().error)

        viewModel.onWebState(WebStateUpdate(clearError = true))
        assertNull(viewModel.uiState.first().error)
    }

    @Test
    fun `toggleReader flips reader requested, marks forced, and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.toggleReader()
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.readerRequested)
        assertTrue(state.readerForced)
        assertFalse(fakePreferences.readerEnabled.first())
        assertTrue(fakePreferences.readerForced.first())
    }

    @Test
    fun `toggleCssInjection flips css injection and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.toggleCssInjection()
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.cssInjectionEnabled)
        assertFalse(fakePreferences.cssInjectionEnabled.first())
    }

    @Test
    fun `toggleThemeInverted flips theme inverted and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.toggleThemeInverted()
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertTrue(state.themeInverted)
        assertTrue(fakePreferences.themeInverted.first())
    }

    @Test
    fun `menu and drawer visibility toggles`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.showMenu(true)
        assertTrue(viewModel.uiState.first().menuVisible)
        viewModel.showMenu(false)
        assertFalse(viewModel.uiState.first().menuVisible)
        viewModel.showUrlDrawer(true)
        assertTrue(viewModel.uiState.first().urlDrawerVisible)
        viewModel.showUrlDrawer(false)
        assertFalse(viewModel.uiState.first().urlDrawerVisible)
    }

    @Test
    fun `showUrlEditor sets mode and initial value`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://first.example.com", "https://second.example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.showUrlEditor(UrlEditorMode.Edit(1))
        val state = viewModel.uiState.first()
        assertTrue(state.urlEditorVisible)
        assertEquals(UrlEditorMode.Edit(1), state.urlEditorMode)
        assertEquals("https://second.example.com", state.urlEditorInitialValue)
    }

    @Test
    fun `addNewUrl normalizes and moves to front of recent urls`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://old.example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.addNewUrl("https://new.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://new.example.com", state.requestedUrl)
        assertEquals(listOf("https://new.example.com", "https://old.example.com"), state.recentUrls)
        assertEquals("https://new.example.com", fakePreferences.lastUrl.first())
    }

    @Test
    fun `editUrl updates existing url and persists`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://old.example.com", "https://other.example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.editUrl(0, "https://updated.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(listOf("https://updated.example.com", "https://other.example.com"), state.recentUrls)
        assertEquals(state.recentUrls, fakePreferences.recentUrls.first())
    }

    @Test
    fun `removeUrl deletes url and persists`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://one.example.com", "https://two.example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.removeUrl(0)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(listOf("https://two.example.com"), state.recentUrls)
        assertEquals(state.recentUrls, fakePreferences.recentUrls.first())
    }

    @Test
    fun `open and close web input editor`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.openWebInput("hello", "Name")
        val openState = viewModel.uiState.first()
        assertNotNull(openState.webInputEditor)
        assertEquals("hello", openState.webInputEditor?.value)
        assertEquals("Name", openState.webInputEditor?.label)

        viewModel.closeWebInput()
        assertNull(viewModel.uiState.first().webInputEditor)
    }

    @Test
    fun `submitUrl normalizes and persists valid url`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.showUrlEditor(UrlEditorMode.Add)
        viewModel.submitUrl("example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://example.com", state.requestedUrl)
        assertFalse(state.urlEditorVisible)
        assertFalse(state.urlDrawerVisible)
        assertFalse(state.menuVisible)
        assertEquals("https://example.com", fakePreferences.lastUrl.first())
        assertEquals(listOf("https://example.com"), fakePreferences.recentUrls.first())
    }

    @Test
    fun `submitUrl ignores invalid url`() = runTest {
        val viewModel = BrowserViewModel(
            preferences = fakePreferences,
            initialUrl = "https://initial.example.com"
        )
        viewModel.showUrlEditor(UrlEditorMode.Add)
        viewModel.submitUrl("not a valid url")
        val state = viewModel.uiState.first()
        assertEquals("https://initial.example.com", state.requestedUrl)
        assertTrue(state.urlEditorVisible)
    }

    @Test
    fun `submitUrl deduplicates recent urls`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://dup.example.com", "https://other.example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.submitUrl("https://dup.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(listOf("https://dup.example.com", "https://other.example.com"), state.recentUrls)
    }

    @Test
    fun `closeUrlEditor resets editor state`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.showUrlEditor(UrlEditorMode.Edit(0))
        viewModel.closeUrlEditor()
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(UrlEditorMode.Add, state.urlEditorMode)
        assertEquals("", state.urlEditorInitialValue)
    }

    @Test
    fun `editUrl closes editor when url is invalid`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.showUrlEditor(UrlEditorMode.Edit(0))
        viewModel.editUrl(0, "not a valid url")
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(listOf("https://example.com"), state.recentUrls)
    }

    @Test
    fun `removeUrl out of range is ignored`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.removeUrl(10)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(listOf("https://example.com"), state.recentUrls)
        assertEquals(state.recentUrls, fakePreferences.recentUrls.first())
    }

    @Test
    fun `recent urls are capped at 20`() = runTest {
        val urls = (1..25).map { "https://site$it.example.com" }
        fakePreferences.setRecentUrls(urls)
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.submitUrl("https://new.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(20, state.recentUrls.size)
        assertEquals("https://new.example.com", state.recentUrls.first())
    }

    @Test
    fun `editUrl removes duplicate after editing`() = runTest {
        fakePreferences.setRecentUrls(listOf("https://a.example.com", "https://b.example.com", "https://c.example.com"))
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        advanceUntilIdle()

        viewModel.editUrl(2, "https://a.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(listOf("https://a.example.com", "https://b.example.com"), state.recentUrls)
        assertEquals(state.recentUrls, fakePreferences.recentUrls.first())
    }

    @Test
    fun `showUrlEditor closes menu and url drawer`() = runTest {
        val viewModel = BrowserViewModel(preferences = fakePreferences)
        viewModel.showMenu(true)
        viewModel.showUrlDrawer(true)
        viewModel.showUrlEditor(UrlEditorMode.Add)
        val state = viewModel.uiState.first()
        assertFalse(state.menuVisible)
        assertFalse(state.urlDrawerVisible)
        assertTrue(state.urlEditorVisible)
    }

    private class FakeReaderPreferences : ReaderPreferences {
        private val _readerEnabled = MutableStateFlow(true)
        private val _readerForced = MutableStateFlow(false)
        private val _lastUrl = MutableStateFlow<String?>(null)
        private val _themeInverted = MutableStateFlow(false)
        private val _cssInjectionEnabled = MutableStateFlow(true)
        private val _recentUrls = MutableStateFlow<List<String>>(emptyList())

        override val readerEnabled: Flow<Boolean> = _readerEnabled
        override val readerForced: Flow<Boolean> = _readerForced
        override val lastUrl: Flow<String?> = _lastUrl
        override val themeInverted: Flow<Boolean> = _themeInverted
        override val cssInjectionEnabled: Flow<Boolean> = _cssInjectionEnabled
        override val recentUrls: Flow<List<String>> = _recentUrls

        override suspend fun setReaderEnabled(enabled: Boolean) {
            _readerEnabled.value = enabled
        }

        override suspend fun setReaderForced(forced: Boolean) {
            _readerForced.value = forced
        }

        override suspend fun setLastUrl(url: String) {
            _lastUrl.value = url
        }

        override suspend fun setThemeInverted(inverted: Boolean) {
            _themeInverted.value = inverted
        }

        override suspend fun setCssInjectionEnabled(enabled: Boolean) {
            _cssInjectionEnabled.value = enabled
        }

        override suspend fun setRecentUrls(urls: List<String>) {
            _recentUrls.value = urls
        }
    }
}
