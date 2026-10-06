package com.holymeowlabs.catnipkiosk.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KioskSettingsSerializationTest {

    private val everythingChanged = KioskSettings(
        startUrl = "https://example.com/lobby",
        navMode = NavMode.PAGE_ONLY,
        includeSubdomains = false,
        extraDomains = listOf("login.example-sso.com", "cdn.example.net"),
        showBlockedMessage = false,
        zoomPercent = 150,
        cursorEnabled = true,
        keepScreenOn = false,
        reloadOnFailure = false,
        scheduledReload = ScheduledReload.DailyAt(3, 0),
        startOnBoot = false,
        pinLockoutEnabled = true,
    )

    @Test
    fun settingsRoundTrip() {
        val json = SettingsJson.encodeSettings(everythingChanged)
        assertThat(SettingsJson.decodeSettings(json)).isEqualTo(everythingChanged)
    }

    @Test
    fun everyScheduledReloadVariantRoundTrips() {
        for (schedule in listOf(ScheduledReload.Off, ScheduledReload.EveryMinutes(60), ScheduledReload.DailyAt(23, 59))) {
            val s = everythingChanged.copy(scheduledReload = schedule)
            assertThat(SettingsJson.decodeSettings(SettingsJson.encodeSettings(s))).isEqualTo(s)
        }
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() {
        val json = SettingsJson.encodeSettings(everythingChanged).replaceFirst("{", "{\"someFutureSetting\":42,")
        assertThat(SettingsJson.decodeSettings(json)).isEqualTo(everythingChanged)
    }

    @Test
    fun unknownEnumValueFallsBackToTheDefaultInsteadOfDiscardingSettings() {
        val decoded = SettingsJson.decodeSettings("""{"startUrl":"https://example.com/","navMode":"STRICT","zoomPercent":150}""")
        assertThat(decoded).isEqualTo(KioskSettings(startUrl = "https://example.com/", zoomPercent = 150))
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        assertThat(SettingsJson.decodeSettings("""{"startUrl":"https://example.com/"}"""))
            .isEqualTo(KioskSettings(startUrl = "https://example.com/"))
    }

    @Test
    fun securityStateRoundTrips() {
        val state = SecurityState("aGFzaA==", "c2FsdA==", 120_000, failedAttempts = 3, lockedUntilEpochMs = 1_700_000_000_000)
        assertThat(SettingsJson.decodeSecurity(SettingsJson.encodeSecurity(state))).isEqualTo(state)
    }
}
