package com.holymeowlabs.catnipkiosk

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.holymeowlabs.catnipkiosk.app.Route
import com.holymeowlabs.catnipkiosk.app.nextRoute
import com.holymeowlabs.catnipkiosk.input.CornerTapDetector
import com.holymeowlabs.catnipkiosk.input.KeyMapping
import com.holymeowlabs.catnipkiosk.input.KeySequenceDetector
import com.holymeowlabs.catnipkiosk.pin.PinScreen
import com.holymeowlabs.catnipkiosk.pin.PinViewModel
import com.holymeowlabs.catnipkiosk.security.PinGate
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.kiosk.Connectivity
import com.holymeowlabs.catnipkiosk.kiosk.KioskScreen
import com.holymeowlabs.catnipkiosk.kiosk.KioskViewModel
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import com.holymeowlabs.catnipkiosk.ui.theme.KioskTheme
import com.holymeowlabs.catnipkiosk.web.KioskWebView
import com.holymeowlabs.catnipkiosk.web.WebEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** One view for the Activity's life (recreated only if its renderer dies), so admin screens don't reload the page. */
    internal lateinit var webView: KioskWebView
        private set

    private val vm by lazy { KioskViewModel(System::currentTimeMillis, lifecycleScope) }
    private lateinit var connectivity: Connectivity
    private var kioskSettings: KioskSettings? = null
    private var security: SecurityState? = null
    private val keySequence = KeySequenceDetector()
    private val cornerTaps by lazy { CornerTapDetector(zoneSizePx = SECRET_CORNER_DP * resources.displayMetrics.density) }
    private var pin by mutableStateOf<PinViewModel?>(null)
    private var pinJobs: Job? = null
    internal var route by mutableStateOf<Route?>(null)
        private set
    private var networkAvailable by mutableStateOf(true)
    private var webViewGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        webView = newWebView()
        onBackPressedDispatcher.addCallback(this) { if (webView.canGoBack()) webView.goBack() }
        connectivity = Connectivity(this) { available ->
            runOnUiThread {
                networkAvailable = available
                if (available) vm.onNetworkAvailable()
            }
        }

        val repo = SettingsRepository.get(this)
        lifecycleScope.launch { repo.settings.combine(repo.security, ::Pair).collect { (s, sec) -> onStored(s, sec) } }
        lifecycleScope.launch { vm.reloadRequests.collect { webView.loadStart() } }

        setContent {
            KioskTheme {
                val ui by vm.ui.collectAsState()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val current = route
                    // The page stays composed under the admin screens so opening them doesn't reload it.
                    if (current == Route.Kiosk || current == Route.Pin || current == Route.Settings) {
                        key(webViewGeneration) {
                            KioskScreen(
                                webView, ui, networkAvailable, vm.toast, System::currentTimeMillis,
                                coveredByAdmin = current != Route.Kiosk,
                            )
                        }
                    }
                    when (current) {
                        Route.Pin -> pin?.let { p ->
                            val state by p.state.collectAsState()
                            PinScreen(state, System::currentTimeMillis, p::digit, p::delete, p::ok, p::cancel)
                        }
                        // Settings (Task 11) and Setup (Task 10) are not built yet.
                        Route.Settings -> {
                            BackHandler { route = Route.Kiosk }
                            Text(stringResource(R.string.settings_pending), Modifier.align(Alignment.Center))
                        }
                        Route.Setup -> Text(stringResource(R.string.setup_pending), Modifier.align(Alignment.Center))
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Secret entry observes keys and touches but never consumes them; the page still gets every event. */
    // androidx.core's ComponentActivity marks its override of the public framework method @RestrictTo.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && route == Route.Kiosk &&
            keySequence.onKey(KeyMapping.toDir(event.keyCode), event.eventTime)
        ) {
            openPin()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && route == Route.Kiosk &&
            cornerTaps.onTap(event.x, event.y, event.eventTime)
        ) {
            openPin()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun openPin() {
        val stored = security ?: return
        val vmPin = PinViewModel(
            initial = stored,
            lockoutEnabled = kioskSettings?.pinLockoutEnabled == true,
            gate = PinGate(PinHasher(), System::currentTimeMillis),
            save = SettingsRepository.get(this)::saveSecurity,
            scope = lifecycleScope,
            nowMs = System::currentTimeMillis,
            work = Dispatchers.Default,
        )
        pinJobs?.cancel()
        pinJobs = lifecycleScope.launch {
            launch { vmPin.unlocked.collect { route = Route.Settings } }
            launch { vmPin.cancelled.collect { route = Route.Kiosk } }
        }
        pin = vmPin
        route = Route.Pin
    }

    override fun onStart() {
        super.onStart()
        connectivity.start()
    }

    override fun onStop() {
        connectivity.stop()
        super.onStop()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun onStored(stored: KioskSettings?, storedSecurity: SecurityState?) {
        val previous = kioskSettings
        kioskSettings = stored
        security = storedSecurity
        route = nextRoute(route, stored, storedSecurity)
        if (stored == null || route == Route.Setup) return

        if (stored.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (previous?.scheduledReload != stored.scheduledReload) vm.onSettingsChanged(stored)
        // The repository flow is distinct, but only navigation fields warrant a reload.
        if (previous == null || previous.navigationFields() != stored.navigationFields()) webView.loadStart()
    }

    private fun KioskSettings.navigationFields() = listOf(startUrl, navMode, includeSubdomains, extraDomains)

    private fun newWebView() = KioskWebView(this).apply {
        settingsProvider = { checkNotNull(kioskSettings) }
        onEvent = { event ->
            if (event == WebEvent.RendererGone) replaceWebView()
            kioskSettings?.let { vm.onWebEvent(event, it) }
        }
    }

    /** The renderer died: this view is unusable. The view model then requests a reload. */
    private fun replaceWebView() {
        val dead = webView
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        webView = newWebView()
        webViewGeneration++
    }

    private companion object {
        /** Side of the top-left square for the tablet's 5-tap secret entry. */
        const val SECRET_CORNER_DP = 80f
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
