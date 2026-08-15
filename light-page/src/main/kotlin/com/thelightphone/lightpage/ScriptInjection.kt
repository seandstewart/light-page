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

    private fun String.asJsString(): String = JSONObject.quote(this)

    /**
     * Injects the base theme style into the page early, before the first paint.
     * Idempotent: reuses the existing style element if it is already present.
     */
    fun injectBaseTheme(view: WebView) {
        view.evaluateJavascript(
            """
            (function(){
              var s = document.getElementById('__light_base_theme')
                   || document.createElement('style');
              s.id = '__light_base_theme';
              s.textContent = ${baseCss.asJsString()};
              (document.head || document.documentElement).appendChild(s);
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
        val payload = hooksJs.replace("__BASE_CSS__", baseCss.asJsString())
        view.evaluateJavascript(payload, null)
    }
}
