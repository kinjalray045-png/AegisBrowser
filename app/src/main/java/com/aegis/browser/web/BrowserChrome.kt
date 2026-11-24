package com.aegis.browser.web

import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.aegis.browser.BrowserActivity
import com.aegis.browser.ui.BrowserTab

class BrowserChrome(
    private val activity: BrowserActivity,
    private val tab: BrowserTab,
    private val shield: NavigationShield
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        activity.onProgress(tab, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        activity.onTitle(tab, title.orEmpty())
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        if (shield.canBlockPopups(view.url)) {
            shield.notifyPopupBlocked(view.url)
            return false
        }
        return activity.openPopupWindow(tab, resultMsg)
    }

    override fun onCloseWindow(window: WebView) {
        activity.closePopupWindow(window)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return activity.launchFileChooser(filePathCallback, fileChooserParams)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        activity.requestGeolocationPermission(origin, callback)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        activity.handleWebPermission(request)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        activity.enterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        activity.exitFullscreen()
    }
}
