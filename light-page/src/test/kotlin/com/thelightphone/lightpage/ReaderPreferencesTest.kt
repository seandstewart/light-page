package com.thelightphone.lightpage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPreferencesTest {

    private val dataStore = FakeDataStore()

    @Test
    fun `setPageTheme DARK persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        preferences.setPageTheme(PageTheme.DARK)

        assertEquals(PageTheme.DARK, preferences.pageTheme.first())
    }

    @Test
    fun `setPageTheme LIGHT persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        preferences.setPageTheme(PageTheme.LIGHT)

        assertEquals(PageTheme.LIGHT, preferences.pageTheme.first())
    }

    @Test
    fun `setReaderEnabled false persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        preferences.setReaderEnabled(false)

        assertFalse(preferences.readerEnabled.first())
    }

    @Test
    fun `setReaderEnabled true persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        preferences.setReaderEnabled(true)

        assertTrue(preferences.readerEnabled.first())
    }

    @Test
    fun `setCssInjectionEnabled false persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        preferences.setCssInjectionEnabled(false)

        assertFalse(preferences.cssInjectionEnabled.first())
    }

    @Test
    fun `setCssInjectionEnabled true persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        preferences.setCssInjectionEnabled(true)

        assertTrue(preferences.cssInjectionEnabled.first())
    }

    @Test
    fun `setLastUrl persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        assertNull(preferences.lastUrl.first())

        preferences.setLastUrl("https://example.com")

        assertEquals("https://example.com", preferences.lastUrl.first())
    }

    @Test
    fun `setRecentUrls persists and emits`() = runTest {
        val preferences = ReaderPreferences(dataStore)

        assertTrue(preferences.recentUrls.first().isEmpty())

        preferences.setRecentUrls(listOf("https://one.example.com", "https://two.example.com"))

        assertEquals(
            listOf("https://one.example.com", "https://two.example.com"),
            preferences.recentUrls.first()
        )
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val current = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = current.asStateFlow()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(current.value)
            current.value = updated
            return updated
        }
    }
}
