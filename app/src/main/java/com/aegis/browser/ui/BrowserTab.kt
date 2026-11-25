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

    /**
     * Registrable domain ("site key") this tab is pinned to while Site Lock is
     * on. Null means the lock is not anchored yet; the first real page the tab
     * commits becomes the anchor.
     */
    var lockedSite: String? = null

    /** Last URL that belonged to [lockedSite]; used to restore after a block. */
    var lockedUrl: String? = null

    /** Set while an explicit Back/Forward navigation is allowed to move the lock. */
    var allowSiteLockOnce: Boolean = false

    /** True while a cross-site load is being reverted, to prevent restore loops. */
    var siteLockRestoring: Boolean = false

    val displayUrl: String
        get() = if (isHome) "" else url

    val label: String
        get() = title.trim().ifEmpty {
            if (isHome) "New tab" else com.aegis.browser.core.Urls.pretty(url)
        }
}
