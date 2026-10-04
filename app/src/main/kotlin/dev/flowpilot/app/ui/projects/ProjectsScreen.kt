package dev.flowpilot.app.ui.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.ui.components.BarTitle
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.FpTopBar
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.ProjectShape
import dev.flowpilot.app.ui.components.SkeletonRow
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.components.listSegment
import dev.flowpilot.app.ui.components.rowPress
import dev.flowpilot.app.ui.components.scrolled
import dev.flowpilot.app.ui.components.titleGone
import dev.flowpilot.app.ui.home.HomeViewModel
import dev.flowpilot.app.ui.newchat.tilde
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion
import dev.flowpilot.core.api.Project
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.chat.ToolDescriber

/**
 * Every project in one list card, most recently active first, laid out like Chats: a fixed bar whose small title
 * fades in under a scrolling large one. Tapping a project starts a new chat there.
 */
@Composable
fun ProjectsScreen(vm: HomeViewModel, contentPadding: PaddingValues, onStartIn: (String) -> Unit, onNew: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scratch = vm.conn.knownScratch
    val home = scratch?.removeSuffix("/FlowPilot/Scratch")
    // The server can hold more than one project for the same folder; show the folder once, at its latest activity.
    val projects = remember(ui.state.projects, scratch) {
        ui.state.projects.values.filterNot { (scratch != null && it.canonical.startsWith(scratch)) || it.canonical == "/" }
            .sortedByDescending { it.time.active }.distinctBy { it.canonical }
    }
    val counts = remember(ui.state.sessions, ui.state.projects) {
        val folder = ui.state.projects.mapValues { it.value.canonical }
        ui.state.sessions.values.filter { it.parentID == null }.groupingBy { folder[it.projectID] }.eachCount()
    }
    val list = rememberLazyListState()
    val scrolled by list.scrolled()
    val titleGone by list.titleGone(TITLE)

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        FpTopBar(scrolled) {
            BarTitle("Projects", titleGone, Modifier.padding(start = 12.dp).weight(1f))
        }
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp), modifier = Modifier.fillMaxSize()) {
            item(TITLE) {
                Text("Projects", style = FpType.display, color = Fp.colors.ink, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
            }
            when {
                projects.isEmpty() && ui.firstLoad ->
                    items(5, key = { "sk$it" }, contentType = { "skeleton" }) { i -> SkeletonRow(Modifier.listSegment(i, 5)) }
                projects.isEmpty() -> item("empty") {
                    Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth()) {
                        EmptyState("No projects yet", body = "Projects appear here once a chat runs in a folder.", icon = Ic.folder, action = "New chat", onAction = onNew)
                    }
                }
                else -> itemsIndexed(projects, key = { _, p -> p.canonical }) { i, p ->
                    ProjectRow(
                        p, counts[p.canonical] ?: 0, home, onClick = { onStartIn(p.canonical) },
                        modifier = Modifier.animateItem(fadeInSpec = Motion.fadeIn(), placementSpec = Motion.settle(), fadeOutSpec = Motion.fadeOut())
                            .listSegment(i, projects.size),
                    )
                }
            }
        }
    }
}

private const val TITLE = "title"

/** A project: its pebble, the name, the path in code, how many chats and when it was last active, and a chevron. */
@Composable
private fun ProjectRow(p: Project, chats: Int, home: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fp.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp).rowPress(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectShape(p.displayName, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.displayName, style = FpType.body, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(ToolDescriber.shortPath(tilde(p.canonical, home), 36), style = FpType.codeSmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val footer = buildList {
                add(if (chats == 1) "1 chat" else "$chats chats")
                if (p.time.active > 0) add(Format.relative(p.time.active))
            }.joinToString(" · ")
            Text(footer, style = FpType.caption, color = c.inkMuted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Sym(Ic.chevronRight, null, size = 20.dp, tint = c.inkMuted)
    }
}
