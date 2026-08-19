package com.thelightphone.lightpage

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private lateinit var dataStore: FakeDataStore
    private lateinit var preferences: ReaderPreferences

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any<String>()) } answers { mockUriFor(arg<String>(0)) }
        dataStore = FakeDataStore()
        preferences = ReaderPreferences(dataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    @Test
    fun `initial state uses provided url and reader requested`() = runTest {
        val viewModel = BrowserViewModel(
            preferences = preferences,
            initialUrl = "https://test.example.com"
        )
        val state = viewModel.uiState.first()
        assertEquals("https://test.example.com", state.requestedUrl)
        assertTrue(state.readerRequested)
    }

    @Test
    fun `initial state restores preferences`() = runTest {
        preferences.setLastUrl("https://saved.example.com")
        preferences.setReaderEnabled(false)
        preferences.setPageTheme(PageTheme.LIGHT)
        preferences.setCssInjectionEnabled(false)
        preferences.setRecentUrls(listOf("https://one.example.com", "https://two.example.com"))

        val viewModel = BrowserViewModel(
            preferences = preferences,
            initialUrl = "https://test.example.com"
        )
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://saved.example.com", state.requestedUrl)
        assertFalse(state.readerRequested)
        assertEquals(PageTheme.LIGHT, state.pageTheme)
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
        val viewModel = BrowserViewModel(preferences = preferences)
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
    fun `onWebState with reader error sets error and readerApplied false`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.onWebState(
            WebStateUpdate(
                readerApplied = false,
                error = BrowserError.Reader(ReaderErrorCode.TOO_SHORT)
            )
        )
        val state = viewModel.uiState.first()
        assertFalse(state.readerApplied)
        assertEquals(BrowserError.Reader(ReaderErrorCode.TOO_SHORT), state.error)
    }

    @Test
    fun `onWebState clears error only when clearError is true`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.onWebState(WebStateUpdate(error = BrowserError.Offline))
        assertNotNull(viewModel.uiState.first().error)

        viewModel.onWebState(WebStateUpdate(loading = true))
        assertNotNull(viewModel.uiState.first().error)

        viewModel.onWebState(WebStateUpdate(clearError = true))
        assertNull(viewModel.uiState.first().error)
    }

    @Test
    fun `toggleReader flips reader requested, marks forced, and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.toggleReader()
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.readerRequested)
        assertTrue(state.readerForced)
        assertFalse(preferences.readerEnabled.first())
        assertTrue(preferences.readerForced.first())
    }

    @Test
    fun `toggleCssInjection flips css injection and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.toggleCssInjection()
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.cssInjectionEnabled)
        assertFalse(preferences.cssInjectionEnabled.first())
    }

    @Test
    fun `setPageTheme sets page theme and persists`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.setPageTheme(PageTheme.LIGHT)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(PageTheme.LIGHT, state.pageTheme)
        assertEquals(PageTheme.LIGHT, preferences.pageTheme.first())
    }

    @Test
    fun `menu and drawer visibility toggles`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
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
        preferences.setRecentUrls(listOf("https://first.example.com", "https://second.example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.showUrlEditor(UrlEditorMode.Edit(1))
        val state = viewModel.uiState.first()
        assertTrue(state.urlEditorVisible)
        assertEquals(UrlEditorMode.Edit(1), state.urlEditorMode)
        assertEquals("https://second.example.com", state.urlEditorInitialValue)
    }

    @Test
    fun `submitUrl normalizes and moves to front of recent urls`() = runTest {
        preferences.setRecentUrls(listOf("https://old.example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.submitUrl("https://new.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://new.example.com", state.requestedUrl)
        assertEquals(listOf("https://new.example.com", "https://old.example.com"), state.recentUrls)
        assertEquals("https://new.example.com", preferences.lastUrl.first())
    }

    @Test
    fun `editUrl updates existing url and persists`() = runTest {
        preferences.setRecentUrls(listOf("https://old.example.com", "https://other.example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.editUrl(0, "https://updated.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(listOf("https://updated.example.com", "https://other.example.com"), state.recentUrls)
        assertEquals(state.recentUrls, preferences.recentUrls.first())
    }

    @Test
    fun `removeUrl deletes url and persists`() = runTest {
        preferences.setRecentUrls(listOf("https://one.example.com", "https://two.example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.removeUrl(0)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(listOf("https://two.example.com"), state.recentUrls)
        assertEquals(state.recentUrls, preferences.recentUrls.first())
    }

    @Test
    fun `open and close web input editor`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
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
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.showUrlEditor(UrlEditorMode.Add)
        viewModel.submitUrl("example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals("https://example.com", state.requestedUrl)
        assertFalse(state.urlEditorVisible)
        assertFalse(state.urlDrawerVisible)
        assertFalse(state.menuVisible)
        assertEquals("https://example.com", preferences.lastUrl.first())
        assertEquals(listOf("https://example.com"), preferences.recentUrls.first())
    }

    @Test
    fun `submitUrl ignores invalid url`() = runTest {
        val viewModel = BrowserViewModel(
            preferences = preferences,
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
        preferences.setRecentUrls(listOf("https://dup.example.com", "https://other.example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.submitUrl("https://dup.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(listOf("https://dup.example.com", "https://other.example.com"), state.recentUrls)
    }

    @Test
    fun `closeUrlEditor resets editor state`() = runTest {
        preferences.setRecentUrls(listOf("https://example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
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
        preferences.setRecentUrls(listOf("https://example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.showUrlEditor(UrlEditorMode.Edit(0))
        viewModel.editUrl(0, "not a valid url")
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(listOf("https://example.com"), state.recentUrls)
    }

    @Test
    fun `removeUrl out of range is ignored`() = runTest {
        preferences.setRecentUrls(listOf("https://example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.removeUrl(10)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(listOf("https://example.com"), state.recentUrls)
        assertEquals(state.recentUrls, preferences.recentUrls.first())
    }

    @Test
    fun `recent urls are capped at 20`() = runTest {
        val urls = (1..25).map { "https://site$it.example.com" }
        preferences.setRecentUrls(urls)
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.submitUrl("https://new.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(20, state.recentUrls.size)
        assertEquals("https://new.example.com", state.recentUrls.first())
    }

    @Test
    fun `editUrl removes duplicate after editing`() = runTest {
        preferences.setRecentUrls(listOf("https://a.example.com", "https://b.example.com", "https://c.example.com"))
        val viewModel = BrowserViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.editUrl(2, "https://a.example.com")
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.urlEditorVisible)
        assertEquals(listOf("https://a.example.com", "https://b.example.com"), state.recentUrls)
        assertEquals(state.recentUrls, preferences.recentUrls.first())
    }

    @Test
    fun `showUrlEditor closes menu and url drawer`() = runTest {
        val viewModel = BrowserViewModel(preferences = preferences)
        viewModel.showMenu(true)
        viewModel.showUrlDrawer(true)
        viewModel.showUrlEditor(UrlEditorMode.Add)
        val state = viewModel.uiState.first()
        assertFalse(state.menuVisible)
        assertFalse(state.urlDrawerVisible)
        assertTrue(state.urlEditorVisible)
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val current = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = current.asStateFlow()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(current.value)
            current.value = updated
            return updated
        }
    }

    private fun mockUriFor(input: String): Uri {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            return mockUri(null, null)
        }
        val scheme = when {
            trimmed.startsWith("https://") -> "https"
            trimmed.startsWith("http://") -> "http"
            trimmed.startsWith("file://") -> "file"
            trimmed.startsWith("javascript:") -> "javascript"
            else -> null
        }
        val host = scheme?.let {
            trimmed.removePrefix("$it://").substringBefore('/').takeIf { h -> h.isNotBlank() }
        }
        return mockUri(scheme, host)
    }

    private fun mockUri(scheme: String?, host: String?): Uri = mockk<Uri>().apply {
        every { this@apply.scheme } returns scheme
        every { this@apply.host } returns host
    }
}
