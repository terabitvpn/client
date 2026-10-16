package ru.terabitvpn.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.terabitvpn.resources.Res
import ru.terabitvpn.resources.beeline_sans_black
import org.jetbrains.compose.resources.Font

/** Terabit palette: brand green #014320 for fills, a lighter tint of it for text and glows on the dark background. */
@Immutable
data class BrandColors(
    val bg: Color = Color(0xFF030D08),
    val bgRaised: Color = Color(0xFF07160F),
    val ink: Color = Color(0xFFEEF6F1),
    val ink2: Color = Color(0xFFB4C9BD),
    val ink3: Color = Color(0xFF7C9487),
    val accent: Color = Color(0xFF2FBF71),
    val accent2: Color = Color(0xFF014320),
    val accentInk: Color = Color(0xFF03150C),
    val ok: Color = Color(0xFF9BE8B8),
    val warn: Color = Color(0xFFFFD08A),
    val bad: Color = Color(0xFFFF9AA9),
    val line: Color = Color(0x14FFFFFF),
    val glass: Color = Color(0x0DFFFFFF),
    val glassStrong: Color = Color(0x17FFFFFF),
)

object Motion {
    /** --ease: cubic-bezier(.22, 1, .36, 1) */
    val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    fun <T> soft() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    fun <T> quick(ms: Int = 320) = tween<T>(ms, easing = Ease)
}

val LocalBrandColors = staticCompositionLocalOf { BrandColors() }
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Server-tunable look (see RemoteDesign). */
val LocalDesign = androidx.compose.runtime.compositionLocalOf { ru.terabitvpn.core.design.DesignTokens() }

object Brand {
    val colors: BrandColors @Composable get() = LocalBrandColors.current
}

/** Beeline Sans ships here as a single Black face: every weight the UI asks for maps onto it. */
@Composable
fun brandFamily(): FontFamily = FontFamily(
    Font(Res.font.beeline_sans_black, FontWeight.Normal),
    Font(Res.font.beeline_sans_black, FontWeight.Medium),
    Font(Res.font.beeline_sans_black, FontWeight.SemiBold),
    Font(Res.font.beeline_sans_black, FontWeight.Bold),
    Font(Res.font.beeline_sans_black, FontWeight.ExtraBold),
    Font(Res.font.beeline_sans_black, FontWeight.Black),
)

@Composable
fun TerabitTheme(accentArgb: Long, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val target = Color(accentArgb.toInt())
    val accent by animateColorAsState(target, Motion.quick(500))
    val colors = BrandColors(
        accent = accent,
        // Deeper companion shade of the accent for gradients: pulled towards the brand green #014320.
        accent2 = lerp(accent, Color(0xFF014320), 0.6f),
        accentInk = lerp(accent, Color.Black, 0.86f),
    )
    val family = brandFamily()
    val base = TextStyle(fontFamily = family, color = colors.ink)
    val typography = Typography(
        displayLarge = base.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
        displayMedium = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = base.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink2),
        bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, color = colors.ink3),
        labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.accentInk,
        secondary = colors.ok,
        background = colors.bg,
        onBackground = colors.ink,
        surface = colors.bgRaised,
        onSurface = colors.ink,
        surfaceVariant = Color(0xFF14231B),
        onSurfaceVariant = colors.ink2,
        surfaceContainer = Color(0xFF0D1A14),
        surfaceContainerHigh = Color(0xFF11201A),
        surfaceContainerHighest = Color(0xFF162720),
        surfaceContainerLow = Color(0xFF07160F),
        outline = colors.line,
        outlineVariant = colors.line,
        error = colors.bad,
    )
    CompositionLocalProvider(LocalBrandColors provides colors, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
