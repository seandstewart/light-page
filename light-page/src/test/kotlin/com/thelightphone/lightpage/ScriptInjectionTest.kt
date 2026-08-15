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
        val injection = ScriptInjection(baseCss = "body{color:red}", readerCss = "", hooksJs = "")
        val view = mockk<WebView>(relaxed = true)

        injection.injectBaseTheme(view)

        val scriptSlot = slot<String>()
        verify { view.evaluateJavascript(capture(scriptSlot), null) }
        val script = scriptSlot.captured
        assertTrue(script.contains("__light_base_theme"))
        assertTrue(script.contains("\"encoded\""))
    }

    @Test
    fun `injectBootScript replaces placeholders and evaluates the payload`() {
        val injection = ScriptInjection(
            baseCss = "body{color:black}",
            readerCss = "#root{color:black}",
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
}
