package com.aegis.browser.ui

import com.aegis.browser.web.AegisWebView
import com.aegis.browser.web.BlobBridge

class BrowserTab(
    val id: String,
    var url: String,
    var title: String,
    val incognito: Boolean
) {
    var webView: AegisWebView? = null
    var blobBridge: BlobBridge? = null
    var shieldHandler: androidx.webkit.ScriptHandler? = null
    var dntHandler: androidx.webkit.ScriptHandler? = null
    var loaded: Boolean = false
    var isLoading: Boolean = false
    var isHome: Boolean = url.isBlank()
    var progress: Int = 0
    var lastActive: Long = System.currentTimeMillis()
    var isPopup: Boolean = false

    val displayUrl: String
        get() = if (isHome) "" else url

    val label: String
        get() = title.trim().ifEmpty {
            if (isHome) "New tab" else com.aegis.browser.core.Urls.pretty(url)
        }
}
