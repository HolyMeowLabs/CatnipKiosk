package com.holymeowlabs.catnipkiosk.web

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.holymeowlabs.catnipkiosk.policy.NavDecision
import com.holymeowlabs.catnipkiosk.policy.NavigationPolicy

/** Enforces [NavigationPolicy] on main-frame navigations and reports load outcomes. */
internal class KioskWebViewClient(private val owner: KioskWebView) : WebViewClient() {

    private fun blocked(url: String) = NavigationPolicy.decide(url, owner.settingsProvider()) == NavDecision.BLOCK

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val url = request.url.toString()
        if (!blocked(url)) {
            owner.mainFrameUrl = url
            return false
        }
        // A redirect or script bounce while the start page loads is a setup problem; a user tap is not.
        if (owner.initialLoad && !request.hasGesture()) {
            reportStartPageBlocked(request.url)
        } else {
            owner.onEvent(WebEvent.Blocked(url))
        }
        return true
    }

    /** Names only the host: the URL may carry tokens. */
    private fun reportStartPageBlocked(url: Uri) {
        owner.startPageBlocked = true
        owner.onEvent(WebEvent.StartPageBlocked(NavigationPolicy.normalizeHost(url.host).orEmpty()))
    }

    /**
     * Backstop for navigations that skip shouldOverrideUrlLoading, such as form POSTs.
     * During the initial load, reloading the start page could loop, so it is reported instead.
     */
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url == null || !blocked(url)) return
        view.stopLoading()
        if (owner.initialLoad) {
            reportStartPageBlocked(Uri.parse(url))
        } else {
            owner.onEvent(WebEvent.Blocked(url))
            owner.loadStart()
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        owner.initialLoad = false
        if (!owner.startPageBlocked) owner.onEvent(WebEvent.PageLoaded)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) owner.onEvent(WebEvent.MainFrameFailed(error.description.toString()))
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame && response.statusCode >= 500) {
            owner.onEvent(WebEvent.MainFrameFailed("HTTP ${response.statusCode}"))
        }
    }

    /** Never proceeds. Also fires for subresources, which fail silently like any broken image. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        if (sameOrigin(error.url, owner.mainFrameUrl)) owner.onEvent(WebEvent.MainFrameFailed("ssl"))
    }

    private fun sameOrigin(a: String?, b: String?): Boolean {
        val x = a?.let(Uri::parse) ?: return false
        val y = b?.let(Uri::parse) ?: return false
        return x.scheme.equals(y.scheme, ignoreCase = true) &&
            x.host.equals(y.host, ignoreCase = true) && x.port == y.port
    }

    /** The owner destroys this view and creates a new one. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        owner.onEvent(WebEvent.RendererGone)
        return true
    }
}
