package com.aegis.browser.ui

import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import com.aegis.browser.BrowserActivity
import com.aegis.browser.R
import com.aegis.browser.databinding.ItemMenuRowBinding
import com.aegis.browser.databinding.ItemMenuTileBinding
import com.aegis.browser.databinding.SheetMenuBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class MenuSheet(private val activity: BrowserActivity) {

    private val dialog = BottomSheetDialog(activity)
    private val binding = SheetMenuBinding.inflate(activity.layoutInflater)

    fun show() {
        binding.menuGrid.columnCount = 4

        addTile(R.drawable.ic_add, R.string.menu_new_tab) {
            dialog.dismiss()
            activity.createTab(incognito = false)
        }
        addTile(R.drawable.ic_incognito, R.string.menu_incognito) {
            dialog.dismiss()
            activity.createTab(incognito = true)
        }
        addTile(R.drawable.ic_star_outline, R.string.menu_bookmark) {
            dialog.dismiss()
            activity.toggleBookmark()
        }
        addTile(R.drawable.ic_bookmark, R.string.menu_bookmarks) {
            dialog.dismiss()
            activity.showBookmarksSheet()
        }
        addTile(R.drawable.ic_history, R.string.menu_history) {
            dialog.dismiss()
            activity.showHistorySheet()
        }
        addTile(R.drawable.ic_download, R.string.menu_downloads) {
            dialog.dismiss()
            activity.showDownloadsSheet()
        }
        addTile(R.drawable.ic_find, R.string.menu_find) {
            dialog.dismiss()
            activity.showFindBar()
        }
        addTile(R.drawable.ic_share, R.string.menu_share) {
            dialog.dismiss()
            activity.shareCurrentPage()
        }

        addRow(
            icon = R.drawable.ic_desktop,
            titleRes = R.string.menu_desktop,
            value = null,
            switch = true,
            checked = activity.prefs.desktopMode
        ) { checked ->
            dialog.dismiss()
            activity.setDesktopMode(checked)
        }

        addRow(
            icon = R.drawable.ic_shield,
            titleRes = R.string.menu_shield,
            value = activity.getString(
                if (activity.prefs.shieldEnabled) R.string.shield_on else R.string.shield_off
            )
        ) {
            dialog.dismiss()
            activity.showShieldSheet()
        }

        addRow(
            icon = R.drawable.ic_lock,
            titleRes = R.string.site_lock,
            switch = true,
            checked = activity.prefs.siteLockEnabled
        ) { checked ->
            activity.setSiteLock(checked)
        }

        addRow(
            icon = R.drawable.ic_settings,
            titleRes = R.string.menu_settings
        ) {
            dialog.dismiss()
            activity.openSettings()
        }

        addRow(
            icon = R.drawable.ic_info,
            titleRes = R.string.menu_about
        ) {
            dialog.dismiss()
            activity.showAboutDialog()
        }

        dialog.setContentView(binding.root)
        dialog.setOnShowListener {
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    private fun addTile(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
        val tile = ItemMenuTileBinding.inflate(activity.layoutInflater, binding.menuGrid, false)
        tile.tileIcon.setImageResource(iconRes)
        tile.tileLabel.setText(labelRes)
        tile.root.setOnClickListener { onClick() }

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        binding.menuGrid.addView(tile.root, params)
    }

    private fun addRow(
        icon: Int,
        titleRes: Int,
        value: String? = null,
        switch: Boolean = false,
        checked: Boolean = false,
        onClick: (Boolean) -> Unit
    ) {
        val row = ItemMenuRowBinding.inflate(activity.layoutInflater, binding.menuRows, false)
        row.rowIcon.setImageResource(icon)
        row.rowTitle.setText(titleRes)

        if (!value.isNullOrBlank()) {
            row.rowValue.text = value
            row.rowValue.visibility = View.VISIBLE
        }

        if (switch) {
            row.rowChevron.visibility = View.GONE
            row.rowSwitch.visibility = View.VISIBLE
            row.rowSwitch.isChecked = checked
            row.rowSwitch.setOnCheckedChangeListener { _, isChecked -> onClick(isChecked) }
            row.root.setOnClickListener { row.rowSwitch.toggle() }
        } else {
            row.rowChevron.visibility = View.VISIBLE
            row.root.setOnClickListener { onClick(false) }
        }

        binding.menuRows.addView(row.root)
    }
}
