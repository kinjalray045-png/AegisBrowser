package com.aegis.browser.ui

import com.aegis.browser.BrowserActivity
import com.aegis.browser.R
import com.aegis.browser.databinding.SheetTabsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.GridLayoutManager

class TabSwitcherSheet(private val activity: BrowserActivity) {

    private val dialog = BottomSheetDialog(activity)
    private val binding = SheetTabsBinding.inflate(activity.layoutInflater)
    private val adapter = TabsAdapter(
        onSelect = { tab ->
            activity.selectTab(tab.id)
            dialog.dismiss()
        },
        onClose = { tab ->
            activity.closeTab(tab.id)
            refresh()
        }
    )

    fun show() {
        binding.tabsList.layoutManager = GridLayoutManager(activity, 2)
        binding.tabsList.adapter = adapter

        binding.newTabButton.setOnClickListener {
            dialog.dismiss()
            activity.createTab(incognito = false)
        }
        binding.newIncognitoButton.setOnClickListener {
            dialog.dismiss()
            activity.createTab(incognito = true)
        }
        binding.closeAllButton.setOnClickListener { confirmCloseAll() }

        dialog.setContentView(binding.root)
        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
        refresh()
    }

    private fun refresh() {
        adapter.submitList(activity.tabManager.tabs.toList())
        binding.tabsTitle.text = activity.getString(
            R.string.tab_count,
            activity.tabManager.tabs.size
        )
        binding.closeAllButton.isEnabled = activity.tabManager.tabs.isNotEmpty()
    }

    private fun confirmCloseAll() {
        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.close_all_confirm)
            .setPositiveButton(R.string.close_all_tabs) { _, _ ->
                dialog.dismiss()
                activity.closeAllTabs()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
