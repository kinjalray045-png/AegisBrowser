package com.aegis.browser.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TabStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("aegis_tabs", Context.MODE_PRIVATE)

    data class SavedTabs(
        val tabs: List<Pair<String, String>>,
        val activeIndex: Int
    )

    fun save(tabs: List<Pair<String, String>>, activeIndex: Int) {
        val array = JSONArray()
        tabs.forEach { (url, title) ->
            array.put(
                JSONObject().apply {
                    put("url", url)
                    put("title", title)
                }
            )
        }
        sp.edit()
            .putString("tabs", array.toString())
            .putInt("active", activeIndex.coerceAtLeast(0))
            .apply()
    }

    fun restore(): SavedTabs {
        val raw = sp.getString("tabs", null) ?: return SavedTabs(emptyList(), 0)
        val tabs = mutableListOf<Pair<String, String>>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url")
                if (url.isNotEmpty()) {
                    tabs.add(url to obj.optString("title"))
                }
            }
        }
        return SavedTabs(tabs, sp.getInt("active", 0))
    }

    fun clear() {
        sp.edit().clear().apply()
    }
}
