package dev.flowpilot.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.ProjectShape
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.home.HomeViewModel
import dev.flowpilot.app.ui.newchat.tilde
import dev.flowpilot.app.ui.theme.CodeSmallStyle
import dev.flowpilot.core.api.Project
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.chat.ToolDescriber

/** Two-column grid of projects. Tapping one starts a new chat there. */
@Composable
fun ProjectsScreen(vm: HomeViewModel, contentPadding: PaddingValues, onStartIn: (String) -> Unit, onNew: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scratch = vm.conn.knownScratch
    val home = scratch?.removeSuffix("/FlowPilot/Scratch")
    val projects = remember(ui.state.projects, scratch) {
        ui.state.projects.values.filterNot { (scratch != null && it.canonical.startsWith(scratch)) || it.canonical == "/" }.sortedByDescending { it.time.active }
    }
    val counts = remember(ui.state.sessions) { ui.state.sessions.values.filter { it.parentID == null }.groupingBy { it.projectID }.eachCount() }
    if (projects.isEmpty() && !ui.firstLoad) {
        EmptyState("No projects yet", body = "Projects appear here once a chat runs in a folder.", icon = Ic.folder, action = "New chat", onAction = onNew, modifier = Modifier.padding(contentPadding))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Projects", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(projects.distinctBy { it.id }, key = { it.id }) { p -> ProjectCard(p, counts[p.id] ?: 0, home) { onStartIn(p.canonical) } }
    }
}

@Composable
private fun ProjectCard(p: Project, chats: Int, home: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = Radius.lgIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            ProjectShape(p.displayName, 48.dp)
            Spacer(Modifier.height(12.dp))
            Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ToolDescriber.shortPath(tilde(p.canonical, home), 28), style = CodeSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            val footer = buildList {
                add(if (chats == 1) "1 chat" else "$chats chats")
                if (p.time.active > 0) add(Format.relative(p.time.active))
            }.joinToString(" · ")
            Text(footer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
