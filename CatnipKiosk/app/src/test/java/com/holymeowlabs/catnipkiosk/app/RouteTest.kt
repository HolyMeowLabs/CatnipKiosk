package com.holymeowlabs.catnipkiosk.app

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import org.junit.Test

class RouteTest {
    private val settings = KioskSettings(startUrl = "https://example.com/")
    private val security = SecurityState("aGFzaA==", "c2FsdA==", 60_000)

    @Test
    fun configuredDeviceStartsInTheKiosk() {
        assertThat(launchRoute(settings, security)).isEqualTo(Route.Kiosk)
    }

    @Test
    fun missingSettingsGoesToSetup() {
        assertThat(launchRoute(null, security)).isEqualTo(Route.Setup)
    }

    @Test
    fun missingSecurityIsNeverTreatedAsNoPin() {
        assertThat(launchRoute(settings, null)).isEqualTo(Route.Setup)
    }
}
