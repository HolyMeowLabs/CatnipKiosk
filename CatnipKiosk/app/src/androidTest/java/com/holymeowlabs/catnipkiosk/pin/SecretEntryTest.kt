package com.holymeowlabs.catnipkiosk.pin

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.MainActivity
import com.holymeowlabs.catnipkiosk.app.Route
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run on the tablet AVD (touch); the D-pad sequence also works there. */
@RunWith(AndroidJUnit4::class)
class SecretEntryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SettingsRepository.get(context)
    private val server = MockWebServer()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() = runBlocking {
        repo.clearAll()
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html").setBody(
                "<!doctype html><html><body><p>page</p>" +
                    "<script>window.keys = 0; addEventListener('keydown', () => keys++)</script></body></html>",
            ),
        )
        server.start()
        repo.saveSettings(KioskSettings(startUrl = "http://localhost:${server.port}/start"))
        repo.saveSecurity(PinHasher(iterations = 1_000).create("1234"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor { onActivity { it.webView.progress } == 100 }
        delay(500)
    }

    @After
    fun tearDown() = runBlocking {
        scenario.close()
        server.shutdown()
        repo.clearAll()
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private suspend fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met within $timeoutMs ms" }
            delay(50)
        }
    }

    private suspend fun pageKeyCount(): Int {
        val result = CompletableDeferred<String>()
        onActivity { it.webView.evaluateJavascript("keys") { r -> result.complete(r) } }
        return result.await().toInt()
    }

    @Test
    fun dpadSequenceOpensPinAndThePageStillSeesEveryKey() = runBlocking {
        onActivity { it.webView.requestFocus() }
        val up = KeyEvent.KEYCODE_DPAD_UP
        val down = KeyEvent.KEYCODE_DPAD_DOWN
        val left = KeyEvent.KEYCODE_DPAD_LEFT
        val right = KeyEvent.KEYCODE_DPAD_RIGHT
        // Count the page's keys before the PIN screen hides the WebView.
        listOf(up, up, down, down, left, right, left).forEach { device.pressKeyCode(it) }
        delay(500)
        assertThat(pageKeyCount()).isEqualTo(7)
        device.pressKeyCode(right)
        waitFor { onActivity { it.route } == Route.Pin }
        assertThat(pageKeyCount()).isEqualTo(8)
    }

    @Test
    fun fiveTapsInTheTopLeftCornerOpenPin() = runBlocking {
        repeat(5) { device.click(10, 10) }
        waitFor { onActivity { it.route } == Route.Pin }
    }

    @Test
    fun fiveTapsInTheCentreDoNotOpenPin() = runBlocking {
        repeat(5) { device.click(device.displayWidth / 2, device.displayHeight / 2) }
        delay(1_000)
        assertThat(onActivity { it.route }).isEqualTo(Route.Kiosk)
    }

    @Test
    fun failedAttemptsSurviveCancellingAndReopeningThePinScreen() = runBlocking {
        repo.saveSettings(KioskSettings(startUrl = "http://localhost:${server.port}/start", pinLockoutEnabled = true))
        delay(500)
        repeat(2) {
            repeat(5) { device.click(10, 10) }
            waitFor { onActivity { it.route } == Route.Pin }
            "9999".forEach { c -> device.pressKeyCode(KeyEvent.KEYCODE_0 + (c - '0')) }
            device.findObject(By.text("OK")).click()
            delay(1_000)
            device.pressBack()
            waitFor { onActivity { it.route } == Route.Kiosk }
        }
        repeat(5) { device.click(10, 10) }
        waitFor { onActivity { it.route } == Route.Pin }
        "9999".forEach { c -> device.pressKeyCode(KeyEvent.KEYCODE_0 + (c - '0')) }
        device.findObject(By.text("OK")).click()
        waitFor { device.hasObject(By.textContains("2 attempts left")) }
    }

    @Test
    fun correctPinOpensSettingsAndBackFromPinReturnsToTheKiosk() = runBlocking {
        repeat(5) { device.click(10, 10) }
        waitFor { onActivity { it.route } == Route.Pin }
        device.pressBack()
        waitFor { onActivity { it.route } == Route.Kiosk }
        assertThat(onActivity { it.webView.visibility }).isEqualTo(View.VISIBLE)

        repeat(5) { device.click(10, 10) }
        waitFor { onActivity { it.route } == Route.Pin }
        assertThat(onActivity { it.webView.visibility }).isEqualTo(View.INVISIBLE)
        "1234".forEach { device.pressKeyCode(KeyEvent.KEYCODE_0 + (it - '0')) }
        device.findObject(By.text("OK")).click()
        waitFor { onActivity { it.route } == Route.Settings }
    }
}
