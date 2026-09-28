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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TopAppBarDefaults
import dev.flowpilot.app.ui.components.FpButton
import dev.flowpilot.app.ui.components.FpIconButton
import dev.flowpilot.app.ui.components.FpSegmented
import dev.flowpilot.app.ui.components.FpSwitch
import dev.flowpilot.app.ui.components.Segment
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
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
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var forgetting by remember { mutableStateOf<SavedServer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = FpType.titleLg) },
                navigationIcon = { FpIconButton(Ic.back, "Back", onClick = onBack, modifier = Modifier.padding(start = 4.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Fp.colors.ground, scrolledContainerColor = Fp.colors.ground),
            )
        },
        containerColor = Fp.colors.ground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SectionHeader("Computer")
            ListGroup {
                servers.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = Fp.colors.line)
                    val isCurrent = s.id == current?.server?.id
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !isCurrent) { scope.launch { g.prefs.selectServer(s.id) } }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym(Ic.dns, null, tint = if (isCurrent) Fp.colors.accent else Fp.colors.inkMuted)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name + if (isCurrent) " · connected" else "", style = FpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Fp.colors.ink)
                            Text(s.baseUrl + (s.version?.let { " · OpenCode $it" } ?: ""), style = CodeSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FpIconButton(Ic.delete, "Forget ${s.name}", onClick = { forgetting = s })
                    }
                }
                if (servers.isNotEmpty()) HorizontalDivider(color = Fp.colors.line)
                Row(Modifier.fillMaxWidth().clickable(onClick = onPair).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym(Ic.qr, null, tint = Fp.colors.inkMuted)
                    Spacer(Modifier.width(16.dp))
                    Text("Pair another computer", style = FpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Fp.colors.ink)
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
                    Text("Theme", style = FpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Fp.colors.ink)
                    Text(
                        when (settings.themeMode) {
                            ThemeMode.System -> "Follows your phone: Stone by day, Basalt at night."
                            ThemeMode.Light -> "Stone: warm light surfaces."
                            ThemeMode.Dark -> "Basalt: dark surfaces, easy on the eyes at night."
                        },
                        style = FpType.caption, color = Fp.colors.inkMuted,
                    )
                    FpSegmented(
                        listOf(Segment(ThemeMode.System, "System"), Segment(ThemeMode.Light, "Light"), Segment(ThemeMode.Dark, "Dark")),
                        settings.themeMode,
                        { m -> scope.launch { g.prefs.updateSettings { it.copy(themeMode = m) } } },
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }

            SectionHeader("About")
            ListGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("FlowPilot ${dev.flowpilot.app.BuildConfig.VERSION_NAME}", style = FpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Fp.colors.ink)
                    Text("A phone client for OpenCode 2.", style = FpType.caption, color = Fp.colors.inkMuted)
                    Spacer(Modifier.height(12.dp))
                    FpButton("Copy connection diagnostics", small = true, icon = Ic.copy, onClick = {
                        val report = "FlowPilot ${dev.flowpilot.app.BuildConfig.VERSION_NAME}\nServer ${current?.serverVersion?.value ?: "unknown"}\nConnection ${current?.state?.value}\n" + dev.flowpilot.core.sync.Diagnostics.export()
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                    })
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
        Sym(icon, null, tint = Fp.colors.inkMuted)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Fp.colors.ink)
            if (body != null) Text(body, style = FpType.caption, color = Fp.colors.inkMuted)
        }
        Sym(Ic.chevronRight, null, tint = Fp.colors.inkMuted)
    }
}

@Composable
fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FpType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = Fp.colors.ink)
            if (body != null) Text(body, style = FpType.caption, color = Fp.colors.inkMuted)
        }
        Spacer(Modifier.width(16.dp))
        FpSwitch(checked, onChange)
    }
}
