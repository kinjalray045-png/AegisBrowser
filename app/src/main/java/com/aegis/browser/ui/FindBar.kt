package com.aegis.browser.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.WebView
import com.aegis.browser.BrowserActivity
import com.aegis.browser.databinding.ActivityBrowserBinding

/**
 * Small floating find-in-page bar. It stays attached to whichever tab is active
 * and re-runs the current query when the bar is reopened.
 */
class FindBar(
    private val activity: BrowserActivity,
    private val binding: ActivityBrowserBinding,
    private val webViewProvider: () -> WebView?
) {

    var isVisible: Boolean = false
        private set

    init {
        binding.findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isVisible) search(s?.toString().orEmpty())
            }
        })

        binding.findPrev.setOnClickListener {
            runCatching { webViewProvider()?.findNext(false) }
        }
        binding.findNext.setOnClickListener {
            runCatching { webViewProvider()?.findNext(true) }
        }
        binding.findClose.setOnClickListener { hide() }
    }

    fun show() {
        isVisible = true
        binding.findBar.visibility = View.VISIBLE
        binding.findInput.requestFocus()
        binding.findInput.selectAll()
        activity.showKeyboard(binding.findInput)
        search(binding.findInput.text.toString())
    }

    fun hide() {
        isVisible = false
        binding.findBar.visibility = View.GONE
        binding.findCount.text = ""
        runCatching { webViewProvider()?.clearMatches() }
        activity.hideKeyboard(binding.findInput)
    }

    fun search(query: String) {
        val view = webViewProvider() ?: return
        runCatching {
            if (query.isEmpty()) {
                view.clearMatches()
                binding.findCount.text = ""
            } else {
                view.findAllAsync(query)
            }
        }
    }

    fun onResult(activeMatch: Int, totalMatches: Int) {
        if (!isVisible) return
        binding.findCount.text = if (totalMatches <= 0) {
            "0/0"
        } else {
            "${activeMatch + 1}/$totalMatches"
        }
    }
}
