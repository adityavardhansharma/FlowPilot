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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import dev.flowpilot.app.ui.components.FpChip
import dev.flowpilot.app.ui.components.FpIconButton
import dev.flowpilot.app.ui.components.Keycap
import dev.flowpilot.app.ui.components.SendKey
import dev.flowpilot.app.ui.components.SendState
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Motion
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressable
import dev.flowpilot.app.ui.theme.pressed
import dev.flowpilot.app.ui.theme.raised
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.flowpilot.app.ui.theme.CodeStyle
import dev.flowpilot.core.api.CommandInfo
import dev.flowpilot.core.chat.ComposerInput
import dev.flowpilot.core.sync.catching
import dev.flowpilot.core.api.Agent
import dev.flowpilot.core.chat.QueuedMessage

@Composable
fun QueuedChips(queued: List<QueuedMessage>, onEdit: (String) -> Unit, onCancel: (String) -> Unit) {
    if (queued.isEmpty()) return
    val c = Fp.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        queued.forEach { q ->
            Row(
                Modifier.fillMaxWidth().pressable(Radius.lg, c.surface, { onEdit(q.id) }, label = "Edit queued message").padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym(Ic.schedule, null, size = 18.dp, tint = c.accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text("Sends when the agent finishes", style = FpType.caption, color = c.inkMuted)
                    Text(q.text, style = FpType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FpIconButton(Ic.close, "Remove queued message", onClick = { onCancel(q.id) })
            }
        }
    }
}

/**
 * The docked composer: attachment chips and suggestions on top, then the text, then a toolbar with +, one
 * mode-and-model chip and send. The chip reads "Build · Model" and opens a single sheet for both. While the agent
 * works, send becomes a split button (send now steers, the arrow queues) beside stop.
 *
 * Shortcuts, as in OpenCode's own clients: `/` at the start lists slash commands, `@` lists files to add as
 * context, and `!` in an empty box switches to running a shell command. The + menu offers the same, plus photos.
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
    shell: Boolean,
    onShell: (Boolean) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (String) -> Unit,
    commands: List<CommandInfo>,
    fileMatches: List<String>,
    onFileQuery: (String?) -> Unit,
    onFilePicked: (String) -> Unit,
    onPickImages: () -> Unit,
    folder: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = Fp.colors
    // The field keeps its own cursor so shortcuts know what is being typed; text set from outside (sent, restored,
    // a queued message pulled back) moves the cursor to the end.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    // [text] comes back through the view model's flow a frame or so after each keystroke. Those echoes are
    // recognised and dropped; anything else is a change from outside and replaces the field.
    val echoes = remember { ArrayDeque<String>() }
    LaunchedEffect(text) {
        val echo = echoes.indexOf(text)
        if (echo >= 0) repeat(echo + 1) { echoes.removeFirst() }
        else {
            echoes.clear()
            if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
        }
    }
    fun set(value: TextFieldValue) {
        val changed = value.text != field.text
        field = value
        if (changed) {
            echoes.addLast(value.text)
            if (echoes.size > 64) echoes.removeFirst()
            onText(value.text)
        }
    }

    val trigger = if (shell) null else ComposerInput.trigger(field.text, field.selection.start)
    val mentionQuery = (trigger as? ComposerInput.Trigger.Mention)?.query
    LaunchedEffect(mentionQuery) { onFileQuery(mentionQuery) }
    val commandMatches = (trigger as? ComposerInput.Trigger.Command)?.let { t ->
        commands.filter { it.name.startsWith(t.query, true) } + commands.filter { !it.name.startsWith(t.query, true) && it.name.contains(t.query, true) }
    }.orEmpty().take(6)

    fun pickCommand(name: String) {
        val rest = field.text.substring(field.selection.start.coerceIn(0, field.text.length)).trimStart()
        val next = "/$name " + rest
        set(TextFieldValue(next, TextRange(name.length + 2)))
    }
    fun pickFile(path: String) {
        val t = trigger as? ComposerInput.Trigger.Mention ?: return
        val (next, cursor) = ComposerInput.completeMention(field.text, field.selection.start, t.start, path)
        set(TextFieldValue(next, TextRange(cursor)))
        onFilePicked(path)
        onFileQuery(null)
    }
    fun insertAtCursor(token: String) {
        val at = field.selection.start.coerceIn(0, field.text.length)
        val pad = if (at > 0 && !field.text[at - 1].isWhitespace()) " " else ""
        set(TextFieldValue(field.text.substring(0, at) + pad + token + field.text.substring(at), TextRange(at + pad.length + token.length)))
    }

    // The one object that is always there floats on shadow-lift with radius-xl.
    Box(modifier.fillMaxWidth().raised(Radius.xl, scheme.surface, lift = true)) {
        Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            when {
                commandMatches.isNotEmpty() -> Suggestions(commandMatches.map { Suggestion(it.name, "/" + it.name, it.description, Ic.slash) }, ::pickCommand)
                mentionQuery != null && fileMatches.isNotEmpty() -> Suggestions(fileMatches.map { Suggestion(it, it.substringAfterLast('/'), it.substringBeforeLast('/', "").ifEmpty { null }, Ic.file) }, ::pickFile)
            }
            if (attachments.isNotEmpty()) AttachmentRow(attachments, onRemoveAttachment)
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (shell) {
                    // Amber: the command runs on the person's computer.
                    Row(
                        Modifier.clip(Radius.sm).background(scheme.amberSoft).clickable(onClickLabel = "Leave shell mode") { onShell(false) }
                            .padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym(Ic.terminal, null, size = 14.dp, tint = scheme.onAmberSoft)
                        Spacer(Modifier.width(4.dp))
                        Text("shell", style = CodeStyle.copy(fontSize = 12.sp), color = scheme.onAmberSoft)
                        Sym(Ic.close, "Leave shell mode", size = 14.dp, tint = scheme.onAmberSoft)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (field.text.isEmpty()) {
                        Text(
                            when {
                                shell -> "Run a command" + (folder?.let { " in ${it.trimEnd('/').substringAfterLast('/')}" } ?: "")
                                running -> "Steer the agent, or queue a follow-up"
                                else -> "Ask the agent, / for commands, @ for files"
                            },
                            style = FpType.bodyLg, color = scheme.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { v ->
                            // "!" typed into an empty box switches to shell mode, like OpenCode's terminal app.
                            if (!shell && field.text.isEmpty() && v.text == "!") { onShell(true); return@BasicTextField }
                            set(v)
                        },
                        enabled = enabled,
                        maxLines = 6,
                        textStyle = (if (shell) CodeStyle.copy(fontSize = 15.sp, lineHeight = 22.sp) else FpType.bodyLg).copy(color = scheme.ink),
                        cursorBrush = SolidColor(scheme.accent),
                        keyboardOptions = if (shell) KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
                        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlusMenu(
                    onPhotos = onPickImages,
                    onFile = { insertAtCursor("@"); catching { focus.requestFocus() } },
                    onCommand = {
                        if (shell) onShell(false)
                        if (!field.text.startsWith("/")) set(TextFieldValue("/" + field.text, TextRange(1)))
                        catching { focus.requestFocus() }
                    },
                    onShell = { onShell(true); catching { focus.requestFocus() } },
                    enabled = enabled,
                )
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ModeModelChip(if (agents.isEmpty()) null else agentLabel(agent, agents), agent, modelLabel, onPicker)
                }
                Spacer(Modifier.width(8.dp))
                val sendable = field.text.isNotBlank() || (!shell && attachments.any { it is Attachment.Image })
                SendControl(hasText = sendable, running = running, enabled = enabled, onSend = onSend, onStop = onStop)
            }
        }
    }
}

/** The + button: photos, and the three typed shortcuts for people who don't know them yet. */
@Composable
private fun PlusMenu(onPhotos: () -> Unit, onFile: () -> Unit, onCommand: () -> Unit, onShell: () -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val c = Fp.colors
    Box {
        FpIconButton(Ic.add, "Add photos, files, commands", onClick = { open = true }, enabled = enabled)
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = Radius.lg, containerColor = c.surfaceRaised, shadowElevation = 12.dp, tonalElevation = 0.dp,
        ) {
            @Composable
            fun item(icon: Int, title: String, body: String, shortcut: String?, action: () -> Unit) = DropdownMenuItem(
                text = { Column { Text(title, style = FpType.label, color = c.ink); Text(body, style = FpType.caption, color = c.inkMuted) } },
                leadingIcon = { Sym(icon, tint = c.ink) },
                trailingIcon = if (shortcut != null) { { Keycap(shortcut) } } else null,
                onClick = { open = false; action() },
                modifier = Modifier.padding(horizontal = 6.dp).clip(Radius.md),
            )
            item(Ic.image, "Photos", "Attach images for the model to see", null, onPhotos)
            item(Ic.file, "File as context", "Add a file from the project", "@", onFile)
            item(Ic.slash, "Command", "Run a slash command", "/", onCommand)
            item(Ic.terminal, "Shell command", "Run it on your computer", "!", onShell)
        }
    }
}

private data class Suggestion(val value: String, val title: String, val detail: String?, val icon: Int)

/** Matches for what is being typed, above the text. Tapping one completes it. */
@Composable
private fun Suggestions(items: List<Suggestion>, onPick: (String) -> Unit) {
    val c = Fp.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        items.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(Radius.md).clickable { onPick(s.value) }.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym(s.icon, null, size = 18.dp, tint = c.inkMuted)
                Spacer(Modifier.width(10.dp))
                Text(s.title, style = FpType.label, color = c.ink, maxLines = 1)
                s.detail?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = FpType.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = c.line)
    }
}

/** Photos and files riding along: pressed-in chips with a thumbnail and a remove button. */
@Composable
private fun AttachmentRow(items: List<Attachment>, onRemove: (String) -> Unit) {
    val c = Fp.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { a ->
            Row(Modifier.height(44.dp).pressed(Radius.md, c.surfaceSunken).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                when (a) {
                    is Attachment.Image -> Image(a.preview, a.name, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(Radius.sm))
                    is Attachment.File -> Box(Modifier.size(36.dp).clip(Radius.sm).background(c.accentSoft), contentAlignment = Alignment.Center) {
                        Sym(Ic.file, null, size = 18.dp, tint = c.onAccentSoft)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(a.name, style = FpType.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                FpIconButton(Ic.close, "Remove ${a.name}", onClick = { onRemove(a.id) }, small = true)
            }
        }
    }
}

/** "Build · Sonnet ▾" with the mode's icon: the current mode and model in one chip. Tapping opens the combined picker. */
@Composable
private fun ModeModelChip(mode: String?, agent: String?, model: String, onClick: () -> Unit) {
    val c = Fp.colors
    Row(
        Modifier.height(34.dp).pressable(Radius.full, c.surface, onClick, label = "Change mode and model").padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(if (mode != null) agentIcon(agent) else Ic.autoAwesome, null, size = 16.dp, tint = c.ink)
        Spacer(Modifier.width(6.dp))
        if (mode != null) {
            Text(mode, style = FpType.label, color = c.ink, maxLines = 1)
            Text(" · ", style = FpType.label, color = c.inkMuted)
        }
        Text(model, style = FpType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(450)), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
        Sym(Ic.expandMore, null, size = 16.dp, tint = c.inkMuted)
    }
}

/**
 * Idle: the send key. Working with nothing typed: the send key turns into stop. Working with text typed: stop,
 * a "Later" chip that queues it for after this turn, and the send key, which steers the current turn.
 */
@Composable
private fun SendControl(hasText: Boolean, running: Boolean, enabled: Boolean, onSend: (Boolean) -> Unit, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(running && hasText, enter = fadeIn(Motion.settle()) + scaleIn(Motion.settle()), exit = fadeOut(Motion.quick()) + scaleOut(Motion.quick())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FpIconButton(Ic.stop, "Stop", onClick = onStop)
                FpChip("Later", onClick = { onSend(true) }, icon = Ic.schedule)
                Spacer(Modifier.width(6.dp))
            }
        }
        SendKey(
            state = when {
                running && !hasText -> SendState.Stop
                enabled && hasText -> SendState.Send
                else -> SendState.Disabled
            },
            onClick = { if (running && !hasText) onStop() else onSend(false) },
        )
    }
}
