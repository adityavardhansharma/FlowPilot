@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.chat

import dev.flowpilot.app.ui.components.FpSpinner

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
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressed
import dev.flowpilot.app.ui.theme.raised
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
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
        // The person's own words: accent-soft, one tighter corner toward the edge they came from.
        Box(
            Modifier.widthIn(max = maxWidth).alpha(if (entry.pending) 0.7f else 1f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 8.dp))
                .background(Fp.colors.accentSoft),
        ) {
            SelectionContainer {
                Text(entry.text, style = FpType.bodyLg, color = Fp.colors.onAccentSoft, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        if (entry.files.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.files.take(4).forEach { f ->
                    val name = f.substringAfterLast('/')
                    val image = name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif") || f.startsWith("image/")
                    Row(Modifier.height(28.dp).pressed(Radius.full, Fp.colors.surfaceSunken).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym(if (image) Ic.image else Ic.file, null, size = 14.dp, tint = Fp.colors.inkMuted)
                        Spacer(Modifier.width(4.dp))
                        Text(name, style = FpType.caption, color = Fp.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                    }
                }
            }
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
            // Process, not the answer: a quiet well in ink-muted, never the accent.
            Text(
                part.text.substring(0, shown),
                style = FpType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = Fp.colors.inkMuted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).pressed(Radius.md, Fp.colors.surfaceSunken).padding(horizontal = 16.dp, vertical = 12.dp),
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
    Box(Modifier.fillMaxWidth().raised(Radius.lg, Fp.colors.surface)) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !live) { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live) FpSpinner(Modifier.size(18.dp)) else Sym(if (failed > 0) Ic.warning else Ic.check, null, size = 20.dp, tint = if (failed > 0) Fp.colors.danger else Fp.colors.success)
                Spacer(Modifier.width(10.dp))
                val header = if (live) {
                    val cur = tools.lastOrNull { it.status == ToolStatus.Running || it.status == ToolStatus.Streaming } ?: tools.last()
                    ToolDescriber.describe(cur).let { "${it.verb} ${it.target}".trim() }
                } else ToolDescriber.summary(tools)
                Text(header, style = FpType.label, color = Fp.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!live) Sym(if (open) Ic.expandLess else Ic.expandMore, if (open) "Collapse" else "Expand", size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = Fp.colors.line)
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
                    ToolStatus.Running, ToolStatus.Streaming -> FpSpinner(Modifier.size(18.dp))
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
        MarkerKind.AgentSwitched -> if (entry.text.contains("plan", ignoreCase = true)) Ic.checklist else Ic.build
        MarkerKind.ModelSwitched -> Ic.autoAwesome
        MarkerKind.Compaction -> Ic.unfoldLess
        MarkerKind.Moved -> Ic.folder
        MarkerKind.Synthetic -> Ic.info
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        HorizontalDivider(Modifier.weight(1f), color = Fp.colors.line)
        Spacer(Modifier.width(8.dp))
        Sym(icon, null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(entry.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f), color = Fp.colors.line)
    }
}

@Composable
fun ShellEntry(entry: ChatEntry.Shell) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("You ran" + (entry.exit?.let { " · exit ${it.toInt()}" } ?: ""), style = FpType.caption, color = Fp.colors.inkMuted)
        CodeBlock("$ " + entry.command + (entry.output?.let { "\n" + it.take(8000) } ?: ""), "sh", maxLines = 30)
    }
}
