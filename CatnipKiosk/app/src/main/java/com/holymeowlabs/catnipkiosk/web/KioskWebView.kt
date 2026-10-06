package com.holymeowlabs.catnipkiosk.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.ActionMode
import android.webkit.CookieManager
import android.webkit.WebView
import com.holymeowlabs.catnipkiosk.settings.KioskSettings

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class KioskWebView(context: Context) : WebView(context) {
    var settingsProvider: () -> KioskSettings = { error("settingsProvider not set") }
    var onEvent: (WebEvent) -> Unit = {}

    /** True from [loadStart] until the first page finishes; a blocked navigation then means a setup problem. */
    internal var initialLoad = false

    /** The current main-frame load was blocked or failed; its error page "finishing" is not PageLoaded. */
    internal var currentLoadFailed = false

    /** The main-frame URL most recently requested, to tell its SSL errors from subresources'. */
    internal var mainFrameUrl: String? = null

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptCookie(true)
        setDownloadListener { _, _, _, _, _ -> }
        webViewClient = KioskWebViewClient(this)
        webChromeClient = KioskChromeClient()
    }

    fun loadStart() {
        val url = settingsProvider().startUrl
        initialLoad = true
        currentLoadFailed = false
        mainFrameUrl = url
        loadUrl(url)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
        super.startActionMode(callback?.let(::filtering))

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        super.startActionMode(callback?.let(::filtering), type)

    private fun filtering(callback: ActionMode.Callback) = FilteringActionModeCallback(callback) { id ->
        // Chromium's menu ids belong to the WebView package, whose resources are loaded into ours.
        runCatching { resources.getResourceEntryName(id) }.getOrNull()
    }
}
