@file:OptIn(ExperimentalMaterial3Api::class)

package dev.flowpilot.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.data.ModelVisibility
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.chat.ChatUi
import dev.flowpilot.app.ui.chat.supporting
import dev.flowpilot.app.ui.components.CenteredLoading
import dev.flowpilot.app.ui.components.EmptyState
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.SectionHeader
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.friendly
import dev.flowpilot.app.ui.graph
import dev.flowpilot.app.ui.home.SearchField
import dev.flowpilot.core.api.Model
import kotlinx.coroutines.launch

/** Which models the picker shows. Changes save instantly; there is no Save button. */
@Composable
fun ModelsScreen(conn: ServerConnection, onBack: () -> Unit) {
    val g = graph
    val vis by g.prefs.visibility(conn.server.id).collectAsStateWithLifecycle(ModelVisibility())
    var models by remember { mutableStateOf<List<Model>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(conn) {
        runCatching { conn.client.models() }.onSuccess { models = it }.onFailure { error = it.friendly() }
    }
    fun visible(m: Model) = vis.isVisible(m.key, ChatUi.defaultVisible(m))
    fun set(keys: Collection<String>, v: Boolean) = scope.launch { g.prefs.setVisible(conn.server.id, keys, v) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Model visibility") }, navigationIcon = { IconButton(onClick = onBack) { Sym(Ic.back, "Back") } })
    }) { padding ->
        val list = models
        when {
            error != null -> EmptyState("Couldn't load models", body = error, icon = Ic.error, modifier = Modifier.padding(padding))
            list == null -> Column(Modifier.padding(padding)) { CenteredLoading() }
            list.isEmpty() -> EmptyState("No models yet", body = "Connect a provider in OpenCode on your computer.", icon = Ic.autoAwesome, modifier = Modifier.padding(padding))
            else -> {
                val groups = list.filter { query.isBlank() || it.name.contains(query, true) || it.providerID.contains(query, true) }
                    .groupBy { it.providerID }.toSortedMap()
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item("search") { SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = "Search models") }
                    groups.forEach { (provider, ms) ->
                        item("h$provider") {
                            val allOn = ms.all(::visible)
                            SectionHeader(provider.replaceFirstChar { it.uppercase() }) {
                                TextButton(onClick = { set(ms.map { it.key }, !allOn) }) { Text(if (allOn) "Hide all" else "Show all") }
                            }
                        }
                        items(ms.distinctBy { it.key }.sortedBy { it.name }, key = { it.key }) { m ->
                            val on = visible(m)
                            Row(Modifier.fillMaxWidth().clickable { set(listOf(m.key), !on) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sup = m.supporting()
                                    if (sup.isNotEmpty()) Text(sup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(16.dp))
                                Switch(checked = on, onCheckedChange = { set(listOf(m.key), it) })
                            }
                        }
                    }
                }
            }
        }
    }
}
