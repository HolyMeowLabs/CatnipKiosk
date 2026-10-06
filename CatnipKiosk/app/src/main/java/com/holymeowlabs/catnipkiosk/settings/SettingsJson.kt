package com.holymeowlabs.catnipkiosk.settings

import kotlinx.serialization.json.Json

/** Storage encoding for settings; tolerant of fields added or removed by other app versions. */
object SettingsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        // An enum value this version doesn't know falls back to the field default.
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encodeSettings(s: KioskSettings): String = json.encodeToString(KioskSettings.serializer(), s)
    fun decodeSettings(text: String): KioskSettings = json.decodeFromString(KioskSettings.serializer(), text)
    fun encodeSecurity(s: SecurityState): String = json.encodeToString(SecurityState.serializer(), s)
    fun decodeSecurity(text: String): SecurityState = json.decodeFromString(SecurityState.serializer(), text)
}
