package dev.flowpilot.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressable
import dev.flowpilot.app.ui.theme.pressed
import dev.flowpilot.app.ui.theme.raised

/*
 * The design system's components, built on foundation rather than Material: every control is pressed out of the
 * surface and sinks while held (see Depth.kt). Names follow the design system artifact.
 */

enum class FpButtonVariant { Default, Primary, Tonal, Danger, Ghost }

/** Button. `Primary` at most once per view; labels are verbs in sentence case. */
@Composable
fun FpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FpButtonVariant = FpButtonVariant.Default,
    small: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = Fp.colors
    val (fill, ink) = when (variant) {
        FpButtonVariant.Default -> c.surface to c.ink
        FpButtonVariant.Primary -> c.accent to c.onAccent
        FpButtonVariant.Tonal -> c.accentSoft to c.onAccentSoft
        FpButtonVariant.Danger -> c.dangerSoft to c.onDangerSoft
        FpButtonVariant.Ghost -> Color.Transparent to c.ink
    }
    // Every button is a pill: its radius is half its height at any size, so corners never look clipped.
    val shape = Radius.full
    Row(
        modifier
            .heightIn(min = if (small) 36.dp else 44.dp)
            .pressable(shape, fill, onClick, enabled = enabled, flat = variant == FpButtonVariant.Ghost)
            .padding(horizontal = if (small) 16.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) ink else c.inkFaint
        if (icon != null) { Sym(icon, null, size = 18.dp, tint = tint); Spacer(Modifier.width(8.dp)) }
        Text(text, style = FpType.label, color = tint, maxLines = 1)
    }
}

/** A round icon-only control; flat at rest, a well while pressed. [raised] for floating use. */
@Composable
fun FpIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    small: Boolean = false,
    enabled: Boolean = true,
    tint: Color = Fp.colors.ink,
) {
    Box(
        modifier
            .size(if (small) 32.dp else 40.dp)
            .pressable(Radius.full, if (raised) Fp.colors.surface else Color.Transparent, onClick, enabled = enabled, label = label, flat = !raised),
        contentAlignment = Alignment.Center,
    ) { Sym(icon, label, size = if (small) 18.dp else 22.dp, tint = if (enabled) tint else Fp.colors.inkFaint) }
}

enum class SendState { Send, Stop, Disabled }

/** The send key: the one round accent object on screen. Stop flips it to ink. */
@Composable
fun SendKey(state: SendState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fp.colors
    val fill by animateColorAsState(if (state == SendState.Stop) c.ink else c.accent, Motion.color(), label = "send")
    val ink by animateColorAsState(
        when (state) { SendState.Stop -> c.ground; SendState.Send -> c.onAccent; SendState.Disabled -> c.inkFaint },
        Motion.color(), label = "sendInk",
    )
    Box(
        modifier.size(44.dp).pressable(Radius.full, fill, onClick, enabled = state != SendState.Disabled, label = if (state == SendState.Stop) "Stop" else "Send"),
        contentAlignment = Alignment.Center,
    ) {
        // The glyph swaps in place: the old one shrinks out fast, the new one grows in on spring-snappy.
        AnimatedContent(
            state == SendState.Stop,
            transitionSpec = {
                (fadeIn(Motion.fadeIn()) + scaleIn(Motion.snappy(), initialScale = 0.6f))
                    .togetherWith(fadeOut(Motion.fadeOut()) + scaleOut(Motion.press(), targetScale = 0.6f))
                    .using(null)
            },
            label = "sendGlyph",
        ) { stop ->
            Sym(if (stop) Ic.stop else Ic.arrowUp, if (stop) "Stop" else "Send", size = 22.dp, tint = ink)
        }
    }
}

/** A 34dp pill. [selected] sits flush in accent-soft; [well] is pressed in, for things attached rather than chosen. */
@Composable
fun FpChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    selected: Boolean = false,
    well: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Fp.colors
    val fill by animateColorAsState(if (selected) c.accentSoft else c.surface, Motion.color(), label = "chipFill")
    val ink by animateColorAsState(if (selected) c.onAccentSoft else c.ink, Motion.color(), label = "chipInk")
    val base = if (well) Modifier.pressed(Radius.full, c.surfaceSunken).selectable(selected = false, onClick = onClick)
    else Modifier.pressable(Radius.full, fill, onClick, role = Role.Checkbox, flat = selected)
    Row(
        modifier.height(34.dp).then(base).semantics { this.selected = selected }
            .padding(start = 12.dp, end = if (trailing != null) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Sym(icon, null, size = 16.dp, tint = ink); Spacer(Modifier.width(6.dp)) }
        else if (!well) {
            // Selecting grows a check in from zero width, so the label slides over instead of jumping.
            AnimatedVisibility(
                selected,
                enter = expandHorizontally(Motion.snappy(), expandFrom = Alignment.Start) + fadeIn(Motion.fadeIn()),
                exit = shrinkHorizontally(Motion.snappy(), shrinkTowards = Alignment.Start) + fadeOut(Motion.fadeOut()),
            ) {
                Row { Sym(Ic.check, null, size = 16.dp, tint = ink); Spacer(Modifier.width(4.dp)) }
            }
        }
        Text(text, style = FpType.label, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke(this)
    }
}

data class Segment<T>(val value: T, val label: String, @DrawableRes val icon: Int? = null)

/** A sunken track holding one raised key that slides to the selected option. For two or three choices. */
@Composable
fun <T> FpSegmented(segments: List<Segment<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Fp.colors
    val haptic = LocalHapticFeedback.current
    val index = segments.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.height(44.dp).pressed(Radius.full, c.surfaceSunken).padding(4.dp)) {
        val each = maxWidth / segments.size
        val x by animateDpAsState(each * index, Motion.snappy(), label = "segment")
        Box(Modifier.offset(x = x).width(each).fillMaxHeight().raised(Radius.full, c.surfaceRaised))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            segments.forEach { s ->
                val on = s.value == selected
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(Radius.full)
                        .selectable(selected = on, role = Role.RadioButton) { if (!on) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(s.value) } },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val ink by animateColorAsState(if (on) c.ink else c.inkMuted, Motion.color(), label = "segInk")
                    if (s.icon != null) { Sym(s.icon, null, size = 16.dp, tint = ink); Spacer(Modifier.width(6.dp)) }
                    Text(s.label, style = FpType.label, color = ink, maxLines = 1)
                }
            }
        }
    }
}

/** A switch in the same physics: a sunken track, a raised knob. The track fills with accent when on. */
@Composable
fun FpSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Fp.colors
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, Motion.snappy(), label = "switch")
    val stretch by animateDpAsState(if (down) 4.dp else 0.dp, Motion.press(), label = "stretch")
    val track by animateColorAsState(if (checked) c.accent else c.surfaceSunken, Motion.color(), label = "track")
    // Off, the knob must still read against a dark well, so in Basalt it takes a lighter stone.
    val knob by animateColorAsState(
        when { checked -> c.onAccent; c.isDark -> androidx.compose.ui.graphics.lerp(c.surfaceRaised, c.ink, 0.42f); else -> c.surfaceRaised },
        Motion.color(), label = "knob",
    )
    Box(
        modifier.size(52.dp, 32.dp).pressed(Radius.full, track)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onChange(it)
            }
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x - if (checked) stretch else 0.dp).size(24.dp + stretch, 24.dp).raised(Radius.full, knob))
    }
}

/** A card on the ground: `surface`, `radius-lg`, `shadow-rest`. */
@Composable
fun FpCard(modifier: Modifier = Modifier, color: Color = Fp.colors.surface, shape: Shape = Radius.lg, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().raised(shape, color), content = content)
}

/**
 * A count on a tab or row: amber for things that need the person, accent otherwise. It pops on spring-lively each
 * time the count changes, and caps at 99+.
 */
@Composable
fun FpBadge(count: Int, modifier: Modifier = Modifier, amber: Boolean = true) {
    if (count <= 0) return
    val c = Fp.colors
    val pop = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count != last) { last = count; pop.snapTo(1.25f); pop.animateTo(1f, Motion.lively()) }
    }
    Box(
        modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .heightIn(min = 16.dp).widthIn(min = 16.dp)
            .clip(Radius.full).background(if (amber) c.amber else c.accent).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            style = FpType.caption.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight(650)),
            color = if (amber) c.onAmber else c.onAccent,
        )
    }
}

/** A keycap for a typed shortcut: `/`, `@`, `!`. */
@Composable
fun Keycap(key: String) {
    Box(Modifier.heightIn(min = 26.dp).widthIn(min = 26.dp).raised(Radius.sm, Fp.colors.surface).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        Text(key, style = FpType.code.copy(fontSize = 13.sp, fontWeight = FontWeight(500)), color = Fp.colors.inkMuted)
    }
}

/** A short note in inverted colours with at most one action. Used as the snackbar of every screen. */
@Composable
fun FpToast(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = Fp.colors
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 480.dp).fillMaxWidth()
            .raised(Radius.lg, c.ink, lift = true).padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = FpType.body, color = c.ground, modifier = Modifier.weight(1f))
        data.visuals.actionLabel?.let { label ->
            Box(Modifier.clip(Radius.full).clickable(role = Role.Button) { data.performAction() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(label, style = FpType.label.copy(fontWeight = FontWeight(600)), color = c.ground)
            }
        }
    }
}

/** A breathing dot: the working state, at 1.6s. */
@Composable
fun BreathingDot(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "breathe")
    val s by t.animateFloat(1f, 0.55f, infiniteRepeatable(tween(800, easing = Motion.settleEasing), RepeatMode.Reverse), label = "s")
    Box(modifier.size(8.dp).scale(s).clip(Radius.full).background(color))
}

/**
 * The pebble: an irregular pressed-in disc, FlowPilot's own shape for projects. Each corner has its own
 * horizontal and vertical radius, as percentages of the size.
 */
val PebbleShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    // (x-radius, y-radius) per corner: top-left, top-right, bottom-right, bottom-left.
    val tl = Offset(0.42f * w, 0.50f * h); val tr = Offset(0.58f * w, 0.44f * h)
    val br = Offset(0.52f * w, 0.56f * h); val bl = Offset(0.48f * w, 0.50f * h)
    moveTo(tl.x, 0f)
    lineTo(w - tr.x, 0f)
    arcTo(androidx.compose.ui.geometry.Rect(Offset(w - 2 * tr.x, 0f), Size(2 * tr.x, 2 * tr.y)), 270f, 90f, false)
    lineTo(w, h - br.y)
    arcTo(androidx.compose.ui.geometry.Rect(Offset(w - 2 * br.x, h - 2 * br.y), Size(2 * br.x, 2 * br.y)), 0f, 90f, false)
    lineTo(bl.x, h)
    arcTo(androidx.compose.ui.geometry.Rect(Offset(0f, h - 2 * bl.y), Size(2 * bl.x, 2 * bl.y)), 90f, 90f, false)
    lineTo(0f, tl.y)
    arcTo(androidx.compose.ui.geometry.Rect(Offset(0f, 0f), Size(2 * tl.x, 2 * tl.y)), 180f, 90f, false)
    close()
}

/** A project's pebble with its initial. Each project gets its own tilt, so neighbours don't look stamped. */
@Composable
fun Pebble(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier, muted: Boolean = false) {
    val c = Fp.colors
    val h = name.hashCode() and 0x7fffffff
    val tint = when {
        muted -> c.inkFaint
        h % 3 == 0 -> c.accent
        c.isDark -> c.ink
        else -> c.inkMuted
    }
    Box(
        modifier.size(size).rotate((h % 4) * 90f).pressed(PebbleShape, c.surfaceSunken)
            .then(if (c.isDark) Modifier.border(1.dp, c.line, PebbleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·",
            style = TextStyle(fontWeight = FontWeight(600), fontSize = (size.value * 0.4f).sp, fontFamily = FpType.label.fontFamily),
            color = tint,
            modifier = Modifier.rotate(-(h % 4) * 90f),
        )
    }
}

/** A still status dot with a soft halo: a healthy connection. Only work breathes, so this one never moves. */
@Composable
fun StillDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).clip(Radius.full).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(8.dp).clip(Radius.full).background(color))
    }
}

/** The overline label over a list section: 11sp capitals in ink-muted. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = FpType.overline, color = Fp.colors.inkMuted, modifier = modifier)
}

/** Provides ink as the content colour, for Material pieces placed on a system surface. */
@Composable
fun OnSurface(color: Color = Fp.colors.ink, content: @Composable () -> Unit) {
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides color, content = content)
}


/** The working spinner: a three-quarter arc on the 1.75 stroke, turning at 1.1s. Size it with [modifier]. */
@Composable
fun FpSpinner(modifier: Modifier = Modifier, color: Color = Fp.colors.accent) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "a")
    androidx.compose.foundation.Canvas(modifier.size(20.dp).rotate(angle)) {
        val stroke = size.minDimension * 0.09f
        drawArc(
            color = color, startAngle = 0f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/**
 * One row of a list card inside a LazyColumn: rows of a group share one `surface` card with `radius-lg` ends and
 * `line` hairlines between them, inset past the leading avatar by [inset].
 */
fun Modifier.listSegment(index: Int, count: Int, inset: Dp = 72.dp): Modifier = composed {
    val c = Fp.colors
    val first = index == 0
    val last = index == count - 1
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp,
    )
    this.padding(horizontal = 16.dp).clip(shape).background(c.surface).drawWithContent {
        drawContent()
        if (!first) drawLine(c.line, Offset(inset.toPx(), 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
}
