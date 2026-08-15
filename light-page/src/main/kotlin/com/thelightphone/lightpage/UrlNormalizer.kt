package com.thelightphone.lightpage

import java.net.URI

/**
 * URL normalization and policy validation for the M4 URL entry modal.
 *
 * Input is trimmed, whitespace is collapsed, and a missing scheme is inferred
 * as `https://`. IDN and host-like inputs are accepted; inputs that are not
 * plausible web URLs are rejected.
 */
object UrlNormalizer {

    private val SCHEME_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*:)")

    /**
     * Normalize a raw user-entered URL string.
     *
     * - Trims whitespace.
     * - Adds `https://` if no scheme is present.
     * - Returns the input as-is if it is already a supported scheme so that
     *   existing valid URLs are not rewritten unexpectedly.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val hasScheme = SCHEME_REGEX.containsMatchIn(trimmed)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    /**
     * Validate a normalized URL against the browser policy and a rough
     * host/url shape check. Returns the normalized URL if it is acceptable,
     * or `null` if it should be rejected.
     *
     * This mirrors [BrowserPolicy] (https allowed, http allowed only in debug)
     * without requiring an Android `Uri` object, which keeps the normalizer
     * unit-testable on the JVM.
     */
    fun validate(raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null

        val uri = try {
            URI(normalized)
        } catch (e: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("https", "http")) return null
        if (scheme == "http" && !BuildConfig.DEBUG) return null
        if (uri.host.isNullOrBlank()) return null

        return normalized
    }
}
