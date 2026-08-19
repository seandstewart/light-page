package com.thelightphone.lightpage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistence layer for reader defaults, theme/CSS toggles, and the recent URL list.
 *
 * Backed by the DataStore provided by the Light SDK.
 */
class ReaderPreferences(private val dataStore: DataStore<Preferences>) {

    val readerEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_READER_ENABLED] ?: true }

    val readerForced: Flow<Boolean> = dataStore.data
        .map { it[KEY_READER_FORCED] ?: false }

    val lastUrl: Flow<String?> = dataStore.data
        .map { it[KEY_LAST_URL] }

    val pageTheme: Flow<PageTheme> = dataStore.data
        .map { it[KEY_PAGE_THEME]?.let { name -> PageTheme.valueOf(name) } ?: PageTheme.DARK }

    val cssInjectionEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_CSS_INJECTION_ENABLED] ?: true }

    val recentUrls: Flow<List<String>> = dataStore.data
        .map { prefs -> prefs[KEY_RECENT_URLS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList() }

    suspend fun setReaderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_READER_ENABLED] = enabled }
    }

    suspend fun setReaderForced(forced: Boolean) {
        dataStore.edit { it[KEY_READER_FORCED] = forced }
    }

    suspend fun setLastUrl(url: String) {
        dataStore.edit { it[KEY_LAST_URL] = url }
    }

    suspend fun setPageTheme(theme: PageTheme) {
        dataStore.edit { it[KEY_PAGE_THEME] = theme.name }
    }

    suspend fun setCssInjectionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CSS_INJECTION_ENABLED] = enabled }
    }

    suspend fun setRecentUrls(urls: List<String>) {
        dataStore.edit { it[KEY_RECENT_URLS] = urls.joinToString("\n") }
    }

    private companion object {
        val KEY_READER_ENABLED = booleanPreferencesKey("reader_enabled")
        val KEY_READER_FORCED = booleanPreferencesKey("reader_forced")
        val KEY_LAST_URL = stringPreferencesKey("last_url")
        val KEY_PAGE_THEME = stringPreferencesKey("page_theme")
        val KEY_CSS_INJECTION_ENABLED = booleanPreferencesKey("css_injection_enabled")
        val KEY_RECENT_URLS = stringPreferencesKey("recent_urls")
    }
}
