package ru.terabitvpn.core

import ru.terabitvpn.core.vpn.Probe
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeTest {
    @Test
    fun parsesPingOutput() {
        assertEquals(45, Probe.parsePingOutput("Ответ от 1.2.3.4: число байт=32 время=45мс TTL=55"))
        assertEquals(1, Probe.parsePingOutput("Reply from 1.2.3.4: bytes=32 time<1ms TTL=128"))
        assertEquals(23, Probe.parsePingOutput("64 bytes from 1.2.3.4: icmp_seq=1 ttl=55 time=23.4 ms"))
        assertEquals(-1, Probe.parsePingOutput("Request timed out."))
    }
}
