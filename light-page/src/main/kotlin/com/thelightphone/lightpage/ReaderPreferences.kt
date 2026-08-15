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
 * Persistence layer for reader defaults and the last loaded URL.
 *
 * Backed by the [SealedLightContext.dataStore] provided by the Light SDK.
 */
interface ReaderPreferences {
    val readerEnabled: Flow<Boolean>
    val lastUrl: Flow<String?>

    suspend fun setReaderEnabled(enabled: Boolean)
    suspend fun setLastUrl(url: String)
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

    override suspend fun setReaderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_READER_ENABLED] = enabled }
    }

    override suspend fun setLastUrl(url: String) {
        dataStore.edit { it[KEY_LAST_URL] = url }
    }

    private companion object {
        val KEY_READER_ENABLED = booleanPreferencesKey("reader_enabled")
        val KEY_LAST_URL = stringPreferencesKey("last_url")
    }
}
