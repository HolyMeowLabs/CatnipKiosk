package com.holymeowlabs.catnipkiosk.web

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.holymeowlabs.catnipkiosk.MainActivity
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZoomAndFullscreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SettingsRepository.get(context)
    private val server = MockWebServer()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = runBlocking {
        repo.clearAll()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody(
                    "<!doctype html><html><head><meta name='viewport' content='width=device-width'></head>" +
                        "<body style='margin:0'><div id='d' style='width:100vw;height:100vh;background:#09c' " +
                        "onclick='this.requestFullscreen()'>tap for full screen</div></body></html>",
                )
        }
        server.start()
    }

    @After
    fun tearDown() = runBlocking {
        scenario?.close()
        server.shutdown()
        repo.clearAll()
    }

    private suspend fun launch(zoom: Int) {
        repo.saveSettings(KioskSettings(startUrl = "http://localhost:${server.port}/start", zoomPercent = zoom))
        repo.saveSecurity(PinHasher(iterations = 1_000).create("1234"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor { onActivity { it.webView.progress } == 100 }
        delay(500)
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
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

    private suspend fun innerWidth(): Int {
        val result = CompletableDeferred<String>()
        onActivity { it.webView.evaluateJavascript("innerWidth") { r -> result.complete(r) } }
        return result.await().toInt()
    }

    @Test
    fun zoom150ShowsAboutTwoThirdsAsMuchPage() = runBlocking {
        launch(zoom = 100)
        val normal = innerWidth()
        scenario!!.close()
        launch(zoom = 150)
        val zoomed = innerWidth()
        assertWithMessage("innerWidth at 100%% = $normal, at 150%% = $zoomed")
            .that(zoomed.toDouble()).isWithin(normal * 0.05).of(normal * 2.0 / 3.0)
    }

    @Test
    fun rendererCrashDuringFullScreenClosesTheFullScreenView() = runBlocking {
        launch(zoom = 100)
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        waitFor { onActivity { it.isShowingFullScreen } }
        onActivity { it.webView.loadUrl("chrome://crash") }
        waitFor { !onActivity { it.isShowingFullScreen } }
    }

    @Test
    fun fullScreenShowsOverThePageAndBackLeavesItWithoutNavigating() = runBlocking {
        launch(zoom = 100)
        val url = onActivity { it.webView.url }
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        waitFor { onActivity { it.isShowingFullScreen } }
        device.pressBack()
        waitFor { !onActivity { it.isShowingFullScreen } }
        delay(500)
        assertThat(onActivity { it.webView.url }).isEqualTo(url)
    }
}
