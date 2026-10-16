package ru.terabitvpn.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * One running Terabit per user. A second launch (e.g. a `terabit://import/...` link clicked in the
 * browser) hands its arguments to the first instance over a loopback socket and exits.
 */
object SingleInstance {

    private var server: ServerSocket? = null

    /** @return true if we are the first instance; otherwise [args] were forwarded and we should exit. */
    fun acquire(dataDir: String, args: Array<String>, onArgs: (List<String>) -> Unit): Boolean {
        val portFile = File(dataDir, "run/instance.port")
        val port = portFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        if (port != null) {
            val sent = runCatching {
                Socket(InetAddress.getLoopbackAddress(), port).use { s ->
                    s.soTimeout = 1500
                    s.getOutputStream().bufferedWriter().apply {
                        write("terabit\n")
                        args.forEach { write(it.replace('\n', ' ') + "\n") }
                        flush()
                    }
                }
            }.isSuccess
            if (sent) return false
        }
        val ss = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return true
        server = ss
        portFile.parentFile.mkdirs()
        portFile.writeText(ss.localPort.toString())
        Thread {
            while (!ss.isClosed) {
                val c = runCatching { ss.accept() }.getOrNull() ?: break
                runCatching {
                    c.use { sock ->
                        val lines = sock.getInputStream().bufferedReader().readLines()
                        if (lines.firstOrNull() == "terabit") onArgs(lines.drop(1))
                    }
                }
            }
        }.apply { isDaemon = true; name = "terabit-instance" }.start()
        return true
    }

    /** Frees the instance slot (we are handing over to an elevated copy). */
    fun release() {
        runCatching { server?.close() }
        server = null
    }

    /** Registers `terabit://` for the installed app (per user, no admin): links in the browser open Terabit. */
    fun registerUrlScheme() {
        if (hostOs != HostOs.WINDOWS) return
        val exe = ProcessHandle.current().info().command().orElse(null) ?: return
        if (exe.endsWith("java.exe") || exe.endsWith("javaw.exe")) return // dev run: nothing to register
        runCatching {
            val root = WinReg.HKEY_CURRENT_USER
            val base = "Software\\Classes\\terabit"
            Advapi32Util.registryCreateKey(root, "$base\\shell\\open\\command")
            Advapi32Util.registrySetStringValue(root, base, "", "URL:Terabit VPN")
            Advapi32Util.registrySetStringValue(root, base, "URL Protocol", "")
            Advapi32Util.registryCreateKey(root, "$base\\DefaultIcon")
            Advapi32Util.registrySetStringValue(root, "$base\\DefaultIcon", "", "\"$exe\",0")
            Advapi32Util.registrySetStringValue(root, "$base\\shell\\open\\command", "", "\"$exe\" \"%1\"")
        }
    }
}
