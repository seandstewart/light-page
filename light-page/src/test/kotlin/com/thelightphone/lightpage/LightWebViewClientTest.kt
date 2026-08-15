package com.thelightphone.lightpage

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
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightWebViewClientTest {

    private val injection = ScriptInjection(baseCss = "", readerCss = "", hooksJs = "")

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

    private var lastError: BrowserError? = null

    private fun client(): LightWebViewClient {
        lastError = null
        return LightWebViewClient(
            injection = injection,
            onState = { lastError = it.error }
        )
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
}
