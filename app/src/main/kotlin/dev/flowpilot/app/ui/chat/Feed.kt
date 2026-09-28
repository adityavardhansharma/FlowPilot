@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.components.CodeBlock
import dev.flowpilot.app.ui.components.DiffView
import dev.flowpilot.app.ui.components.ErrorCard
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.MarkdownText
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.theme.CodeFamily
import dev.flowpilot.app.ui.theme.CodeSmallStyle
import dev.flowpilot.app.ui.theme.code
import dev.flowpilot.core.api.ApiError
import dev.flowpilot.core.chat.ChatEntry
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.chat.MarkerKind
import dev.flowpilot.core.chat.Part
import dev.flowpilot.core.chat.ToolDescriber
import dev.flowpilot.core.chat.ToolStatus
import kotlinx.coroutines.delay

@Composable
fun UserBubble(entry: ChatEntry.User, onRetry: () -> Unit) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.85f).dp
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = maxWidth).alpha(if (entry.pending) 0.7f else 1f),
        ) {
            SelectionContainer {
                Text(entry.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        if (entry.files.isNotEmpty()) {
            Text(entry.files.joinToString { it.substringAfterLast('/') }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if (entry.failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym(Ic.error, null, size = 16.dp, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("Not sent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun AssistantText(part: Part.Text) {
    val shown = rememberStreamReveal(part.text, part.streaming)
    val revealing = shown < part.text.length
    SelectionContainer {
        MarkdownText(if (revealing) part.text.substring(0, shown) else part.text, streaming = part.streaming || revealing, modifier = Modifier.fillMaxWidth())
    }
}

/** "Thinking · 4s", collapsed by default. The raw reasoning expands in place. */
@Composable
fun ReasoningRow(part: Part.Reasoning) {
    var open by rememberSaveable(part.key) { mutableStateOf(false) }
    val seconds = elapsed(part.started, part.completed, part.streaming)
    val t = rememberInfiniteTransition(label = "think")
    val shimmer by t.animateFloat(1f, 0.45f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Row(
            Modifier.clickable { open = !open }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(Ic.psychology, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                if (part.streaming) "Thinking" + (seconds?.let { " · $it" } ?: "") else "Thought" + (seconds?.let { " for $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (part.streaming) shimmer else 1f),
            )
            Sym(if (open) Ic.expandLess else Ic.expandMore, if (open) "Hide thinking" else "Show thinking", size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            val shown = rememberStreamReveal(part.text, part.streaming)
            Text(
                part.text.substring(0, shown),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun elapsed(start: Long?, end: Long?, live: Boolean): String? {
    if (start == null) return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (live) LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val ms = (end ?: now) - start
    return if (ms < 1000) null else "${ms / 1000}s"
}

/**
 * Consecutive tool calls. Live: the header reads the running tool in the present tense and every row is shown.
 * Done: it collapses to a past-tense summary that expands on tap.
 */
@Composable
fun WorkGroup(tools: List<Part.Tool>, live: Boolean) {
    var open by rememberSaveable(tools.first().id) { mutableStateOf(false) }
    val expanded = live || open
    val failed = tools.count { it.status == ToolStatus.Error }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !live) { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live) LoadingIndicator(Modifier.size(20.dp)) else Sym(if (failed > 0) Ic.warning else Ic.checkCircle, null, size = 20.dp, tint = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                val header = if (live) {
                    val cur = tools.lastOrNull { it.status == ToolStatus.Running || it.status == ToolStatus.Streaming } ?: tools.last()
                    ToolDescriber.describe(cur).let { "${it.verb} ${it.target}".trim() }
                } else ToolDescriber.summary(tools)
                Text(header, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!live) Sym(if (open) Ic.expandLess else Ic.expandMore, if (open) "Collapse" else "Expand", size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.padding(vertical = 4.dp)) { tools.forEach { ToolRow(it) } }
            }
        }
    }
}

@Composable
fun ToolRow(tool: Part.Tool) {
    val text = ToolDescriber.describe(tool)
    val failed = tool.status == ToolStatus.Error
    var open by rememberSaveable(tool.id) { mutableStateOf(failed) }
    LaunchedEffect(failed) { if (failed) open = true }
    val scheme = MaterialTheme.colorScheme
    val hasDetail = tool.output != null || tool.error != null || text.changes.any { it.patch != null } || (tool.input?.get("command") != null)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = hasDetail) { open = !open }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when (tool.status) {
                    ToolStatus.Running, ToolStatus.Streaming -> LoadingIndicator(Modifier.size(18.dp))
                    ToolStatus.Error -> Sym(Ic.error, "Failed", size = 18.dp, tint = scheme.error)
                    ToolStatus.Completed -> Sym(Ic.tool(text.icon), null, size = 18.dp, tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                buildAnnotatedString {
                    append(text.verb)
                    if (text.target.isNotEmpty()) {
                        append(" ")
                        if (text.code) withStyle(SpanStyle(fontFamily = CodeFamily)) { append(text.target) } else append(text.target)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) scheme.error else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (text.additions > 0 || text.deletions > 0) {
                Text("+${text.additions}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.code.diffAddInk)
                Spacer(Modifier.width(4.dp))
                Text("−${text.deletions}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.code.diffRemoveInk)
            } else if (text.meta != null) {
                Text(text.meta!!, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(open && hasDetail, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val command = (tool.input?.get("command") as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (command != null) CodeBlock(command, "sh", maxLines = 12)
                text.changes.forEach { c ->
                    if (c.patch != null) {
                        Text(c.file, style = CodeSmallStyle, color = scheme.onSurfaceVariant)
                        DiffView(c.patch!!, maxLines = 200)
                    }
                }
                tool.error?.let { Text(it.message.ifBlank { it.type }, style = MaterialTheme.typography.bodyMedium, color = scheme.error) }
                if (text.changes.isEmpty() && !tool.output.isNullOrBlank()) CodeBlock(tool.output!!.take(8000), "output", maxLines = 24)
            }
        }
    }
}

/** "Claude Opus 5 · 12.4k tokens · $0.42 · 1m 12s", under a finished turn. */
@Composable
fun StatsLine(entry: ChatEntry.Assistant, modelName: String?) {
    val parts = buildList {
        (modelName ?: entry.model?.id)?.let { add(it) }
        entry.tokens?.total?.takeIf { it > 0 }?.let { add(Format.tokens(it) + " tokens") }
        entry.cost?.takeIf { it > 0 }?.let { add(Format.cost(it)) }
        if (entry.completed != null && entry.completed!! > entry.created) add(Format.duration(entry.completed!! - entry.created))
    }
    if (parts.isNotEmpty()) {
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TurnError(error: ApiError, onRetry: () -> Unit) {
    val body = when {
        error.status == 429 || error.message.contains("rate", ignoreCase = true) -> "The model returned an error: rate limit reached. Try again in a minute or pick another model."
        error.message.isNotBlank() -> "The model returned an error: ${error.message}"
        else -> "The turn failed (${error.type})."
    }
    ErrorCard("Turn failed", body, onAction = onRetry)
}

@Composable
fun MarkerLine(entry: ChatEntry.Marker) {
    val icon = when (entry.kind) {
        MarkerKind.Stopped -> Ic.stopCircle
        MarkerKind.Failed -> Ic.error
        MarkerKind.AgentSwitched -> Ic.build
        MarkerKind.ModelSwitched -> Ic.autoAwesome
        MarkerKind.Compaction -> Ic.unfoldLess
        MarkerKind.Moved -> Ic.folder
        MarkerKind.Synthetic -> Ic.info
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Spacer(Modifier.width(8.dp))
        Sym(icon, null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(entry.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
fun ShellEntry(entry: ChatEntry.Shell) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("You ran" + (entry.exit?.let { " · exit ${it.toInt()}" } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CodeBlock("$ " + entry.command + (entry.output?.let { "\n" + it.take(8000) } ?: ""), "sh", maxLines = 30)
    }
}
