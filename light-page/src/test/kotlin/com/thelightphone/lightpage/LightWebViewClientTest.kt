package com.thelightphone.lightpage

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LightWebViewClientTest {

    private val injection =
        ScriptInjection(baseCss = "", readerCss = "", readabilityJs = "", purifyJs = "", hooksJs = "")

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
    fun `tls error is surfaced`() {
        val client = client()
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = mockk<SslError>()

        client.onReceivedSslError(mockk(), handler, error)

        verify { handler.cancel() }
        assertEquals(BrowserError.Tls, lastError)
    }

    @Test
    fun `renderer crash is surfaced`() {
        val client = client()
        val detail = mockk<RenderProcessGoneDetail>()

        val consumed = client.onRenderProcessGone(mockk(), detail)

        assertTrue(consumed)
        assertEquals(BrowserError.RendererGone, lastError)
    }

    @Test
    fun `main frame network error is surfaced as offline`() {
        val client = client()
        val request = mainFrameRequest()
        val error = mockk<WebResourceError>().apply {
            every { errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
        }

        client.onReceivedError(mockk(), request, error)

        assertEquals(BrowserError.Offline, lastError)
    }

    @Test
    fun `main frame ssl error is surfaced as tls`() {
        val client = client()
        val request = mainFrameRequest()
        val error = mockk<WebResourceError>().apply {
            every { errorCode } returns WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
        }

        client.onReceivedError(mockk(), request, error)

        assertEquals(BrowserError.Tls, lastError)
    }

    @Test
    fun `non main frame network error is ignored`() {
        val client = client()
        val request = mockk<WebResourceRequest>().apply {
            every { isForMainFrame } returns false
        }
        val error = mockk<WebResourceError>().apply {
            every { errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
        }

        client.onReceivedError(mockk(), request, error)

        assertNull(lastError)
    }

    @Test
    fun `main frame http error is surfaced`() {
        val client = client()
        val request = mainFrameRequest()
        val response = mockk<WebResourceResponse>().apply {
            every { statusCode } returns 404
        }

        client.onReceivedHttpError(mockk(), request, response)

        assertEquals(BrowserError.Http(404), lastError)
    }

    @Test
    fun `non main frame http error is ignored`() {
        val client = client()
        val request = mockk<WebResourceRequest>().apply {
            every { isForMainFrame } returns false
        }
        val response = mockk<WebResourceResponse>().apply {
            every { statusCode } returns 500
        }

        client.onReceivedHttpError(mockk(), request, response)

        assertNull(lastError)
    }

    @Test
    fun `blocked scheme is reported and navigation is overridden`() {
        val client = client()
        val request = mockk<WebResourceRequest>().apply {
            every { url } returns uri("file")
        }

        val override = client.shouldOverrideUrlLoading(mockk(), request)

        assertTrue(override)
        assertEquals(BrowserError.UnsupportedScheme("file"), lastError)
    }

    @Test
    fun `allowed scheme is not overridden`() {
        val client = client()
        val request = mockk<WebResourceRequest>().apply {
            every { url } returns uri("https")
        }

        val override = client.shouldOverrideUrlLoading(mockk(), request)

        assertFalse(override)
        assertNull(lastError)
    }

    @Test
    fun `onPageStarted clears error and reports loading`() {
        val client = client()
        val view = webView()
        client.onPageStarted(view, "https://example.com", mockk<Bitmap>())

        val update = lastUpdate
        assertEquals("https://example.com", update?.url)
        assertTrue(update?.loading == true)
        assertTrue(update?.clearError == true)
    }

    @Test
    fun `onPageFinished preserves existing error`() {
        val client = client()
        val view = webView()
        client.onReceivedError(view, mainFrameRequest(), mockk<WebResourceError>().apply {
            every { errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
        })
        assertEquals(BrowserError.Offline, lastError)

        client.onPageFinished(view, "https://example.com")

        assertEquals(BrowserError.Offline, lastError)
        val update = lastUpdate
        assertEquals("https://example.com", update?.url)
        assertFalse(update?.loading == true)
    }

    private var lastError: BrowserError? = null
    private var lastUpdate: WebStateUpdate? = null

    private fun client(): LightWebViewClient {
        lastError = null
        lastUpdate = null
        return LightWebViewClient(
            injection = injection,
            onState = {
                lastError = when {
                    it.error != null -> it.error
                    it.clearError -> null
                    else -> lastError
                }
                lastUpdate = it
            }
        )
    }

    private fun webView(): WebView = mockk<WebView>(relaxed = true).apply {
        every { canGoBack() } returns false
        every { canGoForward() } returns false
    }

    private fun mainFrameRequest(scheme: String = "https", host: String = "example.com"): WebResourceRequest {
        val uri = mockk<Uri>().apply {
            every { this@apply.scheme } returns scheme
        }
        return mockk<WebResourceRequest>().apply {
            every { isForMainFrame } returns true
            every { this@apply.url } returns uri
        }
    }

    private fun uri(scheme: String): Uri = mockk<Uri>().apply {
        every { this@apply.scheme } returns scheme
    }
}
