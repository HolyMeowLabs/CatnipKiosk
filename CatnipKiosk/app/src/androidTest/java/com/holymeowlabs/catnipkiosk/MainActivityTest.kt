package com.holymeowlabs.catnipkiosk

import android.content.Context
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.lockdown.LockdownController
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import com.holymeowlabs.catnipkiosk.isTv
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SettingsRepository.get(context)
    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, () -> MockResponse>()
    private val requested = CopyOnWriteArrayList<String>()
    private var scenario: ActivityScenario<MainActivity>? = null

    private val start get() = "http://localhost:${server.port}/start"

    @Before
    fun setUp() = runBlocking {
        repo.clearAll()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                requested += path
                return routes[path]?.invoke() ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = runBlocking {
        scenario?.close()
        server.shutdown()
        repo.clearAll()
    }

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<!doctype html><html><body>$body</body></html>")

    private suspend fun configure(settings: KioskSettings = KioskSettings(startUrl = start)) {
        repo.saveSettings(settings)
        repo.saveSecurity(SecurityState("aGFzaA==", "c2FsdA==", 60_000))
    }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private suspend fun awaitRequests(path: String, count: Int, timeoutMs: Long = 10_000) =
        waitFor("$count request(s) for $path", timeoutMs) { requested.count { it == path } >= count }

    /** Polls [condition]; on timeout reports what was awaited, the requests seen and the page URL. */
    private suspend fun waitFor(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                val url = scenario?.let { onActivity { a -> a.webView.url } }
                throw AssertionError("Timed out waiting for $what; requests=$requested url=$url")
            }
            delay(50)
        }
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** A configuration change Android handles by recreating the Activity must not close the kiosk. */
    @Test
    fun recreatingTheActivityKeepsTheKioskRunning() = runBlocking {
        routes["/start"] = { html("start") }
        configure()
        launch()
        awaitRequests("/start", 1)
        scenario!!.recreate()
        delay(1_000)
        assertThat(scenario!!.state).isEqualTo(Lifecycle.State.RESUMED)
    }

    /** Becoming the Home app makes Android start a second instance in a Home task; the old one must go. */
    @Test
    fun becomingHomeLeavesASingleKioskInstance() = runBlocking {
        routes["/start"] = { html("start") }
        configure()
        launch()
        awaitRequests("/start", 1)
        shell("cmd role add-role-holder android.app.role.HOME ${context.packageName}")
        // Google TV keeps the Home role for its own launcher; this scenario is tablet-only.
        assumeTrue("Home key ignores the Home role on TV", LockdownController(context).isHomeApp() && !isTv(context))
        try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
            waitFor("original instance destroyed") { scenario!!.state == Lifecycle.State.DESTROYED }
        } finally {
            shell("cmd role remove-role-holder android.app.role.HOME ${context.packageName}")
        }
    }

    private fun shell(command: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    @Test
    fun configuredLaunchLoadsTheStartPage() = runBlocking {
        routes["/start"] = { html("start") }
        configure()
        launch()
        awaitRequests("/start", 1)
    }

    @Test
    fun unconfiguredLaunchLoadsNothing() = runBlocking {
        routes["/start"] = { html("start") }
        repo.saveSettings(KioskSettings(startUrl = start)) // settings without security: still Setup
        launch()
        delay(3_000)
        assertThat(requested).isEmpty()
    }

    @Test
    fun mainFrameFailureIsRetried() = runBlocking {
        val calls = AtomicInteger()
        routes["/start"] = { if (calls.getAndIncrement() == 0) MockResponse().setResponseCode(503) else html("ok") }
        configure()
        launch()
        awaitRequests("/start", 2, timeoutMs = 15_000)
    }

    @Test
    fun startPageIsLaidOutAtFullViewportHeight() = runBlocking {
        routes["/start"] = { html("""<div id="d" style="height:100vh">tall</div>""") }
        configure()
        launch()
        waitFor("start page rendered") { onActivity { it.webView.progress } == 100 }
        delay(500)
        val heights = CompletableDeferred<String>()
        onActivity {
            it.webView.evaluateJavascript("document.getElementById('d').offsetHeight + ',' + innerHeight") { r ->
                heights.complete(r.trim('"'))
            }
        }
        val (div, viewport) = heights.await().split(',').map(String::toInt)
        assertThat(viewport).isGreaterThan(0)
        assertThat(div).isEqualTo(viewport)
    }

    @Test
    fun backGoesBackWithinTheSiteAndIsANoOpOnTheStartPage() = runBlocking {
        routes["/start"] = {
            html("""<a href="/two" style="position:fixed;left:0;top:0;width:100vw;height:100vh">two</a>""")
        }
        routes["/two"] = { html("two") }
        configure()
        launch()
        awaitRequests("/start", 1)
        waitFor("start page rendered") { onActivity { it.webView.progress } == 100 }
        delay(500)
        // A real tap: Chromium skips history entries left without user activation.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        waitFor("page /two") { onActivity { it.webView.url }?.endsWith("/two") == true }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        waitFor("back to /start") { onActivity { it.webView.url }?.endsWith("/start") == true }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        delay(500)
        assertThat(scenario!!.state).isEqualTo(Lifecycle.State.RESUMED)
    }

    @Test
    fun keepScreenOnFollowsTheSetting() = runBlocking {
        routes["/start"] = { html("start") }
        configure(KioskSettings(startUrl = start, keepScreenOn = true))
        launch()
        awaitRequests("/start", 1)
        assertThat(onActivity { it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON })
            .isNotEqualTo(0)
        repo.saveSettings(KioskSettings(startUrl = start, keepScreenOn = false))
        waitFor("keep-screen-on cleared", 5_000) {
            onActivity { it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON } == 0
        }
    }
}
