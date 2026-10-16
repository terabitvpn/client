package ru.terabitvpn.core.stage

import ru.terabitvpn.core.JsonX
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.max

/**
 * Time-synced lines for the playing track from lrclib.net, matched by artist and duration (never
 * another song with the same name). Platform-free: the caller supplies the HTTP GET.
 */
object Lyrics {

    /** Best lines for the track, and whether they carry real timestamps; null when nothing fits. */
    fun lookup(title: String, artist: String, durationMs: Long, get: (url: String) -> String?): Pair<List<LyricLine>, Boolean>? {
        val cleanTitle = strip(title)
        val queries = buildList {
            add("track_name=${enc(cleanTitle)}" + if (artist.isNotBlank()) "&artist_name=${enc(strip(artist))}" else "")
            if (artist.isNotBlank()) add("track_name=${enc(cleanTitle)}")
            add("q=${enc((artist + " " + cleanTitle).trim())}")
        }
        for (q in queries) {
            val body = get("https://lrclib.net/api/search?$q") ?: continue
            pick(body, cleanTitle, artist, durationMs)?.let { return it }
        }
        return null
    }

    fun pick(body: String, title: String, artist: String, durationMs: Long): Pair<List<LyricLine>, Boolean>? {
        val arr = runCatching { JsonX.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        val wantArtist = norm(strip(artist))
        val wantTitle = norm(title.replace(Regex("(?i)\\s+(feat\\.?|ft\\.?)\\s+.*$"), ""))
        var best: Pair<List<LyricLine>, Boolean>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val a = norm(o.str("artistName"))
            // Same-name songs by other artists must never be picked.
            if (wantArtist.isNotEmpty() && a.isNotEmpty() && !(a.contains(wantArtist) || wantArtist.contains(a))) continue
            val syncedText = o.str("syncedLyrics")
            val lines = if (syncedText.isNotBlank()) parseLrc(syncedText) else emptyList()
            val synced = lines.isNotEmpty()
            val durMs = ((o["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong()
            val usable = if (synced) lines else plain(o.str("plainLyrics"), durMs)
            if (usable.isEmpty()) continue
            var score = if (synced) 35.0 else 0.0
            val tn = norm(o.str("trackName"))
            if (wantTitle == tn) score += 120 else if (tn.contains(wantTitle)) score += 70
            if (wantArtist.isNotEmpty()) score += if (a == wantArtist) 90 else 45
            if (durationMs > 0 && durMs > 0) score += max(0.0, 80.0 - abs(durationMs - durMs) / 1000.0)
            if (score > bestScore) { bestScore = score; best = usable to synced }
        }
        return best
    }

    fun parseLrc(text: String): List<LyricLine> {
        val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val out = ArrayList<LyricLine>()
        text.lines().forEach { row ->
            val times = stamp.findAll(row).map { m ->
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                (m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 + ms
            }.toList()
            val words = stamp.replace(row, "").trim()
            if (times.isNotEmpty() && words.isNotEmpty()) times.forEach { out += LyricLine(it, words) }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Unsynced text spread evenly over the song (no timing to follow then). */
    fun plain(text: String, durMs: Long): List<LyricLine> {
        val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return emptyList()
        val step = max(1500L, (if (durMs > 0) durMs else rows.size * 3500L) / rows.size)
        return rows.mapIndexed { i, r -> LyricLine(i * step, r) }
    }

    /** Cache format: key, "synced"/"plain", then "ms\ttext" rows. */
    fun encode(key: String, v: Pair<List<LyricLine>, Boolean>): String = buildString {
        appendLine(key); appendLine(if (v.second) "synced" else "plain")
        v.first.forEach { appendLine("${it.timeMs}\t${it.text}") }
    }

    fun decode(key: String, text: String): Pair<List<LyricLine>, Boolean>? {
        val rows = text.lines()
        if (rows.firstOrNull() != key) return null
        val synced = rows.getOrNull(1) == "synced"
        return rows.drop(2).mapNotNull { r -> r.indexOf('\t').takeIf { it > 0 }?.let { LyricLine(r.substring(0, it).toLong(), r.substring(it + 1)) } } to synced
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
    fun strip(s: String) = s.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").replace(Regex("\\s*\\[[^]]*]\\s*$"), "")
        .replace(Regex("(?i)\\s*[-–—]\\s*(official.*|audio|video|lyrics?)\\s*$"), "").trim()

    private fun enc(s: String): String = buildString {
        s.encodeToByteArray().forEach { b ->
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "-_.~") append(ch)
            else if (ch == ' ') append('+')
            else { append('%'); append("0123456789ABCDEF"[c shr 4]); append("0123456789ABCDEF"[c and 15]) }
        }
    }
}
