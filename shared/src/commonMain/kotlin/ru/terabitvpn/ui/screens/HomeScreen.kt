package ru.terabitvpn.ui.screens

import ru.terabitvpn.ui.components.hoverSound
import ru.terabitvpn.ui.components.outerShadow
import androidx.compose.ui.draw.clipToBounds

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.terabitvpn.core.TerabitController
import ru.terabitvpn.core.model.Profile
import ru.terabitvpn.core.model.Server
import ru.terabitvpn.core.vpn.Traffic
import ru.terabitvpn.core.vpn.VpnState
import ru.terabitvpn.ui.Format
import ru.terabitvpn.ui.components.ConnectOrb
import ru.terabitvpn.ui.components.GlassCard
import ru.terabitvpn.ui.components.GlowBar
import ru.terabitvpn.ui.components.IconBubble
import ru.terabitvpn.ui.components.OrbState
import ru.terabitvpn.ui.components.PingPill
import ru.terabitvpn.ui.components.RollingText
import ru.terabitvpn.ui.components.Sparkline
import ru.terabitvpn.ui.components.Tag
import ru.terabitvpn.ui.components.AnnouncementCard
import ru.terabitvpn.ui.components.KineticText
import ru.terabitvpn.ui.components.LiveDot
import ru.terabitvpn.ui.components.appear
import ru.terabitvpn.ui.components.orbHalo
import ru.terabitvpn.ui.components.orbitBorder
import ru.terabitvpn.ui.protocolLabel
import ru.terabitvpn.ui.theme.Brand
import ru.terabitvpn.ui.theme.Motion
import ru.terabitvpn.ui.title
import ru.terabitvpn.ui.transportLabel
import kotlinx.coroutines.delay

/** Everything the home screen shows, gathered once and shared by the phone and desktop layouts. */
@Stable
class HomeModel(
    val state: VpnState,
    val orb: OrbState,
    val traffic: Traffic,
    val server: Server?,
    val profile: Profile?,
    val ping: Long?,
    val pinging: Boolean,
    val now: Long,
    val down: SnapshotStateList<Float>,
    val up: SnapshotStateList<Float>,
    /** mihomo: how many selector groups drive the traffic (0 when Xray or none). */
    val mihomoGroups: Int,
)

@Composable
fun rememberHome(controller: TerabitController): HomeModel {
    val state by controller.state.collectAsState()
    val traffic by controller.backend.traffic.collectAsState()
    val selectedId by controller.selectedServerId.collectAsState()
    val profiles by controller.profiles.collectAsState()
    val pings by controller.pings.collectAsState()
    val pinging by controller.pinging.collectAsState()
    val liveGroups by controller.mihomoGroups.groups.collectAsState()
    val staticGroups by controller.staticMihomoGroups.collectAsState()

    val server = remember(selectedId, profiles) { controller.selectedServer() }
    val profile = remember(server, profiles) { server?.let { controller.profileOf(it.id) } ?: profiles.firstOrNull() }
    val orb = when (state) {
        is VpnState.Connected -> OrbState.CONNECTED
        VpnState.Connecting, VpnState.Disconnecting -> OrbState.CONNECTING
        is VpnState.Failed -> OrbState.ERROR
        VpnState.Idle -> OrbState.IDLE
    }
    val down = remember { mutableStateListOf<Float>() }
    val up = remember { mutableStateListOf<Float>() }
    LaunchedEffect(traffic) {
        down.add(traffic.downSpeed.toFloat()); if (down.size > 48) down.removeAt(0)
        up.add(traffic.upSpeed.toFloat()); if (up.size > 48) up.removeAt(0)
    }
    LaunchedEffect(orb) { if (orb != OrbState.CONNECTED) { down.clear(); up.clear() } }
    var now by remember { mutableLongStateOf(TerabitController.now()) }
    LaunchedEffect(Unit) { while (true) { now = TerabitController.now(); delay(1000) } }
    return HomeModel(
        state, orb, traffic, server, profile, pings[server?.id]?.ms, server?.id in pinging, now, down, up,
        mihomoGroups = liveGroups.ifEmpty { staticGroups }.size,
    )
}

// ============================================================================ phone

@Composable
fun HomeScreen(controller: TerabitController, onPickServer: () -> Unit, contentPadding: PaddingValues) {
    val m = rememberHome(controller)
    val c = Brand.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Terabit", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
            ru.terabitvpn.ui.components.NoticeBell(controller)
            Spacer(Modifier.width(8.dp))
            m.profile?.supportUrl?.let { url -> IconBubble(Icons.Rounded.SupportAgent, onClick = { controller.platform.openUrl(url) }) }
        }
        ru.terabitvpn.ui.components.UpdateBanner(controller, Modifier.padding(top = 12.dp))
        AnnouncementCard(controller, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(18.dp))
        HomeHero(m, controller, 236.dp)
        Spacer(Modifier.height(22.dp))
        AnimatedVisibility(m.orb == OrbState.CONNECTED, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedCard("Загрузка", Icons.Rounded.ArrowDownward, m.traffic.downSpeed, m.traffic.downTotal, m.down, c.ok, Modifier.weight(1f).appear(0))
                SpeedCard("Отдача", Icons.Rounded.ArrowUpward, m.traffic.upSpeed, m.traffic.upTotal, m.up, c.accent, Modifier.weight(1f).appear(1))
            }
        }
        ServerCard(m, onPickServer, Modifier.appear(2))
        if (m.profile != null && (m.profile.url != null || m.profile.info != null)) {
            Spacer(Modifier.height(12.dp))
            SubscriptionCard(m.profile, m.now, controller, Modifier.appear(3))
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ============================================================================ desktop

/**
 * Wide layout: orb centre stage with live stats under it, the quick server panel on the right.
 */
@Composable
fun HomeDesktop(controller: TerabitController, onAdd: () -> Unit) {
    val m = rememberHome(controller)
    val c = Brand.colors
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
    // The servers panel shares the width with the main column: it narrows first and hides when the
    // main column would drop under 480 dp (servers stay one click away on the «Серверы» tab).
    val panel = (maxWidth * 0.4f).coerceIn(320.dp, 400.dp)
    val showPanel = maxWidth - panel - 22.dp >= 480.dp
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top line: which subscription, and when it runs out.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    KineticText(greeting(m.now, controller.platform.utcOffsetMinutes()), style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.clipToBounds())
                }
                ru.terabitvpn.ui.components.NoticeBell(controller)
                Spacer(Modifier.width(8.dp))
                m.profile?.supportUrl?.let { url -> IconBubble(Icons.Rounded.SupportAgent, onClick = { controller.platform.openUrl(url) }) }
            }
            AnnouncementCard(controller, Modifier.widthIn(max = 760.dp).padding(top = 12.dp))
            Spacer(Modifier.height(18.dp))
            HomeHero(m, controller, 290.dp)
            Spacer(Modifier.height(26.dp))
            // Off: one clear "ready" card instead of empty dashes. On: live traffic.
            AnimatedContent(
                targetState = m.orb == OrbState.CONNECTED,
                transitionSpec = {
                    // slide only (a fade cuts the cards' shadows); the old cards leave at once
                    (slideInVertically(Motion.quick(420)) { it / 5 } togetherWith androidx.compose.animation.ExitTransition.None)
                        // no clipping while the height changes: the cards' shadows spill outside
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            ) { live ->
                if (!live) ReadyCard(m, controller)
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SpeedCard("Загрузка", Icons.Rounded.ArrowDownward, m.traffic.downSpeed, m.traffic.downTotal, m.down, c.ok, Modifier.weight(1f).appear(0), live = true)
                    SpeedCard("Отдача", Icons.Rounded.ArrowUpward, m.traffic.upSpeed, m.traffic.upTotal, m.up, c.accent, Modifier.weight(1f).appear(1), live = true)
                    Column(Modifier.weight(0.8f).appear(2), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatTile(Icons.Rounded.NetworkPing, "Пинг", m.ping?.takeIf { it > 0 }?.let { "$it мс" } ?: "—", pingColor(m.ping))
                        StatTile(Icons.Rounded.Timer, "Сессия", (m.state as? VpnState.Connected)?.let { Format.duration(m.now - it.since) } ?: "—", c.ink)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                if (m.profile != null && (m.profile.url != null || m.profile.info != null)) SubscriptionCard(m.profile, m.now, controller, Modifier.appear(3))
            }
            Spacer(Modifier.height(10.dp))
        }
        // Right: servers always at hand (when there is room for them).
        if (showPanel) GlassCard(Modifier.width(panel).fillMaxHeight().appear(1), padding = 0.dp, strong = true) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Серверы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            ServersScreen(controller, PaddingValues(bottom = 12.dp), onAdd = onAdd, showHeader = false, compact = true)
        }
    }
    }
}

/** Time-of-day greeting for the desktop header (local clock). */
private fun greeting(nowMs: Long, offsetMin: Int): String {
    val h = (((nowMs / 60_000 + offsetMin) / 60) % 24).toInt()
    return when (h) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

/**
 * Shown while disconnected, in two parts: «Готов к подключению» — the regular servers (the server that is
 * picked, or else «Лучший сейчас») — and «4G» — the route for networks where normal traffic is cut off.
 * Each part has its own connect button.
 */
@Composable
private fun ReadyCard(m: HomeModel, controller: TerabitController) {
    val c = Brand.colors
    val pings by controller.pings.collectAsState()
    val pinging by controller.pinging.collectAsState()
    val regular = m.server?.takeIf { !it.isLimited }
        ?: controller.bestServer()
        ?: controller.allServers().firstOrNull { !it.isAuto && !it.isLimited }
    val limited = controller.bestLimitedServer()
    GlassCard(Modifier.fillMaxWidth(), padding = 18.dp, strong = true) {
        Column {
            ReadyRow(
                label = if (m.orb == OrbState.ERROR && regular?.id == m.server?.id) "Попробуем ещё раз?" else "Готов к подключению",
                server = regular, ping = regular?.let { pings[it.id]?.ms }, pinging = regular?.id in pinging,
                m = m, controller = controller,
            )
            if (limited != null) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Spacer(Modifier.height(14.dp))
                ReadyRow(
                    label = if (m.orb == OrbState.ERROR && limited.id == m.server?.id) "4G · попробуем ещё раз?" else "4G · обход белых списков",
                    server = limited, ping = pings[limited.id]?.ms, pinging = limited.id in pinging,
                    m = m, controller = controller,
                )
            }
        }
    }
}

/** One part of [ReadyCard]: the server, its protocol and ping, and the button that picks it and connects. */
@Composable
private fun ReadyRow(label: String, server: Server?, ping: Long?, pinging: Boolean, m: HomeModel, controller: TerabitController) {
    val c = Brand.colors
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 440.dp
        val connecting = m.orb == OrbState.CONNECTING
        val connect: @Composable (Modifier) -> Unit = { mod ->
            ru.terabitvpn.ui.components.AccentButton(
                if (connecting && server?.id == m.server?.id) "Подключаю…" else "Подключить",
                {
                    controller.haptic(ru.terabitvpn.core.vpn.Haptic.HEAVY)
                    server?.let { controller.select(it.id) }
                    controller.toggle()
                },
                modifier = mod,
                icon = Icons.Rounded.Bolt,
                enabled = server != null && !connecting,
            )
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServerAvatar(server, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (server == null) Text("Сервер не выбран", style = MaterialTheme.typography.titleMedium)
                    else {
                        val t = server.title()
                        ru.terabitvpn.ui.components.FlagText(t.title + (t.subtitle?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        // The ping sits on the protocol line: the name gets the row's full width instead of
                        // being squeezed between a pill and the button.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listOfNotNull(server.protocolLabel(), server.transportLabel()).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (server.canPing) {
                                Spacer(Modifier.width(8.dp))
                                PingPill(ping, pinging)
                            }
                        }
                    }
                }
                if (!narrow) {
                    Spacer(Modifier.width(12.dp))
                    connect(Modifier)
                }
            }
            if (narrow) {
                Spacer(Modifier.height(14.dp))
                connect(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun pingColor(ms: Long?): Color {
    val c = Brand.colors
    return when {
        ms == null || ms <= 0 -> c.ink3
        ms < 180 -> c.ok
        ms < 450 -> c.warn
        else -> c.bad
    }
}

// ============================================================================ pieces

@Composable
fun HomeHero(m: HomeModel, controller: TerabitController, orbSize: Dp) {
    val c = Brand.colors
    val stage = ru.terabitvpn.ui.stage.LocalStage.current
    val singing = stage?.track?.value?.playing == true
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ConnectOrb(m.orb, onClick = {
            controller.haptic()
            controller.toggle()
        }, size = orbSize, modifier = Modifier.orbHalo(m.orb),
            beat = { stage?.a?.let { it.beat * (1f - 0.6f * it.mood) } ?: 0f },
            flare = { 0f },
        )
        Spacer(Modifier.height(14.dp))
        KineticText(
            when (m.orb) {
                OrbState.IDLE -> "Не подключено"
                OrbState.CONNECTING -> if (m.state == VpnState.Disconnecting) "Отключаюсь…" else "Подключаюсь…"
                OrbState.CONNECTED -> "Ты под защитой"
                OrbState.ERROR -> "Не удалось подключиться"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = if (m.orb == OrbState.ERROR) c.bad else c.ink,
        )
        Spacer(Modifier.height(4.dp))
        // Music playing on the PC: the line types itself under the orb.
        if (singing && stage != null && m.state !is VpnState.Failed) ru.terabitvpn.ui.stage.SungLine(stage, Modifier.padding(top = 4.dp))
        else when (val st = m.state) {
            is VpnState.Connected -> RollingText(
                Format.duration(m.now - st.since),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = c.ok,
            )
            is VpnState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    st.message, style = MaterialTheme.typography.bodySmall, color = c.ink3, textAlign = TextAlign.Center,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(8.dp))
                ru.terabitvpn.ui.components.SoftButton("Скопировать ошибку", {
                    controller.platform.copyToClipboard(st.message)
                    controller.haptic()
                }, icon = Icons.Rounded.ContentCopy)
            }
            else -> Text(
                when (st) {
                    VpnState.Connecting -> "Устанавливаю туннель"
                    else -> if (controller.platform.isDesktop) "Нажми на кнопку, чтобы включить" else "Коснись кнопки, чтобы включить"
                },
                style = MaterialTheme.typography.bodyMedium, color = c.ink3, textAlign = TextAlign.Center, maxLines = 3,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}

@Composable
fun SpeedCard(
    label: String, icon: ImageVector, speed: Long, total: Long,
    history: List<Float>, color: Color, modifier: Modifier, dim: Boolean = false, live: Boolean = false,
) {
    val c = Brand.colors
    GlassCard(modifier.orbitBorder(live && speed > 0, color, 26.dp), padding = 16.dp, glow = if (live) color else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink3, modifier = Modifier.weight(1f))
            if (live) LiveDot(color)
        }
        Spacer(Modifier.height(10.dp))
        RollingText(
            if (dim) "—" else Format.speed(speed),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = if (dim) c.ink3 else c.ink,
        )
        Text(if (dim) "нет соединения" else "всего ${Format.bytes(total)}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Sparkline(history, color, Modifier.fillMaxWidth().height(36.dp))
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, color: Color) {
    val c = Brand.colors
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.ink3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink3)
        }
        Spacer(Modifier.height(6.dp))
        RollingText(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = color)
    }
}

@Composable
fun ServerCard(m: HomeModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val server = m.server
    // mihomo: the selectors decide where the traffic goes, so the card leads straight to them
    // instead of naming one pinned server.
    if (m.mihomoGroups > 0) {
        GlassCard(modifier.fillMaxWidth(), onClick = onClick, strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(46.dp * 0.34f))
                        .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.26f), c.accent2.copy(alpha = 0.12f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AccountTree, null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Селекторы", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                    Text("Выбор через селекторы", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("Группы решают, куда идёт трафик", style = MaterialTheme.typography.bodySmall, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${m.mihomoGroups}", style = MaterialTheme.typography.labelMedium, color = c.ink3, modifier = Modifier.padding(horizontal = 4.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.ink3)
            }
        }
        return
    }
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerAvatar(server, 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("Сервер", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                if (server == null) {
                    Text("Не выбран", style = MaterialTheme.typography.titleMedium)
                } else {
                    val t = server.title()
                    ru.terabitvpn.ui.components.FlagText(t.title + (t.subtitle?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Tag(server.protocolLabel(), color = c.accent)
                        server.transportLabel()?.let { Tag(it) }
                    }
                }
            }
            if (server != null && server.canPing) PingPill(m.ping, m.pinging)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.ink3)
        }
    }
}

@Composable
fun ServerAvatar(server: Server?, size: Dp) {
    val c = Brand.colors
    val flag = server?.title()?.flag
    Box(
        Modifier.size(size).outerShadow(RoundedCornerShape(size * 0.34f), 0.6f).clip(RoundedCornerShape(size * 0.34f))
            .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.26f), c.accent2.copy(alpha = 0.12f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            flag != null -> ru.terabitvpn.ui.components.FlagIcon(flag, size * 0.42f)
            server?.isAuto == true -> Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(size * 0.48f))
            server?.protocol == "hysteria" -> Icon(Icons.Rounded.Bolt, null, tint = c.warn, modifier = Modifier.size(size * 0.5f))
            else -> Icon(Icons.Rounded.Shield, null, tint = c.accent, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/**
 * The active subscription at a glance: name (and a switcher when there are several), time and
 * traffic left, the provider's note (`announce`), and its actions — refresh, the provider's page,
 * support — right on the card instead of buried in the server list.
 */
@Composable
fun SubscriptionCard(profile: Profile, now: Long, controller: TerabitController, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val info = profile.info
    val refreshing by controller.refreshing.collectAsState()
    val profiles by controller.profiles.collectAsState()
    val subs = profiles.subscriptions()
    val nav = LocalSubscriptionNav.current
    // The card itself opens the subscription page; its buttons act right here.
    GlassCard(modifier.fillMaxWidth(), padding = 16.dp, onClick = { nav.open(profile.id) }) {
        // Name + when it was fetched; the switcher when there is more than one subscription.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ru.terabitvpn.ui.components.FlagText(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.url != null) Text(Format.updatedAgo(profile.updatedAt, now), style = MaterialTheme.typography.bodySmall, color = c.ink3, maxLines = 1)
            }
        }

        if (info != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (info.unlimitedTime) "Бессрочно" else Format.expiryPhrase(info.expire, now),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        info.unlimitedTime -> c.ink
                        info.expire * 1000 <= now -> c.bad
                        info.expire * 1000 - now < 3 * 86_400_000L -> c.warn
                        else -> c.ink
                    },
                    modifier = Modifier.weight(1f),
                )
                if (info.pools.isEmpty()) Text(
                    if (info.unlimitedTraffic) "${Format.bytes(info.used)} · ∞" else "${Format.bytes(info.used)} из ${Format.bytes(info.total)}",
                    style = MaterialTheme.typography.labelMedium, color = c.ink2,
                )
            }
            if (info.pools.isNotEmpty()) {
                info.pools.forEach { p ->
                    Spacer(Modifier.height(12.dp))
                    PoolBar(p)
                }
                if (info.cycleEnd > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Трафик обновится через ${Format.remaining(info.cycleEnd, now)}", style = MaterialTheme.typography.bodySmall)
                }
            } else if (!info.unlimitedTraffic) {
                Spacer(Modifier.height(10.dp))
                val frac = info.used.toFloat() / info.total.toFloat()
                GlowBar(frac, if (frac > 0.9f) c.bad else if (frac > 0.7f) c.warn else c.accent)
            }
        }

        // The provider's note lives on the subscription page (a tap on this card); its info block
        // (trial countdown, expired, renew soon) is shown right here, where it gets acted on.
        profile.notice?.let { n ->
            Spacer(Modifier.height(12.dp))
            ProviderNoticeBlock(n, controller)
        }

        val actions = buildList<Triple<ImageVector, String, () -> Unit>> {
            if (profile.url != null) add(Triple(Icons.Rounded.Refresh, "Обновить") { controller.haptic(); controller.refresh(profile.id, manual = true) })
            // Support is the button at the top of the screen; here: the provider's page and renewing.
            profile.webPageUrl?.let { url -> add(Triple(Icons.Rounded.Public, "Подписка") { controller.platform.openUrl(url) }) }
            profile.renewLink()?.let { url -> add(Triple(Icons.Rounded.Payments, "Продлить") { controller.platform.openUrl(url) }) }
        }
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (icon, label, onClick) ->
                    val busy = label == "Обновить" && profile.id in refreshing
                    Row(
                        Modifier.weight(1f).outerShadow(RoundedCornerShape(12.dp), 0.5f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))
                            .hoverSound().clickable(enabled = !busy, onClick = onClick).padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) ru.terabitvpn.ui.components.Spinner(c.accent, Modifier.size(14.dp))
                        else Icon(icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One traffic pool: title, used / total and a glowing bar (mint for regular, violet for white lists). */
@Composable
fun PoolBar(p: ru.terabitvpn.core.model.TrafficPool) {
    val c = Brand.colors
    val frac = if (p.total > 0) p.used.toFloat() / p.total else 0f
    val color = when {
        frac > 0.95f -> c.bad
        frac > 0.8f -> c.warn
        p.isWhitelist -> c.accent
        else -> c.ok
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(p.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(
            if (p.total > 0) "${Format.bytes(p.used)} из ${Format.bytes(p.total)}" else "${Format.bytes(p.used)} · ∞",
            style = MaterialTheme.typography.labelMedium, color = c.ink2,
        )
    }
    Spacer(Modifier.height(6.dp))
    GlowBar(frac, color)
}
