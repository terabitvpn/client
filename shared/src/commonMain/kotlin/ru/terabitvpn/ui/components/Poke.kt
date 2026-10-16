package ru.terabitvpn.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI

/** Light haptic tick, provided by the app (respects the user's vibration setting). */
val LocalHaptic = staticCompositionLocalOf<() -> Unit> { {} }

/** Haptic of a chosen strength for UI pieces (rows, switches, segmented controls). */
val LocalHapticOf = staticCompositionLocalOf<(ru.terabitvpn.core.vpn.Haptic) -> Unit> { {} }

/** A quiet note when the cursor enters something clickable (desktop). */
val LocalHoverSound = staticCompositionLocalOf<() -> Unit> { {} }

/** Plays the hover note when the pointer enters this element. Put in front of every `clickable`. */
fun Modifier.hoverSound(): Modifier = composed {
    val sound = LocalHoverSound.current
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) sound()
            }
        }
    }
}

internal val TAU = (2 * PI).toFloat()
