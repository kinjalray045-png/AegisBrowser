package com.aegis.browser.web

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.aegis.browser.core.DownloadRecord
import com.aegis.browser.core.DownloadStore
import java.io.File

object Downloads {

    fun enqueue(
        context: Context,
        store: DownloadStore,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mime: String?
    ): Boolean {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
        val fileName = runCatching {
            URLUtil.guessFileName(url, contentDisposition, mime)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "download"

        return runCatching {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(url)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                mime?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            store.add(
                DownloadRecord(
                    id = id,
                    url = url,
                    title = fileName,
                    mime = mime.orEmpty(),
                    time = System.currentTimeMillis(),
                    localUri = null,
                    blob = false
                )
            )
            true
        }.getOrDefault(false)
    }

    fun decodeDataUrl(url: String): Pair<ByteArray, String>? {
        if (!url.startsWith("data:", true)) return null
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val meta = url.substring(5, comma)
        val payload = url.substring(comma + 1)
        val mime = meta.substringBefore(';').ifBlank { "text/plain" }
        val bytes = runCatching {
            if (meta.contains("base64", ignoreCase = true)) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                Uri.decode(payload).toByteArray(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        return bytes to mime
    }

    fun saveBytes(context: Context, name: String, mime: String, bytes: ByteArray): Uri? {
        val safeName = name.ifBlank { "download" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: return null
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            runCatching { resolver.update(uri, values, null, null) }
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = uniqueFile(dir, safeName)
            val ok = runCatching {
                file.outputStream().use { it.write(bytes) }
            }.isSuccess
            if (!ok) return null
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrNull()
        }
    }

    fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var index = 1
        while (file.exists()) {
            file = File(dir, "$base ($index)$ext")
            index++
        }
        return file
    }

    fun extensionFor(mime: String): String = when {
        mime.contains("image/png") -> ".png"
        mime.contains("image/jpeg") -> ".jpg"
        mime.contains("image/webp") -> ".webp"
        mime.contains("image/gif") -> ".gif"
        mime.contains("application/pdf") -> ".pdf"
        mime.contains("zip") -> ".zip"
        mime.contains("audio/mpeg") -> ".mp3"
        mime.contains("video/mp4") -> ".mp4"
        mime.contains("text/plain") -> ".txt"
        else -> ""
    }
}
