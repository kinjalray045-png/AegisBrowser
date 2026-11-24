package com.aegis.browser.core

import android.net.Uri

data class Bookmark(
    val id: String,
    val url: String,
    val title: String,
    val createdAt: Long
)

data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val visitedAt: Long
)

data class DownloadRecord(
    val id: Long,
    val url: String,
    val title: String,
    val mime: String,
    val time: Long,
    val localUri: String?,
    val blob: Boolean
)

data class DownloadItem(
    val record: DownloadRecord,
    val status: Int,
    val bytes: Long,
    val localUri: Uri?
)

enum class SearchEngine(
    val key: String,
    val label: String,
    val searchUrl: String,
    val homeUrl: String
) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com/"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com/"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/"),
    ECOSIA("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "https://www.ecosia.org/"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s", "https://www.startpage.com/");

    fun searchUrlFor(query: String): String = searchUrl.replace("%s", Uri.encode(query))

    companion object {
        fun fromKey(key: String?): SearchEngine =
            entries.firstOrNull { it.key == key } ?: GOOGLE
    }
}
