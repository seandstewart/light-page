package com.thelightphone.lightpage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrowserErrorMessageTest {

    @Test
    fun `messages match the documented status text`() {
        assertEquals("TLS error", BrowserError.Tls.message())
        assertEquals("Offline", BrowserError.Offline.message())
        assertEquals("Renderer gone", BrowserError.RendererGone.message())
        assertEquals("HTTP error 404", BrowserError.Http(404).message())
        assertEquals("Unsupported scheme (foo)", BrowserError.UnsupportedScheme("foo").message())
    }
}
