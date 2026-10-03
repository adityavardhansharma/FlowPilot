package dev.flowpilot.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressable
import dev.flowpilot.app.ui.theme.raised

data class DockTab<T>(val value: T, val label: String, @DrawableRes val icon: Int, val badge: Int = 0)

/**
 * The dock: the tabs in one raised pill, floating on elevation-3, with the accent action beside it.
 *
 * The selection is ONE indicator that slides between tabs on spring-snappy, so switching never blinks one pill out
 * and another in. The chosen tab's label grows in from zero width on the same spring while the old one shrinks
 * away, which slides the icons over rather than jumping them. Tabs press like every other control and tick on
 * change.
 */
@Composable
fun <T> FpDock(
    tabs: List<DockTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes actionIcon: Int? = null,
    actionLabel: String = "",
    onAction: () -> Unit = {},
) {
    val c = Fp.colors
    val haptic = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(
            Modifier.weight(1f).height(DockHeight).raised(Radius.full, c.surfaceRaised, lift = true).padding(DockInset),
        ) {
            val cell = maxWidth / tabs.size
            val index = tabs.indexOfFirst { it.value == selected }.coerceAtLeast(0)
            val x by animateDpAsState(cell * index, Motion.snappy(), label = "dockIndicator")
            // The indicator is concentric with the dock: the dock's radius minus its inset, which for pills is a pill.
            Box(Modifier.offset(x = x).width(cell).fillMaxHeight().clip(Radius.full).background(c.accentSoft))
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                tabs.forEach { t ->
                    val on = t.value == selected
                    val ink by animateColorAsState(if (on) c.onAccentSoft else c.inkMuted, Motion.color(), label = "dockInk")
                    Row(
                        Modifier.weight(1f).fillMaxHeight()
                            .pressable(Radius.full, Color.Transparent, {
                                if (!on) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(t.value) }
                            }, role = Role.Tab, flat = true)
                            .semantics {
                                this.selected = on
                                contentDescription = if (t.badge > 0) "${t.label}, ${t.badge} waiting" else t.label
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Box {
                            Sym(t.icon, null, size = 22.dp, tint = ink)
                            if (t.badge > 0) FpBadge(t.badge, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp))
                        }
                        AnimatedVisibility(
                            on,
                            enter = expandHorizontally(Motion.snappy(), expandFrom = Alignment.Start) + fadeIn(Motion.fadeIn(delay = 40)),
                            exit = shrinkHorizontally(Motion.snappy(), shrinkTowards = Alignment.Start) + fadeOut(Motion.fadeOut()),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // A badge needs room of its own before the label starts.
                                Spacer(Modifier.width(if (t.badge > 0) 14.dp else 8.dp))
                                Text(t.label, style = FpType.label.copy(fontSize = FpType.body.fontSize), color = ink, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (actionIcon != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(DockHeight).pressable(Radius.full, c.accent, onAction, label = actionLabel),
                contentAlignment = Alignment.Center,
            ) { Sym(actionIcon, actionLabel, size = 24.dp, tint = c.onAccent) }
        }
    }
}

private val DockHeight = 56.dp
private val DockInset = 5.dp
