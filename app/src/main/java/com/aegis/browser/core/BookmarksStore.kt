package com.aegis.browser.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BookmarksStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("aegis_bookmarks", Context.MODE_PRIVATE)
    private val items = mutableListOf<Bookmark>()

    init {
        load()
    }

    fun all(): List<Bookmark> = items.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    fun isBookmarked(url: String): Boolean = items.any { it.url == url }

    fun find(url: String): Bookmark? = items.firstOrNull { it.url == url }

    fun add(url: String, title: String): Bookmark {
        val entry = Bookmark(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title.trim().ifEmpty { Urls.pretty(url) },
            createdAt = System.currentTimeMillis()
        )
        items.add(0, entry)
        save()
        return entry
    }

    fun toggle(url: String, title: String): Boolean {
        val existing = find(url)
        return if (existing != null) {
            remove(existing.id)
            false
        } else {
            add(url, title)
            true
        }
    }

    fun update(id: String, title: String, url: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = items[index]
        items[index] = old.copy(
            title = title.trim().ifEmpty { Urls.pretty(url) },
            url = url
        )
        save()
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    fun search(query: String): List<Bookmark> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items.toList()
        return items.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
    }

    private fun load() {
        items.clear()
        val raw = sp.getString("items", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    Bookmark(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        createdAt = obj.optLong("createdAt")
                    )
                )
            }
        }
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("title", item.title)
                    put("createdAt", item.createdAt)
                }
            )
        }
        sp.edit().putString("items", array.toString()).apply()
    }
}
