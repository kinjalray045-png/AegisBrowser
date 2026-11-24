package com.aegis.browser.core

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("aegis_settings", Context.MODE_PRIVATE)

    var shieldEnabled: Boolean
        get() = sp.getBoolean("shield_enabled", true)
        set(value) = sp.edit().putBoolean("shield_enabled", value).apply()

    var shieldPopups: Boolean
        get() = sp.getBoolean("shield_popups", true)
        set(value) = sp.edit().putBoolean("shield_popups", value).apply()

    var shieldRedirects: Boolean
        get() = sp.getBoolean("shield_redirects", true)
        set(value) = sp.edit().putBoolean("shield_redirects", value).apply()

    var shieldAppLinks: Boolean
        get() = sp.getBoolean("shield_app_links", true)
        set(value) = sp.edit().putBoolean("shield_app_links", value).apply()

    var javaScriptEnabled: Boolean
        get() = sp.getBoolean("javascript", true)
        set(value) = sp.edit().putBoolean("javascript", value).apply()

    var loadImages: Boolean
        get() = sp.getBoolean("images", true)
        set(value) = sp.edit().putBoolean("images", value).apply()

    var cookiesEnabled: Boolean
        get() = sp.getBoolean("cookies", true)
        set(value) = sp.edit().putBoolean("cookies", value).apply()

    var doNotTrack: Boolean
        get() = sp.getBoolean("dnt", true)
        set(value) = sp.edit().putBoolean("dnt", value).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop", false)
        set(value) = sp.edit().putBoolean("desktop", value).apply()

    var textZoom: Int
        get() = sp.getInt("text_zoom", 100)
        set(value) = sp.edit().putInt("text_zoom", value.coerceIn(70, 160)).apply()

    /** One of AppCompatDelegate.MODE_NIGHT_*. -1 follows the system. */
    var themeMode: Int
        get() = sp.getInt("theme_mode", -1)
        set(value) = sp.edit().putInt("theme_mode", value).apply()

    var searchEngineKey: String
        get() = sp.getString("search_engine", SearchEngine.GOOGLE.key) ?: SearchEngine.GOOGLE.key
        set(value) = sp.edit().putString("search_engine", value).apply()

    var searchEngine: SearchEngine
        get() = SearchEngine.fromKey(searchEngineKey)
        set(value) {
            searchEngineKey = value.key
        }

    var homepage: String
        get() = sp.getString("homepage", "") ?: ""
        set(value) = sp.edit().putString("homepage", value.trim()).apply()

    val homepageUrl: String
        get() = homepage.takeIf { it.isNotEmpty() } ?: searchEngine.homeUrl

    var blockedTotal: Int
        get() = sp.getInt("blocked_total", 0)
        set(value) = sp.edit().putInt("blocked_total", value.coerceAtLeast(0)).apply()

    var allowedSites: Set<String>
        get() = sp.getStringSet("allowed_sites", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("allowed_sites", value.toSet()).apply()

    fun isSiteAllowed(host: String?): Boolean {
        val key = Urls.siteKey(host) ?: return false
        return allowedSites.contains(key)
    }

    fun allowSite(host: String?) {
        val key = Urls.siteKey(host) ?: return
        allowedSites = allowedSites + key
    }

    fun disallowSite(host: String?) {
        val key = Urls.siteKey(host) ?: return
        allowedSites = allowedSites - key
    }

    fun resetAll() {
        sp.edit().clear().apply()
    }
}
