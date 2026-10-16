package ru.terabitvpn.core.link

import kotlin.io.encoding.Base64

/** Minimal RFC 3986 splitter for share links (`scheme://user@host:port/path?query#fragment`). */
internal class ShareUri(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int,
    val path: String,
    val query: Map<String, String>,
    val fragment: String?,
) {
    fun q(key: String): String? = query[key]?.takeIf { it.isNotEmpty() }

    companion object {
        fun parse(raw: String): ShareUri? {
            val link = raw.trim()
            val schemeEnd = link.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = link.substring(0, schemeEnd).lowercase()
            var rest = link.substring(schemeEnd + 3)

            var fragment: String? = null
            rest.indexOf('#').takeIf { it >= 0 }?.let {
                fragment = percentDecode(rest.substring(it + 1))
                rest = rest.substring(0, it)
            }
            var query = emptyMap<String, String>()
            rest.indexOf('?').takeIf { it >= 0 }?.let {
                query = parseQuery(rest.substring(it + 1))
                rest = rest.substring(0, it)
            }
            var path = ""
            rest.indexOf('/').takeIf { it >= 0 }?.let {
                path = rest.substring(it)
                rest = rest.substring(0, it)
            }
            var userInfo: String? = null
            rest.lastIndexOf('@').takeIf { it >= 0 }?.let {
                userInfo = percentDecode(rest.substring(0, it))
                rest = rest.substring(it + 1)
            }
            val host: String
            var port = 0
            if (rest.startsWith("[")) {
                val close = rest.indexOf(']')
                if (close < 0) return null
                host = rest.substring(1, close)
                rest.substring(close + 1).removePrefix(":").toIntOrNull()?.let { port = it }
            } else {
                val colon = rest.lastIndexOf(':')
                if (colon >= 0) {
                    host = rest.substring(0, colon)
                    port = rest.substring(colon + 1).toIntOrNull() ?: return null
                } else {
                    host = rest
                }
            }
            return ShareUri(scheme, userInfo, host, port, path, query, fragment)
        }

        fun parseQuery(q: String): Map<String, String> =
            q.split('&').filter { it.isNotEmpty() }.associate { kv ->
                val eq = kv.indexOf('=')
                if (eq < 0) percentDecode(kv) to ""
                else percentDecode(kv.substring(0, eq)) to percentDecode(kv.substring(eq + 1))
            }
    }
}

internal fun percentDecode(s: String): String {
    if ('%' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                out.add(hex.toByte()); i += 3; continue
            }
        }
        // '+' stays literal: share links are not form-encoded and base64 passwords contain '+'.
        c.toString().encodeToByteArray().forEach { out.add(it) }
        i++
    }
    return out.toByteArray().decodeToString()
}

/** Lenient base64: standard or URL-safe alphabet, padding optional, whitespace ignored. */
internal fun decodeBase64Lenient(s: String): String? {
    val clean = s.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/').trimEnd('=')
    if (clean.isEmpty()) return null
    val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
    return try {
        Base64.decode(padded).decodeToString()
    } catch (_: IllegalArgumentException) {
        null
    }
}
