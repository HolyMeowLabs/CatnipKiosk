package com.holymeowlabs.catnipkiosk.kiosk

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.Flow

/**
 * The page with whatever the kiosk state puts over it. [webView] outlives this composable
 * (it is kept while admin screens are open), so it is detached from any previous parent.
 */
@Composable
fun KioskScreen(webView: View, ui: KioskUi, networkAvailable: Boolean, toasts: Flow<Unit>, nowMs: () -> Long) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                webView.apply {
                    (parent as? ViewGroup)?.removeView(this)
                    // AndroidView defaults to WRAP_CONTENT, which WebView treats as a content-sized
                    // height and lays the page out with a zero-height viewport (100vh = 0).
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        when (ui) {
            KioskUi.Showing -> Unit
            is KioskUi.Reconnecting -> ReconnectingScreen(ui, networkAvailable, nowMs)
            is KioskUi.SetupProblem -> SetupProblemScreen(ui.blockedHost)
        }
        BlockedToast(toasts)
    }
}
