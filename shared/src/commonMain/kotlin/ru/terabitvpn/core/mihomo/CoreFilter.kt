package ru.terabitvpn.core.mihomo

import ru.terabitvpn.core.model.CoreType
import ru.terabitvpn.core.model.Profile

/**
 * One core at a time: with Xray only what Xray runs is shown and used, with mihomo only what mihomo
 * runs. Clash/mihomo profiles are mihomo-only, full Xray-JSON configs are Xray-only, plain links
 * go with the chosen core. Returns null when nothing of the profile fits.
 */
fun Profile.visibleFor(core: CoreType): Profile? = when (core) {
    CoreType.XRAY -> if (mihomo != null) null else {
        // tuic / anytls / mieru links have no Xray outbound
        val runnable = servers.filter { it.config != null || it.outbound != null }
        if (servers.isNotEmpty() && runnable.isEmpty()) null else copy(servers = runnable)
    }
    CoreType.MIHOMO -> {
        // mihomo's link parser has no WireGuard links
        val runnable = servers.filter { it.config == null && !(it.link != null && it.protocol in ru.terabitvpn.core.link.LinkParser.XRAY_ONLY) }
        if (servers.isNotEmpty() && runnable.isEmpty()) null else copy(servers = runnable)
    }
}
