package com.thelightphone.lightpage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistence layer for reader defaults, theme/CSS toggles, and the recent URL list.
 *
 * Backed by the [SealedLightContext.dataStore] provided by the Light SDK.
 */
interface ReaderPreferences {
    val readerEnabled: Flow<Boolean>
    val lastUrl: Flow<String?>
    val themeInverted: Flow<Boolean>
    val cssInjectionEnabled: Flow<Boolean>
    val recentUrls: Flow<List<String>>

    suspend fun setReaderEnabled(enabled: Boolean)
    suspend fun setLastUrl(url: String)
    suspend fun setThemeInverted(inverted: Boolean)
    suspend fun setCssInjectionEnabled(enabled: Boolean)
    suspend fun setRecentUrls(urls: List<String>)
}

/**
 * DataStore-backed implementation of [ReaderPreferences].
 */
class DataStoreReaderPreferences(context: SealedLightContext) : ReaderPreferences {

    private val dataStore: DataStore<Preferences> = context.dataStore

    override val readerEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_READER_ENABLED] ?: true }

    override val lastUrl: Flow<String?> = dataStore.data
        .map { it[KEY_LAST_URL] }

    override val themeInverted: Flow<Boolean> = dataStore.data
        .map { it[KEY_THEME_INVERTED] ?: false }

    override val cssInjectionEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_CSS_INJECTION_ENABLED] ?: true }

    override val recentUrls: Flow<List<String>> = dataStore.data
        .map { it[KEY_RECENT_URLS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList() }

    override suspend fun setReaderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_READER_ENABLED] = enabled }
    }

    override suspend fun setLastUrl(url: String) {
        dataStore.edit { it[KEY_LAST_URL] = url }
    }

    override suspend fun setThemeInverted(inverted: Boolean) {
        dataStore.edit { it[KEY_THEME_INVERTED] = inverted }
    }

    override suspend fun setCssInjectionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CSS_INJECTION_ENABLED] = enabled }
    }

    override suspend fun setRecentUrls(urls: List<String>) {
        dataStore.edit { it[KEY_RECENT_URLS] = urls.joinToString("\n") }
    }

    private companion object {
        val KEY_READER_ENABLED = booleanPreferencesKey("reader_enabled")
        val KEY_LAST_URL = stringPreferencesKey("last_url")
        val KEY_THEME_INVERTED = booleanPreferencesKey("theme_inverted")
        val KEY_CSS_INJECTION_ENABLED = booleanPreferencesKey("css_injection_enabled")
        val KEY_RECENT_URLS = stringPreferencesKey("recent_urls")
    }
}
