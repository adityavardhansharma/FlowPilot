@file:OptIn(ExperimentalMaterial3Api::class)

package dev.flowpilot.app.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.data.LinkState
import dev.flowpilot.app.ui.components.BarTitle
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.ErrorCard
import dev.flowpilot.app.ui.components.FpActionsSheet
import dev.flowpilot.app.ui.components.FpButton
import dev.flowpilot.app.ui.components.FpButtonVariant
import dev.flowpilot.app.ui.components.FpChip
import dev.flowpilot.app.ui.components.FpDialog
import dev.flowpilot.app.ui.components.FpIconButton
import dev.flowpilot.app.ui.components.FpTextDialog
import dev.flowpilot.app.ui.components.FpTopBar
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.LoadingRow
import dev.flowpilot.app.ui.components.SectionHeader
import dev.flowpilot.app.ui.components.ServerChip
import dev.flowpilot.app.ui.components.SheetAction
import dev.flowpilot.app.ui.components.SkeletonRow
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.components.listSegment
import dev.flowpilot.app.ui.components.scrolled
import dev.flowpilot.app.ui.components.titleGone
import dev.flowpilot.app.ui.theme.FadeThrough
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressed
import dev.flowpilot.core.sync.catching
import dev.flowpilot.core.home.HomeFilter
import dev.flowpilot.core.home.ThreadRowModel

/**
 * Chats. The bar stays put (server, a small title, search, settings); the large title and the filters scroll with
 * the list, and the small title fades in once the large one is gone. "Needs you" comes first, then pinned, then by
 * day, each section one card. A long press opens the chat's actions.
 */
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
    var actionsFor by remember { mutableStateOf<ThreadRowModel?>(null) }
    var renaming by remember { mutableStateOf<ThreadRowModel?>(null) }
    var deleting by remember { mutableStateOf<ThreadRowModel?>(null) }
    var searching by rememberSaveable { mutableStateOf(ui.query.isNotEmpty()) }

    LaunchedEffect(ui.message) { ui.message?.let { showMessage(it); vm.consumeMessage() } }

    // Chats with an approval waiting that aren't on the loaded pages still join "Needs you".
    val waiting by vm.conn.pending.sessions.collectAsStateWithLifecycle()
    val groups = remember(ui.state, pinned, ui.filter, scratch, waiting) { ui.state.groups(pinned, scratch, ui.filter, waiting = waiting.values) }
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
    val scrolled by list.scrolled()
    val titleGone by list.titleGone(TITLE)

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val endSearch = {
        vm.setQuery("")
        focusManager.clearFocus()
        keyboard?.hide()
        searching = false
    }
    BackHandler(searching) { endSearch() }

    fun projectName(r: ThreadRowModel) = if (scratch != null && r.session.location.directory.startsWith(scratch)) "No project" else r.project?.displayName ?: r.session.location.directory.substringAfterLast('/')
    val open: (ThreadRowModel) -> Unit = { onOpenChat(it.session.id) }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        FpTopBar(scrolled) {
            FadeThrough(searching, Modifier.weight(1f), contentAlignment = Alignment.CenterStart, label = "homeBar") { s ->
                if (s) SearchBar(ui.query, vm::setQuery, onCancel = endSearch)
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.dp))
                    ServerChip(vm.conn.server.name, link, onClick = onSettings)
                    Spacer(Modifier.width(12.dp))
                    BarTitle("Chats", titleGone, Modifier.weight(1f))
                    FpIconButton(Ic.search, "Search chats", onClick = { searching = true })
                    FpIconButton(Ic.settings, "Settings", onClick = onSettings)
                }
            }
        }
        val pull = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            state = pull,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pull, isRefreshing = ui.refreshing, modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Fp.colors.surfaceRaised, color = Fp.colors.accent,
                )
            },
        ) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp), modifier = Modifier.fillMaxSize()) {
                if (!searching) {
                    item(TITLE) {
                        Text("Chats", style = FpType.display, color = Fp.colors.ink, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp))
                    }
                    item("filters") {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeFilter.entries.forEach { f -> FpChip(f.label, onClick = { vm.setFilter(f) }, selected = ui.filter == f) }
                        }
                    }
                }
                if (link == LinkState.Unauthorized) item("unauthorized") {
                    ErrorCard("Your pairing expired", "Scan a new code to reconnect.", Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), action = "Pair", onAction = onSettings)
                }
                when {
                    query.isNotEmpty() -> {
                        if (searchRows.isEmpty()) item("noMatch") { Empty { EmptyState("No chats match \"$query\".", icon = Ic.search) } }
                        else {
                            item("qh") { Spacer(Modifier.height(12.dp)) }
                            itemsIndexed(searchRows, key = { _, r -> "q" + r.session.id }) { i, r ->
                                ThreadRow(r, projectName(r), onClick = { open(r) }, onLongClick = { actionsFor = r }, modifier = Modifier.arrive(this).listSegment(i, searchRows.size))
                            }
                        }
                    }
                    ui.firstLoad && groups.isEmpty() -> {
                        item("skh") { Spacer(Modifier.height(16.dp)) }
                        items(6, key = { "sk$it" }, contentType = { "skeleton" }) { i -> SkeletonRow(Modifier.listSegment(i, 6), widths = SKELETON[i % SKELETON.size]) }
                    }
                    groups.isEmpty() && ui.error != null -> item("offlineEmpty") {
                        Empty {
                            EmptyState(
                                "Can't reach your computer", body = ui.error, icon = Ic.wifiOff, action = "Retry", onAction = { vm.refresh() },
                                secondary = "Change computer", onSecondary = onSettings,
                            )
                        }
                    }
                    groups.isEmpty() -> item("empty") {
                        Empty {
                            EmptyState(
                                if (ui.filter == HomeFilter.All) "No chats yet" else "Nothing here",
                                body = if (ui.filter == HomeFilter.All) "Start one and the agent gets to work on your computer." else null,
                                icon = Ic.chat,
                                action = if (ui.filter == HomeFilter.All) "New chat" else null,
                                onAction = onNewChat,
                            )
                        }
                    }
                    else -> {
                        if (ui.error != null) item("offline") {
                            Text(
                                "Offline. Showing saved chats.",
                                style = FpType.caption,
                                color = Fp.colors.inkMuted,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                            )
                        }
                        groups.forEach { g ->
                            item("h" + g.label, contentType = "header") {
                                SectionHeader(g.label, Modifier.arrive(this), color = if (g.attention) Fp.colors.amber else Fp.colors.inkMuted)
                            }
                            // Keyed by chat alone, so a chat that moves between sections slides there instead of blinking.
                            itemsIndexed(g.rows, key = { _, r -> r.session.id }, contentType = { _, _ -> "row" }) { i, r ->
                                ThreadRow(r, projectName(r), onClick = { open(r) }, onLongClick = { actionsFor = r }, modifier = Modifier.arrive(this).listSegment(i, g.rows.size))
                            }
                        }
                        if (ui.loadingMore) item("more") { LoadingRow() }
                    }
                }
            }
        }
    }

    actionsFor?.let { r ->
        FpActionsSheet(
            title = r.title,
            actions = listOf(
                SheetAction(Ic.pin, if (r.pinned) "Unpin" else "Pin") { vm.togglePin(r.session.id) },
                SheetAction(Ic.editSquare, "Rename") { renaming = r },
                SheetAction(Ic.delete, "Delete", danger = true) { deleting = r },
            ),
            onDismiss = { actionsFor = null },
        )
    }
    renaming?.let { r ->
        FpTextDialog("Rename chat", r.title, "Rename", onDismiss = { renaming = null }) { vm.rename(r.session.id, it); renaming = null }
    }
    deleting?.let { r ->
        FpDialog(
            title = "Delete this chat?",
            body = "\"${r.title}\" and its history are removed from your computer. Files the agent changed stay as they are.",
            icon = Ic.delete,
            danger = true,
            confirm = "Delete",
            onConfirm = { vm.delete(r.session.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

private const val TITLE = "title"
private val SKELETON = listOf(0.62f to 0.38f, 0.48f to 0.30f, 0.70f to 0.42f, 0.55f to 0.34f)

/** Rows and headers arrive, leave and move on spring-settle; opacity uses the fade durations. */
private fun Modifier.arrive(scope: LazyItemScope): Modifier = with(scope) {
    animateItem(fadeInSpec = Motion.fadeIn(), placementSpec = Motion.settle(), fadeOutSpec = Motion.fadeOut())
}

/** An empty state inside the list: it fills most of the screen under the title, so it sits where the rows would. */
@Composable
private fun LazyItemScope.Empty(content: @Composable () -> Unit) {
    Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth()) { content() }
}

/** The bar while searching: a well that takes focus, and Cancel to leave. */
@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { catching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchField(value, onChange, Modifier.weight(1f).focusRequester(focus))
        Spacer(Modifier.width(4.dp))
        FpButton("Cancel", onCancel, variant = FpButtonVariant.Ghost, small = true)
    }
}

/** A search well: pressed into the ground, the way every input is. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search chats") {
    val c = Fp.colors
    BasicTextField(
        value, onChange,
        singleLine = true,
        textStyle = FpType.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier,
        decorationBox = { inner ->
            Row(Modifier.height(44.dp).pressed(Radius.full, c.surfaceSunken).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym(Ic.search, null, size = 18.dp, tint = c.inkMuted)
                Spacer(Modifier.size(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = FpType.body, color = c.inkMuted, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) FpIconButton(Ic.close, "Clear search", onClick = { onChange("") }, small = true)
                else Spacer(Modifier.size(32.dp))
            }
        },
    )
}
