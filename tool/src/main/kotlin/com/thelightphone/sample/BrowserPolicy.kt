package com.thelightphone.sample

import android.net.Uri

/**
 * Security policy for the M1 browser shell.
 *
 * The [ONLY_TLS] flag defaults to `true` (production). Set it to `false` as a
 * dev override to allow plain `http` navigations; everything else is blocked.
 */
object BrowserPolicy {

    /**
     * When `true` only `https` (and internal `about:`) navigations are allowed.
     * When `false` plain `http` is also permitted for local development.
     */
    const val ONLY_TLS = true

    private val ALLOWED_SCHEMES = setOf("https", "about")

    fun isAllowed(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http") return !ONLY_TLS
        return scheme in ALLOWED_SCHEMES
    }
}
