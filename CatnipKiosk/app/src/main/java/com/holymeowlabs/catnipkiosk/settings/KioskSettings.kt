package com.holymeowlabs.catnipkiosk.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NavMode { PAGE_ONLY, DOMAIN }

@Serializable
sealed interface ScheduledReload {
    @Serializable
    @SerialName("off")
    data object Off : ScheduledReload

    @Serializable
    @SerialName("every_minutes")
    data class EveryMinutes(val minutes: Int) : ScheduledReload

    @Serializable
    @SerialName("daily_at")
    data class DailyAt(val hour: Int, val minute: Int) : ScheduledReload
}

@Serializable
data class KioskSettings(
    val startUrl: String,
    val navMode: NavMode = NavMode.DOMAIN,
    val includeSubdomains: Boolean = true,
    /** Bare lowercase punycode hosts (see DomainInput); each also allows its subdomains. */
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
