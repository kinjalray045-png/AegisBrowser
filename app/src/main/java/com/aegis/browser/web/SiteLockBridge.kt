package com.aegis.browser.web

import android.webkit.JavascriptInterface
import com.aegis.browser.BrowserActivity
import com.aegis.browser.ui.BrowserTab

/**
 * Lets the in-page Site Lock shim report a tap it cancelled, so the user sees
 * why the page did not react.
 *
 * A random page can call this too, so every call is checked on the UI thread:
 * Site Lock must be on and the reported URL must really point outside the
 * tab's locked site. BrowserActivity also debounces the notices.
 */
class SiteLockBridge(
    private val activity: BrowserActivity,
    private val tab: BrowserTab
) {

    @JavascriptInterface
    fun blocked(url: String?) {
        val target = url?.trim().orEmpty()
        if (target.isEmpty()) return
        activity.runOnUiThread {
            if (activity.prefs.siteLockEnabled && activity.isSiteLockBlocked(tab, target)) {
                activity.onSiteLockBlocked(tab, target)
            }
        }
    }
}
