package ru.terabitvpn.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import ru.terabitvpn.ui.theme.Motion

/**
 * Odometer text: every character that changes rolls vertically (up when the number grows).
 * Digits keep their slot, so "00:12:39 → 00:12:40" only rolls the last two.
 */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    Row(modifier) {
        // Pad from the left so digits keep their positions while the length changes.
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(Motion.quick(260)) { if (up) it else -it } + fadeIn(Motion.quick(200))) togetherWith
                        (slideOutVertically(Motion.quick(260)) { if (up) -it else it } + fadeOut(Motion.quick(160))) using
                        SizeTransform(clip = true)
                },
                label = "roll$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}
