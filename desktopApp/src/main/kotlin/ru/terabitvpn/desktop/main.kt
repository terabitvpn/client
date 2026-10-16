package ru.terabitvpn.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.terabitvpn.core.TerabitController
import ru.terabitvpn.core.vpn.VpnState
import ru.terabitvpn.ui.TerabitApp
import kotlinx.coroutines.runBlocking
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    ru.terabitvpn.core.vpn.LoopbackAuthenticator.install()
    val platform = DesktopPlatform()
    var controller: TerabitController? = null
    val raise = androidx.compose.runtime.mutableIntStateOf(0)
    // Second launch (e.g. a terabit:// link) → hand the link to the running Terabit and quit.
    val first = SingleInstance.acquire(platform.dataDir, args) { forwarded ->
        forwarded.firstOrNull { !it.startsWith("--") }?.let { link -> controller?.import(link) }
        raise.intValue++
    }
    if (!first) return
    if (!platform.portable) SingleInstance.registerUrlScheme()
    val xray = DesktopXrayBackend(platform)
    val mihomo = DesktopMihomoBackend(platform, xray)
    val backend = ru.terabitvpn.core.mihomo.DualCoreBackend(xray, mihomo)
    // The kill switch lets the running core through: Xray or mihomo, whichever is up.
    if (hostOs == HostOs.WINDOWS) platform.killSwitch = WindowsKillSwitch(platform.dataDir, { if (backend.onMihomo) mihomo.exe else java.io.File(xray.coreDir, DesktopXrayBackend.exeName) }, xray::isElevated)
    // TUN needs admin: relaunch the installed app elevated (UAC prompt) right away instead of failing on connect.
    val exe = ProcessHandle.current().info().command().orElse("")
    if (hostOs == HostOs.WINDOWS && exe.endsWith("Terabit.exe", true) && "--elevated" !in args && !xray.isElevated()) {
        val saved = runCatching { java.io.File(platform.dataDir, "settings.json").readText() }.getOrDefault("")
        if ("\"desktopMode\":\"TUN\"" in saved.replace(" ", "")) {
            val argList = (args.toList() + "--elevated").joinToString(",") { "'" + it.replace("'", "''") + "'" }
            val ok = runCatching {
                ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                    "Start-Process -FilePath '${exe.replace("'", "''")}' -Verb RunAs -ArgumentList $argList").start().waitFor() == 0
            }.getOrDefault(false)
            if (ok) {
                SingleInstance.release()
                return
            }
        }
    }
    val c = TerabitController(platform, backend)
    controller = c
    // terabit://… or a subscription URL passed on the command line (e.g. from a URL handler).
    args.firstOrNull { !it.startsWith("--") }?.let { c.import(it) }
    val autostart = "--autostart" in args
    val resume = java.io.File(platform.dataDir, "run/resume")
    val resumeAfterUpdate = resume.isFile.also { if (it) resume.delete() }
    if ((autostart && c.settings.value.startOnBoot) || resumeAfterUpdate) {
        kotlinx.coroutines.runBlocking { c.connect() }
    }

    val icon = DesktopPlatform::class.java.classLoader.getResourceAsStream("terabit.png")
        ?.use { ImageIO.read(it) }?.toComposeImageBitmap()?.let { BitmapPainter(it) }

    application {
        val state by c.state.collectAsState()
        val windowState = rememberWindowState(
            size = DpSize(1180.dp, 780.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
            isMinimized = false,
        )

        var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!autostart) }
        val trayState = androidx.compose.ui.window.rememberTrayState()
        // New version → a Windows notification too, so the update can't go unnoticed behind the tray.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            var told: String? = null
            c.updater.offer.collect { o ->
                if (o != null && o.version != told) {
                    told = o.version
                    trayState.sendNotification(androidx.compose.ui.window.Notification("Вышла Terabit ${o.version}", "Открой Terabit и нажми «Обновить» — установится само, настройки сохранятся."))
                }
            }
        }
        var trayHintShown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        fun quit() {
            runBlocking { c.disconnect() }
            exitApplication()
        }

        fun show() {
            visible = true
            windowState.isMinimized = false
        }

        // ✕ keeps Terabit (and the VPN) running in the tray, like other VPN clients.
        fun closeWindow() {
            if (!c.settings.value.closeToTray) return quit()
            visible = false
            if (!trayHintShown) {
                trayHintShown = true
                trayState.sendNotification(
                    androidx.compose.ui.window.Notification(
                        "Terabit работает в трее",
                        if (c.state.value is VpnState.Connected) "VPN остаётся включённым. Открыть — клик по значку в трее." else "Открыть — клик по значку в трее.",
                    ),
                )
            }
        }
        platform.quitForUpdate = {
            // Remember that the VPN was on: the relaunched (updated) Terabit reconnects by itself.
            if (c.state.value is VpnState.Connected) runCatching {
                java.io.File(platform.dataDir, "run/resume").apply { parentFile.mkdirs() }.writeText("1")
            }
            javax.swing.SwingUtilities.invokeLater { quit() }
        }

        if (icon != null) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = if (state is VpnState.Connected) "Terabit — защищено" else "Terabit",
                onAction = ::show,
                menu = {
                    Item("Открыть Terabit", onClick = ::show)
                    Item(if (state is VpnState.Connected) "Отключить" else "Подключить", onClick = { c.toggle() })
                    Separator()
                    Item("Выход", onClick = ::quit)
                },
            )
        }

        Window(
            onCloseRequest = ::closeWindow,
            state = windowState,
            title = "Terabit",
            icon = icon,
            visible = visible,
        ) {
            window.minimumSize = java.awt.Dimension(380, 600)
            if (hostOs == HostOs.WINDOWS) WindowsChrome.darkTitleBar(window)
            androidx.compose.runtime.LaunchedEffect(raise.intValue) {
                if (raise.intValue > 0) {
                    visible = true
                    windowState.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            // Looping decorations stop while the window is in the tray or minimized (see Ambient.kt).
            androidx.compose.runtime.LaunchedEffect(visible, windowState.isMinimized) {
                ru.terabitvpn.ui.components.AmbientMotion.visible.value = visible && !windowState.isMinimized
            }
            TerabitApp(c)
        }
    }
}
