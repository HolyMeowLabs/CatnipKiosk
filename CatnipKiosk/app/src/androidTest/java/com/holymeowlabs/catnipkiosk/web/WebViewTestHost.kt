package com.holymeowlabs.catnipkiosk.web

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/** Hosts a [KioskWebView] full screen in a bare activity and records its events. */
class WebViewTestHost : Closeable {
    private val scenario = ActivityScenario.launch(ComponentActivity::class.java)
    val events = Channel<WebEvent>(Channel.UNLIMITED)
    val log = CopyOnWriteArrayList<WebEvent>()
    lateinit var view: KioskWebView
        private set

    @Volatile
    private var settings = KioskSettings(startUrl = "about:blank")

    init {
        scenario.onActivity { activity ->
            view = KioskWebView(activity).also {
                it.settingsProvider = { settings }
                it.onEvent = { e -> log += e; events.trySend(e) }
                activity.setContentView(it)
            }
        }
    }

    /** Makes [url] the start URL and loads it. */
    fun load(url: String) {
        settings = KioskSettings(startUrl = url)
        onMain { view.loadStart() }
    }

    suspend inline fun <reified T : WebEvent> awaitEvent(timeoutMs: Long = 10_000): T =
        withTimeout(timeoutMs) {
            var e = events.receive()
            while (e !is T) e = events.receive()
            e
        }

    fun evalJs(script: String): String? {
        var result: String? = null
        val done = CountDownLatch(1)
        onMain { view.evaluateJavascript(script) { result = it; done.countDown() } }
        check(done.await(5, TimeUnit.SECONDS)) { "evaluateJavascript timed out" }
        return result
    }

    fun url(): String? {
        var url: String? = null
        onMain { url = view.url }
        return url
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    override fun close() {
        onMain { view.destroy() }
        scenario.close()
    }
}
