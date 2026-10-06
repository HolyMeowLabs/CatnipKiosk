package com.holymeowlabs.catnipkiosk.web

import android.content.Context
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskWebViewTest {

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, MockResponse>()
    private val requested = CopyOnWriteArrayList<String>()
    private lateinit var host: WebViewTestHost

    /** The start host; everything on it is allowed (DOMAIN mode, no extras). */
    private val local get() = "http://localhost:${server.port}"

    /** A different host on the same server, so a blocked request would still be recorded. */
    private val other get() = "http://127.0.0.1:${server.port}"

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                requested += path
                return routes[path] ?: MockResponse().setResponseCode(404).setBody("not found")
            }
        }
        server.start()
        host = WebViewTestHost()
    }

    @After
    fun tearDown() {
        host.close()
        server.shutdown()
    }

    private fun html(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<!doctype html><html><body>$body</body></html>")

    private fun redirect(to: String) = MockResponse().setResponseCode(302).setHeader("Location", to)

    private suspend fun startWith(body: String) {
        routes["/start"] = html(body)
        host.load("$local/start")
        host.awaitEvent<WebEvent.PageLoaded>()
    }

    @Test
    fun offHostLinkIsBlockedAndNeverRequested() = runBlocking {
        startWith("""<a id="l" href="$other/x">x</a>""")
        host.evalJs("document.getElementById('l').click()")
        assertThat(host.awaitEvent<WebEvent.Blocked>().url).endsWith("/x")
        assertThat(requested).doesNotContain("/x")
    }

    @Test
    fun allowedLinkLoads() = runBlocking {
        routes["/ok"] = html("ok")
        startWith("""<a id="l" href="$local/ok">ok</a>""")
        host.evalJs("document.getElementById('l').click()")
        host.awaitEvent<WebEvent.PageLoaded>()
        assertThat(host.url()).endsWith("/ok")
        assertThat(host.log.filterIsInstance<WebEvent.Blocked>()).isEmpty()
    }

    @Test
    fun offHostRedirectAfterStartIsBlocked() = runBlocking {
        routes["/go"] = redirect("$other/target")
        startWith("""<a id="l" href="$local/go">go</a>""")
        host.evalJs("document.getElementById('l').click()")
        assertThat(host.awaitEvent<WebEvent.Blocked>().url).endsWith("/target")
        assertThat(requested).doesNotContain("/target")
        assertThat(host.log.filterIsInstance<WebEvent.StartPageBlocked>()).isEmpty()
    }

    @Test
    fun startPageRedirectingOffHostReportsOnlyTheHost() = runBlocking {
        routes["/start"] = redirect("$other/sso?token=secret")
        host.load("$local/start")
        assertThat(host.awaitEvent<WebEvent.StartPageBlocked>().blockedHost).isEqualTo("127.0.0.1")
        assertThat(requested).doesNotContain("/sso")
    }

    @Test
    fun startPageScriptBounceOffHostReportsOnlyTheHost() = runBlocking {
        routes["/start"] = html("""<script>location.href = "$other/sso?token=secret"</script>""")
        host.load("$local/start")
        assertThat(host.awaitEvent<WebEvent.StartPageBlocked>().blockedHost).isEqualTo("127.0.0.1")
        assertThat(requested).doesNotContain("/sso")
    }

    @Test
    fun userTapOffHostWhileStartPageStillLoadingIsBlockedNotASetupProblem() = runBlocking {
        routes["/slow.png"] = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        routes["/start"] = html(
            """<a href="$other/x" style="position:fixed;left:0;top:0;width:100vw;height:100vh">x</a>
            <img src="$local/slow.png">""",
        )
        host.load("$local/start")
        withTimeout(5_000) { while (host.evalJs("document.readyState") != "\"interactive\"") delay(50) }
        tapCentre()
        assertThat(host.awaitEvent<WebEvent.Blocked>().url).endsWith("/x")
        assertThat(host.log.filterIsInstance<WebEvent.StartPageBlocked>()).isEmpty()
    }

    @Test
    fun startPageAutoPostingOffHostIsASetupProblemNotAReloadLoop() = runBlocking {
        routes["/post"] = html("posted")
        routes["/start"] = html(
            """<form id="f" method="post" action="$other/post"><input name="a" value="1"></form>
            <script>document.getElementById('f').submit()</script>""",
        )
        host.load("$local/start")
        assertThat(host.awaitEvent<WebEvent.StartPageBlocked>().blockedHost).isEqualTo("127.0.0.1")
        delay(2_000)
        assertThat(requested.count { it == "/start" }).isEqualTo(1)
    }

    @Test
    fun offHostFormPostIsStoppedAndStartReloads() = runBlocking {
        routes["/post"] = html("posted")
        startWith("""<form id="f" method="post" action="$other/post"><input name="a" value="1"></form>""")
        host.evalJs("document.getElementById('f').submit()")
        assertThat(host.awaitEvent<WebEvent.Blocked>().url).endsWith("/post")
        host.awaitEvent<WebEvent.PageLoaded>()
        assertThat(host.url()).isEqualTo("$local/start")
    }

    @Test
    fun targetBlankToAllowedHostLoadsInPlace() = runBlocking {
        routes["/ok"] = html("ok")
        startWith(fullScreenBlankLink("$local/ok"))
        tapCentre()
        host.awaitEvent<WebEvent.PageLoaded>()
        assertThat(host.url()).endsWith("/ok")
    }

    @Test
    fun windowOpenToAllowedHostLoadsInPlace() = runBlocking {
        routes["/ok"] = html("ok")
        startWith(fullScreenButton("window.open('$local/ok')"))
        tapCentre()
        host.awaitEvent<WebEvent.PageLoaded>()
        assertThat(host.url()).endsWith("/ok")
    }

    @Test
    fun targetBlankToOtherHostIsBlocked() = runBlocking {
        startWith(fullScreenBlankLink("$other/x"))
        tapCentre()
        assertThat(host.awaitEvent<WebEvent.Blocked>().url).endsWith("/x")
        assertThat(requested).doesNotContain("/x")
    }

    @Test
    fun windowOpenToOtherHostIsBlocked() = runBlocking {
        startWith(fullScreenButton("window.open('$other/x')"))
        tapCentre()
        assertThat(host.awaitEvent<WebEvent.Blocked>().url).endsWith("/x")
        assertThat(requested).doesNotContain("/x")
    }

    @Test
    fun subframeNavigationIsNotFiltered() = runBlocking {
        routes["/frame"] =
            html("""<a id="l" href="$other/frame2">f</a><script>document.getElementById('l').click()</script>""")
        routes["/frame2"] = html("frame2")
        // WebView consults the client for subframes only on non-http schemes, hence a mailto: frame.
        routes["/mail"] =
            html("""<a id="m" href="mailto:a@example.com">m</a><script>document.getElementById('m').click()</script>""")
        startWith("""<iframe src="$other/frame"></iframe><iframe src="$other/mail"></iframe>""")
        withTimeout(10_000) { while ("/frame2" !in requested || "/mail" !in requested) delay(50) }
        delay(1_000)
        assertThat(host.log.filter { it is WebEvent.Blocked || it is WebEvent.StartPageBlocked }).isEmpty()
        assertThat(host.url()).isEqualTo("$local/start")
    }

    @Test
    fun serverErrorOnMainFrameFails() = runBlocking<Unit> {
        routes["/start"] = html("down", code = 503)
        host.load("$local/start")
        host.awaitEvent<WebEvent.MainFrameFailed>()
    }

    @Test
    fun unreachableStartPageFails() = runBlocking<Unit> {
        val port = server.port
        server.shutdown()
        host.load("http://localhost:$port/start")
        host.awaitEvent<WebEvent.MainFrameFailed>()
    }

    @Test
    fun subresourceErrorsDoNotFailThePage() = runBlocking {
        routes["/broken.png"] = MockResponse().setResponseCode(503)
        startWith("""<img src="$local/broken.png"><img src="http://localhost:1/none.png">""")
        withTimeout(10_000) { while ("/broken.png" !in requested) delay(50) }
        delay(1_000)
        assertThat(host.log.filterIsInstance<WebEvent.MainFrameFailed>()).isEmpty()
    }

    @Test
    fun notFoundIsShownAsReturned() = runBlocking {
        routes["/start"] = html("missing", code = 404)
        host.load("$local/start")
        host.awaitEvent<WebEvent.PageLoaded>()
        delay(500)
        assertThat(host.log.filterIsInstance<WebEvent.MainFrameFailed>()).isEmpty()
    }

    @Test
    fun audioAutoplaysWithoutAGesture() = runBlocking {
        routes["/tone.wav"] =
            MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(silentWav()))
        startWith("""<audio id="a" autoplay loop src="/tone.wav"></audio>""")
        val playing = runCatching {
            withTimeout(5_000) {
                while (host.evalJs("!document.getElementById('a').paused") != "true") delay(100)
            }
        }.isSuccess
        assertThat(playing).isTrue()
    }

    @Test
    fun sslErrorNeverProceeds() = runBlocking {
        val tls = untrustedTlsServer()
        tls.enqueue(html("secret page"))
        try {
            host.load("https://localhost:${tls.port}/start")
            host.awaitEvent<WebEvent.MainFrameFailed>()
            delay(500)
            assertThat(tls.requestCount).isEqualTo(0)
        } finally {
            tls.shutdown()
        }
    }

    @Test
    fun subresourceSslErrorDoesNotFailThePage() = runBlocking {
        val tls = untrustedTlsServer()
        try {
            startWith("""<img id="i" src="https://127.0.0.1:${tls.port}/x.png">""")
            delay(1_500)
            assertThat(host.log.filterIsInstance<WebEvent.MainFrameFailed>()).isEmpty()
            assertThat(tls.requestCount).isEqualTo(0)
        } finally {
            tls.shutdown()
        }
    }

    /** HTTPS server whose self-signed certificate WebView will reject. */
    private fun untrustedTlsServer() = MockWebServer().apply {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").build()
        useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
        start()
    }

    @Test
    fun rendererCrashIsReportedNotFatal() = runBlocking<Unit> {
        startWith("ok")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { host.view.loadUrl("chrome://crash") }
        host.awaitEvent<WebEvent.RendererGone>()
    }

    @Test
    fun selectionMenuHidesItemsOutsideTheAllowList() {
        lateinit var copy: MenuItem
        lateinit var share: MenuItem
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val mode = host.view.startActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    copy = menu.add(Menu.NONE, android.R.id.copy, 0, "Copy")
                    share = menu.add(Menu.NONE, 0x7f0a1234, 1, "Share")
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                override fun onDestroyActionMode(mode: ActionMode) = Unit
            }, ActionMode.TYPE_FLOATING)
            mode?.finish()
        }
        assertThat(copy.isVisible).isTrue()
        assertThat(share.isVisible).isFalse()
    }

    @Test
    fun realSelectionToolbarKeepsCopyAndDropsShare() = runBlocking<Unit> {
        startWith("""<p style="font-size:120px;margin:0">selectme words</p>""")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.swipe(150, 100, 150, 100, 200) // long press on the first word
        assertThat(device.wait(Until.hasObject(By.text("Copy")), 5_000)).isTrue()
        assertThat(device.hasObject(By.text("Share"))).isFalse()
        device.pressBack()
    }

    @Test
    fun permissionRequestsAreDenied() {
        val request = FakePermissionRequest()
        KioskChromeClient().onPermissionRequest(request)
        assertThat(request.denied).isTrue()
        assertThat(request.granted).isNull()

        var allowed: Boolean? = null
        KioskChromeClient().onGeolocationPermissionsShowPrompt(
            "http://localhost",
            GeolocationPermissions.Callback { _, allow, _ -> allowed = allow },
        )
        assertThat(allowed).isFalse()
    }

    @Test
    fun fileChooserIsNotShown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var shown = true
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WebView(context)
            shown = KioskChromeClient().onShowFileChooser(view, { _: Array<Uri>? -> }, null)
            view.destroy()
        }
        assertThat(shown).isFalse()
    }

    private fun fullScreenButton(onClick: String) =
        """<button onclick="$onClick" style="position:fixed;left:0;top:0;width:100vw;height:100vh">go</button>"""

    /** Scripted clicks on target=_blank count as gesture-less pop-ups, so these tests tap. */
    private fun fullScreenBlankLink(href: String) =
        """<a target="_blank" href="$href" style="position:fixed;left:0;top:0;width:100vw;height:100vh">go</a>"""

    private fun tapCentre() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.click(device.displayWidth / 2, device.displayHeight / 2)
    }

    private class FakePermissionRequest : PermissionRequest() {
        var granted: Array<String>? = null
        var denied = false
        override fun getOrigin(): Uri = Uri.parse("http://localhost/")
        override fun getResources() = arrayOf(RESOURCE_AUDIO_CAPTURE, RESOURCE_VIDEO_CAPTURE)
        override fun grant(resources: Array<String>?) { granted = resources }
        override fun deny() { denied = true }
    }

    /** One second of 8 kHz, 8-bit mono silence. */
    private fun silentWav(): ByteArray {
        val samples = 8000
        val b = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(8000)
            .putShort(1).putShort(8)
        b.put("data".toByteArray()).putInt(samples)
        repeat(samples) { b.put(0x80.toByte()) }
        return b.array()
    }
}
