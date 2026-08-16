package com.thelightphone.lightpage

import android.webkit.WebView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScriptInjectionTest {

    @BeforeEach
    fun setup() {
        mockkStatic(JSONObject::class)
        every { JSONObject.quote(any<String>()) } returns "\"encoded\""
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(JSONObject::class)
    }

    @Test
    fun `injectBaseTheme evaluates a script that references the base CSS`() {
        val injection = ScriptInjection(
            baseCss = "body{color:red}",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injection.injectBaseTheme(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("__light_base_theme"))
        assertTrue(script.contains("\"encoded\""))
    }

    @Test
    fun `injectBaseTheme skips injection when CSS injection is disabled`() {
        val injection = ScriptInjection(
            baseCss = "body{color:red}",
            readerCss = "",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injection.injectBaseTheme(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("__lightCssInjectionEnabled === false"))
    }

    @Test
    fun `injectLibraries evaluates the Readability and DOMPurify scripts`() {
        val injection = ScriptInjection(
            baseCss = "",
            readerCss = "",
            readabilityJs = "window.__readabilityLoaded = true;",
            purifyJs = "window.__purifyLoaded = true;",
            hooksJs = ""
        )
        val view = mockk<WebView>(relaxed = true)

        injection.injectLibraries(view)

        val scripts = mutableListOf<String>()
        verify(atLeast = 2) { view.evaluateJavascript(capture(scripts), null) }
        assertTrue(scripts.any { it.contains("__readabilityLoaded") }, "Readability script should be evaluated")
        assertTrue(scripts.any { it.contains("__purifyLoaded") }, "DOMPurify script should be evaluated")
    }

    @Test
    fun `injectBootScript replaces placeholders and evaluates the payload`() {
        val injection = ScriptInjection(
            baseCss = "body{color:black}",
            readerCss = "#root{color:black}",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = "__ENSURE_STYLE__; const css = __BASE_CSS__; const r = __READER_CSS__;"
        )
        val view = mockk<WebView>(relaxed = true)

        injection.injectBootScript(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("const ensureStyle"), "ensureStyle helper should be injected")
        assertTrue(script.contains("\"encoded\""), "CSS should be JSON-encoded as a string literal")
        assertTrue(!script.contains("__ENSURE_STYLE__"), "placeholder should be replaced")
        assertTrue(!script.contains("__BASE_CSS__"), "placeholder should be replaced")
        assertTrue(!script.contains("__READER_CSS__"), "reader CSS placeholder should be replaced")
    }

    @Test
    fun `injectBootScript replaces all occurrences of placeholders`() {
        val injection = ScriptInjection(
            baseCss = "base",
            readerCss = "reader",
            readabilityJs = "",
            purifyJs = "",
            hooksJs = "__BASE_CSS__; __BASE_CSS__; __READER_CSS__; __READER_CSS__; __ENSURE_STYLE__; __ENSURE_STYLE__;"
        )
        val view = mockk<WebView>(relaxed = true)

        injection.injectBootScript(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(!script.contains("__BASE_CSS__"), "all base CSS placeholders should be replaced")
        assertTrue(!script.contains("__READER_CSS__"), "all reader CSS placeholders should be replaced")
        assertTrue(!script.contains("__ENSURE_STYLE__"), "all ensureStyle placeholders should be replaced")
    }
}
