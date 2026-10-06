package com.holymeowlabs.catnipkiosk.web

import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Denies every page permission request and never opens a file picker (another app). */
internal class KioskChromeClient(private val owner: KioskWebView? = null) : WebChromeClient() {
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val o = owner
        if (o == null) callback.onCustomViewHidden() else o.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        owner?.onHideCustomView?.invoke()
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ) = false
}
