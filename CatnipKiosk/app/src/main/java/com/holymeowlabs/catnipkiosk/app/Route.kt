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
