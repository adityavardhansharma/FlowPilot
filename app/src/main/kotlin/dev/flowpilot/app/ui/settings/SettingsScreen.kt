@file:OptIn(ExperimentalMaterial3Api::class)

package dev.flowpilot.app.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import dev.flowpilot.app.data.SavedServer
import dev.flowpilot.app.data.Settings
import dev.flowpilot.app.data.ThemeMode
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.ListGroup
import dev.flowpilot.app.ui.components.SectionHeader
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.graph
import dev.flowpilot.app.ui.theme.CodeSmallStyle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onPair: () -> Unit, onModels: () -> Unit) {
    val g = graph
    val servers by g.prefs.servers.collectAsStateWithLifecycle(emptyList())
    val current by g.connection.collectAsStateWithLifecycle()
    val settings by g.prefs.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    var forgetting by remember { mutableStateOf<SavedServer?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Sym(Ic.back, "Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SectionHeader("Computer")
            ListGroup {
                servers.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    val isCurrent = s.id == current?.server?.id
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !isCurrent) { scope.launch { g.prefs.selectServer(s.id) } }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym(Ic.dns, null, tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name + if (isCurrent) " · connected" else "", style = MaterialTheme.typography.bodyLarge)
                            Text(s.baseUrl + (s.version?.let { " · OpenCode $it" } ?: ""), style = CodeSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { forgetting = s }) { Sym(Ic.delete, "Forget ${s.name}") }
                    }
                }
                if (servers.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                Row(Modifier.fillMaxWidth().clickable(onClick = onPair).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym(Ic.qr, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text("Pair another computer", style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionHeader("Models")
            ListGroup {
                NavRow(Ic.tune, "Model visibility", "Choose which models the picker shows", onModels)
            }

            SectionHeader("Chat")
            ListGroup {
                SwitchRow("Show thinking", "Keep the agent's reasoning in the feed, collapsed", settings.showReasoning) { v ->
                    scope.launch { g.prefs.updateSettings { it.copy(showReasoning = v) } }
                }
            }

            SectionHeader("Appearance")
            ListGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = settings.themeMode == m,
                                onClick = { scope.launch { g.prefs.updateSettings { it.copy(themeMode = m) } } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.name) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    SwitchRow("Dynamic color", "Use colors from your wallpaper instead of jade", settings.dynamicColor) { v ->
                        scope.launch { g.prefs.updateSettings { it.copy(dynamicColor = v) } }
                    }
                }
            }

            SectionHeader("About")
            ListGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("FlowPilot ${dev.flowpilot.app.BuildInfo.VERSION}", style = MaterialTheme.typography.bodyLarge)
                    Text("A phone client for OpenCode 2.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.padding(PaddingValues(bottom = 24.dp)))
        }
    }

    forgetting?.let { s ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Forget ${s.name}?") },
            text = { Text("FlowPilot removes its saved pairing for this computer. Chats stay on the computer.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { g.prefs.removeServer(s.id); g.cache.clear(s.id) }; forgetting = null }) {
                    Text("Forget", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NavRow(icon: Int, title: String, body: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Sym(Ic.chevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange, thumbContent = if (checked) { { Sym(Ic.check, null, size = 16.dp) } } else null)
    }
}
