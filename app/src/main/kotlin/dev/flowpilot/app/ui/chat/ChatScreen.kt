@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.chat

import dev.flowpilot.core.sync.catching

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.components.CenteredLoading
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.ErrorCard
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.LoadingRow
import dev.flowpilot.app.ui.components.ProjectShape
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.graph
import dev.flowpilot.core.chat.ChatEntry
import dev.flowpilot.core.chat.FeedItem
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.chat.feed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the reversed feed draws, newest first. Cards the user must act on sit at the very bottom. */
private sealed interface Row0 {
    val key: String
    data class Feed(val item: FeedItem) : Row0 { override val key get() = item.key }
    data class Permission(val p: dev.flowpilot.core.api.PermissionRequest) : Row0 { override val key get() = "p" + p.id }
    data class Question(val f: dev.flowpilot.core.api.Form) : Row0 { override val key get() = "f" + f.id }
    data object Waiting : Row0 { override val key get() = "waiting" }
    data class RunError(val e: dev.flowpilot.core.api.ApiError) : Row0 { override val key get() = "run-error" }
    data class Retrying(val info: dev.flowpilot.core.chat.RetryInfo) : Row0 { override val key get() = "retry" }
    data object Older : Row0 { override val key get() = "older" }
}

@Composable
fun ChatScreen(conn: ServerConnection, sessionID: String?, directory: String?, onBack: () -> Unit, onManageModels: () -> Unit) {
    val g = graph
    val vm: ChatViewModel = viewModel(key = "${conn.identity}:${sessionID ?: "new:$directory"}") { ChatViewModel(g, conn, sessionID, directory) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val settings by g.prefs.settings.collectAsStateWithLifecycle(dev.flowpilot.app.data.Settings())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var picker by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(Unit) {
        if (sessionID == null) { delay(300); catching { focus.requestFocus() }; keyboard?.show() }
    }

    val chat = ui.chat
    val feedCache = remember { dev.flowpilot.core.chat.FeedCache() }
    val feed by androidx.compose.runtime.produceState<List<dev.flowpilot.core.chat.FeedItem>>(emptyList(), chat.entries, chat.running) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { feedCache.feed(chat) }
    }
    val rows = remember(feed, chat.permissions, chat.forms, chat.retry, chat.error, chat.hasOlder, chat.running, settings.showReasoning) {
        buildList<Row0> {
            if (chat.hasOlder) add(Row0.Older)
            feed.forEach { item ->
                if (item is FeedItem.Reasoning && !settings.showReasoning) return@forEach
                add(Row0.Feed(item))
            }
            val lastIsUser = chat.entries.lastOrNull() is ChatEntry.User
            if (chat.running && lastIsUser) add(Row0.Waiting)
            chat.retry?.let { add(Row0.Retrying(it)) }
            val feedHasError = chat.entries.lastOrNull().let { it is ChatEntry.Assistant && it.error != null }
            if (!chat.running && chat.error != null && !feedHasError) add(Row0.RunError(chat.error!!))
            chat.permissions.forEach { add(Row0.Permission(it)) }
            chat.forms.forEach { add(Row0.Question(it)) }
        }.distinctBy { it.key }.asReversed() // A repeated key crashes LazyColumn; keep the first.
    }

    val list = rememberLazyListState()
    val atBottom by remember { derivedStateOf { list.firstVisibleItemIndex <= 1 } }
    var seenCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(atBottom, rows.size) { if (atBottom) seenCount = rows.size }
    val unseen = if (atBottom) 0 else (rows.size - seenCount).coerceAtLeast(0)
    LaunchedEffect(ui.sentTick) { if (ui.sentTick > 0) list.animateScrollToItem(0) }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= list.layoutInfo.totalItemsCount - 20) vm.loadOlder()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = ui.projectName.ifEmpty { ui.directory?.substringAfterLast('/') ?: "" }
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Sym(Ic.back, "Back") } },
                actions = {
                    if (!ui.isNew) Box {
                        IconButton(onClick = { menu = true }) { Sym(Ic.more, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Sym(Ic.editSquare) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Refresh") }, leadingIcon = { Sym(Ic.refresh) }, onClick = { menu = false; vm.load() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (ui.syncing || (ui.loadError != null && chat.entries.isNotEmpty())) {
                Text(
                    if (ui.loadError != null) "Showing saved conversation. ${ui.loadError}" else "Catching up…",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.loading && chat.entries.isEmpty() -> CenteredLoading()
                    ui.loadError != null && chat.entries.isEmpty() -> EmptyState("Couldn't open this chat", body = ui.loadError, icon = Ic.error, action = "Retry", onAction = { vm.load() })
                    rows.isEmpty() -> NewChatHero(ui.projectName.ifEmpty { if (directory == null) "No project" else directory.substringAfterLast('/') })
                    else -> LazyColumn(
                        state = list,
                        reverseLayout = true,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(rows, key = { it.key }, contentType = { it::class.simpleName }) { row ->
                            Box(Modifier.animateItem(fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec())) {
                                ChatRow(row, ui, vm)
                            }
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(chat.running || chat.needsYou > 0, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        WorkingPill(chat.runStartedAt, chat.needsYou)
                    }
                }
                JumpToLatest(!atBottom, unseen, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                    scope.launch { list.animateScrollToItem(0) }
                }
            }
            QueuedChips(chat.queued, onEdit = vm::editQueued, onCancel = vm::cancelQueued)
            Composer(
                text = draft,
                onText = vm::editDraft,
                running = chat.running,
                enabled = !ui.creating,
                agents = ui.agents,
                agent = ui.agent,
                onAgent = vm::selectAgent,
                modelLabel = ui.currentModel?.name ?: ui.model?.id ?: "Model",
                onModel = { picker = true },
                onSend = { queue -> vm.send(draft, queue) },
                onStop = vm::stop,
                focus = focus,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).navigationBarsPadding(),
            )
        }
    }

    if (picker) {
        ModelPickerSheet(
            all = ui.models,
            visible = ui.visibleModels,
            recent = ui.recent,
            selected = ui.model,
            onSelect = vm::selectModel,
            onManage = { picker = false; onManageModels() },
            onDismiss = { picker = false },
        )
    }
    if (renaming) {
        var text by remember { mutableStateOf(ui.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(text.trim()); renaming = false }, enabled = text.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatRow(row: Row0, ui: ChatUi, vm: ChatViewModel) {
    when (row) {
        is Row0.Feed -> when (val item = row.item) {
            is FeedItem.UserBubble -> UserBubble(item.entry) { vm.retry(item.entry) }
            is FeedItem.Text -> AssistantText(item.part)
            is FeedItem.Reasoning -> ReasoningRow(item.part)
            is FeedItem.Work -> WorkGroup(item.tools, item.live)
            is FeedItem.Stats -> StatsLine(item.entry, item.entry.model?.let { r -> ui.models.firstOrNull { it.id == r.id && it.providerID == r.providerID }?.name })
            is FeedItem.Error -> TurnError(item.error) { vm.retryLastTurn() }
            is FeedItem.Marker -> MarkerLine(item.entry)
            is FeedItem.Shell -> ShellEntry(item.entry)
        }
        is Row0.Permission -> PermissionCard(row.p, onDecide = { vm.reply(row.p, it) })
        is Row0.Question -> FormCard(row.f, onSubmit = { vm.answer(row.f, it) }, onDismiss = { vm.dismiss(row.f) })
        Row0.Waiting -> WaitingRow()
        is Row0.RunError -> TurnError(row.e) { vm.retryLastTurn() }
        is Row0.Retrying -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(row.info.at) { while (now < row.info.at) { delay(500); now = System.currentTimeMillis() } }
            val secs = ((row.info.at - now) / 1000).coerceAtLeast(0)
            Text(
                "Model error: ${row.info.error.message.ifBlank { row.info.error.type }}. Retrying" + (if (secs > 0) " in ${secs}s" else "…") + " (attempt ${row.info.attempt})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row0.Older -> if (ui.loadingOlder) LoadingRow() else Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun JumpToLatest(visible: Boolean, unseen: Int, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(), modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym(Ic.jump, "Jump to latest", size = 20.dp)
                if (unseen > 0) { Spacer(Modifier.width(6.dp)); Text("$unseen new", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun WaitingRow() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1500); show = true }
    if (show) Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("Starting…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else Spacer(Modifier.height(24.dp))
}

/** "Working · 0:42", or amber "Needs you · 1 approval" while something waits on the user. */
@Composable
private fun WorkingPill(startedAt: Long?, needsYou: Int) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val scheme = MaterialTheme.colorScheme
    val amber = needsYou > 0
    Surface(
        color = if (amber) scheme.tertiaryContainer else scheme.secondaryContainer,
        contentColor = if (amber) scheme.onTertiaryContainer else scheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 2.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.height(36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (amber) Sym(Ic.lock, null, size = 18.dp) else LoadingIndicator(Modifier.size(20.dp), color = scheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            val text = if (amber) "Needs you · " + if (needsYou == 1) "1 approval" else "$needsYou approvals"
            else "Working" + (startedAt?.let { " · " + Format.duration((now - it).coerceAtLeast(0)) } ?: "")
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NewChatHero(project: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ProjectShape(project, 72.dp, muted = project == "No project")
        Spacer(Modifier.height(16.dp))
        Text(project, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("What should the agent do?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
