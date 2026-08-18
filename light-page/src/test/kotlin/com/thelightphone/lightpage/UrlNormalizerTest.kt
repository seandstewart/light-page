package com.thelightphone.lightpage

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UrlNormalizerTest {

    @BeforeEach
    fun setup() {
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `normalize adds https when scheme is missing`() {
        every { Uri.parse("example.com") } returns uri(null)
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test
    fun `normalize preserves existing scheme`() {
        every { Uri.parse("https://example.com") } returns uri("https")
        every { Uri.parse("http://example.com") } returns uri("http")
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com"))
        assertEquals("http://example.com", UrlNormalizer.normalize("http://example.com"))
    }

    @Test
    fun `normalize trims whitespace`() {
        every { Uri.parse("example.com") } returns uri(null)
        assertEquals("https://example.com", UrlNormalizer.normalize("  example.com  "))
    }

    @Test
    fun `validate accepts https url`() {
        every { Uri.parse("example.com") } returns uri(null)
        every { Uri.parse("https://example.com") } returns uri("https", "example.com")
        assertEquals("https://example.com", UrlNormalizer.validate("example.com"))
    }

    @Test
    fun `validate rejects unsupported schemes`() {
        every { Uri.parse("file:///etc/passwd") } returns uri("file", "etc.passwd")
        every { Uri.parse("javascript:alert(1)") } returns uri("javascript")
        assertNull(UrlNormalizer.validate("file:///etc/passwd"))
        assertNull(UrlNormalizer.validate("javascript:alert(1)"))
    }

    @Test
    fun `validate rejects empty input`() {
        assertNull(UrlNormalizer.validate(""))
        assertNull(UrlNormalizer.validate("   "))
    }

    @Test
    fun `validate rejects input without host`() {
        every { Uri.parse("https://") } returns uri("https", null)
        assertNull(UrlNormalizer.validate("https://"))
    }

    private fun uri(scheme: String?, host: String? = null): Uri = mockk<Uri>().apply {
        every { this@apply.scheme } returns scheme
        every { this@apply.host } returns host
    }
}
