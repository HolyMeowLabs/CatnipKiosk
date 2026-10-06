package com.holymeowlabs.catnipkiosk.cursor

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

/** Run on the TV AVD. */
@RunWith(AndroidJUnit4::class)
class CursorClickTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SettingsRepository.get(context)
    private val server = MockWebServer()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() = runBlocking {
        repo.clearAll()
        // The button covers the top half; the cursor starts at the centre, just below it.
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html").setBody(
                "<!doctype html><html><body style='margin:0'>" +
                    "<button id='b' style='position:fixed;left:0;top:0;width:100vw;height:50vh' " +
                    "onclick='window.clicks=(window.clicks||0)+1'>b</button>" +
                    "<script>window.keys=0; addEventListener('keydown', () => keys++)</script></body></html>",
            ),
        )
        server.start()
        repo.saveSettings(KioskSettings(startUrl = "http://localhost:${server.port}/start", cursorEnabled = true))
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

    private suspend fun js(expr: String): String {
        val result = CompletableDeferred<String>()
        onActivity { it.webView.evaluateJavascript(expr) { r -> result.complete(r) } }
        return result.await()
    }

    @Test
    fun okClicksWhereTheCursorIsAndArrowsDoNotReachThePage() = runBlocking {
        repeat(5) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP) }
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor { runBlocking { js("window.clicks || 0") } == "1" }
        assertThat(js("keys")).isEqualTo("0")
    }

    @Test
    fun okBelowTheButtonDoesNotClickIt() = runBlocking {
        repeat(5) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN) }
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        delay(1_000)
        assertThat(js("window.clicks || 0")).isEqualTo("0")
    }

    @Test
    fun secretSequenceStillOpensThePinInCursorMode() = runBlocking {
        val u = KeyEvent.KEYCODE_DPAD_UP
        val d = KeyEvent.KEYCODE_DPAD_DOWN
        val l = KeyEvent.KEYCODE_DPAD_LEFT
        val r = KeyEvent.KEYCODE_DPAD_RIGHT
        listOf(u, u, d, d, l, r, l, r).forEach { device.pressKeyCode(it) }
        waitFor { onActivity { it.route } == Route.Pin }
    }
}
