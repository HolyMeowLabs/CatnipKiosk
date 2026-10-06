package com.holymeowlabs.catnipkiosk.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private fun createStore(context: Context, name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { context.applicationContext.preferencesDataStoreFile(name) },
    )

/**
 * On-device settings store. A null value means "not configured"; unreadable
 * data is treated the same way so a corrupt store sends the admin back to
 * setup instead of crash-looping the kiosk.
 */
class SettingsRepository private constructor(private val store: DataStore<Preferences>) {

    val settings: Flow<KioskSettings?> = store.data.map { prefs ->
        prefs[SETTINGS]?.let { decodeOrNull { SettingsJson.decodeSettings(it) } }
    }.distinctUntilChanged()

    val security: Flow<SecurityState?> = store.data.map { prefs ->
        prefs[SECURITY]?.let { decodeOrNull { SettingsJson.decodeSecurity(it) } }
    }.distinctUntilChanged()

    suspend fun saveSettings(s: KioskSettings) {
        store.edit { it[SETTINGS] = SettingsJson.encodeSettings(s) }
    }

    suspend fun saveSecurity(s: SecurityState) {
        store.edit { it[SECURITY] = SettingsJson.encodeSecurity(s) }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    @VisibleForTesting
    internal suspend fun writeRawSettingsForTest(raw: String) {
        store.edit { it[SETTINGS] = raw }
    }

    /** kotlinx's SerializationException is an IllegalArgumentException. */
    private inline fun <T> decodeOrNull(decode: () -> T): T? =
        try {
            decode()
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        private val SETTINGS = stringPreferencesKey("settings_json")
        private val SECURITY = stringPreferencesKey("security_json")
        private const val STORE_NAME = "kiosk"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(createStore(context, STORE_NAME)).also { instance = it }
            }

        /** A separate store file, built exactly like the real one; tests only. */
        @VisibleForTesting
        internal fun forStoreName(context: Context, name: String) = SettingsRepository(createStore(context, name))
    }
}
