@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package dev.flowpilot.app.ui.home

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
import androidx.compose.material3.LoadingIndicator
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
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.home.RowStatus
import dev.flowpilot.core.home.ThreadRowModel

/** One chat in a list: project shape, title, live or folder line, time and status. 72dp tall. */
@Composable
fun ThreadRow(row: ThreadRowModel, projectName: String, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
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
                Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(scheme.surface), contentAlignment = Alignment.Center) {
                    Sym(Ic.pin, "Pinned", size = 12.dp, tint = scheme.primary)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.status == RowStatus.Unread || row.status == RowStatus.NeedsYou) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = when (row.status) {
                    RowStatus.NeedsYou -> scheme.tertiary
                    RowStatus.Working -> scheme.primary
                    RowStatus.Failed -> scheme.error
                    else -> scheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.relative(row.lastActivity), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            StatusMark(row.status)
        }
    }
}

@Composable
fun StatusMark(status: RowStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        RowStatus.NeedsYou -> Surface(color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer, shape = CircleShape) {
            Text("Needs you", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        RowStatus.Working -> LoadingIndicator(Modifier.size(20.dp), color = scheme.primary)
        RowStatus.Failed -> Sym(Ic.error, "Failed", size = 18.dp, tint = scheme.error)
        RowStatus.Unread -> Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
        RowStatus.Idle -> Spacer(Modifier.size(10.dp))
    }
}
