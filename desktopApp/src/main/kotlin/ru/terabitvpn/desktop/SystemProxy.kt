package ru.terabitvpn.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** Points the OS-wide proxy at our local HTTP/SOCKS inbounds ("system proxy" mode). */
object SystemProxy {

    private const val WIN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
    private const val BYPASS = "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.2*;172.30.*;172.31.*;192.168.*;*.local;<local>"

    /** [httpPort] is the loopback no-auth HTTP inbound (OS proxy settings can't carry a password). */
    fun enable(httpPort: Int) {
        when (hostOs) {
            HostOs.WINDOWS -> {
                Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, WIN_KEY, "ProxyEnable", 1)
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, WIN_KEY, "ProxyServer", "127.0.0.1:$httpPort")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, WIN_KEY, "ProxyOverride", BYPASS)
                refreshWindows()
            }
            HostOs.MACOS -> macServices().forEach { svc ->
                exec("networksetup", "-setwebproxy", svc, "127.0.0.1", "$httpPort")
                exec("networksetup", "-setsecurewebproxy", svc, "127.0.0.1", "$httpPort")
            }
            HostOs.LINUX -> {
                gsettings("org.gnome.system.proxy", "mode", "'manual'")
                for (schema in listOf("http", "https")) {
                    gsettings("org.gnome.system.proxy.$schema", "host", "'127.0.0.1'")
                    gsettings("org.gnome.system.proxy.$schema", "port", "$httpPort")
                }
            }
        }
    }

    fun disable() {
        runCatching {
            when (hostOs) {
                HostOs.WINDOWS -> {
                    Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, WIN_KEY, "ProxyEnable", 0)
                    refreshWindows()
                }
                HostOs.MACOS -> macServices().forEach { svc ->
                    exec("networksetup", "-setwebproxystate", svc, "off")
                    exec("networksetup", "-setsecurewebproxystate", svc, "off")
                    exec("networksetup", "-setsocksfirewallproxystate", svc, "off")
                }
                HostOs.LINUX -> gsettings("org.gnome.system.proxy", "mode", "'none'")
            }
        }
    }

    /** True when the OS proxy still points at our port — e.g. after a crash. */
    fun isOurs(httpPort: Int): Boolean = runCatching {
        when (hostOs) {
            HostOs.WINDOWS ->
                Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, WIN_KEY, "ProxyEnable") == 1 &&
                    Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, WIN_KEY, "ProxyServer") == "127.0.0.1:$httpPort"
            HostOs.MACOS -> macServices().any { exec("networksetup", "-getwebproxy", it).let { o -> "Enabled: Yes" in o && "Port: $httpPort" in o } }
            HostOs.LINUX -> exec("gsettings", "get", "org.gnome.system.proxy", "mode").contains("manual")
        }
    }.getOrDefault(false)

    private fun macServices(): List<String> =
        exec("networksetup", "-listallnetworkservices").lines().drop(1).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") }

    private fun gsettings(schema: String, key: String, value: String) {
        runCatching { exec("gsettings", "set", schema, key, value) }
    }

    private interface WinInet : StdCallLibrary {
        fun InternetSetOptionW(hInternet: Pointer?, option: Int, buffer: Pointer?, length: Int): Boolean
    }

    /** Tell WinINet consumers (browsers, most apps) that the settings changed, without a relogin. */
    private fun refreshWindows() {
        runCatching {
            val lib = Native.load("wininet", WinInet::class.java)
            lib.InternetSetOptionW(null, 39, null, 0) // INTERNET_OPTION_SETTINGS_CHANGED
            lib.InternetSetOptionW(null, 37, null, 0) // INTERNET_OPTION_REFRESH
        }
    }
}
