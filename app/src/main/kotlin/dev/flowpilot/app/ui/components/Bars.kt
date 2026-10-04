package dev.flowpilot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion

/**
 * A fixed top bar on the ground, 56dp tall. Its hairline only shows once content has scrolled beneath it, so a
 * screen at rest has no line across it.
 */
@Composable
fun FpTopBar(scrolled: Boolean, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Fp.colors.ground)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        Hairline(scrolled)
    }
}

/** A `line` hairline that fades in and out; it never moves the layout. */
@Composable
fun Hairline(visible: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, if (visible) Motion.fadeIn() else Motion.fadeOut(), label = "hairline")
    Box(modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = a }.background(Fp.colors.line))
}

/** The bar's small title, which fades in once the screen's large title has scrolled away. */
@Composable
fun BarTitle(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, if (visible) Motion.fadeIn() else Motion.fadeOut(), label = "barTitle")
    Text(text, style = FpType.title, color = Fp.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.alpha(a))
}

/** True once anything has scrolled under the bar. */
@Composable
fun LazyListState.scrolled(): State<Boolean> = remember(this) { derivedStateOf { canScrollBackward } }

/** True once more than half of the large title (the item keyed [key]) has gone under the bar. */
@Composable
fun LazyListState.titleGone(key: Any): State<Boolean> = remember(this, key) {
    derivedStateOf {
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        if (item == null) firstVisibleItemIndex > 0 else -item.offset > item.size / 2
    }
}

/**
 * A flat list row's press: state-press paints at once under the finger (no ripple) and fades out quickly on
 * release. [onLongClick] gets the system's long-press haptic.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowPress(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, longClickLabel: String? = null, enabled: Boolean = true): Modifier = composed {
    val c = Fp.colors
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val tint by animateColorAsState(if (down) c.ink.copy(alpha = if (c.isDark) 0.08f else 0.06f) else Color.Transparent, Motion.fadeOut(), label = "rowPress")
    background(tint).combinedClickable(
        interactionSource = source, indication = null, enabled = enabled,
        onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel,
    )
}
