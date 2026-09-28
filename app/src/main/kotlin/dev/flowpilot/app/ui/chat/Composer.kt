@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.core.api.Agent
import dev.flowpilot.core.chat.QueuedMessage

@Composable
fun QueuedChips(queued: List<QueuedMessage>, onEdit: (String) -> Unit, onCancel: (String) -> Unit) {
    if (queued.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        queued.forEach { q ->
            Surface(onClick = { onEdit(q.id) }, color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym(Ic.schedule, null, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("Sends when the agent finishes", style = MaterialTheme.typography.labelSmall)
                        Text(q.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.IconButton(onClick = { onCancel(q.id) }) { Sym(Ic.close, "Remove queued message", size = 18.dp) }
                }
            }
        }
    }
}

/**
 * The docked composer: text on top, then a toolbar with one mode-and-model chip and send. The chip reads
 * "Build · Model" and opens a single sheet for both. While the agent works, send becomes a split button
 * (send now steers, the arrow queues) beside stop.
 */
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    running: Boolean,
    enabled: Boolean,
    agents: List<Agent>,
    agent: String?,
    modelLabel: String,
    onPicker: () -> Unit,
    onSend: (queue: Boolean) -> Unit,
    onStop: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).heightIn(min = 24.dp)) {
                if (text.isEmpty()) {
                    Text(if (running) "Steer the agent, or queue a follow-up" else "Ask the agent to do something", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    enabled = enabled,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ModeModelChip(if (agents.isEmpty()) null else agentLabel(agent, agents), agent, modelLabel, onPicker)
                }
                Spacer(Modifier.width(8.dp))
                SendControl(hasText = text.isNotBlank(), running = running, enabled = enabled, onSend = onSend, onStop = onStop)
            }
        }
    }
}

/** "Build · Sonnet ▾" with the mode's icon: the current mode and model in one chip. Tapping opens the combined picker. */
@Composable
private fun ModeModelChip(mode: String?, agent: String?, model: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = scheme.surfaceContainerHighest, modifier = Modifier.height(36.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(if (mode != null) agentIcon(agent) else Ic.autoAwesome, null, size = 16.dp, tint = scheme.primary)
            Spacer(Modifier.width(6.dp))
            if (mode != null) {
                Text(mode, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(" · ", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
            Text(model, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Sym(Ic.dropDown, "Change mode and model", size = 20.dp)
        }
    }
}

@Composable
private fun SendControl(hasText: Boolean, running: Boolean, enabled: Boolean, onSend: (Boolean) -> Unit, onStop: () -> Unit) {
    val mode = when {
        running && hasText -> 2
        running -> 1
        else -> 0
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = mode,
        transitionSpec = { (scaleIn(motion.fastSpatialSpec()) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
        label = "send",
    ) { m ->
        when (m) {
            1 -> FilledIconButton(
                onClick = onStop,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
            ) { Sym(Ic.stop, "Stop") }
            2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onStop, modifier = Modifier.size(40.dp)) { Sym(Ic.stop, "Stop", size = 20.dp) }
                Spacer(Modifier.width(6.dp))
                var menu by remember { mutableStateOf(false) }
                Box {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(onClick = { onSend(false) }, enabled = enabled) {
                                Sym(Ic.arrowUp, null, size = 20.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("Send")
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(checked = menu, onCheckedChange = { menu = it }, enabled = enabled) {
                                Sym(Ic.expandMore, "More send options", size = 20.dp)
                            }
                        },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Column { Text("Send now"); Text("Steers the current turn", style = MaterialTheme.typography.bodySmall) } },
                            leadingIcon = { Sym(Ic.arrowUp) },
                            onClick = { menu = false; onSend(false) },
                        )
                        DropdownMenuItem(
                            text = { Column { Text("Send when finished"); Text("Queues it for after this turn", style = MaterialTheme.typography.bodySmall) } },
                            leadingIcon = { Sym(Ic.schedule) },
                            onClick = { menu = false; onSend(true) },
                        )
                    }
                }
            }
            else -> FilledIconButton(
                onClick = { onSend(false) },
                enabled = enabled && hasText,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) { Sym(Ic.arrowUp, "Send") }
        }
    }
}
