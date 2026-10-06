package com.holymeowlabs.catnipkiosk.web

/** What the kiosk WebView reports to its owner. */
sealed interface WebEvent {
    data object PageLoaded : WebEvent
    data class Blocked(val url: String) : WebEvent

    /** The start page itself redirected to a host the policy blocks; names only the host. */
    data class StartPageBlocked(val blockedHost: String) : WebEvent

    /** Main-frame network error, SSL error or HTTP 5xx. */
    data class MainFrameFailed(val description: String) : WebEvent
    data object RendererGone : WebEvent
}
