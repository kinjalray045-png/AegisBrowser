package com.aegis.browser.web

import android.webkit.JavascriptInterface
import com.aegis.browser.BrowserActivity
import com.aegis.browser.ui.BrowserTab

/**
 * Tiny bridge used only by the built-in new-tab page. Every call is ignored
 * unless the tab is still showing that page, so a random website cannot use it
 * to steer the browser.
 */
class HomeBridge(
    private val activity: BrowserActivity,
    private val tab: BrowserTab
) {

    @JavascriptInterface
    fun search(query: String) {
        if (!tab.isHome) return
        activity.runOnUiThread { activity.onHomeSearch(query) }
    }

    @JavascriptInterface
    fun shield() {
        if (!tab.isHome) return
        activity.runOnUiThread { activity.onHomeShield() }
    }
}
