@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.data.LinkState
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.ErrorCard
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.LoadingRow
import dev.flowpilot.app.ui.components.SectionHeader
import dev.flowpilot.app.ui.components.ServerChip
import dev.flowpilot.app.ui.components.SkeletonRow
import dev.flowpilot.app.ui.components.Sym
import androidx.compose.foundation.lazy.itemsIndexed
import dev.flowpilot.app.ui.components.FpChip
import dev.flowpilot.app.ui.components.FpIconButton
import dev.flowpilot.app.ui.components.listSegment
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressed
import dev.flowpilot.core.home.HomeFilter
import dev.flowpilot.core.home.ThreadRowModel

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle(emptySet())
    val link by vm.conn.state.collectAsStateWithLifecycle()
    val scratch = vm.conn.knownScratch
    var menuFor by remember { mutableStateOf<ThreadRowModel?>(null) }
    var renaming by remember { mutableStateOf<ThreadRowModel?>(null) }
    var deleting by remember { mutableStateOf<ThreadRowModel?>(null) }

    LaunchedEffect(ui.message) { ui.message?.let { showMessage(it); vm.consumeMessage() } }

    val groups = remember(ui.state, pinned, ui.filter, scratch) { ui.state.groups(pinned, scratch, ui.filter) }
    val query = ui.query.trim()
    val searchRows = remember(query, ui.state, ui.searchHits, pinned, scratch) {
        if (query.isEmpty()) emptyList() else {
            val local = ui.state.sessions.values.filter { it.parentID == null && (it.title ?: "").contains(query, ignoreCase = true) }
            (local + ui.searchHits).distinctBy { it.id }.sortedByDescending { it.time.updated }.map { ui.state.row(it, pinned, scratch) }
        }
    }
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it > list.layoutInfo.totalItemsCount - 8 } ?: false } }
    LaunchedEffect(Unit) { snapshotFlow { nearEnd }.collect { if (it && query.isEmpty()) vm.loadMore() } }

    fun projectName(r: ThreadRowModel) = if (scratch != null && r.session.location.directory.startsWith(scratch)) "No project" else r.project?.displayName ?: r.session.location.directory.substringAfterLast('/')

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        // Top bar: server chip, search, settings.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ServerChip(vm.conn.server.name, link, onClick = onSettings)
            Spacer(Modifier.size(8.dp))
            SearchField(ui.query, vm::setQuery, Modifier.weight(1f))
            Spacer(Modifier.size(4.dp))
            FpIconButton(Ic.settings, "Settings", onClick = onSettings)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeFilter.entries.forEach { f ->
                FpChip(f.label, onClick = { vm.setFilter(f) }, selected = ui.filter == f)
            }
        }
        if (link == LinkState.Unauthorized) {
            ErrorCard("Your pairing expired", "Scan a new code to reconnect.", Modifier.padding(16.dp), action = "Pair", onAction = onSettings)
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            when {
                ui.firstLoad -> Column { repeat(8) { SkeletonRow() } }
                query.isNotEmpty() && searchRows.isEmpty() -> EmptyState("No chats match \"$query\".", icon = Ic.search)
                query.isEmpty() && groups.isEmpty() && ui.error != null -> EmptyState(
                    "Can't reach your computer", body = ui.error, icon = Ic.wifiOff, action = "Retry", onAction = { vm.refresh() },
                    secondary = "Change computer", onSecondary = onSettings,
                )
                query.isEmpty() && groups.isEmpty() -> EmptyState(
                    if (ui.filter == HomeFilter.All) "No chats yet" else "Nothing here",
                    body = if (ui.filter == HomeFilter.All) "Start one and the agent gets to work on your computer." else null,
                    icon = Ic.chat,
                    action = if (ui.filter == HomeFilter.All) "New chat" else null,
                    onAction = onNewChat,
                )
                else -> LazyColumn(state = list, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp), modifier = Modifier.fillMaxSize()) {
                    if (ui.error != null && query.isEmpty()) item("offline") {
                        Text(
                            "Offline. Showing saved chats.",
                            style = FpType.caption,
                            color = Fp.colors.inkMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    if (query.isNotEmpty()) {
                        item("qh") { Spacer(Modifier.height(12.dp)) }
                        itemsIndexed(searchRows, key = { _, r -> "q" + r.session.id }) { i, r ->
                            ThreadRow(r, projectName(r), onClick = { onOpenChat(r.session.id) }, onLongClick = { menuFor = r }, modifier = Modifier.listSegment(i, searchRows.size))
                        }
                    } else groups.forEach { g ->
                        item("h" + g.label, contentType = "header") {
                            SectionHeader(g.label, color = if (g.attention) Fp.colors.amber else Fp.colors.inkMuted)
                        }
                        itemsIndexed(g.rows, key = { _, r -> g.label + r.session.id }, contentType = { _, _ -> "row" }) { i, r ->
                            ThreadRow(r, projectName(r), onClick = { onOpenChat(r.session.id) }, onLongClick = { menuFor = r }, modifier = Modifier.animateItem().listSegment(i, g.rows.size))
                        }
                    }
                    if (ui.loadingMore) item("more") { LoadingRow() }
                }
            }
            menuFor?.let { r ->
                Box(Modifier.align(Alignment.Center)) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text(if (r.pinned) "Unpin" else "Pin") }, leadingIcon = { Sym(Ic.pin) }, onClick = { vm.togglePin(r.session.id); menuFor = null })
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Sym(Ic.editSquare) }, onClick = { renaming = r; menuFor = null })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Sym(Ic.delete) }, onClick = { deleting = r; menuFor = null })
                    }
                }
            }
        }
    }

    renaming?.let { r ->
        var text by remember(r.session.id) { mutableStateOf(r.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(r.session.id, text.trim()); renaming = null }, enabled = text.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this chat?") },
            text = { Text("\"${r.title}\" and its history are removed from your computer. Files the agent changed stay as they are.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(r.session.id); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A search well: pressed into the ground, the way every input is. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search chats") {
    val c = Fp.colors
    Row(modifier.height(44.dp).pressed(Radius.full, c.surfaceSunken).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(Ic.search, null, size = 18.dp, tint = c.inkMuted)
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = FpType.body, color = c.inkMuted, maxLines = 1)
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = FpType.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) FpIconButton(Ic.close, "Clear search", onClick = { onChange("") }, small = true)
    }
}
