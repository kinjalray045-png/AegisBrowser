package com.aegis.browser.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.aegis.browser.BrowserActivity
import com.aegis.browser.core.Urls
import com.aegis.browser.ui.BrowserTab

class BrowserClient(
    private val activity: BrowserActivity,
    private val tab: BrowserTab,
    private val shield: NavigationShield
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        if (Urls.isInternal(url)) {
            activity.handleInternalUrl(url)
            return true
        }

        // Never let a page jump straight into a script or a local file.
        if (url.startsWith("javascript:", ignoreCase = true) ||
            url.startsWith("file:", ignoreCase = true) ||
            url.startsWith("content:", ignoreCase = true)
        ) {
            return true
        }

        if (shield.shouldBlock(request) != null) {
            view.stopLoading()
            return true
        }

        if (Urls.isSpecialScheme(url) || !Urls.isHttp(url)) {
            if (Urls.hasScheme(url)) {
                activity.confirmExternalLink(url)
                return true
            }
        }

        return false
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        activity.onPageStarted(tab, url ?: return)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        activity.onPageFinished(tab, url ?: view.url.orEmpty())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (!url.isNullOrBlank()) {
            activity.onUrlCommitted(tab, url)
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (!request.isForMainFrame) return
        if (error.errorCode == ERROR_UNKNOWN) return
        activity.onPageError(
            tab,
            request.url.toString(),
            error.errorCode,
            error.description?.toString().orEmpty()
        )
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        activity.onSslError(tab, handler, error)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse
    ) {
        activity.onSafeBrowsingHit(callback)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        activity.onRendererGone(tab)
        return true
    }
}
