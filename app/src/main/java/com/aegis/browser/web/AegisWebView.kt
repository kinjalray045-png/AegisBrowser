package com.aegis.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.aegis.browser.core.Prefs

@SuppressLint("SetJavaScriptEnabled")
class AegisWebView(context: Context) : WebView(context) {

    private val defaultUserAgent: String = settings.userAgentString

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = true
        setBackgroundColor(Color.TRANSPARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
            setGeolocationEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { settings.safeBrowsingEnabled = true }
        }
    }

    fun applyPrefs(prefs: Prefs, darkTheme: Boolean) {
        settings.apply {
            javaScriptEnabled = prefs.javaScriptEnabled
            loadsImagesAutomatically = prefs.loadImages
            blockNetworkImage = !prefs.loadImages
            textZoom = prefs.textZoom
            userAgentString = buildUserAgent(prefs.desktopMode)
            // The shield decides in onCreateWindow whether a pop-up is allowed,
            // so JS must always be able to reach it.
            javaScriptCanOpenWindowsAutomatically = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(prefs.cookiesEnabled)
            setAcceptThirdPartyCookies(this@AegisWebView, prefs.cookiesEnabled)
        }
        applyDarkMode(darkTheme)
    }

    fun buildUserAgent(desktop: Boolean): String {
        if (!desktop) return defaultUserAgent
        val version = Regex("Chrome/([0-9.]+)")
            .find(defaultUserAgent)
            ?.groupValues
            ?.getOrNull(1)
            ?: "124.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$version Safari/537.36"
    }

    private fun applyDarkMode(dark: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, dark)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                settings,
                if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            )
        }
    }
}
