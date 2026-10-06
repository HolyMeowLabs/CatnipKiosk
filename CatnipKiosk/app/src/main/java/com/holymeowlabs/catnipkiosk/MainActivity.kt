package com.holymeowlabs.catnipkiosk

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
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
import com.holymeowlabs.catnipkiosk.app.launchRoute
import com.holymeowlabs.catnipkiosk.kiosk.Connectivity
import com.holymeowlabs.catnipkiosk.kiosk.KioskScreen
import com.holymeowlabs.catnipkiosk.kiosk.KioskViewModel
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import com.holymeowlabs.catnipkiosk.ui.theme.KioskTheme
import com.holymeowlabs.catnipkiosk.web.KioskWebView
import com.holymeowlabs.catnipkiosk.web.WebEvent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** One view for the Activity's life (recreated only if its renderer dies), so admin screens don't reload the page. */
    internal lateinit var webView: KioskWebView
        private set

    private val vm by lazy { KioskViewModel(System::currentTimeMillis, lifecycleScope) }
    private lateinit var connectivity: Connectivity
    private var kioskSettings: KioskSettings? = null
    private var route by mutableStateOf<Route?>(null)
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
                    when (route) {
                        Route.Kiosk -> key(webViewGeneration) {
                            KioskScreen(webView, ui, networkAvailable, vm.toast, System::currentTimeMillis)
                        }
                        // Setup (Task 10) and the admin screens (Tasks 9, 11) are not built yet.
                        Route.Setup -> Text(stringResource(R.string.setup_pending), Modifier.align(Alignment.Center))
                        else -> Unit
                    }
                }
            }
        }
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

    private fun onStored(stored: KioskSettings?, security: SecurityState?) {
        val previous = kioskSettings
        kioskSettings = stored
        route = launchRoute(stored, security)
        if (stored == null || route != Route.Kiosk) return

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

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
