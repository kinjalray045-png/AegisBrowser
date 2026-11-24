package com.aegis.browser.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import com.aegis.browser.core.TabStore
import com.aegis.browser.core.Urls
import com.aegis.browser.web.AegisWebView

/**
 * Owns the list of tabs and the single attached WebView. Inactive tabs keep
 * their WebView alive (paused) so switching is instant, and tabs are created
 * lazily when restored so startup stays cheap.
 */
class TabManager(
    private val container: FrameLayout,
    private val host: Host
) {

    interface Host {
        fun createTabView(tab: BrowserTab): AegisWebView
        fun loadTab(tab: BrowserTab)
        fun onActiveTabChanged(tab: BrowserTab?)
        fun onTabListChanged()
    }

    val tabs = mutableListOf<BrowserTab>()
    var activeId: String? = null
        private set

    val active: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeId }

    val activeWebView: AegisWebView?
        get() = active?.webView

    private var counter = 0

    fun newTab(
        url: String = "",
        incognito: Boolean = false,
        select: Boolean = true,
        autoLoad: Boolean = true
    ): BrowserTab? {
        if (tabs.size >= MAX_TABS) return null
        val tab = BrowserTab(
            id = "tab-${System.currentTimeMillis()}-${counter++}",
            url = url,
            title = "",
            incognito = incognito
        )
        tab.isHome = url.isBlank() || Urls.isHome(url)
        tab.loaded = !autoLoad
        tabs.add(tab)
        if (select) select(tab.id)
        host.onTabListChanged()
        return tab
    }

    fun select(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        if (tab.id == activeId && tab.webView != null) {
            host.onActiveTabChanged(tab)
            return
        }

        val previous = active
        previous?.webView?.let { old ->
            old.onPause()
            container.removeView(old)
        }

        activeId = tab.id
        tab.lastActive = System.currentTimeMillis()

        val webView = tab.webView ?: host.createTabView(tab).also { tab.webView = it }
        if (webView.parent == null) {
            container.addView(webView)
        }
        webView.onResume()
        webView.resumeTimers()

        if (!tab.loaded) {
            tab.loaded = true
            host.loadTab(tab)
        }

        host.onActiveTabChanged(tab)
        host.onTabListChanged()
    }

    fun close(id: String, replaceIfEmpty: Boolean = true) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]
        val wasActive = tab.id == activeId

        if (wasActive) {
            tab.webView?.let { container.removeView(it) }
            activeId = null
        }
        destroy(tab)
        tabs.removeAt(index)

        if (wasActive) {
            val next = tabs.getOrNull(index - 1) ?: tabs.getOrNull(index)
            if (next != null) {
                select(next.id)
            } else if (replaceIfEmpty) {
                newTab(incognito = tab.incognito)
            } else {
                host.onActiveTabChanged(null)
            }
        }
        host.onTabListChanged()
    }

    fun closeAll() {
        tabs.toList().forEach { destroy(it) }
        tabs.clear()
        activeId = null
        container.removeAllViews()
        host.onActiveTabChanged(null)
        host.onTabListChanged()
    }

    fun destroy(tab: BrowserTab) {
        tab.webView?.let { webView ->
            runCatching {
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = null
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        }
        tab.webView = null
        tab.blobBridge = null
    }

    fun findTabFor(webView: WebView): BrowserTab? =
        tabs.firstOrNull { it.webView === webView }

    fun createPopupTab(incognito: Boolean): BrowserTab? =
        newTab(url = "", incognito = incognito, select = true, autoLoad = false)

    fun save(store: TabStore) {
        val saved = tabs.filter { !it.incognito }
        val current = active
        val index = if (current != null && !current.incognito) {
            saved.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        } else {
            0
        }
        store.save(saved.map { it.url to it.title }, index)
    }

    fun restore(store: TabStore): Boolean {
        val saved = store.restore()
        if (saved.tabs.isEmpty()) return false
        saved.tabs.forEach { (url, title) ->
            val tab = BrowserTab(
                id = "tab-${System.currentTimeMillis()}-${counter++}",
                url = url,
                title = title,
                incognito = false
            )
            tab.isHome = url.isBlank() || Urls.isHome(url)
            tabs.add(tab)
        }
        val index = saved.activeIndex.coerceIn(0, tabs.size - 1)
        select(tabs[index].id)
        return true
    }

    fun clear() {
        tabs.toList().forEach { destroy(it) }
        tabs.clear()
        activeId = null
        container.removeAllViews()
    }

    companion object {
        const val MAX_TABS = 20
    }
}
