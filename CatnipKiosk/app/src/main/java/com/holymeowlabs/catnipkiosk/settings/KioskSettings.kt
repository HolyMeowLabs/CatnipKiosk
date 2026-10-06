package com.holymeowlabs.catnipkiosk.settings

enum class NavMode { PAGE_ONLY, DOMAIN }

sealed interface ScheduledReload {
    data object Off : ScheduledReload
    data class EveryMinutes(val minutes: Int) : ScheduledReload
    data class DailyAt(val hour: Int, val minute: Int) : ScheduledReload
}

data class KioskSettings(
    val startUrl: String,
    val navMode: NavMode = NavMode.DOMAIN,
    val includeSubdomains: Boolean = true,
    /** Bare lowercase hosts; each also allows its subdomains. */
    val extraDomains: List<String> = emptyList(),
    val showBlockedMessage: Boolean = true,
    /** 50..200 in steps of 10. */
    val zoomPercent: Int = 100,
    val cursorEnabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val reloadOnFailure: Boolean = true,
    val scheduledReload: ScheduledReload = ScheduledReload.Off,
    val startOnBoot: Boolean = true,
    val pinLockoutEnabled: Boolean = false,
) {
    companion object {
        fun defaults(startUrl: String, isTv: Boolean) =
            KioskSettings(startUrl = startUrl, cursorEnabled = isTv)
    }
}
