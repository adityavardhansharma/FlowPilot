@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package dev.flowpilot.app.ui.home

import dev.flowpilot.app.ui.components.FpSpinner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.ProjectShape
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import androidx.compose.ui.unit.sp
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.home.RowStatus
import dev.flowpilot.core.home.ThreadRowModel

/** One chat in a list: project shape, title, live or folder line, time and status. 72dp tall. */
@Composable
fun ThreadRow(row: ThreadRowModel, projectName: String, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fp.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Chat options")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ProjectShape(projectName, 40.dp, muted = projectName == "No project")
            if (row.pinned) {
                Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
                    Sym(Ic.pin, "Pinned", size = 12.dp, tint = c.accent)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = FpType.body,
                color = c.ink,
                fontWeight = if (row.status == RowStatus.Unread || row.status == RowStatus.NeedsYou) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.supporting,
                style = FpType.caption,
                color = when (row.status) {
                    RowStatus.NeedsYou -> c.amber
                    RowStatus.Working -> c.accent
                    RowStatus.Failed -> c.danger
                    else -> c.inkMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.relative(row.lastActivity), style = FpType.caption, color = c.inkMuted)
            Spacer(Modifier.size(4.dp))
            StatusMark(row.status)
        }
    }
}

@Composable
fun StatusMark(status: RowStatus) {
    val c = Fp.colors
    when (status) {
        RowStatus.NeedsYou -> Row(Modifier.clip(CircleShape).background(c.amberSoft).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(Ic.lock, null, size = 12.dp, tint = c.onAmberSoft)
            Spacer(Modifier.width(4.dp))
            Text("Needs you", style = FpType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium), color = c.onAmberSoft)
        }
        RowStatus.Working -> FpSpinner(Modifier.size(18.dp), color = c.accent)
        RowStatus.Failed -> Sym(Ic.error, "Failed", size = 18.dp, tint = c.danger)
        RowStatus.Unread -> Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
        RowStatus.Idle -> Spacer(Modifier.size(8.dp))
    }
}
