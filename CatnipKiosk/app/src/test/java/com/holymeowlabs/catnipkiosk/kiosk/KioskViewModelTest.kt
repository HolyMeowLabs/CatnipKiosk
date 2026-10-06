package com.holymeowlabs.catnipkiosk.kiosk

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.NavMode
import com.holymeowlabs.catnipkiosk.settings.ScheduledReload
import com.holymeowlabs.catnipkiosk.web.WebEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KioskViewModelTest {

    private val settings = KioskSettings(startUrl = "https://example.com/lobby")

    private class Harness(scope: TestScope) {
        val vm = KioskViewModel({ scope.currentTime }, scope.backgroundScope)
        val reloads = mutableListOf<Unit>()
        val toasts = mutableListOf<Unit>()

        init {
            val d = UnconfinedTestDispatcher(scope.testScheduler)
            scope.backgroundScope.launch(d) { vm.reloadRequests.toList(reloads) }
            scope.backgroundScope.launch(d) { vm.toast.toList(toasts) }
        }
    }

    @Test
    fun failureSchedulesFirstRetryAfterFiveSeconds() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.Reconnecting(1, 5_000))
        advanceTimeBy(4_999); assertThat(h.reloads).isEmpty()
        advanceTimeBy(2); assertThat(h.reloads).hasSize(1)
    }

    @Test
    fun consecutiveFailuresEscalateTheDelay() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
        advanceTimeBy(5_001)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.Reconnecting(2, currentTime + 10_000))
        advanceTimeBy(9_999); assertThat(h.reloads).hasSize(1)
        advanceTimeBy(2); assertThat(h.reloads).hasSize(2)
    }

    @Test
    fun pageLoadedShowsThePageAndResetsTheAttemptCount() = runTest {
        val h = Harness(this)
        repeat(2) {
            h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
            advanceTimeBy(60_001)
        }
        h.vm.onWebEvent(WebEvent.PageLoaded, settings)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.Showing)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.Reconnecting(1, currentTime + 5_000))
    }

    @Test
    fun networkBackWhileReconnectingRetriesImmediatelyOnce() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
        h.vm.onNetworkAvailable()
        runCurrent()
        assertThat(h.reloads).hasSize(1)
        advanceTimeBy(10_000)
        assertThat(h.reloads).hasSize(1)
    }

    @Test
    fun networkBackWhileShowingDoesNotReload() = runTest {
        val h = Harness(this)
        h.vm.onNetworkAvailable()
        runCurrent()
        assertThat(h.reloads).isEmpty()
    }

    @Test
    fun failureWithReloadOnFailureOffStaysShowingWithoutReloading() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings.copy(reloadOnFailure = false))
        advanceTimeBy(120_000)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.Showing)
        assertThat(h.reloads).isEmpty()
    }

    @Test
    fun blockedShowsTheMessageOnlyWhenEnabled() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.Blocked("https://evil.example/"), settings)
        assertThat(h.toasts).hasSize(1)
        h.vm.onWebEvent(WebEvent.Blocked("https://evil.example/"), settings.copy(showBlockedMessage = false))
        assertThat(h.toasts).hasSize(1)
    }

    @Test
    fun startPageBlockedIsASetupProblemNamingTheHostWithNoRetry() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.MainFrameFailed("net"), settings)
        h.vm.onWebEvent(WebEvent.StartPageBlocked("login.example.net"), settings)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.SetupProblem("login.example.net"))
        advanceTimeBy(120_000)
        assertThat(h.reloads).isEmpty()
    }

    @Test
    fun startPageRedirectWithinTheSameSiteIsFlaggedSoTheAdviceFits() = runTest {
        val h = Harness(this)
        val pageOnly = settings.copy(navMode = NavMode.PAGE_ONLY)
        h.vm.onWebEvent(WebEvent.StartPageBlocked("example.com"), pageOnly)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.SetupProblem("example.com", sameSite = true))
        h.vm.onWebEvent(WebEvent.StartPageBlocked("login.example.net"), pageOnly)
        assertThat(h.vm.ui.value).isEqualTo(KioskUi.SetupProblem("login.example.net", sameSite = false))
    }

    @Test
    fun scheduledReloadEveryMinuteFiresAndRearms() = runTest {
        val h = Harness(this)
        h.vm.onSettingsChanged(settings.copy(scheduledReload = ScheduledReload.EveryMinutes(1)))
        advanceTimeBy(59_999); assertThat(h.reloads).isEmpty()
        advanceTimeBy(2); assertThat(h.reloads).hasSize(1)
        advanceTimeBy(60_000); assertThat(h.reloads).hasSize(2)
    }

    @Test
    fun turningScheduledReloadOffCancelsIt() = runTest {
        val h = Harness(this)
        h.vm.onSettingsChanged(settings.copy(scheduledReload = ScheduledReload.EveryMinutes(1)))
        advanceTimeBy(30_000)
        h.vm.onSettingsChanged(settings.copy(scheduledReload = ScheduledReload.Off))
        advanceTimeBy(120_000)
        assertThat(h.reloads).isEmpty()
    }

    @Test
    fun rendererGoneRequestsAReload() = runTest {
        val h = Harness(this)
        h.vm.onWebEvent(WebEvent.RendererGone, settings)
        runCurrent()
        assertThat(h.reloads).hasSize(1)
    }
}
