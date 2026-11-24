package com.aegis.browser

import android.Manifest
import android.app.DownloadManager
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aegis.browser.core.BookmarksStore
import com.aegis.browser.core.DownloadItem
import com.aegis.browser.core.DownloadRecord
import com.aegis.browser.core.DownloadStore
import com.aegis.browser.core.HistoryStore
import com.aegis.browser.core.Prefs
import com.aegis.browser.core.TabStore
import com.aegis.browser.core.Urls
import com.aegis.browser.databinding.ActivityBrowserBinding
import com.aegis.browser.ui.BrowserTab
import com.aegis.browser.ui.FindBar
import com.aegis.browser.ui.ListRow
import com.aegis.browser.ui.ListSheet
import com.aegis.browser.ui.MenuSheet
import com.aegis.browser.ui.ShieldSheet
import com.aegis.browser.ui.SuggestionsController
import com.aegis.browser.ui.TabManager
import com.aegis.browser.ui.TabSwitcherSheet
import com.aegis.browser.web.AegisWebView
import com.aegis.browser.web.BlobBridge
import com.aegis.browser.web.BrowserChrome
import com.aegis.browser.web.BrowserClient
import com.aegis.browser.web.Downloads
import com.aegis.browser.web.HomeBridge
import com.aegis.browser.web.HomePage
import com.aegis.browser.web.NavigationShield
import com.aegis.browser.web.ShieldScripts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

class BrowserActivity : AppCompatActivity(), TabManager.Host {

    private lateinit var binding: ActivityBrowserBinding

    lateinit var prefs: Prefs
        private set
    lateinit var bookmarks: BookmarksStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var downloadStore: DownloadStore
        private set
    lateinit var shield: NavigationShield
        private set
    lateinit var tabManager: TabManager
        private set

    private lateinit var tabStore: TabStore
    private lateinit var suggestions: SuggestionsController
    private lateinit var findBar: FindBar

    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var pendingWebPermission: PermissionRequest? = null

    private var currentFullscreenView: View? = null
    private var currentFullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBackAt = 0L

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        applyPrefsToAllTabs()
        updateShieldIcon()
        updateBookmarkIcon()
        updateAddressBar()
        val active = tabManager.active
        if (active?.isHome == true) loadHome(active)
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFilePathCallback
        pendingFilePathCallback = null
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback?.onReceiveValue(uris)
    }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val callback = pendingGeoCallback
        val origin = pendingGeoOrigin
        pendingGeoCallback = null
        pendingGeoOrigin = null
        if (callback != null && origin != null) {
            callback.invoke(origin, grants.values.any { it }, false)
        }
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val request = pendingWebPermission
        pendingWebPermission = null
        if (request != null) {
            if (grants.isNotEmpty() && grants.values.all { it }) {
                request.grant(request.resources)
            } else {
                request.deny()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()

        prefs = Prefs(this)
        bookmarks = BookmarksStore(this)
        history = HistoryStore(this)
        downloadStore = DownloadStore(this)
        tabStore = TabStore(this)

        shield = NavigationShield(prefs)
        shield.onBlocked = { showBlockedSnackbar(it) }
        shield.onPopupBlocked = { showPopupBlockedSnackbar() }

        tabManager = TabManager(binding.webContainer, this)
        findBar = FindBar(this, binding) { tabManager.activeWebView }
        suggestions = SuggestionsController(this, binding, prefs, bookmarks, history) { url ->
            navigateActive(url)
        }

        setupUi()
        setupBack()

        if (!tabManager.restore(tabStore)) {
            tabManager.newTab()
        }
        handleIntent(intent)
        updateTabBadge()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainColumn) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        val night = isNight()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    private fun setupUi() {
        binding.shieldButton.setOnClickListener {
            hideSuggestions()
            showShieldSheet()
        }
        binding.overflowButton.setOnClickListener {
            hideSuggestions()
            MenuSheet(this).show()
        }
        binding.reloadButton.setOnClickListener { reloadOrStop() }
        binding.tabsButton.setOnClickListener {
            hideSuggestions()
            TabSwitcherSheet(this).show()
        }
        binding.backButton.setOnClickListener {
            hideSuggestions()
            goBack()
        }
        binding.forwardButton.setOnClickListener {
            hideSuggestions()
            val view = tabManager.activeWebView
            if (view?.canGoForward() == true) view.goForward()
        }
        binding.homeButton.setOnClickListener {
            hideSuggestions()
            loadHomeActive()
        }
        binding.bookmarkButton.setOnClickListener {
            hideSuggestions()
            toggleBookmark()
        }

        binding.addressInput.setOnClickListener {
            val text = binding.addressInput.text.toString()
            if (text.isNotBlank()) {
                binding.addressInput.selectAll()
            }
            showKeyboard(binding.addressInput)
            suggestions.show(if (text.isNotBlank()) "" else text)
        }
        binding.securityIcon.setOnClickListener {
            binding.addressInput.requestFocus()
            binding.addressInput.selectAll()
            showKeyboard(binding.addressInput)
            suggestions.show(binding.addressInput.text.toString())
        }
        binding.addressInput.setOnEditorActionListener { _, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (go || enter) {
                submitAddress()
                true
            } else {
                false
            }
        }

        binding.retryButton.setOnClickListener { retryPage() }
        binding.errorHomeButton.setOnClickListener {
            binding.errorView.visibility = View.GONE
            loadHomeActive()
        }

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            val tab = tabManager.active
            if (tab == null || tab.isHome) {
                binding.swipeRefresh.isRefreshing = false
            } else {
                tab.webView?.reload()
            }
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this) { handleBack() }
    }

    private fun handleBack() {
        when {
            currentFullscreenView != null -> exitFullscreen()
            findBar.isVisible -> findBar.hide()
            suggestions.isShowing -> suggestions.hide()
            tabManager.activeWebView?.canGoBack() == true -> tabManager.activeWebView?.goBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2_000) {
                    finish()
                } else {
                    lastBackAt = now
                    toast(R.string.exit_confirm)
                }
            }
        }
    }

    private fun submitAddress() {
        val text = binding.addressInput.text.toString().trim()
        if (text.isEmpty()) return
        val url = Urls.toUrl(text, prefs.searchEngine)
        suggestions.hide()
        binding.addressInput.clearFocus()
        hideKeyboard(binding.addressInput)
        navigateActive(url)
    }

    private fun retryPage() {
        binding.errorView.visibility = View.GONE
        val tab = tabManager.active ?: return
        if (tab.isHome) loadHome(tab) else tab.webView?.reload()
    }

    private fun reloadOrStop() {
        val tab = tabManager.active ?: return
        val view = tab.webView ?: return
        if (tab.isLoading) {
            view.stopLoading()
            tab.isLoading = false
            binding.reloadButton.setIconResource(R.drawable.ic_refresh)
            binding.progressBar.visibility = View.INVISIBLE
        } else if (tab.isHome) {
            loadHome(tab)
        } else {
            view.reload()
        }
    }

    private fun goBack() {
        val tab = tabManager.active
        val view = tab?.webView
        if (view?.canGoBack() == true) {
            view.goBack()
            return
        }
        if (tab != null && tabManager.tabs.size > 1) {
            tabManager.close(tab.id)
            return
        }
        toast(R.string.exit_confirm)
    }

    private fun loadHomeActive() {
        val tab = tabManager.active
        if (tab == null) {
            createTab()
        } else {
            loadHome(tab)
        }
    }

    private fun loadHome(tab: BrowserTab) {
        val links = history.topSites(8).map { entry ->
            entry.url to entry.title.ifBlank { Urls.displayHost(entry.url) }
        }
        val html = HomePage.render(prefs.searchEngine, links, prefs.shieldEnabled)
        tab.isHome = true
        tab.url = Urls.HOME_URL
        tab.title = getString(R.string.tab_title_new)
        tab.webView?.loadDataWithBaseURL(
            Urls.HOME_URL,
            html,
            "text/html",
            "utf-8",
            null
        )
        updateAddressBar()
    }

    private fun navigateActive(url: String) {
        if (url.isBlank()) return
        if (Urls.isInternal(url)) {
            handleInternalUrl(url)
            return
        }
        if (url.startsWith("file:", true) ||
            url.startsWith("content:", true) ||
            url.startsWith("javascript:", true)
        ) {
            toast(R.string.invalid_url)
            return
        }
        if (!Urls.isHttp(url) && Urls.hasScheme(url) && !Urls.isInternal(url)) {
            confirmExternalLink(url)
            return
        }

        val tab = tabManager.active
        if (tab == null) {
            createTab(url = url)
            return
        }

        suggestions.hide()
        binding.addressInput.clearFocus()
        hideKeyboard(binding.addressInput)
        binding.errorView.visibility = View.GONE

        tab.isHome = Urls.isHome(url)
        tab.url = url
        tab.title = ""
        if (tab.isHome) {
            loadHome(tab)
        } else {
            tab.webView?.loadUrl(url)
        }
        updateAddressBar()
    }

    private fun openFromOutside(url: String) {
        if (url.isBlank()) return
        val active = tabManager.active
        if (active == null || active.isHome) {
            navigateActive(url)
        } else {
            createTab(url = url)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString?.takeIf { it.isNotBlank() }?.let { openFromOutside(it) }
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY)
                if (!query.isNullOrBlank()) {
                    openFromOutside(prefs.searchEngine.searchUrlFor(query))
                }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    val url = Urls.extractUrl(text)
                    openFromOutside(url ?: prefs.searchEngine.searchUrlFor(text))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun createTab(incognito: Boolean = false, url: String = "") {
        suggestions.hide()
        val tab = tabManager.newTab(url = url, incognito = incognito)
        if (tab == null) toast(R.string.tab_limit)
    }

    fun selectTab(id: String) {
        hideSuggestions()
        tabManager.select(id)
    }

    fun closeTab(id: String) {
        tabManager.close(id)
    }

    fun closeAllTabs() {
        tabManager.closeAll()
        createTab()
    }

    fun showFindBar() {
        hideSuggestions()
        findBar.show()
    }

    fun showShieldSheet() {
        hideSuggestions()
        ShieldSheet(this).show()
    }

    fun showBookmarksSheet() {
        hideSuggestions()
        ListSheet(
            activity = this,
            title = getString(R.string.bookmarks),
            searchHint = getString(R.string.search_bookmarks),
            emptyText = getString(R.string.no_bookmarks),
            rowsProvider = { query ->
                bookmarks.search(query).map { bookmark ->
                    ListRow(
                        key = bookmark.id,
                        title = bookmark.title.ifBlank { Urls.pretty(bookmark.url) },
                        subtitle = Urls.pretty(bookmark.url),
                        leadingText = Urls.letterFor(bookmark.title, bookmark.url),
                        actionIcon = R.drawable.ic_delete,
                        actionDescription = getString(R.string.delete),
                        onAction = {
                            bookmarks.remove(bookmark.id)
                            updateBookmarkIcon()
                        },
                        onClick = { navigateActive(bookmark.url) }
                    )
                }
            }
        ).show()
    }

    fun showHistorySheet() {
        hideSuggestions()
        ListSheet(
            activity = this,
            title = getString(R.string.history),
            searchHint = getString(R.string.search_history),
            emptyText = getString(R.string.no_history),
            rowsProvider = { query ->
                history.search(query).map { entry ->
                    ListRow(
                        key = entry.id,
                        title = entry.title.ifBlank { Urls.pretty(entry.url) },
                        subtitle = Urls.pretty(entry.url),
                        leadingText = Urls.letterFor(entry.title, entry.url),
                        actionIcon = R.drawable.ic_delete,
                        actionDescription = getString(R.string.delete),
                        onAction = { history.remove(entry.id) },
                        onClick = { navigateActive(entry.url) }
                    )
                }
            }
        ).show()
    }

    fun showDownloadsSheet() {
        hideSuggestions()
        ListSheet(
            activity = this,
            title = getString(R.string.downloads),
            searchHint = getString(R.string.search_downloads),
            emptyText = getString(R.string.no_downloads),
            rowsProvider = { query ->
                val q = query.trim().lowercase()
                downloadStore.all()
                    .filter {
                        q.isEmpty() ||
                            it.record.title.lowercase().contains(q) ||
                            it.record.url.lowercase().contains(q)
                    }
                    .map { item ->
                        val record = item.record
                        val status = getString(downloadStatus(item.status))
                        val size = if (item.bytes > 0) {
                            Formatter.formatShortFileSize(this, item.bytes)
                        } else {
                            ""
                        }
                        ListRow(
                            key = "${record.id}-${record.time}",
                            title = record.title,
                            subtitle = listOf(status, size)
                                .filter { it.isNotBlank() }
                                .joinToString("  ·  "),
                            leadingIcon = R.drawable.ic_download,
                            actionIcon = R.drawable.ic_delete,
                            actionDescription = getString(R.string.delete_download),
                            onAction = { downloadStore.remove(record.id) },
                            onClick = { openDownload(item) }
                        )
                    }
            }
        ).show()
    }

    fun showAllowedSitesSheet() {
        hideSuggestions()
        ListSheet(
            activity = this,
            title = getString(R.string.allowlist),
            searchHint = null,
            emptyText = getString(R.string.no_allowed_sites),
            rowsProvider = { query ->
                val q = query.trim().lowercase()
                prefs.allowedSites.sorted()
                    .filter { q.isEmpty() || it.contains(q) }
                    .map { site ->
                        ListRow(
                            key = site,
                            title = site,
                            subtitle = getString(R.string.shield_summary_off),
                            leadingText = site.firstOrNull()?.uppercase() ?: "?",
                            actionIcon = R.drawable.ic_delete,
                            actionDescription = getString(R.string.remove_site),
                            onAction = {
                                prefs.disallowSite(site)
                                onShieldSettingsChanged()
                            },
                            onClick = { navigateActive("https://$site") }
                        )
                    }
            }
        ).show()
    }

    fun openSettings() {
        hideSuggestions()
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    fun toggleBookmark() {
        val tab = tabManager.active ?: return
        if (tab.isHome || !Urls.isHttp(tab.url)) {
            toast(R.string.enter_url)
            return
        }
        val added = bookmarks.toggle(tab.url, tab.title)
        toast(if (added) R.string.bookmarked else R.string.bookmark_removed)
        updateBookmarkIcon()
    }

    fun shareCurrentPage() {
        val tab = tabManager.active ?: return
        val url = if (tab.isHome) prefs.homepageUrl else tab.url
        if (url.isBlank()) return
        shareText(url)
    }

    fun setDesktopMode(enabled: Boolean) {
        prefs.desktopMode = enabled
        applyPrefsToAllTabs()
        val tab = tabManager.active
        if (tab?.isHome == true) {
            loadHome(tab)
        } else {
            tab?.webView?.reload()
        }
        toast(if (enabled) R.string.desktop_mode_on else R.string.desktop_mode_off)
    }

    fun showAboutDialog() {
        val version = BuildConfig.VERSION_NAME
        val engine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.getCurrentWebViewPackage()?.versionName
        } else {
            null
        }
        val message = buildString {
            append(getString(R.string.about_text))
            append("\n\n")
            append(getString(R.string.settings_version))
            append(" ")
            append(version)
            if (!engine.isNullOrBlank()) {
                append("\n")
                append(getString(R.string.settings_webview))
                append(" ")
                append(engine)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    fun onHomeSearch(query: String) {
        if (query.isBlank()) return
        navigateActive(prefs.searchEngine.searchUrlFor(query))
    }

    fun onHomeShield() {
        showShieldSheet()
    }

    fun onShieldSettingsChanged() {
        tabManager.tabs.forEach { tab ->
            val view = tab.webView ?: return@forEach
            installShieldScripts(tab, view)
            view.evaluateJavascript(shield.runtimeScript(tab.url), null)
        }
        updateShieldIcon()
    }

    private fun applyPrefsToAllTabs() {
        val night = isNight()
        tabManager.tabs.forEach { tab ->
            val view = tab.webView ?: return@forEach
            view.applyPrefs(prefs, night)
            installShieldScripts(tab, view)
            view.evaluateJavascript(shield.runtimeScript(tab.url), null)
        }
    }

    private fun installShieldScripts(tab: BrowserTab, webView: WebView) {
        tab.shieldHandler?.let { handler ->
            runCatching { WebViewCompat.removeDocumentStartJavaScript(webView, handler) }
        }
        tab.dntHandler?.let { handler ->
            runCatching { WebViewCompat.removeDocumentStartJavaScript(webView, handler) }
        }
        tab.shieldHandler = null
        tab.dntHandler = null
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        tab.shieldHandler = runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                ShieldScripts.documentStart(prefs.shieldEnabled, prefs.allowedSites),
                setOf("*")
            )
        }.getOrNull()
        tab.dntHandler = runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                ShieldScripts.doNotTrack(prefs.doNotTrack),
                setOf("*")
            )
        }.getOrNull()
    }

    override fun createTabView(tab: BrowserTab): AegisWebView {
        val webView = AegisWebView(this)
        webView.applyPrefs(prefs, isNight())
        webView.webViewClient = BrowserClient(this, tab, shield)
        webView.webChromeClient = BrowserChrome(this, tab, shield)
        webView.setFindListener { active, total -> findBar.onResult(active, total) }
        webView.addJavascriptInterface(BlobBridge(this).also { tab.blobBridge = it }, "AegisBlob")
        webView.addJavascriptInterface(HomeBridge(this, tab), "AegisHome")
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(tab, url, userAgent, contentDisposition, mimeType)
        }
        webView.setOnLongClickListener { handleLongPress(webView) }
        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                binding.addressInput.hasFocus()
            ) {
                binding.addressInput.clearFocus()
                hideKeyboard(binding.addressInput)
                suggestions.hide()
            }
            false
        }
        installShieldScripts(tab, webView)
        return webView
    }

    override fun loadTab(tab: BrowserTab) {
        if (tab.isHome || tab.url.isBlank() || Urls.isHome(tab.url)) {
            loadHome(tab)
        } else {
            tab.webView?.loadUrl(tab.url)
        }
    }

    override fun onActiveTabChanged(tab: BrowserTab?) {
        shield.setCurrent(tab?.url)
        if (findBar.isVisible) findBar.hide()
        binding.errorView.visibility = View.GONE
        binding.progressBar.visibility = View.INVISIBLE
        binding.reloadButton.setIconResource(
            if (tab?.isLoading == true) R.drawable.ic_stop else R.drawable.ic_refresh
        )
        updateAddressBar()
        updateNavButtons()
        updateBookmarkIcon()
        updateShieldIcon()
        updateTabBadge()
    }

    override fun onTabListChanged() {
        updateTabBadge()
    }

    private fun updateTabBadge() {
        val count = tabManager.tabs.size
        binding.tabCountBadge.text = if (count > 99) "99+" else count.toString()
        binding.tabCountBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun updateAddressBar() {
        val tab = tabManager.active
        if (tab == null) {
            suggestions.setTextSilently("")
            binding.securityIcon.setImageResource(R.drawable.ic_search)
            return
        }
        if (!binding.addressInput.hasFocus()) {
            suggestions.setTextSilently(if (tab.isHome) "" else Urls.pretty(tab.url))
        }
        binding.securityIcon.setImageResource(
            when {
                tab.isHome -> R.drawable.ic_search
                Urls.isSecure(tab.url) -> R.drawable.ic_lock
                else -> R.drawable.ic_warning
            }
        )
    }

    private fun updateNavButtons() {
        val view = tabManager.activeWebView
        val back = view?.canGoBack() == true
        val forward = view?.canGoForward() == true
        binding.backButton.isEnabled = back
        binding.forwardButton.isEnabled = forward
        binding.backButton.alpha = if (back) 1f else 0.35f
        binding.forwardButton.alpha = if (forward) 1f else 0.35f
    }

    private fun updateBookmarkIcon() {
        val tab = tabManager.active
        val marked = tab != null && !tab.isHome && bookmarks.isBookmarked(tab.url)
        binding.bookmarkButton.setIconResource(
            if (marked) R.drawable.ic_star else R.drawable.ic_star_outline
        )
    }

    private fun updateShieldIcon() {
        val tab = tabManager.active
        val on = prefs.shieldEnabled && !prefs.isSiteAllowed(Urls.host(tab?.url))
        binding.shieldButton.setIconResource(
            if (on) R.drawable.ic_shield else R.drawable.ic_shield_off
        )
        binding.shieldButton.setIconTintResource(
            if (on) R.color.primary else R.color.on_surface_variant
        )
    }

    fun onPageStarted(tab: BrowserTab, url: String) {
        tab.isLoading = true
        val internal = Urls.isInternal(url)
        if (!internal) {
            tab.url = url
            tab.isHome = false
        }
        if (tab == tabManager.active) {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = 0
            binding.errorView.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            binding.reloadButton.setIconResource(R.drawable.ic_stop)
            updateAddressBar()
            updateNavButtons()
        }
        if (!internal && Urls.isHttp(url)) {
            val view = tab.webView ?: return
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(
                    ShieldScripts.documentStart(prefs.shieldEnabled, prefs.allowedSites),
                    null
                )
            }
            view.evaluateJavascript(shield.runtimeScript(url), null)
        }
    }

    fun onPageFinished(tab: BrowserTab, url: String) {
        tab.isLoading = false
        if (!Urls.isInternal(url)) {
            tab.url = url
            tab.isHome = false
        }
        if (tab == tabManager.active) {
            binding.progressBar.visibility = View.INVISIBLE
            binding.swipeRefresh.isRefreshing = false
            binding.reloadButton.setIconResource(R.drawable.ic_refresh)
            updateAddressBar()
            updateNavButtons()
            updateBookmarkIcon()
            updateShieldIcon()
            shield.onPageLoaded()
            if (findBar.isVisible) findBar.search(binding.findInput.text.toString())
        }
        if (Urls.isHttp(tab.url) && !tab.incognito) {
            history.record(tab.url, tab.title)
        }
    }

    fun onUrlCommitted(tab: BrowserTab, url: String) {
        if (Urls.isInternal(url)) return
        tab.url = url
        tab.isHome = false
        if (tab == tabManager.active) {
            shield.onCommitted(url)
            updateAddressBar()
            updateNavButtons()
            updateBookmarkIcon()
            updateShieldIcon()
        }
    }

    fun onTitle(tab: BrowserTab, title: String) {
        if (title.isNotBlank()) tab.title = title
        if (Urls.isHttp(tab.url) && !tab.incognito && title.isNotBlank()) {
            history.record(tab.url, title)
        }
    }

    fun onProgress(tab: BrowserTab, progress: Int) {
        tab.progress = progress
        if (tab != tabManager.active) return
        binding.progressBar.progress = progress
        if (progress >= 100) {
            binding.progressBar.visibility = View.INVISIBLE
            binding.swipeRefresh.isRefreshing = false
        } else {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    fun onPageError(tab: BrowserTab, url: String, code: Int, description: String) {
        if (tab != tabManager.active) return
        if (!Urls.isHttp(url)) return
        binding.progressBar.visibility = View.INVISIBLE
        binding.swipeRefresh.isRefreshing = false
        binding.reloadButton.setIconResource(R.drawable.ic_refresh)
        binding.errorView.visibility = View.VISIBLE
        binding.errorTitle.setText(R.string.error_title)
        binding.errorMessage.text = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_IO -> getString(R.string.error_offline)
            WebViewClient.ERROR_TIMEOUT -> getString(R.string.error_timeout)
            WebViewClient.ERROR_FILE_NOT_FOUND -> getString(R.string.error_not_found)
            else -> description.takeIf { it.isNotBlank() } ?: getString(R.string.error_generic)
        }
    }

    fun onSslError(tab: BrowserTab, handler: SslErrorHandler, error: android.net.http.SslError) {
        if (isFinishing || isDestroyed) {
            handler.cancel()
            return
        }
        val host = Urls.host(error.url) ?: Urls.displayHost(tab.url)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ssl_title)
            .setMessage(getString(R.string.ssl_message, host))
            .setPositiveButton(R.string.ssl_proceed) { _, _ -> handler.proceed() }
            .setNegativeButton(R.string.ssl_go_back) { _, _ -> handler.cancel() }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    fun onSafeBrowsingHit(callback: SafeBrowsingResponse) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dangerous_site)
            .setMessage(R.string.dangerous_site_message)
            .setPositiveButton(R.string.back_to_safety) { _, _ -> callback.backToSafety(true) }
            .setNegativeButton(R.string.ssl_proceed) { _, _ -> callback.proceed(false) }
            .setOnCancelListener { callback.backToSafety(true) }
            .show()
    }

    fun onRendererGone(tab: BrowserTab) {
        runCatching {
            tab.webView?.let { binding.webContainer.removeView(it) }
        }
        tab.webView = null
        tabManager.close(tab.id)
        toast(R.string.error_generic)
    }

    fun handleInternalUrl(url: String) {
        when {
            url.startsWith("aegis://search", true) -> {
                val query = runCatching { Uri.parse(url).getQueryParameter("q") }.getOrNull()
                if (!query.isNullOrBlank()) onHomeSearch(query) else loadHomeActive()
            }
            url.startsWith("aegis://shield", true) -> onHomeShield()
            Urls.isHome(url) -> {
                val uri = Uri.parse(url)
                val path = uri.path.orEmpty()
                when {
                    path.startsWith("/search") -> {
                        val query = uri.getQueryParameter("q")
                        if (!query.isNullOrBlank()) onHomeSearch(query) else loadHomeActive()
                    }
                    path.startsWith("/shield") -> onHomeShield()
                    else -> loadHomeActive()
                }
            }
        }
    }

    fun confirmExternalLink(url: String) {
        val scheme = url.substringBefore(':').ifBlank { url }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.open_external_title)
            .setMessage(getString(R.string.open_external_message, scheme))
            .setPositiveButton(R.string.open_external_confirm) { _, _ -> launchExternal(url) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun launchExternal(url: String) {
        val intent = if (url.startsWith("intent:", true)) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
        } else {
            runCatching { Intent(Intent.ACTION_VIEW, Uri.parse(url)) }.getOrNull()
        }
        if (intent == null) {
            toast(R.string.no_app)
            return
        }
        runCatching { startActivity(intent) }.onFailure { toast(R.string.no_app) }
    }

    fun openPopupWindow(parent: BrowserTab, resultMsg: Message): Boolean {
        val popup = tabManager.createPopupTab(parent.incognito) ?: return false
        val view = popup.webView ?: return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = view
        resultMsg.sendToTarget()
        popup.isPopup = true
        return true
    }

    fun closePopupWindow(window: WebView) {
        val tab = tabManager.findTabFor(window) ?: return
        tabManager.close(tab.id)
    }

    fun launchFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        pendingFilePathCallback?.onReceiveValue(null)
        pendingFilePathCallback = callback
        return runCatching {
            fileChooserLauncher.launch(params.createIntent())
            true
        }.getOrElse {
            pendingFilePathCallback = null
            callback.onReceiveValue(null)
            false
        }
    }

    fun requestGeolocationPermission(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_needed)
            .setMessage(R.string.location_permission)
            .setPositiveButton(R.string.allow_permission) { _, _ ->
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoCallback = callback
                    pendingGeoOrigin = origin
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
            .setNegativeButton(R.string.deny_permission) { _, _ ->
                callback.invoke(origin, false, false)
            }
            .setOnCancelListener { callback.invoke(origin, false, false) }
            .show()
    }

    fun handleWebPermission(request: PermissionRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_needed)
            .setMessage(R.string.camera_permission)
            .setPositiveButton(R.string.allow_permission) { _, _ ->
                ensureMediaPermissions(request)
            }
            .setNegativeButton(R.string.deny_permission) { _, _ -> request.deny() }
            .setOnCancelListener { request.deny() }
            .show()
    }

    private fun ensureMediaPermissions(request: PermissionRequest) {
        val needed = mutableListOf<String>()
        if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isEmpty()) {
            request.grant(request.resources)
            return
        }
        pendingWebPermission = request
        mediaPermissionLauncher.launch(needed.toTypedArray())
    }

    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (currentFullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        currentFullscreenView = view
        currentFullscreenCallback = callback
        binding.fullscreenContainer.removeAllViews()
        binding.fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        binding.fullscreenContainer.visibility = View.VISIBLE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun exitFullscreen() {
        val view = currentFullscreenView ?: return
        binding.fullscreenContainer.removeView(view)
        binding.fullscreenContainer.visibility = View.GONE
        currentFullscreenView = null
        currentFullscreenCallback?.onCustomViewHidden()
        currentFullscreenCallback = null
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun onBlobDownload(dataUrl: String, mime: String, name: String) {
        val decoded = Downloads.decodeDataUrl(dataUrl)
        if (decoded == null) {
            toast(R.string.download_failed)
            return
        }
        val type = mime.ifBlank { decoded.second }
        val fileName = ensureExtension(name, type)
        val uri = Downloads.saveBytes(this, fileName, type, decoded.first)
        if (uri == null) {
            toast(R.string.download_failed)
            return
        }
        downloadStore.add(
            DownloadRecord(
                id = 0L,
                url = "blob",
                title = fileName,
                mime = type,
                time = System.currentTimeMillis(),
                localUri = uri.toString(),
                blob = true
            )
        )
        toast(R.string.download_started)
    }

    private fun handleDownload(
        tab: BrowserTab,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        when {
            url.startsWith("blob:", true) -> startBlobDownload(tab, url, mimeType, contentDisposition)
            url.startsWith("data:", true) -> saveDataUrl(url, mimeType.orEmpty())
            url.startsWith("http://", true) || url.startsWith("https://", true) -> {
                val ok = Downloads.enqueue(
                    this,
                    downloadStore,
                    url,
                    userAgent,
                    contentDisposition,
                    mimeType
                )
                toast(if (ok) R.string.download_started else R.string.download_failed)
            }
            else -> toast(R.string.download_unsupported)
        }
    }

    private fun startBlobDownload(
        tab: BrowserTab,
        url: String,
        mime: String?,
        disposition: String?
    ) {
        val bridge = tab.blobBridge
        val view = tab.webView
        if (bridge == null || view == null) {
            toast(R.string.download_failed)
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mime).ifBlank { "download" }
        val token = UUID.randomUUID().toString()
        bridge.pendingToken = token
        view.evaluateJavascript(ShieldScripts.blobFetcher(url, token, name), null)
        toast(R.string.preparing_download)
    }

    private fun saveDataUrl(url: String, mime: String) {
        val decoded = Downloads.decodeDataUrl(url)
        if (decoded == null) {
            toast(R.string.download_failed)
            return
        }
        val type = mime.ifBlank { decoded.second }
        val fileName = "download" + Downloads.extensionFor(type)
        val uri = Downloads.saveBytes(this, fileName, type, decoded.first)
        if (uri == null) {
            toast(R.string.download_failed)
            return
        }
        downloadStore.add(
            DownloadRecord(
                id = 0L,
                url = "data",
                title = fileName,
                mime = type,
                time = System.currentTimeMillis(),
                localUri = uri.toString(),
                blob = true
            )
        )
        toast(R.string.download_started)
    }

    private fun ensureExtension(name: String, mime: String): String {
        val base = name.ifBlank { "download" }
        return if (base.contains('.')) base else base + Downloads.extensionFor(mime)
    }

    private fun handleLongPress(webView: WebView): Boolean {
        val result = webView.hitTestResult
        val extra = result.extra ?: return false
        val isImage = result.type == WebView.HitTestResult.IMAGE_TYPE ||
            result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        if (!Urls.isHttp(extra) && !extra.startsWith("data:", true)) return false

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add(getString(R.string.open_in_new_tab) to { createTab(url = extra) })
        actions.add(getString(R.string.copy_link) to { copyToClipboard(extra) })
        actions.add(getString(R.string.share_link) to { shareText(extra) })
        if (isImage) {
            actions.add(
                getString(R.string.save_image) to {
                    if (extra.startsWith("data:", true)) {
                        saveDataUrl(extra, "")
                    } else {
                        val ok = Downloads.enqueue(
                            this,
                            downloadStore,
                            extra,
                            webView.settings.userAgentString,
                            null,
                            null
                        )
                        toast(if (ok) R.string.download_started else R.string.download_failed)
                    }
                }
            )
            actions.add(getString(R.string.copy_image_address) to { copyToClipboard(extra) })
        } else {
            actions.add(
                getString(R.string.download_link) to {
                    val ok = Downloads.enqueue(
                        this,
                        downloadStore,
                        extra,
                        webView.settings.userAgentString,
                        null,
                        null
                    )
                    toast(if (ok) R.string.download_started else R.string.download_failed)
                }
            )
        }

        MaterialAlertDialogBuilder(this)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions[which].second()
            }
            .show()
        return true
    }

    private fun openDownload(item: DownloadItem) {
        val uri = item.localUri
        if (uri != null) {
            val mime = item.record.mime.ifBlank { "application/octet-stream" }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(intent) }.onFailure { toast(R.string.no_app) }
        } else if (Urls.isHttp(item.record.url)) {
            navigateActive(item.record.url)
        } else {
            toast(R.string.download_failed)
        }
    }

    private fun downloadStatus(status: Int): Int = when (status) {
        DownloadManager.STATUS_PENDING -> R.string.download_status_pending
        DownloadManager.STATUS_RUNNING -> R.string.download_status_running
        DownloadManager.STATUS_PAUSED -> R.string.download_status_paused
        DownloadManager.STATUS_SUCCESSFUL -> R.string.download_status_successful
        else -> R.string.download_status_failed
    }

    private fun copyToClipboard(text: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("Aegis", text))
        toast(R.string.copied)
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.share_link)))
        }
    }

    private fun showBlockedSnackbar(blocked: NavigationShield.Blocked) {
        if (isFinishing || isDestroyed) return
        val message = when (blocked.kind) {
            NavigationShield.Kind.POPUP -> getString(R.string.shield_blocked_popup)
            NavigationShield.Kind.REDIRECT ->
                getString(R.string.shield_blocked_redirect, blocked.host)
            NavigationShield.Kind.APP_LINK ->
                getString(R.string.shield_blocked_app, blocked.host)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.allow) {
                when (blocked.kind) {
                    NavigationShield.Kind.APP_LINK -> launchExternal(blocked.url)
                    else -> {
                        prefs.allowSite(blocked.host)
                        onShieldSettingsChanged()
                        if (blocked.url.isNotBlank()) navigateActive(blocked.url)
                    }
                }
            }
            .show()
    }

    private fun showPopupBlockedSnackbar() {
        if (isFinishing || isDestroyed) return
        Snackbar.make(binding.root, R.string.shield_blocked_popup, Snackbar.LENGTH_SHORT)
            .setAction(R.string.allow) {
                prefs.allowSite(Urls.host(tabManager.active?.url))
                onShieldSettingsChanged()
            }
            .show()
    }

    private fun hideSuggestions() {
        suggestions.hide()
        if (binding.addressInput.hasFocus()) {
            binding.addressInput.clearFocus()
            hideKeyboard(binding.addressInput)
        }
    }

    fun showKeyboard(view: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard(view: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        if (::tabManager.isInitialized) {
            tabManager.save(tabStore)
            tabManager.activeWebView?.onPause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::tabManager.isInitialized) {
            tabManager.activeWebView?.onResume()
            tabManager.activeWebView?.resumeTimers()
        }
        updateShieldIcon()
        updateBookmarkIcon()
    }

    override fun onDestroy() {
        if (::tabManager.isInitialized) {
            tabManager.clear()
        }
        super.onDestroy()
    }
}
