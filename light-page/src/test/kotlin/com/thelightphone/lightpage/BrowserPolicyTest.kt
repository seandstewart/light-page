package com.thelightphone.lightpage

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowserPolicyTest {

    @Test
    fun `https is always allowed`() {
        assertTrue(BrowserPolicy.isAllowed(uri("https"), allowHttp = false))
        assertTrue(BrowserPolicy.isAllowed(uri("https"), allowHttp = true))
    }

    @Test
    fun `about scheme is always allowed`() {
        assertTrue(BrowserPolicy.isAllowed(uri("about"), allowHttp = false))
    }

    @Test
    fun `http is allowed only when allowHttp is true`() {
        assertFalse(BrowserPolicy.isAllowed(uri("http"), allowHttp = false))
        assertTrue(BrowserPolicy.isAllowed(uri("http"), allowHttp = true))
    }

    @Test
    fun `dangerous schemes are always blocked`() {
        assertFalse(BrowserPolicy.isAllowed(uri("file"), allowHttp = true))
        assertFalse(BrowserPolicy.isAllowed(uri("intent"), allowHttp = true))
        assertFalse(BrowserPolicy.isAllowed(uri("javascript"), allowHttp = true))
    }

    @Test
    fun `null scheme is blocked`() {
        assertFalse(BrowserPolicy.isAllowed(uri(null), allowHttp = true))
    }

    private fun uri(scheme: String?): Uri = mockk<Uri>().apply {
        every { this@apply.scheme } returns scheme
    }
}
