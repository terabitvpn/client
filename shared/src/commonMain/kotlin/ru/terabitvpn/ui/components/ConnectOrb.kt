package ru.terabitvpn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.terabitvpn.ui.theme.Brand
import ru.terabitvpn.ui.theme.LocalReduceMotion
import ru.terabitvpn.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class OrbState { IDLE, CONNECTING, CONNECTED, ERROR }

/**
 * The big connect button: a liquid-glass orb with a power glyph inside.
 * Idle — soft "tap me" pulses; connecting — a comet ring spins and dust speeds up;
 * connected — the ring closes into an aurora and energy waves roll out.
 * On desktop the orb leans towards the cursor.
 */
@Composable
fun ConnectOrb(
    state: OrbState, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 236.dp,
    /** Stage mode, read only while drawing (60 fps audio must not recompose): beat pulse, drop light. */
    beat: () -> Float = { 0f }, flare: () -> Float = { 0f },
) {
    val c = Brand.colors
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }

    val spin by ambientFloat(0f, 360f, if (state == OrbState.CONNECTING) 1000 else 16_000, LinearEasing)
    val orbitState = ambientFloat(0f, (2 * PI).toFloat(), if (state == OrbState.CONNECTING) 2600 else 11_000, LinearEasing)
    val orbit by orbitState
    val swirl by ambientFloat(0f, 360f, 9000, LinearEasing)
    val wave by ambientFloat(0f, 1f, if (reduce) 4200 else 2600, LinearEasing)
    val breath by ambientFloat(0f, 1f, if (reduce) 5200 else 3000, Motion.EaseInOut, reverse = true)

    val on by animateFloatAsState(if (state == OrbState.CONNECTED) 1f else 0f, tween(900, easing = Motion.Ease))
    val busy by animateFloatAsState(if (state == OrbState.CONNECTING) 1f else 0f, Motion.quick(400))
    val err by animateFloatAsState(if (state == OrbState.ERROR) 1f else 0f, Motion.quick(400))

    // Cursor tracking: eyes look at it, the orb leans in a touch.
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var hover by remember { mutableStateOf(false) }
    val hoverA by animateFloatAsState(if (hover) 1f else 0f, Motion.quick(300))
    // The spring follows only the cursor and settles when there is none; the idle sway is added on top from
    // the ambient clock. A spring chasing the always-moving sway never settled, and a running spring asks for
    // a frame on every screen refresh — the window kept redrawing at the monitor's full rate at rest.
    val lookSpring = animateOffsetAsState(
        pointer?.let { p -> Offset(p.x.coerceIn(-1f, 1f), p.y.coerceIn(-1f, 1f)) } ?: Offset.Zero,
        spring(dampingRatio = 0.6f, stiffness = 120f),
    )
    val idleLook = animateFloatAsState(if (pointer == null) 1f else 0f, spring(dampingRatio = 1f, stiffness = 120f))
    val look by remember(orbitState) {
        androidx.compose.runtime.derivedStateOf {
            val o = orbitState.value
            lookSpring.value + Offset(0.18f * sin(o * 0.5f), 0.12f * cos(o * 0.7f)) * idleLook.value
        }
    }


    val tap: () -> Unit = { onClick() }

    // A ring of light bursts outward every time we become connected.
    val burst = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == OrbState.CONNECTED) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1400, easing = Motion.Ease))
        }
    }

    val dust = remember {
        val r = Random(3)
        List(18) { floatArrayOf(r.nextFloat() * 6.28f, 0.84f + r.nextFloat() * 0.16f, 0.5f + r.nextFloat(), 0.7f + r.nextFloat() * 1.6f, r.nextFloat()) }
    }

    val accent = lerp(lerp(c.accent, c.ok, on * 0.5f), c.bad, err)
    val deep = lerp(c.accent2, c.bad, err * 0.6f)

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val p = e.changes.first().position
                        val half = this.size.width / 2f
                        when (e.type) {
                            PointerEventType.Exit -> { hover = false; pointer = null }
                            else -> {
                                // Outside the orb the eyes still follow, but more lazily.
                                pointer = Offset((p.x - half) / half, (p.y - half) / half) * 0.8f
                                val d = sqrt((p.x - half) * (p.x - half) + (p.y - half) * (p.y - half))
                                hover = d < half * 0.8f
                            }
                        }
                    }
                }
            }
            .pressScale(interaction, 0.93f, hover = 1.03f)
            .graphicsLayer { val k = 1f + 0.045f * beat() + 0.06f * flare(); scaleX = k; scaleY = k }
            // Glass lens tilts towards the cursor in 3D (and sways a little on its own when idle).
            .then(if (reduce) Modifier else Modifier.graphicsLayer {
                // Full tilt only under the cursor; the idle sway stays a hint.
                val k = if (pointer != null) 11f else 3f
                rotationY = look.x * k
                rotationX = -look.y * k
                cameraDistance = 14f * density
            })
            .clip(CircleShape)
            .hoverSound().clickable(interactionSource = interaction, indication = null, onClick = tap),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val center = this.center

            // 1. Bloom
            val bloom = 0.20f + 0.10f * breath + 0.30f * on + 0.10f * hoverA + 0.25f * beat() + 0.45f * flare()
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = bloom), accent.copy(alpha = bloom * 0.3f), Color.Transparent), center, r), r)

            // 2. Waves: gentle invitation pulses when idle, steady energy rings when connected.
            val waveCount = 3
            for (i in 0 until waveCount) {
                val p = (wave + i.toFloat() / waveCount) % 1f
                val alpha = (1f - p) * (0.10f * (1f - on) * (1f - busy) + 0.32f * on)
                if (alpha > 0.005f) drawCircle(accent.copy(alpha = alpha), r * (0.70f + 0.30f * p), style = Stroke(1.5.dp.toPx()))
            }
            if (burst.value < 1f) {
                drawCircle(Color.White.copy(alpha = (1f - burst.value) * 0.6f), r * (0.66f + 0.34f * burst.value), style = Stroke(4.dp.toPx() * (1f - burst.value) + 1f))
            }

            // 3. Orbiting dust
            dust.forEach { d ->
                val a = d[0] + orbit * d[2] * (if (d[4] > 0.5f) 1f else -1f)
                val rr = r * d[1] * (0.92f + 0.03f * sin(orbit * 3 + d[0]))
                val pos = center + Offset(cos(a) * rr, sin(a) * rr * 0.96f)
                val alpha = (0.25f + 0.55f * maxOf(on, busy)) * (0.4f + 0.6f * sin(orbit * 4 + d[0]).let { it * it })
                drawCircle(lerp(accent, Color.White, d[4] * 0.6f).copy(alpha = alpha), d[3].dp.toPx(), pos)
            }

            // 4. Liquid glass disc with a gently wobbling rim.
            val discR = r * 0.70f
            val wobble = Path().apply {
                val n = 90
                for (i in 0..n) {
                    val a = (i.toFloat() / n) * 2 * PI.toFloat()
                    val k = 1f + (0.012f + 0.01f * hoverA + 0.006f * busy) * sin(a * 3 + orbit * 2) + 0.008f * sin(a * 5 - orbit * 3)
                    val x = center.x + cos(a) * discR * k
                    val y = center.y + sin(a) * discR * k
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                wobble,
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f + 0.05f * on), deep.copy(alpha = 0.26f + 0.2f * on), Color(0xFF04100A).copy(alpha = 0.94f)),
                    center = center - Offset(discR * 0.25f, discR * 0.35f), radius = discR * 1.6f,
                ),
            )
            // Aurora swirl inside the glass
            clipPath(wobble) {
                rotate(swirl, center) {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f * (0.35f + 0.65f * on)), Color.Transparent), center + Offset(discR * 0.45f, 0f), discR * 0.8f), discR * 0.8f, center + Offset(discR * 0.45f, 0f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF9BE8B8).copy(alpha = 0.16f * (0.3f + 0.7f * on)), Color.Transparent), center - Offset(discR * 0.5f, discR * 0.2f), discR * 0.7f), discR * 0.7f, center - Offset(discR * 0.5f, discR * 0.2f))
                }
                // Specular highlight
                drawArc(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), startY = center.y - discR, endY = center.y - discR * 0.4f),
                    startAngle = 200f, sweepAngle = 80f, useCenter = false,
                    topLeft = center - Offset(discR * 0.86f, discR * 0.86f), size = Size(discR * 1.72f, discR * 1.72f),
                    style = Stroke(discR * 0.06f, cap = StrokeCap.Round),
                )
            }
            drawPath(wobble, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.03f)), startY = center.y - discR, endY = center.y + discR), style = Stroke(1.2.dp.toPx()))

            // 5. Ring: faint track + conic light. Connecting shows a comet arc.
            val ringR = r * 0.80f
            val stroke = 3.2.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.06f), ringR, style = Stroke(stroke))
            rotate(spin, center) {
                val sweep = Brush.sweepGradient(listOf(Color.Transparent, deep.copy(alpha = 0.7f), accent, Color.White.copy(alpha = 0.9f), Color.Transparent), center)
                val arc = 360f * (0.18f + 0.82f * on) * (1f - busy) + 110f * busy
                drawArc(
                    sweep, startAngle = -arc / 2, sweepAngle = arc.coerceAtLeast(40f), useCenter = false,
                    topLeft = center - Offset(ringR, ringR), size = Size(ringR * 2, ringR * 2),
                    style = Stroke(stroke + 1.5.dp.toPx() * on, cap = StrokeCap.Round),
                    alpha = 0.35f + 0.65f * maxOf(on, busy, 0.25f + 0.2f * breath + 0.3f * hoverA),
                )
            }

            // 6. Power glyph
            val g = discR * 0.62f
            val gs = g * 0.17f
            val tint = lerp(Color.White.copy(alpha = 0.78f), Color.White, maxOf(on, busy))
            val pull = (1f + 0.04f * breath) * (1f - 0.05f * busy)
            translate(center.x - g / 2, center.y - g / 2) {
                scale(pull, pull, Offset(g / 2, g / 2)) {
                    drawArc(
                        tint, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                        topLeft = Offset(gs / 2, gs / 2), size = Size(g - gs, g - gs),
                        style = Stroke(gs, cap = StrokeCap.Round),
                    )
                    drawLine(tint, Offset(g / 2, gs / 2), Offset(g / 2, g * 0.46f), gs, StrokeCap.Round)
                }
            }
        }
    }
}

