package com.thelightphone.lightpage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UrlNormalizerTest {

    @Test
    fun `normalize adds https when scheme is missing`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test
    fun `normalize preserves existing scheme`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com"))
        assertEquals("http://example.com", UrlNormalizer.normalize("http://example.com"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("  example.com  "))
    }

    @Test
    fun `validate accepts https url`() {
        assertEquals("https://example.com", UrlNormalizer.validate("example.com"))
    }

    @Test
    fun `validate rejects unsupported schemes`() {
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
        assertNull(UrlNormalizer.validate("https://"))
    }
}
