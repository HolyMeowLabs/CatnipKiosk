package com.holymeowlabs.catnipkiosk.kiosk

import com.holymeowlabs.catnipkiosk.policy.NavigationPolicy
import com.holymeowlabs.catnipkiosk.reload.ReloadPolicy
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.web.WebEvent
import java.net.URI
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface KioskUi {
    data object Showing : KioskUi
    data class Reconnecting(val attempt: Int, val retryAtMs: Long) : KioskUi
    /** [sameSite]: the start page leads elsewhere on its own host (only possible in page-only mode). */
    data class SetupProblem(val blockedHost: String, val sameSite: Boolean = false) : KioskUi
}

/** Decides what the kiosk shows over the page and when the page is reloaded. */
class KioskViewModel(
    private val nowMs: () -> Long,
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _ui = MutableStateFlow<KioskUi>(KioskUi.Showing)
    val ui: StateFlow<KioskUi> = _ui

    private val _toast = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val toast: SharedFlow<Unit> = _toast

    private val _reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reloadRequests: SharedFlow<Unit> = _reloadRequests

    private var attempt = 0
    private var retry: Job? = null
    private var schedule: Job? = null

    fun onWebEvent(e: WebEvent, settings: KioskSettings) {
        when (e) {
            WebEvent.PageLoaded -> {
                retry?.cancel()
                attempt = 0
                _ui.value = KioskUi.Showing
            }
            is WebEvent.MainFrameFailed -> if (settings.reloadOnFailure) scheduleRetry()
            is WebEvent.Blocked -> if (settings.showBlockedMessage) _toast.tryEmit(Unit)
            is WebEvent.StartPageBlocked -> {
                retry?.cancel()
                val startHost = NavigationPolicy.normalizeHost(runCatching { URI(settings.startUrl).host }.getOrNull())
                _ui.value = KioskUi.SetupProblem(e.blockedHost, sameSite = e.blockedHost == startHost)
            }
            WebEvent.RendererGone -> _reloadRequests.tryEmit(Unit)
        }
    }

    fun onNetworkAvailable() {
        if (_ui.value !is KioskUi.Reconnecting) return
        retry?.cancel()
        _reloadRequests.tryEmit(Unit)
    }

    /** (Re)arms the scheduled reload from now. */
    fun onSettingsChanged(settings: KioskSettings) {
        schedule?.cancel()
        schedule = scope.launch {
            var lastMs = nowMs()
            while (true) {
                val next = ReloadPolicy.nextScheduledReloadMs(settings.scheduledReload, lastMs, nowMs(), zone())
                    ?: return@launch
                delay(next - nowMs())
                lastMs = nowMs()
                _reloadRequests.emit(Unit)
            }
        }
    }

    private fun scheduleRetry() {
        attempt++
        val delayMs = ReloadPolicy.retryDelayMs(attempt)
        _ui.value = KioskUi.Reconnecting(attempt, nowMs() + delayMs)
        retry?.cancel()
        retry = scope.launch {
            delay(delayMs)
            _reloadRequests.emit(Unit)
        }
    }
}
