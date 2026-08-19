package com.thelightphone.lightpage

import android.content.res.AssetManager
import android.webkit.WebView
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PageInjectorTest {

    @Test
    fun `injectBaseStyle evaluates a script that references the base CSS and dark mode`() {
        val injector = PageInjector(
            baseCss = "body{color:red}",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injector.injectBaseStyle(view, PageTheme.DARK)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("__light_base_theme"))
        assertTrue(script.contains("\"body{color:red}\""))
        assertTrue(script.contains("__light_dark_mode"))
    }

    @Test
    fun `injectBaseStyle does not enable dark mode for light theme`() {
        val injector = PageInjector(
            baseCss = "body{color:red}",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injector.injectBaseStyle(view, PageTheme.LIGHT)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("__light_base_theme"))
        assertTrue(script.contains("classList.toggle('__light_dark_mode', false)"))
    }

    @Test
    fun `injectBaseStyle skips injection when CSS injection is disabled`() {
        val injector = PageInjector(
            baseCss = "body{color:red}",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injector.injectBaseStyle(view, PageTheme.DARK)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("__lightCssInjectionEnabled === false"))
    }

    @Test
    fun `injectReaderLibraries evaluates the Readability and DOMPurify scripts`() {
        val injector = PageInjector(
            baseCss = "",
            readerCss = "",
            readabilityJs = "window.__readabilityLoaded = true;",
            purifyJs = "window.__purifyLoaded = true;",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injector.injectReaderLibraries(view)

        val scripts = mutableListOf<String>()
        verify(atLeast = 2) { view.evaluateJavascript(capture(scripts), null) }
        assertTrue(scripts.any { it.contains("__readabilityLoaded") }, "Readability script should be evaluated")
        assertTrue(scripts.any { it.contains("__purifyLoaded") }, "DOMPurify script should be evaluated")
    }

    @Test
    fun `injectPageHooks replaces placeholders and evaluates the payload`() {
        val injector = PageInjector(
            baseCss = "body{color:black}",
            readerCss = "#root{color:black}",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = "__ENSURE_STYLE__; const css = __BASE_CSS__; const r = __READER_CSS__;"
        )
        val view = mockk<WebView>(relaxed = true)

        injector.injectPageHooks(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("const ensureStyle"), "ensureStyle helper should be injected")
        assertTrue(script.contains("\"body{color:black}\""), "base CSS should be escaped as a string literal")
        assertTrue(script.contains("\"#root{color:black}\""), "reader CSS should be escaped as a string literal")
        assertTrue(!script.contains("__ENSURE_STYLE__"), "placeholder should be replaced")
        assertTrue(!script.contains("__BASE_CSS__"), "placeholder should be replaced")
        assertTrue(!script.contains("__READER_CSS__"), "reader CSS placeholder should be replaced")
    }

    @Test
    fun `injectPageHooks replaces all occurrences of placeholders`() {
        val injector = PageInjector(
            baseCss = "base",
            readerCss = "reader",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = "__BASE_CSS__; __BASE_CSS__; __READER_CSS__; __READER_CSS__; __ENSURE_STYLE__; __ENSURE_STYLE__;"
        )
        val view = mockk<WebView>(relaxed = true)

        injector.injectPageHooks(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(!script.contains("__BASE_CSS__"), "all base CSS placeholders should be replaced")
        assertTrue(!script.contains("__READER_CSS__"), "all reader CSS placeholders should be replaced")
        assertTrue(!script.contains("__ENSURE_STYLE__"), "all ensureStyle placeholders should be replaced")
    }

    @Test
    fun `escapeJsString wraps value in double quotes and escapes backslashes and quotes`() {
        val injector = PageInjector(
            baseCss = "",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val method = PageInjector::class.java.getDeclaredMethod("escapeJsString", String::class.java)
        method.isAccessible = true

        val backslashAndQuote = buildString {
            append('"')
            append('a')
            append('\\')
            append('\\')
            append('b')
            append('\\')
            append('"')
            append('c')
            append('"')
        }
        assertEquals(backslashAndQuote, method.invoke(injector, "a\\b\"c"))

        val newline = buildString {
            append('"')
            append("line1")
            append('\\')
            append('n')
            append("line2")
            append('"')
        }
        assertEquals(newline, method.invoke(injector, "line1\nline2"))
    }

    @Test
    fun `refresh evaluates state setter with a JSON payload`() {
        val injector = PageInjector(
            baseCss = "",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)
        val state = BrowserUiState(
            requestedUrl = "https://example.com",
            committedUrl = "https://example.com/page",
            loading = false,
            canGoBack = true,
            canGoForward = false,
            readerRequested = true,
            readerForced = false,
            readerApplied = true,
            cssInjectionEnabled = true,
            pageTheme = PageTheme.DARK
        )

        injector.refresh(view, state)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.startsWith("window.__lightSetState && window.__lightSetState("))
        assertTrue(script.contains("\"requestedUrl\":\"https://example.com\""))
        assertTrue(script.contains("\"committedUrl\":\"https://example.com/page\""))
        assertTrue(script.contains("\"loading\":false"))
        assertTrue(script.contains("\"canGoBack\":true"))
        assertTrue(script.contains("\"canGoForward\":false"))
        assertTrue(script.contains("\"readerRequested\":true"))
        assertTrue(script.contains("\"readerForced\":false"))
        assertTrue(script.contains("\"readerApplied\":true"))
        assertTrue(script.contains("\"cssInjectionEnabled\":true"))
        assertTrue(script.contains("\"pageTheme\":\"DARK\""))
    }

    @Test
    fun `constructor loads all five assets from AssetManager`() {
        val assetManager = mockk<AssetManager>()
        every { assetManager.open(any<String>()) } answers {
            ByteArrayInputStream(arg<String>(0).toByteArray())
        }

        val injector = PageInjector(assetManager)
        val view = mockk<WebView>(relaxed = true)
        injector.injectBaseStyle(view, PageTheme.DARK)

        verify { assetManager.open("light-page-theme.css") }
        verify { assetManager.open("reader-theme.css") }
        verify { assetManager.open("readability.js") }
        verify { assetManager.open("purify.js") }
        verify { assetManager.open("page-hooks.js") }
        verify { view.evaluateJavascript(any<String>(), null) }
    }
}
