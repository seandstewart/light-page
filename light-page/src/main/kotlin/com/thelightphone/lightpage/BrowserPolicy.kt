package com.thelightphone.lightpage

import android.net.Uri

/**
 * Security policy for the M1 browser shell.
 *
 * Plain `http` is allowed only in debug builds so the QA fixtures served from
 * `just serve-fixtures` can be loaded on the emulator. Production/release builds
 * require TLS.
 */
object BrowserPolicy {

    private val ALLOWED_SCHEMES = setOf("https", "about")

    fun isAllowed(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http") return BuildConfig.DEBUG
        return scheme in ALLOWED_SCHEMES
    }
}
