@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.SectionHeader
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.home.SearchField
import dev.flowpilot.core.api.Agent
import dev.flowpilot.core.api.Model
import dev.flowpilot.core.api.ModelRef
import dev.flowpilot.core.chat.Format

fun Model.supporting(): String = buildList {
    if (limit.context > 0) add(Format.context(limit.context) + " context")
    cost.firstOrNull()?.let { c -> if (c.input > 0 || c.output > 0) add("$" + trim(c.input) + " / $" + trim(c.output) + " per M") }
    if (capabilities.input.contains("image")) add("vision")
}.joinToString(" · ")

private fun trim(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0')

/** Icon for an agent: the hammer for Build, the checklist for Plan, sparkles for anything custom. */
fun agentIcon(id: String?): Int = when (id?.lowercase()) {
    "build" -> Ic.build
    "plan" -> Ic.checklist
    else -> Ic.autoAwesome
}

fun agentLabel(id: String?, agents: List<Agent>): String =
    (agents.firstOrNull { it.id == id }?.name ?: id ?: "Agent").replaceFirstChar { it.uppercase() }

/**
 * One sheet for how the agent works: the mode (Build, Plan, or a custom primary agent) on top, then the model.
 * Model list: search, recent, then providers. Only models visible in Settings show unless "Show all" is on.
 * Variants (reasoning effort) appear under the selected row. Switching mode keeps the sheet open, so both can be
 * set in one visit.
 */
@Composable
fun ModelPickerSheet(
    agents: List<Agent>,
    agent: String?,
    onAgent: (String) -> Unit,
    all: List<Model>,
    visible: List<Model>,
    recent: List<ModelRef>,
    selected: ModelRef?,
    onSelect: (ModelRef) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val pool = if (showAll || query.isNotBlank()) all else visible
    val filtered = pool.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) || it.providerID.contains(query, true) }
    val recentModels = if (query.isBlank()) recent.mapNotNull { r -> all.firstOrNull { it.id == r.id && it.providerID == r.providerID } } else emptyList()
    val groups = filtered.groupBy { it.providerID }.toSortedMap()

    val pick: (ModelRef, Boolean) -> Unit = { ref, close -> onSelect(ref); if (close) onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth()) {
            if (agents.isNotEmpty()) {
                Text("Mode", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                AgentChoice(agents, agent, onAgent, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                agents.firstOrNull { it.id == agent }?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(bottom = 12.dp))
            }
            Text("Model", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), placeholder = "Search models")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                if (recentModels.isNotEmpty()) {
                    item("rh") { SectionHeader("Recent") }
                    items(recentModels, key = { "r" + it.key }) { m -> ModelRow(m, selected, pick) }
                }
                groups.forEach { (provider, models) ->
                    item("h$provider") { SectionHeader(provider.replaceFirstChar { it.uppercase() }) }
                    items(models.distinctBy { it.key }.sortedBy { it.name }, key = { it.key }) { m -> ModelRow(m, selected, pick) }
                }
                if (filtered.isEmpty()) item("empty") {
                    Text(
                        if (all.isEmpty()) "No models yet. Connect a provider in OpenCode on your computer." else "No models match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                item("foot") {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    if (query.isBlank() && all.size > visible.size) FooterRow(if (showAll) Ic.visibilityOff else Ic.visibility, if (showAll) "Show only visible models" else "Show all models") { showAll = !showAll }
                    FooterRow(Ic.tune, "Manage models") { onManage() }
                    Spacer(Modifier.navigationBarsPadding().padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun FooterRow(icon: Int, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ModelRow(m: Model, selected: ModelRef?, onPick: (ModelRef, Boolean) -> Unit) {
    val isSelected = selected != null && selected.id == m.id && selected.providerID == m.providerID
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onPick(m.ref.copy(variant = if (isSelected) selected?.variant else null), m.variants.isEmpty() || isSelected) }.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sup = m.supporting()
                if (sup.isNotEmpty()) Text(sup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) Sym(Ic.check, "Selected", size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        }
        if (isSelected && m.variants.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected?.variant == null, onClick = { onPick(m.ref, true) }, label = { Text("Default") })
                m.variants.forEach { v ->
                    FilterChip(selected = selected?.variant == v.id, onClick = { onPick(m.ref.copy(variant = v.id), true) }, label = { Text(v.id.replaceFirstChar { it.uppercase() }) })
                }
            }
        }
    }
}

/** Connected toggle buttons, one per primary agent, filling the row; scrolls sideways past three. */
@Composable
private fun AgentChoice(agents: List<Agent>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val fill = agents.size <= 3
    Row(
        (if (fill) modifier else modifier.horizontalScroll(rememberScrollState())),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        agents.forEachIndexed { i, a ->
            ToggleButton(
                checked = a.id == selected,
                onCheckedChange = { if (a.id != selected) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(a.id) } },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    agents.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = if (fill) Modifier.weight(1f) else Modifier,
            ) {
                Sym(agentIcon(a.id), null, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(a.name.replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
