package ru.terabitvpn.core

import ru.terabitvpn.core.model.AppSettings
import ru.terabitvpn.core.model.RoutingMode
import ru.terabitvpn.core.sub.SubscriptionParser
import ru.terabitvpn.core.xray.Ingress
import ru.terabitvpn.core.xray.LocalProxy
import ru.terabitvpn.core.xray.XrayConfigBuilder
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test

/**
 * Writes the configs the app would build for a real subscription (TERABIT_SUB_JSON = path to a
 * `?fmt=xray` dump) under several settings into build/configs, so `xray run -test` can check them.
 * No-op when the env var is missing (CI).
 */
class DumpConfigs {
    @Test
    fun dump() {
        val src = System.getenv("TERABIT_SUB_JSON") ?: return
        val parsed = SubscriptionParser.parse(File(src).readText(), emptyMap(), "p")
        val out = File("build/configs").apply { deleteRecursively(); mkdirs() }
        val variants = mapOf(
            "default" to AppSettings(),
            "ads" to AppSettings(blockAds = true),
            "global" to AppSettings(routingMode = RoutingMode.GLOBAL),
            "rules" to AppSettings(directDomains = listOf("example.ru"), proxyDomains = listOf("youtube.com"), blockDomains = listOf("ads.example")),
            "fragment_mux" to AppSettings(fragment = true, mux = true),
            "dns_adv" to AppSettings(
                dns = ru.terabitvpn.core.model.DnsPreset.CLOUDFLARE, dnsDirect = ru.terabitvpn.core.model.DirectDns.SYSTEM,
                dnsStrategy = ru.terabitvpn.core.model.DnsStrategy.IPV4_FIRST, dnsCache = false, dnsHosts = listOf("example.com 93.184.216.34"),
            ),
            "dns_custom" to AppSettings(
                dns = ru.terabitvpn.core.model.DnsPreset.CUSTOM, customDns = "tls://1.1.1.1",
                dnsDirect = ru.terabitvpn.core.model.DirectDns.CUSTOM, dnsDirectCustom = "https+local://77.88.8.1/dns-query",
            ),
        )
        parsed.servers.forEachIndexed { i, server ->
            variants.forEach { (name, settings) ->
                val cfg = XrayConfigBuilder.build(server, settings, Ingress.Proxy(LocalProxy(20808 + i, 21808 + i, user = "u", pass = "p"), 0))
                File(out, "s${i}_$name.json").writeText(JsonX.encodeToString(JsonObject.serializer(), cfg))
            }
        }
        println("dumped ${parsed.servers.size * variants.size} configs")
    }
}
