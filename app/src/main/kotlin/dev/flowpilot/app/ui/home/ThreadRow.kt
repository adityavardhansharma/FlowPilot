package dev.flowpilot.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flowpilot.app.ui.components.FpSpinner
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.ProjectShape
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.home.RowStatus
import dev.flowpilot.core.home.ThreadRowModel

/**
 * One chat in a list: the project's pebble, the title, a live or folder line, then the time over one status mark.
 * 72dp tall, flat inside its card. Pressing paints state-press at once (no ripple); a long press opens the chat's
 * actions. The status mark changes by crossfade and a small scale, never by popping.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadRow(row: ThreadRowModel, projectName: String, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fp.colors
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val press by animateColorAsState(if (down) c.ink.copy(alpha = if (c.isDark) 0.08f else 0.06f) else Color.Transparent, Motion.fadeOut(), label = "rowPress")
    val strong = row.status == RowStatus.Unread || row.status == RowStatus.NeedsYou
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(press)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Chat options")
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectShape(projectName, 40.dp, muted = projectName == "No project")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = FpType.body.copy(fontSize = 15.5.sp),
                color = c.ink,
                fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val supportingInk by animateColorAsState(
                when (row.status) {
                    RowStatus.NeedsYou -> c.amber
                    RowStatus.Working -> c.accent
                    RowStatus.Failed -> c.danger
                    else -> c.inkMuted
                },
                Motion.color(), label = "rowSub",
            )
            Text(row.supporting, style = FpType.caption, color = supportingInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.pinned) {
                    Sym(Ic.pin, "Pinned", size = 12.dp, tint = c.inkFaint)
                    Spacer(Modifier.width(4.dp))
                }
                Text(Format.relative(row.lastActivity), style = FpType.caption, color = c.inkMuted)
            }
            AnimatedContent(
                row.status,
                transitionSpec = {
                    (fadeIn(Motion.fadeIn()) + scaleIn(Motion.snappy(), initialScale = 0.7f))
                        .togetherWith(fadeOut(Motion.fadeOut()) + scaleOut(Motion.press(), targetScale = 0.7f))
                        .using(null)
                },
                contentAlignment = Alignment.CenterEnd,
                label = "status",
            ) { StatusMark(it) }
        }
    }
}

@Composable
fun StatusMark(status: RowStatus) {
    val c = Fp.colors
    when (status) {
        RowStatus.NeedsYou -> Row(
            Modifier.clip(CircleShape).background(c.amberSoft).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(Ic.lock, null, size = 12.dp, tint = c.onAmberSoft)
            Spacer(Modifier.width(4.dp))
            Text("Needs you", style = FpType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium), color = c.onAmberSoft)
        }
        RowStatus.Working -> FpSpinner(Modifier.size(16.dp), color = c.accent)
        RowStatus.Failed -> Sym(Ic.error, "Failed", size = 16.dp, tint = c.danger)
        RowStatus.Unread -> Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(c.accent))
        RowStatus.Idle -> Spacer(Modifier.size(16.dp))
    }
}
