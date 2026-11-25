package com.aegis.browser

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aegis.browser.core.HistoryStore
import com.aegis.browser.core.Prefs
import com.aegis.browser.core.SearchEngine
import com.aegis.browser.databinding.ActivitySettingsBinding
import com.aegis.browser.databinding.DialogPromptBinding
import com.aegis.browser.databinding.ItemMenuRowBinding
import com.aegis.browser.databinding.ItemSwitchRowBinding
import com.aegis.browser.ui.ListRow
import com.aegis.browser.ui.ListSheet
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var history: HistoryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        prefs = Prefs(this)
        history = HistoryStore(this)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !night

        build()
    }

    private fun build() {
        binding.settingsContainer.removeAllViews()

        addSection(R.string.settings_privacy)
        val privacy = addCard()
        addSwitch(
            privacy,
            R.string.shield,
            R.string.shield_description,
            prefs.shieldEnabled
        ) { prefs.shieldEnabled = it }
        addSwitch(
            privacy,
            R.string.site_lock,
            R.string.site_lock_desc,
            prefs.siteLockEnabled
        ) { prefs.siteLockEnabled = it }
        addSwitch(
            privacy,
            R.string.blocked_popups,
            R.string.blocked_popups_desc,
            prefs.shieldPopups
        ) { prefs.shieldPopups = it }
        addSwitch(
            privacy,
            R.string.blocked_redirects,
            R.string.blocked_redirects_desc,
            prefs.shieldRedirects
        ) { prefs.shieldRedirects = it }
        addSwitch(
            privacy,
            R.string.blocked_app_links,
            R.string.blocked_app_links_desc,
            prefs.shieldAppLinks
        ) { prefs.shieldAppLinks = it }
        addValueRow(
            privacy,
            R.drawable.ic_shield,
            R.string.allowlist,
            getString(R.string.settings_allowed_sites_desc, prefs.allowedSites.size)
        ) { showAllowedSites() }

        addSection(R.string.settings_browsing)
        val browsing = addCard()
        addValueRow(
            browsing,
            R.drawable.ic_search,
            R.string.settings_search_engine,
            prefs.searchEngine.label
        ) { chooseSearchEngine() }
        addValueRow(
            browsing,
            R.drawable.ic_home,
            R.string.settings_homepage,
            prefs.homepage.ifBlank { prefs.searchEngine.label }
        ) { chooseHomepage() }
        addSwitch(
            browsing,
            R.string.settings_javascript,
            R.string.settings_javascript_desc,
            prefs.javaScriptEnabled
        ) { prefs.javaScriptEnabled = it }
        addSwitch(
            browsing,
            R.string.settings_images,
            R.string.settings_images_desc,
            prefs.loadImages
        ) { prefs.loadImages = it }
        addSwitch(
            browsing,
            R.string.settings_cookies,
            R.string.settings_cookies_desc,
            prefs.cookiesEnabled
        ) { prefs.cookiesEnabled = it }
        addSwitch(
            browsing,
            R.string.settings_dnt,
            R.string.settings_dnt_desc,
            prefs.doNotTrack
        ) { prefs.doNotTrack = it }
        addSwitch(
            browsing,
            R.string.settings_desktop,
            R.string.settings_desktop_desc,
            prefs.desktopMode
        ) { prefs.desktopMode = it }
        addValueRow(
            browsing,
            R.drawable.ic_find,
            R.string.settings_text_scale,
            getString(R.string.settings_text_scale_desc, prefs.textZoom)
        ) { chooseTextZoom() }

        addSection(R.string.settings_appearance)
        val appearance = addCard()
        addValueRow(
            appearance,
            R.drawable.ic_settings,
            R.string.settings_theme,
            themeLabel(prefs.themeMode)
        ) { chooseTheme() }

        addSection(R.string.settings_about)
        val about = addCard()
        addValueRow(
            about,
            R.drawable.ic_info,
            R.string.settings_version,
            BuildConfig.VERSION_NAME
        ) { }
        addValueRow(
            about,
            R.drawable.ic_globe,
            R.string.settings_webview,
            webViewVersion()
        ) { }
        addValueRow(
            about,
            R.drawable.ic_delete,
            R.string.settings_clear_data,
            getString(R.string.settings_clear_data_desc)
        ) { clearData() }
        addValueRow(
            about,
            R.drawable.ic_refresh,
            R.string.settings_reset,
            getString(R.string.settings_reset_desc)
        ) { confirmReset() }
    }

    private fun addSection(titleRes: Int) {
        val header = TextView(this).apply {
            setText(titleRes)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.primary))
            textSize = 12f
            isAllCaps = true
            letterSpacing = 0.08f
            typeface = Typeface.create(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(18), dp(8), dp(6))
        }
        binding.settingsContainer.addView(header)
    }

    private fun addCard(): LinearLayout {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(
                ContextCompat.getColor(this@SettingsActivity, R.color.outline_variant)
            )
            setCardBackgroundColor(
                ContextCompat.getColor(this@SettingsActivity, R.color.surface_container_high)
            )
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        card.addView(
            column,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        binding.settingsContainer.addView(card)
        return column
    }

    private fun addSwitch(
        container: LinearLayout,
        titleRes: Int,
        subtitleRes: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = ItemSwitchRowBinding.inflate(layoutInflater, container, false)
        row.switchTitle.setText(titleRes)
        row.switchSubtitle.setText(subtitleRes)
        row.switchWidget.isChecked = checked
        row.switchWidget.setOnCheckedChangeListener { _, value ->
            onChange(value)
            changed()
        }
        row.root.setOnClickListener { row.switchWidget.toggle() }
        container.addView(row.root)
    }

    private fun addValueRow(
        container: LinearLayout,
        iconRes: Int,
        titleRes: Int,
        value: String,
        onClick: () -> Unit
    ) {
        val row = ItemMenuRowBinding.inflate(layoutInflater, container, false)
        row.rowIcon.setImageResource(iconRes)
        row.rowTitle.setText(titleRes)
        row.rowValue.text = value
        row.rowValue.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
        row.rowChevron.visibility = View.VISIBLE
        row.root.setOnClickListener { onClick() }
        container.addView(row.root)
    }

    private fun themeLabel(mode: Int): String = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
        AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
        else -> getString(R.string.theme_system)
    }

    private fun chooseSearchEngine() {
        val engines = SearchEngine.entries
        val labels = engines.map { it.label }.toTypedArray()
        val current = engines.indexOf(prefs.searchEngine).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_search_engine)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.searchEngine = engines[which]
                changed()
                dialog.dismiss()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseHomepage() {
        val dialogBinding = DialogPromptBinding.inflate(layoutInflater)
        dialogBinding.promptTitleLayout.visibility = View.GONE
        dialogBinding.promptUrlLayout.hint = getString(R.string.custom_homepage_hint)
        dialogBinding.promptUrlInput.setText(prefs.homepage)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_homepage)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.homepage = dialogBinding.promptUrlInput.text?.toString().orEmpty()
                changed()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseTextZoom() {
        val values = intArrayOf(85, 100, 115, 130, 150)
        val labels = values.map { "$it%" }.toTypedArray()
        val current = values.indexOf(prefs.textZoom).let { if (it < 0) 1 else it }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_text_scale)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.textZoom = values[which]
                changed()
                dialog.dismiss()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseTheme() {
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val current = modes.indexOf(prefs.themeMode).let { if (it < 0) 0 else it }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.themeMode = modes[which]
                changed()
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(modes[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearData() {
        val options = arrayOf(
            getString(R.string.clear_history_option),
            getString(R.string.clear_cookies_option),
            getString(R.string.clear_cache_option)
        )
        val checked = booleanArrayOf(true, true, true)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_data_title)
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.clear) { _, _ ->
                if (checked[0]) history.clear()
                if (checked[1]) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                    WebViewDatabase.getInstance(this).clearFormData()
                }
                if (checked[2]) {
                    WebView(this).apply {
                        clearCache(true)
                        destroy()
                    }
                }
                Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
                changed()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.reset_confirm)
            .setPositiveButton(R.string.settings_reset) { _, _ ->
                prefs.resetAll()
                changed()
                AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAllowedSites() {
        ListSheet(
            activity = this,
            title = getString(R.string.allowlist),
            searchHint = null,
            emptyText = getString(R.string.no_allowed_sites),
            rowsProvider = { _ ->
                prefs.allowedSites.sorted().map { site ->
                    ListRow(
                        key = site,
                        title = site,
                        subtitle = getString(R.string.shield_summary_off),
                        leadingText = site.firstOrNull()?.uppercase() ?: "?",
                        actionIcon = R.drawable.ic_delete,
                        actionDescription = getString(R.string.remove_site),
                        onAction = {
                            prefs.disallowSite(site)
                            changed()
                            build()
                        },
                        onClick = { }
                    )
                }
            }
        ).show()
    }

    private fun webViewVersion(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.getCurrentWebViewPackage()?.versionName ?: "System WebView"
        } else {
            "System WebView"
        }
    }

    private fun changed() {
        setResult(RESULT_OK)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
