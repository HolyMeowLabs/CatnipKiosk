package com.holymeowlabs.catnipkiosk.kiosk

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.web.KioskWebView
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var webView: KioskWebView
    private val toasts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val now = 1_000_000L

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = KioskWebView(InstrumentationRegistry.getInstrumentation().targetContext)
            webView.settingsProvider = { KioskSettings(startUrl = "https://example.com/secret-lobby") }
            // The page URL is reachable from the screen through the view; it must never be rendered.
            webView.loadUrl("https://example.com/secret-lobby")
        }
        // Wait until the view reports the URL, so a screen that rendered it would show it.
        val deadline = System.currentTimeMillis() + 10_000
        var url: String? = null
        while (url == null && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { url = webView.url }
            Thread.sleep(50)
        }
        check(url?.contains("secret-lobby") == true) { "page URL never set: $url" }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.destroy() }
    }

    private fun show(ui: KioskUi) = compose.setContent {
        KioskScreen(webView = webView, ui = ui, networkAvailable = false, toasts = toasts, nowMs = { now })
    }

    @Test
    fun reconnectingShowsCountdownAndAttemptButNotTheUrl() {
        // The countdown ticks forever; with auto-advance the test clock would never go idle.
        compose.mainClock.autoAdvance = false
        show(KioskUi.Reconnecting(attempt = 3, retryAtMs = now + 20_000))
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Reconnecting…").assertExists()
        compose.onNodeWithText("Retrying in 20 s · attempt 3").assertExists()
        compose.onAllNodes(hasText("example.com", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("secret-lobby", substring = true)).assertCountEquals(0)
    }

    @Test
    fun setupProblemNamesOnlyTheBlockedHost() {
        show(KioskUi.SetupProblem("login.example.net"))
        compose.onNodeWithText(
            "This page sends viewers to login.example.net, which isn't allowed. Add it under Settings › Navigation.",
        ).assertExists()
        compose.onAllNodes(hasText("secret-lobby", substring = true)).assertCountEquals(0)
    }

    @Test
    fun sameSiteSetupProblemGivesAdviceThatCanWork() {
        show(KioskUi.SetupProblem("example.com", sameSite = true))
        compose.onAllNodes(hasText("The whole site", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("isn't allowed. Add it", substring = true)).assertCountEquals(0)
    }

    @Test
    fun showingHasNoOverlay() {
        show(KioskUi.Showing)
        compose.onAllNodesWithText("Reconnecting…").assertCountEquals(0)
    }

    @Test
    fun blockedEventShowsTheMessage() {
        show(KioskUi.Showing)
        compose.onAllNodesWithText("That page isn't available on this screen.").assertCountEquals(0)
        toasts.tryEmit(Unit)
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("That page isn't available on this screen.").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
