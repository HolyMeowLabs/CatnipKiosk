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

/** An unlocked admin screen left alone this long returns to the kiosk. */
const val ADMIN_IDLE_MS = 120_000L

private fun Route?.isAdmin() = this == Route.Pin || this == Route.Settings

/** Leaving the app (Home, screen off, another app) ends the admin session; return needs the PIN again. */
fun routeAfterStop(current: Route?): Route? = if (current.isAdmin()) Route.Kiosk else current

fun adminSessionExpired(current: Route?, lastInputMs: Long, nowMs: Long): Boolean =
    current.isAdmin() && nowMs - lastInputMs >= ADMIN_IDLE_MS
