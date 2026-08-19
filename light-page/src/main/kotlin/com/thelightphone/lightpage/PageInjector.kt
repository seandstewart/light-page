package com.thelightphone.lightpage

import android.content.res.AssetManager
import android.webkit.WebView

/**
 * Loads CSS/JS assets and injects them into a [WebView] via [evaluateJavascript].
 *
 * Assets are loaded eagerly when constructed from an [AssetManager]. String literals
 * are escaped with [escapeJsString] so they can be safely embedded in JavaScript.
 */
class PageInjector(
    private val baseCss: String,
    private val readerCss: String,
    private val readabilityJs: String,
    private val purifyJs: String,
    private val hooksJs: String
) {

    constructor(assets: AssetManager) : this(
        baseCss = assets.loadAsset("light-page-theme.css"),
        readerCss = assets.loadAsset("reader-theme.css"),
        readabilityJs = assets.loadAsset("readability.js"),
        purifyJs = assets.loadAsset("purify.js"),
        hooksJs = assets.loadAsset("page-hooks.js")
    )

    private companion object {
        fun AssetManager.loadAsset(path: String): String =
            open(path).bufferedReader().readText()
    }

    /**
     * Shared helper used by both the early style injection and the page hooks.
     * Defined in one place so the two paths cannot drift apart.
     */
    private val ensureStyleJs = """
        const ensureStyle = (id, css) => {
          let s = document.getElementById(id);
          if (!s) {
            s = document.createElement("style");
            s.id = id;
            (document.head || document.documentElement).appendChild(s);
          }
          s.textContent = css;
        };
    """.trimIndent()

    /**
     * Injects the base theme style into the page early, before the first paint.
     * Idempotent: reuses the existing style element if it is already present.
     * Skips injection when the user has disabled CSS injection in the page.
     * Toggles `html.__light_dark_mode` when [theme] is [PageTheme.DARK].
     */
    fun injectBaseStyle(view: WebView, theme: PageTheme) {
        view.evaluateJavascript(
            """
            (function(){
              if (window.__lightCssInjectionEnabled === false) return;
              $ensureStyleJs
              ensureStyle('__light_base_theme', ${escapeJsString(baseCss)});
              document.documentElement.classList.toggle('__light_dark_mode', ${theme == PageTheme.DARK});
            })();
            """.trimIndent(),
            null
        )
    }

    /**
     * Injects the Readability.js and DOMPurify libraries required by the page
     * hooks. Re-evaluating is harmless, but the libraries define themselves on
     * `window` and do not need to be reloaded on every refresh.
     */
    fun injectReaderLibraries(view: WebView) {
        view.evaluateJavascript(readabilityJs, null)
        view.evaluateJavascript(purifyJs, null)
    }

    /**
     * Boots the page hook script. The script is idempotent and installs itself
     * once per page; subsequent calls can refresh the base theme via [refresh].
     */
    fun injectPageHooks(view: WebView) {
        val payload = hooksJs
            .replace("__ENSURE_STYLE__", ensureStyleJs)
            .replace("__BASE_CSS__", escapeJsString(baseCss))
            .replace("__READER_CSS__", escapeJsString(readerCss))
        view.evaluateJavascript(payload, null)
    }

    /**
     * Pushes the current browser UI state to the page hook script so it can update
     * the document (reader mode, theme, navigation availability, etc.).
     */
    fun refresh(view: WebView, state: BrowserUiState) {
        val json = buildString {
            append("{")
            append("\"requestedUrl\":${escapeJsString(state.requestedUrl)},")
            append("\"committedUrl\":${state.committedUrl?.let { escapeJsString(it) } ?: "null"},")
            append("\"loading\":${state.loading},")
            append("\"canGoBack\":${state.canGoBack},")
            append("\"canGoForward\":${state.canGoForward},")
            append("\"readerRequested\":${state.readerRequested},")
            append("\"readerForced\":${state.readerForced},")
            append("\"readerApplied\":${state.readerApplied},")
            append("\"cssInjectionEnabled\":${state.cssInjectionEnabled},")
            append("\"pageTheme\":${escapeJsString(state.pageTheme.name)}")
            append("}")
        }
        view.evaluateJavascript("window.__lightSetState && window.__lightSetState($json);", null)
    }

    /**
     * Returns a JavaScript double-quoted string literal for [value], escaping
     * backslashes, double quotes, and common whitespace characters.
     */
    private fun escapeJsString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}
