package com.aegis.browser.ui

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aegis.browser.BrowserActivity
import com.aegis.browser.R
import com.aegis.browser.core.Urls
import com.aegis.browser.databinding.ItemSwitchRowBinding
import com.aegis.browser.databinding.SheetShieldBinding
import com.aegis.browser.web.NavigationShield
import com.google.android.material.bottomsheet.BottomSheetDialog

class ShieldSheet(private val activity: BrowserActivity) {

    private val dialog = BottomSheetDialog(activity)
    private val binding = SheetShieldBinding.inflate(activity.layoutInflater)
    private var siteHost: String? = null

    fun show() {
        val pageUrl = activity.tabManager.active?.url
        siteHost = if (Urls.isHome(pageUrl)) null else Urls.host(pageUrl)

        binding.shieldSwitch.isChecked = activity.prefs.shieldEnabled
        binding.shieldSwitch.setOnCheckedChangeListener { _, checked ->
            activity.prefs.shieldEnabled = checked
            activity.onShieldSettingsChanged()
            updateSummary()
        }

        addToggle(
            R.string.blocked_popups,
            R.string.blocked_popups_desc,
            activity.prefs.shieldPopups
        ) { checked ->
            activity.prefs.shieldPopups = checked
        }

        addToggle(
            R.string.blocked_redirects,
            R.string.blocked_redirects_desc,
            activity.prefs.shieldRedirects
        ) { checked ->
            activity.prefs.shieldRedirects = checked
        }

        addToggle(
            R.string.blocked_app_links,
            R.string.blocked_app_links_desc,
            activity.prefs.shieldAppLinks
        ) { checked ->
            activity.prefs.shieldAppLinks = checked
        }

        bindSiteRow()
        bindStats()
        updateSummary()

        binding.manageAllowed.setOnClickListener {
            dialog.dismiss()
            activity.showAllowedSitesSheet()
        }
        binding.resetStats.setOnClickListener {
            activity.shield.resetStats()
            bindStats()
        }

        dialog.setContentView(binding.root)
        dialog.setOnShowListener {
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    private fun addToggle(
        titleRes: Int,
        subtitleRes: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = ItemSwitchRowBinding.inflate(
            activity.layoutInflater, binding.shieldToggles, false
        )
        row.switchTitle.setText(titleRes)
        row.switchSubtitle.setText(subtitleRes)
        row.switchWidget.isChecked = checked
        row.switchWidget.setOnCheckedChangeListener { _, isChecked ->
            onChange(isChecked)
            activity.onShieldSettingsChanged()
        }
        row.root.setOnClickListener { row.switchWidget.toggle() }
        binding.shieldToggles.addView(row.root)
    }

    private fun bindSiteRow() {
        val host = siteHost
        if (host.isNullOrBlank()) {
            binding.siteHost.text = activity.getString(R.string.tab_title_new)
            binding.siteSwitch.isEnabled = false
            binding.siteSwitch.isChecked = true
            binding.siteDesc.text = activity.getString(R.string.shield_summary_on)
            return
        }

        val allowed = activity.prefs.isSiteAllowed(host)
        binding.siteHost.text = host
        binding.siteSwitch.isEnabled = true
        binding.siteSwitch.isChecked = !allowed
        binding.siteDesc.text = if (allowed) {
            activity.getString(R.string.shield_summary_off)
        } else {
            activity.getString(R.string.protection_this_site_desc, host)
        }

        binding.siteSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                activity.prefs.disallowSite(host)
            } else {
                activity.prefs.allowSite(host)
            }
            activity.onShieldSettingsChanged()
            binding.siteDesc.text = if (checked) {
                activity.getString(R.string.protection_this_site_desc, host)
            } else {
                activity.getString(R.string.shield_summary_off)
            }
            updateSummary()
        }
    }

    private fun updateSummary() {
        val summary = when {
            !activity.prefs.shieldEnabled -> activity.getString(R.string.shield_global_off)
            siteHost != null && activity.prefs.isSiteAllowed(siteHost) ->
                activity.getString(R.string.shield_summary_off)
            else -> activity.getString(R.string.shield_summary_on)
        }
        binding.shieldSummary.text = summary
    }

    private fun bindStats() {
        binding.statsText.text = buildString {
            append(activity.getString(R.string.blocked_this_session, activity.shield.sessionBlocked))
            append("  ·  ")
            append(activity.getString(R.string.blocked_total, activity.prefs.blockedTotal))
        }

        binding.recentBlocks.removeAllViews()
        val items = activity.shield.recent.take(6)
        if (items.isEmpty()) {
            binding.recentBlocks.addView(label(activity.getString(R.string.no_recent_blocks)))
        } else {
            items.forEach { block ->
                binding.recentBlocks.addView(label("${block.host}  ·  ${kindLabel(block.kind)}"))
            }
        }
    }

    private fun label(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(activity, R.color.on_surface_variant))
        val pad = (3 * resources.displayMetrics.density).toInt()
        setPadding(0, pad, 0, pad)
    }

    private fun kindLabel(kind: NavigationShield.Kind): String = when (kind) {
        NavigationShield.Kind.POPUP -> activity.getString(R.string.kind_popup)
        NavigationShield.Kind.REDIRECT -> activity.getString(R.string.kind_redirect)
        NavigationShield.Kind.APP_LINK -> activity.getString(R.string.kind_app_link)
    }
}
