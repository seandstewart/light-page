package com.thelightphone.lightpage

import android.content.res.AssetManager
import android.webkit.WebView
import org.json.JSONObject

/**
 * Loads CSS/JS assets and injects them into a [WebView] via [evaluateJavascript].
 *
 * Asset strings are JSON-encoded with [JSONObject.quote] so they can be safely
 * embedded as JavaScript string literals without manual escaping.
 */
class ScriptInjection(assets: AssetManager) {

    private val baseCss = assets.open("light-page-theme.css")
        .bufferedReader().readText()
    private val hooksJs = assets.open("page-hooks.js")
        .bufferedReader().readText()

    /**
     * Shared helper used by both the early anti-flash injection and the boot
     * script. Defined in one place so the two paths cannot drift apart.
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

    private fun String.asJsString(): String = JSONObject.quote(this)

    /**
     * Injects the base theme style into the page early, before the first paint.
     * Idempotent: reuses the existing style element if it is already present.
     */
    fun injectBaseTheme(view: WebView) {
        view.evaluateJavascript(
            """
            (function(){
              $ensureStyleJs
              ensureStyle('__light_base_theme', ${baseCss.asJsString()});
            })();
            """.trimIndent(),
            null
        )
    }

    /**
     * Boots the page hook script. The script is idempotent and will only install
     * itself once per page; subsequent calls trigger a refresh of the base theme.
     */
    fun injectBootScript(view: WebView) {
        val payload = hooksJs
            .replace("__ENSURE_STYLE__", ensureStyleJs)
            .replace("__BASE_CSS__", baseCss.asJsString())
        view.evaluateJavascript(payload, null)
    }
}
