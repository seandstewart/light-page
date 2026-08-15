package com.thelightphone.lightpage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
        val viewModel = BrowserViewModel(initialUrl = "https://test.example.com")
        val state = viewModel.uiState.first()
        assertEquals("https://test.example.com", state.requestedUrl)
        assertTrue(state.readerRequested)
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
        val viewModel = BrowserViewModel()
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
    fun `toggleReader flips reader requested`() = runTest {
        val viewModel = BrowserViewModel()
        viewModel.toggleReader()
        val state = viewModel.uiState.first()
        assertFalse(state.readerRequested)
    }

    @Test
    fun `submitUrl updates requested url and hides editor`() = runTest {
        val viewModel = BrowserViewModel()
        viewModel.showUrlEditor(true)
        viewModel.submitUrl("https://new.example.com")
        val state = viewModel.uiState.first()
        assertEquals("https://new.example.com", state.requestedUrl)
        assertFalse(state.urlEditorVisible)
    }
}
