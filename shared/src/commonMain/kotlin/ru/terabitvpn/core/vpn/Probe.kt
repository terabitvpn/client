package ru.terabitvpn.core.vpn

import ru.terabitvpn.core.model.PingMethod
import kotlin.concurrent.Volatile

/**
 * How HTTP latency probes through a core are made, shared by every backend (server pings, the
 * connection guard). Set by the controller from [ru.terabitvpn.core.model.AppSettings.pingMethod].
 */
object Probe {
    @Volatile var method: PingMethod = PingMethod.PROXY_GET

    /** HTTP method for probes through the proxy: HEAD only when the user picked it. */
    val httpMethod: String get() = if (method == PingMethod.PROXY_HEAD) "HEAD" else "GET"

    private val TIME = Regex("""(?:time|время|temps|zeit)\s*[=<]\s*([0-9]+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)

    /** Round-trip in ms from one line of `ping` output (any locale we know), or -1. */
    fun parsePingOutput(out: String): Long {
        val m = TIME.find(out) ?: return -1
        val ms = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return -1
        return ms.toLong().coerceAtLeast(1)
    }
}
