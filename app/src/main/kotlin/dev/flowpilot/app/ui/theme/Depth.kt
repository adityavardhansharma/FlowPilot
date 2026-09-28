package dev.flowpilot.app.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/*
 * Pressed out, pressed in. Things you act on are raised: a light top edge (the bevel) and a short soft cast.
 * Things that hold content are wells. Pressing a raised control sinks it into the well state. These three
 * modifiers are `shadow-rest`, `shadow-lift` and `shadow-press` from the tokens.
 */

/** `shadow-rest` (or `shadow-lift` for things that float), then the fill, clipped to [shape]. */
fun Modifier.raised(shape: Shape, color: Color, lift: Boolean = false): Modifier = composed {
    val dark = Fp.colors.isDark
    this
        .dropShadow(
            shape,
            Shadow(
                radius = if (lift) 32.dp else 6.dp,
                color = Color.Black.copy(alpha = if (dark) (if (lift) 0.5f else 0.3f) else (if (lift) 0.12f else 0.05f)),
                offset = DpOffset(0.dp, if (lift) 12.dp else 2.dp),
            ),
        )
        .dropShadow(
            shape,
            Shadow(
                radius = if (lift) 4.dp else 1.dp,
                color = Color.Black.copy(alpha = if (dark) 0.45f else if (lift) 0.06f else 0.05f),
                offset = DpOffset(0.dp, if (lift) 2.dp else 1.dp),
            ),
        )
        .clip(shape)
        .background(color)
        .innerShadow(
            shape,
            Shadow(radius = 0.dp, color = Color.White.copy(alpha = if (dark) 0.05f else 0.75f), offset = DpOffset(0.dp, 1.dp)),
        )
}

/** `shadow-press`: a well, or a control while it is held. */
fun Modifier.pressed(shape: Shape, color: Color): Modifier = composed {
    val dark = Fp.colors.isDark
    this
        .clip(shape)
        .background(color)
        .innerShadow(shape, Shadow(radius = 2.dp, color = Color.Black.copy(alpha = if (dark) 0.55f else 0.10f), offset = DpOffset(0.dp, 1.dp)))
}

/**
 * A raised control that sinks while held: `raised` at rest, `pressed` and 1dp lower under the finger. No ripple;
 * the press is the feedback. [haptic] ticks on release for toggles.
 */
fun Modifier.pressable(
    shape: Shape,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    label: String? = null,
    haptic: Boolean = false,
    flat: Boolean = false,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val nudge by animateDpAsState(if (down && enabled) 1.dp else 0.dp, Motion.quick(), label = "press")
    val feedback = LocalHapticFeedback.current
    val base = this.graphicsLayer { translationY = nudge.toPx() }
    val skin = when {
        !enabled -> Modifier.pressed(shape, Fp.colors.surfaceSunken)
        down -> Modifier.pressed(shape, color)
        flat -> Modifier.clip(shape).background(color)
        else -> Modifier.raised(shape, color)
    }
    base.then(skin).clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClickLabel = label,
    ) {
        if (haptic) feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onClick()
    }
}
