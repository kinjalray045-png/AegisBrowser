package com.aegis.browser.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.aegis.browser.BrowserActivity
import com.aegis.browser.R
import com.aegis.browser.core.BookmarksStore
import com.aegis.browser.core.HistoryStore
import com.aegis.browser.core.Prefs
import com.aegis.browser.core.Urls
import com.aegis.browser.databinding.ActivityBrowserBinding

/**
 * The drop-down under the address bar. Suggestions combine the current query,
 * the user's bookmarks and their history; nothing is sent anywhere.
 */
class SuggestionsController(
    private val activity: BrowserActivity,
    private val binding: ActivityBrowserBinding,
    private val prefs: Prefs,
    private val bookmarks: BookmarksStore,
    private val history: HistoryStore,
    private val onOpen: (String) -> Unit
) {

    private val adapter = SuggestionAdapter { suggestion -> open(suggestion) }
    private var suppress = false

    var isShowing: Boolean = false
        private set

    init {
        binding.suggestionsList.layoutManager = LinearLayoutManager(activity)
        binding.suggestionsList.adapter = adapter
        binding.suggestionScrim.setOnClickListener { hide() }

        binding.addressInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppress) return
                if (binding.addressInput.hasFocus()) {
                    show(s?.toString().orEmpty())
                }
            }
        })
    }

    fun show(query: String) {
        val items = build(query)
        adapter.submitList(items)
        binding.suggestionOverlay.visibility = View.VISIBLE
        isShowing = true
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        binding.suggestionOverlay.visibility = View.GONE
    }

    /** Updates the address bar without opening the suggestion overlay. */
    fun setTextSilently(text: String) {
        suppress = true
        binding.addressInput.setText(text)
        binding.addressInput.setSelection(binding.addressInput.text.length)
        suppress = false
    }

    private fun open(suggestion: Suggestion) {
        hide()
        binding.addressInput.clearFocus()
        activity.hideKeyboard(binding.addressInput)
        onOpen(suggestion.url)
    }

    private fun build(rawQuery: String): List<Suggestion> {
        val query = rawQuery.trim()
        val out = mutableListOf<Suggestion>()
        val engine = prefs.searchEngine

        if (query.isEmpty()) {
            history.topSites(5).forEach { entry ->
                out.add(
                    Suggestion(
                        title = entry.title.ifBlank { Urls.pretty(entry.url) },
                        subtitle = Urls.pretty(entry.url),
                        type = activity.getString(R.string.suggestion_history),
                        iconRes = R.drawable.ic_history,
                        url = entry.url
                    )
                )
            }
            bookmarks.all().take(4).forEach { bookmark ->
                out.add(
                    Suggestion(
                        title = bookmark.title.ifBlank { Urls.pretty(bookmark.url) },
                        subtitle = Urls.pretty(bookmark.url),
                        type = activity.getString(R.string.suggestion_bookmark),
                        iconRes = R.drawable.ic_star,
                        url = bookmark.url
                    )
                )
            }
            return out
        }

        if (Urls.looksLikeUrl(query) || Urls.hasScheme(query)) {
            out.add(
                Suggestion(
                    title = query,
                    subtitle = activity.getString(R.string.open),
                    type = "",
                    iconRes = R.drawable.ic_globe,
                    url = Urls.toUrl(query, engine)
                )
            )
        }

        out.add(
            Suggestion(
                title = activity.getString(R.string.search_for, query),
                subtitle = engine.label,
                type = activity.getString(R.string.suggestion_search),
                iconRes = R.drawable.ic_search,
                url = engine.searchUrlFor(query)
            )
        )

        bookmarks.search(query).take(3).forEach { bookmark ->
            out.add(
                Suggestion(
                    title = bookmark.title.ifBlank { Urls.pretty(bookmark.url) },
                    subtitle = Urls.pretty(bookmark.url),
                    type = activity.getString(R.string.suggestion_bookmark),
                    iconRes = R.drawable.ic_star,
                    url = bookmark.url
                )
            )
        }

        history.search(query).take(5).forEach { entry ->
            out.add(
                Suggestion(
                    title = entry.title.ifBlank { Urls.pretty(entry.url) },
                    subtitle = Urls.pretty(entry.url),
                    type = activity.getString(R.string.suggestion_history),
                    iconRes = R.drawable.ic_history,
                    url = entry.url
                )
            )
        }

        return out
    }
}
