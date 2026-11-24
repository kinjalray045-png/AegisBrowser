package com.aegis.browser.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class HistoryStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("aegis_history", Context.MODE_PRIVATE)
    private val items = mutableListOf<HistoryEntry>()

    init {
        load()
    }

    fun all(): List<HistoryEntry> = items.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    fun record(url: String, title: String) {
        if (!Urls.isHttp(url) || Urls.isHome(url)) return
        val now = System.currentTimeMillis()
        val existing = items.firstOrNull { it.url == url }
        if (existing != null) {
            items.remove(existing)
            items.add(
                0,
                existing.copy(
                    title = title.trim().ifEmpty { existing.title },
                    visitedAt = now
                )
            )
        } else {
            items.add(
                0,
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    title = title.trim().ifEmpty { Urls.pretty(url) },
                    visitedAt = now
                )
            )
        }
        if (items.size > 600) {
            items.subList(500, items.size).clear()
        }
        save()
    }

    fun topSites(limit: Int): List<HistoryEntry> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<HistoryEntry>()
        for (entry in items) {
            val key = Urls.siteKeyOfUrl(entry.url) ?: continue
            if (seen.add(key)) {
                result.add(entry)
                if (result.size >= limit) break
            }
        }
        return result
    }

    fun search(query: String): List<HistoryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items.toList()
        return items.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        save()
    }

    fun removeUrl(url: String) {
        items.removeAll { it.url == url }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun load() {
        items.clear()
        val raw = sp.getString("items", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    HistoryEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        visitedAt = obj.optLong("visitedAt")
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
                    put("visitedAt", item.visitedAt)
                }
            )
        }
        sp.edit().putString("items", array.toString()).apply()
    }
}
