package ru.terabitvpn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path

/** The brand green (#014320): the colour of the mark and of the app icon. */
val BrandGreen = Color(0xFF014320)

/**
 * The Terabit mark: the "T" of Beeline Sans Black, drawn from its outline (so it looks the same on every
 * platform). By default it sits on a white rounded tile like the app icon; with [tint] it is just the
 * letter, for places that already have a background (the tab bar).
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, tint: Color? = null) {
    val glyph = remember {
        Path().apply {
            val p = BRAND_T_POINTS
            moveTo(p[0], p[1])
            for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
            close()
        }
    }
    Canvas(modifier) {
        val side = size.minDimension
        if (tint == null) {
            drawRoundRect(Color.White, size = size, cornerRadius = CornerRadius(side * 0.225f))
        }
        val k = side * (if (tint == null) 0.50f else 0.80f) / 100f
        translate(size.width / 2f - BRAND_T_WIDTH * k / 2f, size.height / 2f - 50f * k) {
            scale(k, k, Offset.Zero) { drawPath(glyph, tint ?: BrandGreen) }
        }
    }
}
