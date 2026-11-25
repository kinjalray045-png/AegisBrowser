package com.aegis.browser.web

import android.webkit.WebResourceRequest
import com.aegis.browser.core.Prefs
import com.aegis.browser.core.Urls

/**
 * The Aegis Shield.
 *
 * With the shield on, a page may not navigate itself away from the site the user
 * is on. User-initiated taps are always allowed, and a small "gesture budget"
 * lets a click that bounces through a redirect chain (search engines, short
 * links, OAuth) finish. Automatic redirects, meta refreshes, window.open
 * pop-ups and app-launch links are stopped.
 */
class NavigationShield(private val prefs: Prefs) {

    enum class Kind { POPUP, REDIRECT, APP_LINK, SITE_LOCK }

    data class Blocked(
        val url: String,
        val host: String,
        val kind: Kind,
        val fromHost: String? = null
    )

    var onBlocked: ((Blocked) -> Unit)? = null
    var onPopupBlocked: (() -> Unit)? = null
    var onSiteLockBlocked: ((Blocked) -> Unit)? = null

    var currentUrl: String? = null
        private set
    var currentSite: String? = null
        private set

    var sessionBlocked: Int = 0
        private set

    val recent = ArrayDeque<Blocked>()

    private val allowedOnce = LinkedHashSet<String>()
    private var gestureBudget = 0
    private var gestureBudgetUntil = 0L

    fun setCurrent(url: String?) {
        currentUrl = url
        currentSite = Urls.siteKeyOfUrl(url)
        gestureBudget = 0
    }

    fun onCommitted(url: String) {
        currentUrl = url
        currentSite = Urls.siteKeyOfUrl(url)
    }

    /** A finished page ends the short redirect window opened by a tap. */
    fun onPageLoaded() {
        gestureBudget = 0
        gestureBudgetUntil = 0L
    }

    fun allowNavigation(url: String) {
        allowedOnce.add(Urls.canonical(url))
        while (allowedOnce.size > 40) {
            allowedOnce.remove(allowedOnce.first())
        }
    }

    fun consumeAllowed(url: String): Boolean = allowedOnce.remove(Urls.canonical(url))

    fun isEnabledFor(host: String?): Boolean =
        prefs.shieldEnabled && !prefs.isSiteAllowed(host)

    fun isAllowlisted(host: String?): Boolean = prefs.isSiteAllowed(host)

    /** JavaScript that flips the in-page switches without a reload. */
    fun runtimeScript(url: String?, siteLockKey: String?): String =
        ShieldScripts.runtimeConfig(
            isEnabledFor(Urls.host(url)),
            prefs.allowedSites,
            prefs.siteLockEnabled && !siteLockKey.isNullOrBlank(),
            siteLockKey.orEmpty()
        )

    /**
     * Called from shouldOverrideUrlLoading. Returning a non-null value means the
     * navigation must be cancelled.
     */
    fun shouldBlock(request: WebResourceRequest): Blocked? {
        if (!prefs.shieldEnabled) return null
        val url = request.url.toString()
        if (Urls.isInternal(url)) return null

        // A site the user explicitly exempted may do whatever it likes.
        if (prefs.isSiteAllowed(Urls.host(currentUrl))) return null

        val special = Urls.isSpecialScheme(url)

        if (!request.isForMainFrame) {
            if (special && prefs.shieldAppLinks) return block(url, Kind.APP_LINK)
            return null
        }

        if (special) {
            if (!prefs.shieldAppLinks) return null
            if (request.hasGesture()) {
                // Let the browser offer "Open in another app?" for a real tap.
                return null
            }
            return block(url, Kind.APP_LINK)
        }

        if (!Urls.isHttp(url)) return null
        val host = Urls.host(url) ?: return null
        if (prefs.isSiteAllowed(host)) return null
        if (consumeAllowed(url)) return null
        if (!prefs.shieldRedirects) return null

        if (request.hasGesture()) {
            grantGestureBudget()
            allowNavigation(url)
            return null
        }

        val targetSite = Urls.siteKey(host)
        if (targetSite != null && targetSite == currentSite) return null

        if (consumeGestureBudget()) {
            allowNavigation(url)
            return null
        }

        return block(url, Kind.REDIRECT)
    }

    /**
     * Backstop for redirects that never reach shouldOverrideUrlLoading
     * (some meta refreshes and server-side chains).
     */
    fun checkPageStart(url: String): Blocked? {
        if (!prefs.shieldEnabled || !prefs.shieldRedirects) return null
        if (Urls.isInternal(url) || !Urls.isHttp(url)) return null
        val host = Urls.host(url) ?: return null
        if (prefs.isSiteAllowed(host)) return null
        if (prefs.isSiteAllowed(Urls.host(currentUrl))) return null
        if (consumeAllowed(url)) return null

        val targetSite = Urls.siteKey(host) ?: return null
        if (targetSite == currentSite) return null

        if (consumeGestureBudget()) {
            allowNavigation(url)
            return null
        }
        return block(url, Kind.REDIRECT)
    }

    fun canBlockPopups(pageUrl: String?): Boolean =
        prefs.shieldEnabled &&
            prefs.shieldPopups &&
            !prefs.isSiteAllowed(Urls.host(pageUrl))

    fun notifyPopupBlocked(targetUrl: String? = null) {
        sessionBlocked++
        prefs.blockedTotal = prefs.blockedTotal + 1
        val host = Urls.host(targetUrl) ?: "pop-up"
        val item = Blocked(targetUrl ?: "", host, Kind.POPUP, Urls.host(currentUrl))
        recent.addFirst(item)
        while (recent.size > 12) recent.removeLast()
        onPopupBlocked?.invoke()
    }

    /**
     * Records a navigation that Site Lock stopped. Unlike the shield this
     * ignores gestures entirely: even a real tap on a hidden ad overlay is
     * refused, which is the whole point of the strict mode.
     */
    fun notifySiteLockBlocked(url: String, lockedSite: String): Blocked {
        sessionBlocked++
        prefs.blockedTotal = prefs.blockedTotal + 1
        val host = Urls.host(url) ?: "pop-up"
        val item = Blocked(url, host, Kind.SITE_LOCK, lockedSite)
        recent.addFirst(item)
        while (recent.size > 12) recent.removeLast()
        onSiteLockBlocked?.invoke(item)
        return item
    }

    fun resetStats() {
        sessionBlocked = 0
        prefs.blockedTotal = 0
        recent.clear()
    }

    private fun grantGestureBudget() {
        gestureBudget = 3
        gestureBudgetUntil = System.currentTimeMillis() + 5_000
    }

    private fun consumeGestureBudget(): Boolean {
        if (gestureBudget <= 0) return false
        if (System.currentTimeMillis() > gestureBudgetUntil) {
            gestureBudget = 0
            return false
        }
        gestureBudget--
        return true
    }

    private fun block(url: String, kind: Kind): Blocked {
        val host = Urls.host(url) ?: Urls.pretty(url)
        val item = Blocked(url, host, kind, Urls.host(currentUrl))
        sessionBlocked++
        prefs.blockedTotal = prefs.blockedTotal + 1
        recent.addFirst(item)
        while (recent.size > 12) recent.removeLast()
        onBlocked?.invoke(item)
        return item
    }
}
