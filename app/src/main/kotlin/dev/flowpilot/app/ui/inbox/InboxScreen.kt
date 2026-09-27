package dev.flowpilot.app.ui.inbox

import dev.flowpilot.core.sync.catching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.data.Ask
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.chat.FormCard
import dev.flowpilot.app.ui.chat.PermissionCard
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.friendly
import kotlinx.coroutines.launch

/** Every approval and question across chats, oldest first. */
@Composable
fun InboxScreen(conn: ServerConnection, contentPadding: PaddingValues, onOpenChat: (String) -> Unit, showMessage: (String) -> Unit) {
    val asks by conn.pending.asks.collectAsStateWithLifecycle()
    val error by conn.pending.error.collectAsStateWithLifecycle()
    val sessions by conn.pending.sessions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (asks.isEmpty()) {
        EmptyState(if (error == null) "Nothing needs you" else "Couldn’t refresh approvals", body = error ?: "The agents will ask here when they do.", icon = Ic.inbox, modifier = Modifier.padding(contentPadding))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (error != null) item("error") { Text("Showing saved requests. $error", style = MaterialTheme.typography.bodySmall) }
        item("title") { Text("Inbox", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp)) }
        items(asks.distinctBy { it.id }, key = { it.id }) { ask ->
            val title = sessions[ask.sessionID]?.title?.takeIf { it.isNotBlank() } ?: "Chat"
            val fail: (Throwable) -> Unit = { showMessage("Couldn't answer. ${it.friendly()}") }
            androidx.compose.foundation.layout.Column(Modifier.animateItem()) {
                when (ask) {
                    is Ask.Permission -> PermissionCard(ask.request, onDecide = { d -> scope.launch { catching { conn.pending.reply(ask.request, d) }.onFailure(fail) } }, chatTitle = title)
                    is Ask.Question -> FormCard(
                        ask.form,
                        onSubmit = { a -> scope.launch { catching { conn.pending.answer(ask.form, a) }.onFailure(fail) } },
                        onDismiss = { scope.launch { catching { conn.pending.dismiss(ask.form) }.onFailure(fail) } },
                        chatTitle = title,
                    )
                }
                TextButton(onClick = { onOpenChat(ask.sessionID) }) { Text("Open chat") }
            }
        }
    }
}
