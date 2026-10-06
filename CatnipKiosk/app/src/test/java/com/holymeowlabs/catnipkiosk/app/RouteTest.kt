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
    fun firstStoreReadPicksTheLaunchRoute() {
        assertThat(nextRoute(null, settings, security)).isEqualTo(Route.Kiosk)
        assertThat(nextRoute(null, null, null)).isEqualTo(Route.Setup)
    }

    @Test
    fun storeUpdatesDoNotPullTheAdminOutOfPinOrSettings() {
        // Each PIN attempt saves SecurityState, which re-emits the store.
        assertThat(nextRoute(Route.Pin, settings, security)).isEqualTo(Route.Pin)
        assertThat(nextRoute(Route.Settings, settings, security)).isEqualTo(Route.Settings)
    }

    @Test
    fun completingSetupMovesToTheKiosk() {
        assertThat(nextRoute(Route.Setup, settings, security)).isEqualTo(Route.Kiosk)
    }

    @Test
    fun losingTheConfigurationAlwaysGoesToSetup() {
        assertThat(nextRoute(Route.Settings, null, security)).isEqualTo(Route.Setup)
        assertThat(nextRoute(Route.Kiosk, settings, null)).isEqualTo(Route.Setup)
    }

    @Test
    fun leavingTheAppEndsTheAdminSession() {
        assertThat(routeAfterStop(Route.Settings)).isEqualTo(Route.Kiosk)
        assertThat(routeAfterStop(Route.Pin)).isEqualTo(Route.Kiosk)
        assertThat(routeAfterStop(Route.Setup)).isEqualTo(Route.Setup)
        assertThat(routeAfterStop(Route.Kiosk)).isEqualTo(Route.Kiosk)
    }

    @Test
    fun anIdleAdminSessionTimesOut() {
        val last = 1_000_000L
        assertThat(adminSessionExpired(Route.Settings, last, last + ADMIN_IDLE_MS - 1)).isFalse()
        assertThat(adminSessionExpired(Route.Settings, last, last + ADMIN_IDLE_MS)).isTrue()
        assertThat(adminSessionExpired(Route.Pin, last, last + ADMIN_IDLE_MS)).isTrue()
        assertThat(adminSessionExpired(Route.Kiosk, last, last + ADMIN_IDLE_MS * 10)).isFalse()
        assertThat(adminSessionExpired(Route.Setup, last, last + ADMIN_IDLE_MS * 10)).isFalse()
    }

    @Test
    fun missingSecurityIsNeverTreatedAsNoPin() {
        assertThat(launchRoute(settings, null)).isEqualTo(Route.Setup)
    }
}
