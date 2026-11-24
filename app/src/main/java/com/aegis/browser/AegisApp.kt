package com.aegis.browser

import android.app.Application
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import com.aegis.browser.core.Prefs

class AegisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
