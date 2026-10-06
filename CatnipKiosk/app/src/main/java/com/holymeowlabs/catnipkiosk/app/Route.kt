package com.holymeowlabs.catnipkiosk.app

import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState

sealed interface Route {
    data object Setup : Route
    data object Kiosk : Route
    data object Pin : Route
    data object Settings : Route
    data object HardLockdownSteps : Route
}

/** Configured means both are stored; a missing SecurityState is never read as "no PIN". */
fun launchRoute(settings: KioskSettings?, security: SecurityState?): Route =
    if (settings != null && security != null) Route.Kiosk else Route.Setup

/**
 * Route after the store emits. Saving settings or a PIN attempt re-emits the store, so an admin
 * already on an admin screen stays there; only first load, setup completion or lost config move.
 */
fun nextRoute(current: Route?, settings: KioskSettings?, security: SecurityState?): Route {
    val launch = launchRoute(settings, security)
    return if (current == null || current == Route.Setup || launch == Route.Setup) launch else current
}
