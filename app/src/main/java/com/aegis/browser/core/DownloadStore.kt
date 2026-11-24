package com.aegis.browser.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

class DownloadStore(context: Context) {

    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("aegis_downloads", Context.MODE_PRIVATE)
    private val items = mutableListOf<DownloadRecord>()

    init {
        load()
    }

    fun add(record: DownloadRecord) {
        items.add(0, record)
        save()
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
        if (id != 0L) {
            runCatching { downloadManager().remove(id) }
        }
        save()
    }

    fun clear() {
        items.forEach { record ->
            if (record.id != 0L) {
                runCatching { downloadManager().remove(record.id) }
            }
        }
        items.clear()
        save()
    }

    fun all(): List<DownloadItem> = items.map { record ->
        if (record.blob || record.id == 0L) {
            DownloadItem(
                record = record,
                status = DownloadManager.STATUS_SUCCESSFUL,
                bytes = 0L,
                localUri = record.localUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            )
        } else {
            query(record)
        }
    }

    private fun query(record: DownloadRecord): DownloadItem {
        val dm = downloadManager()
        val cursor = runCatching {
            dm.query(DownloadManager.Query().setFilterById(record.id))
        }.getOrNull()
        if (cursor == null) {
            return DownloadItem(record, DownloadManager.STATUS_FAILED, 0L, null)
        }
        cursor.use { c ->
            if (!c.moveToFirst()) {
                return DownloadItem(record, DownloadManager.STATUS_FAILED, 0L, null)
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val uri = if (status == DownloadManager.STATUS_SUCCESSFUL) {
                runCatching { dm.getUriForDownloadedFile(record.id) }.getOrNull()
            } else {
                null
            }
            return DownloadItem(
                record = record,
                status = status,
                bytes = if (total > 0) total else soFar,
                localUri = uri
            )
        }
    }

    private fun downloadManager(): DownloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun load() {
        items.clear()
        val raw = sp.getString("items", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    DownloadRecord(
                        id = obj.optLong("id"),
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        mime = obj.optString("mime"),
                        time = obj.optLong("time"),
                        localUri = obj.optString("localUri").takeIf { it.isNotEmpty() },
                        blob = obj.optBoolean("blob")
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
                    put("mime", item.mime)
                    put("time", item.time)
                    put("localUri", item.localUri ?: "")
                    put("blob", item.blob)
                }
            )
        }
        sp.edit().putString("items", array.toString()).apply()
    }
}
