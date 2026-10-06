package com.holymeowlabs.catnipkiosk

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.holymeowlabs.catnipkiosk.app.Route
import com.holymeowlabs.catnipkiosk.app.adminSessionExpired
import com.holymeowlabs.catnipkiosk.app.nextRoute
import com.holymeowlabs.catnipkiosk.app.routeAfterStop
import com.holymeowlabs.catnipkiosk.cursor.CursorController
import com.holymeowlabs.catnipkiosk.cursor.CursorKeys
import com.holymeowlabs.catnipkiosk.cursor.CursorOverlay
import com.holymeowlabs.catnipkiosk.cursor.SyntheticTap
import com.holymeowlabs.catnipkiosk.input.CornerTapDetector
import com.holymeowlabs.catnipkiosk.kiosk.KioskUi
import com.holymeowlabs.catnipkiosk.input.KeyMapping
import com.holymeowlabs.catnipkiosk.input.KeySequenceDetector
import com.holymeowlabs.catnipkiosk.pin.PinScreen
import com.holymeowlabs.catnipkiosk.pin.PinViewModel
import com.holymeowlabs.catnipkiosk.security.PinGate
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settingsui.SettingsScreen
import com.holymeowlabs.catnipkiosk.settingsui.SettingsViewModel
import com.holymeowlabs.catnipkiosk.setup.SetupViewModel
import com.holymeowlabs.catnipkiosk.setup.SetupWizard
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
import kotlinx.coroutines.delay
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
    internal var route by mutableStateOf<Route?>(null)
        private set
    private var networkAvailable by mutableStateOf(true)
    private var webViewGeneration by mutableIntStateOf(0)
    private var lastInputMs = 0L
    private var cursorEnabled by mutableStateOf(false)
    private var cursor: CursorController? = null
    private var cursorPosition by mutableStateOf<Offset?>(null)
    private var cursorMovedMs by mutableLongStateOf(0L)

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
        lifecycleScope.launch {
            while (true) {
                delay(ADMIN_IDLE_CHECK_MS)
                if (adminSessionExpired(route, lastInputMs, SystemClock.uptimeMillis())) route = Route.Kiosk
            }
        }

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
                    val position = cursorPosition
                    if (current == Route.Kiosk && cursorEnabled && ui == KioskUi.Showing && position != null) {
                        CursorOverlay(position, cursorMovedMs)
                    }
                    when (current) {
                        Route.Pin -> pin?.let { p ->
                            val state by p.state.collectAsState()
                            PinScreen(state, System::currentTimeMillis, p::digit, p::delete, p::ok, p::cancel)
                        }
                        Route.Settings -> kioskSettings?.let { current ->
                            // A fresh view model per visit, seeded from the stored settings.
                            val settingsVm = remember { newSettingsViewModel(current) }
                            SettingsScreen(
                                vm = settingsVm,
                                isTv = isTv,
                                onBack = { route = Route.Kiosk },
                                onExitApp = ::finishAndRemoveTask,
                                onReloadNow = {
                                    webView.loadStart()
                                    route = Route.Kiosk
                                },
                            )
                        }
                        // Finishing saves settings and security; the store update then routes to the kiosk.
                        Route.Setup -> {
                            val setupVm = remember { newSetupViewModel() }
                            val state by setupVm.state.collectAsState()
                            SetupWizard(setupVm, state)
                        }
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
        lastInputMs = event.eventTime
        // The secret sequence is fed first, even when the arrows then drive the cursor.
        if (event.action == KeyEvent.ACTION_DOWN && route == Route.Kiosk &&
            keySequence.onKey(KeyMapping.toDir(event.keyCode), event.eventTime)
        ) {
            openPin()
        }
        val cursorActive = route == Route.Kiosk && cursorEnabled && vm.ui.value == KioskUi.Showing &&
            webView.width > 0 && webView.height > 0
        return when (val action = CursorKeys.decide(event.keyCode, event.action, cursorActive)) {
            CursorKeys.Action.PassThrough -> super.dispatchKeyEvent(event)
            CursorKeys.Action.Consume -> true
            CursorKeys.Action.Tap -> {
                val c = cursorController()
                SyntheticTap.dispatch(webView, c.x, c.y)
                true
            }
            is CursorKeys.Action.Move -> {
                val step = cursorController().move(action.dir, event.eventTime - event.downTime)
                cursorPosition = Offset(step.x, step.y)
                cursorMovedMs = event.eventTime
                if (step.scrollDx != 0 || step.scrollDy != 0) scrollPage(step.scrollDx, step.scrollDy)
                true
            }
        }
    }

    /** Recreated when the view's size changes (rotation, renderer recreation). */
    private fun cursorController(): CursorController {
        val w = webView.width.toFloat()
        val h = webView.height.toFloat()
        val existing = cursor
        if (existing != null && existing.widthPx == w && existing.heightPx == h) return existing
        return CursorController(w, h).also {
            cursor = it
            cursorPosition = Offset(it.x, it.y)
        }
    }

    /** JS scroll in CSS pixels; WebView's own scroll offset follows the document's. */
    private fun scrollPage(dxPx: Int, dyPx: Int) {
        val density = resources.displayMetrics.density
        webView.evaluateJavascript("window.scrollBy(${dxPx / density}, ${dyPx / density})", null)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastInputMs = event.eventTime
        if (event.actionMasked == MotionEvent.ACTION_DOWN && route == Route.Kiosk &&
            cornerTaps.onTap(event.x, event.y, event.eventTime)
        ) {
            openPin()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun openPin() {
        val vmPin = pin ?: createPinViewModel() ?: return
        vmPin.reset()
        lastInputMs = SystemClock.uptimeMillis()
        route = Route.Pin
    }

    private val isTv by lazy {
        getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun newSettingsViewModel(current: KioskSettings): SettingsViewModel {
        val repo = SettingsRepository.get(this)
        return SettingsViewModel(
            initial = current,
            security = { security },
            hasher = PinHasher(),
            saveSettings = repo::saveSettings,
            saveSecurity = repo::saveSecurity,
            scope = lifecycleScope,
            work = Dispatchers.Default,
        )
    }

    private fun newSetupViewModel(): SetupViewModel {
        val repo = SettingsRepository.get(this)
        return SetupViewModel(isTv, PinHasher(), repo::saveSettings, repo::saveSecurity, lifecycleScope, Dispatchers.Default)
    }

    /** Created once from the first stored state; it then owns the in-memory security state. */
    private fun createPinViewModel(): PinViewModel? {
        val stored = security ?: return null
        val vmPin = PinViewModel(
            initial = stored,
            lockoutEnabled = kioskSettings?.pinLockoutEnabled == true,
            gate = PinGate(PinHasher(), System::currentTimeMillis),
            save = SettingsRepository.get(this)::saveSecurity,
            scope = lifecycleScope,
            nowMs = System::currentTimeMillis,
            work = Dispatchers.Default,
        )
        lifecycleScope.launch { vmPin.unlocked.collect { route = Route.Settings } }
        lifecycleScope.launch { vmPin.cancelled.collect { route = Route.Kiosk } }
        pin = vmPin
        return vmPin
    }

    override fun onStart() {
        super.onStart()
        connectivity.start()
    }

    override fun onStop() {
        route = routeAfterStop(route)
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
        if (pin?.isCurrentFor(storedSecurity, stored?.pinLockoutEnabled == true) == false) pin = null
        route = nextRoute(route, stored, storedSecurity)
        if (stored == null || route == Route.Setup) return

        cursorEnabled = stored.cursorEnabled
        if (stored.cursorEnabled && cursorPosition == null && webView.width > 0) cursorController()
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
        const val ADMIN_IDLE_CHECK_MS = 5_000L
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
