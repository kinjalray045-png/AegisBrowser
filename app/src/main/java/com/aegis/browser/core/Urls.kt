package com.aegis.browser.core

import android.net.Uri

/**
 * URL helpers shared by the address bar, the tab manager and the shield.
 */
object Urls {

    const val HOME_URL = "https://aegis.home/"

    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$")

    /** Two-label public suffixes that should be treated as one "site". */
    private val twoLevelSuffixes = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "net.uk", "sch.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.in", "net.in", "org.in", "gen.in", "firm.in",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "com.br", "net.br", "org.br", "gov.br",
        "co.za", "org.za", "net.za", "gov.za",
        "com.mx", "org.mx", "gob.mx",
        "co.nz", "org.nz", "net.nz", "govt.nz",
        "com.tr", "org.tr", "net.tr", "gov.tr",
        "com.sg", "edu.sg", "gov.sg",
        "com.hk", "org.hk", "edu.hk", "gov.hk",
        "co.kr", "or.kr", "ne.kr", "go.kr",
        "com.tw", "org.tw", "edu.tw", "gov.tw",
        "com.cn", "net.cn", "org.cn", "gov.cn",
        "com.ar", "org.ar", "gob.ar",
        "com.co", "net.co", "org.co",
        "com.pe", "org.pe", "gob.pe",
        "com.ve", "org.ve", "gob.ve",
        "com.ua", "org.ua", "net.ua", "gov.ua",
        "co.il", "org.il", "ac.il", "gov.il",
        "com.my", "org.my", "edu.my", "gov.my",
        "com.ph", "org.ph", "edu.ph", "gov.ph",
        "com.pk", "org.pk", "edu.pk", "gov.pk",
        "com.eg", "org.eg", "edu.eg", "gov.eg",
        "com.sa", "org.sa", "edu.sa", "gov.sa",
        "co.id", "or.id", "ac.id", "go.id",
        "com.vn", "org.vn", "edu.vn", "gov.vn",
        "com.ng", "org.ng", "edu.ng", "gov.ng",
        "co.ke", "or.ke", "ac.ke", "go.ke",
        "com.pl", "org.pl", "net.pl", "gov.pl",
        "com.ru", "org.ru", "net.ru",
        "co.th", "or.th", "ac.th", "go.th"
    )

    fun isHttp(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    fun isSecure(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    fun isHome(url: String?): Boolean = url != null && url.startsWith(HOME_URL)

    fun isInternal(url: String?): Boolean {
        if (url == null) return false
        return url.startsWith("aegis://") || isHome(url) || url == "about:blank"
    }

    fun hasScheme(input: String): Boolean {
        val value = input.trim()
        val match = schemeRegex.find(value) ?: return false
        val scheme = value.substring(0, match.value.length - 1).lowercase()
        // "localhost:8080" and "example.com:8080" are host:port, not schemes.
        if (scheme == "localhost" || scheme.contains('.')) return false
        return true
    }

    fun looksLikeUrl(input: String): Boolean {
        val value = input.trim()
        if (value.isEmpty() || value.contains(' ')) return false
        if (hasScheme(value)) return true
        if (ipv4Regex.matches(value)) return true
        if (value.equals("localhost", ignoreCase = true) || value.startsWith("localhost:", ignoreCase = true)) return true
        val hostPart = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (!hostPart.contains('.')) return false
        val tld = hostPart.substringAfterLast('.').substringBefore(':')
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    fun toUrl(input: String, engine: SearchEngine): String {
        val value = input.trim()
        if (value.isEmpty()) return engine.homeUrl
        if (hasScheme(value)) return value
        if (looksLikeUrl(value)) return "https://$value"
        return engine.searchUrlFor(value)
    }

    fun host(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    fun siteKeyOfUrl(url: String?): String? = siteKey(host(url))

    fun siteKey(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val value = host.lowercase().removePrefix("www.").substringBefore(':')
        if (value.isEmpty()) return null
        if (ipv4Regex.matches(value)) return value
        val parts = value.split('.').filter { it.isNotEmpty() }
        if (parts.size < 2) return value
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (lastTwo in twoLevelSuffixes && parts.size >= 3) {
            parts.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    /** Canonical form used for "allow once" bookkeeping. */
    fun canonical(url: String): String =
        url.trim().substringBefore('#').removeSuffix("/")

    fun pretty(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removeSuffix("/")

    fun displayHost(url: String?): String =
        host(url)?.removePrefix("www.") ?: ""

    fun isSpecialScheme(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        return scheme in setOf(
            "intent", "market", "mailto", "tel", "sms", "smsto", "geo",
            "whatsapp", "tg", "instagram", "fb", "twitter", "spotify",
            "maps", "file", "content", "javascript"
        )
    }

    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(text)
        return match?.value
    }

    fun letterFor(title: String?, url: String?): String {
        val source = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: displayHost(url).takeIf { it.isNotEmpty() }
            ?: "?"
        return source.first().uppercase()
    }
}
