@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import dev.flowpilot.app.ui.components.rowPress
import dev.flowpilot.app.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
 * works with something typed, stop and "Later" (queue it) join the send key, which then steers.
 *
 * Shortcuts, as in OpenCode's own clients: `/` at the start lists slash commands, `@` lists files to add as
 * context, and `!` in an empty box switches to running a shell command. The + tray offers the same, plus photos.
 *
 * The keyboard is sacred. "+" hides the keyboard but keeps focus in the field, and the attach tray takes the
 * keyboard's place at its last measured height, so the composer doesn't move. Tapping the field brings the keyboard
 * back over the tray. The composer owns the space under it (keyboard, tray or navigation bar, whichever is
 * tallest), so callers must not add IME or navigation-bar padding of their own.
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

    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val nav = WindowInsets.navigationBars
    var tray by rememberSaveable { mutableStateOf(false) }
    // How the tray last closed: for the keyboard (which then covers it) or on its own (it glides away).
    var toKeyboard by remember { mutableStateOf(false) }
    // The tallest the keyboard has been, so the tray can stand in for it exactly. 280dp until it has been seen.
    var keyboardPx by rememberSaveable { mutableIntStateOf(0) }
    val trayPx = if (keyboardPx > 0) keyboardPx else with(density) { (280.dp + DefaultNavBar).roundToPx() }
    val trayHeight = remember { Animatable(0f) }
    LaunchedEffect(ime, density) {
        var last = 0
        snapshotFlow { ime.getBottom(density) }.collect { now ->
            if (now > keyboardPx) keyboardPx = now
            // The keyboard coming back (a tap in the field) closes the tray under it.
            if (tray && now > last) { toKeyboard = true; tray = false }
            last = now
        }
    }
    LaunchedEffect(tray) {
        when {
            // Opened from the keyboard: the tray is already its height and the keyboard slides away off it.
            tray && ime.getBottom(density) > 0 -> trayHeight.snapTo(trayPx.toFloat())
            tray -> trayHeight.animateTo(trayPx.toFloat(), Motion.glide())
            toKeyboard -> {
                // Hold until the keyboard covers the tray, then let go behind it.
                withTimeoutOrNull(600) { snapshotFlow { ime.getBottom(density) }.first { it >= trayHeight.value - 1 } }
                trayHeight.animateTo(0f, Motion.glide())
                toKeyboard = false
            }
            else -> trayHeight.animateTo(0f, Motion.glide())
        }
    }
    // Read through derived state, so the tray's spring relays out the composer without recomposing it each frame.
    val trayShown by remember { derivedStateOf { tray || trayHeight.value > 0f } }
    BackHandler(tray) { tray = false }
    fun closeTrayForKeyboard() {
        if (tray) { toKeyboard = true; tray = false }
        catching { focus.requestFocus() }
        keyboard?.show()
    }
    val fieldTouches = remember { MutableInteractionSource() }
    LaunchedEffect(fieldTouches) {
        fieldTouches.interactions.collect { if (it is PressInteraction.Release && tray) closeTrayForKeyboard() }
    }

    Column(modifier.fillMaxWidth()) {
        // The one object that is always there floats on shadow-lift with radius-xl.
        Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().raised(Radius.xl, scheme.surface, lift = true)) {
            Column(Modifier.animateContentSize(Motion.settle()).padding(top = 4.dp, bottom = 8.dp)) {
                when {
                    commandMatches.isNotEmpty() -> Suggestions(commandMatches.map { Suggestion(it.name, "/" + it.name, it.description, Ic.slash) }, ::pickCommand)
                    mentionQuery != null && fileMatches.isNotEmpty() -> Suggestions(fileMatches.map { Suggestion(it, it.substringAfterLast('/'), it.substringBeforeLast('/', "").ifEmpty { null }, Ic.file) }, ::pickFile)
                }
                if (attachments.isNotEmpty()) AttachmentRow(attachments, onRemoveAttachment)
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (shell) {
                        // Amber: the command runs on the person's computer.
                        Row(
                            Modifier.clip(Radius.sm).background(scheme.amberSoft).rowPress({ onShell(false) }, longClickLabel = null)
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
                                    running -> "Steer, or queue a follow-up"
                                    else -> "Ask the agent to do something"
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
                            interactionSource = fieldTouches,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlusKey(
                        open = tray,
                        enabled = enabled,
                        onClick = {
                            if (tray) closeTrayForKeyboard()
                            else {
                                // Hide the keyboard but leave focus (and the cursor) in the field.
                                toKeyboard = false
                                tray = true
                                keyboard?.hide()
                            }
                        },
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
        // Below the card: the keyboard, the tray or the navigation bar, whichever is tallest. Read while laying out, so
        // the keyboard's slide moves the composer without recomposing it.
        Box(
            Modifier.fillMaxWidth().clipToBounds().layout { m, c ->
                val h = maxOf(ime.getBottom(this), trayHeight.value.roundToInt(), nav.getBottom(this)) + Space.s2.roundToPx()
                val p = m.measure(c.copy(minHeight = 0, maxHeight = maxOf(trayPx, h)))
                layout(c.maxWidth, h) { p.place(0, 0) }
            },
        ) {
            if (trayShown) AttachTray(
                height = with(density) { trayPx.toDp() },
                bottomInset = with(density) { nav.getBottom(this).toDp() },
                onPhotos = { tray = false; onPickImages() },
                onFile = { insertAtCursor("@"); closeTrayForKeyboard() },
                onCommand = {
                    if (shell) onShell(false)
                    if (!field.text.startsWith("/")) set(TextFieldValue("/" + field.text, TextRange(1)))
                    closeTrayForKeyboard()
                },
                onShell = { onShell(true); closeTrayForKeyboard() },
            )
        }
    }
}

/** The tray's height before the keyboard has ever shown includes a typical gesture bar. */
private val DefaultNavBar = 24.dp

/** "+", turning 45° into "×" on spring-snappy while the tray is open. */
@Composable
private fun PlusKey(open: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 45f else 0f, Motion.snappy(), label = "plus")
    Box(
        Modifier.size(40.dp).pressable(Radius.full, Color.Transparent, onClick, enabled = enabled, label = if (open) "Close attachments" else "Add photos, files, commands", flat = true),
        contentAlignment = Alignment.Center,
    ) {
        Sym(Ic.add, null, size = 22.dp, tint = if (enabled) Fp.colors.ink else Fp.colors.inkFaint, modifier = Modifier.graphicsLayer { rotationZ = turn })
    }
}

/**
 * Where the keyboard was: a large Photos tile for the system photo picker, and File, Command and Shell tiles that
 * each show the key that does the same thing while typing. Picking a typed one brings the keyboard back.
 */
@Composable
private fun AttachTray(height: Dp, bottomInset: Dp, onPhotos: () -> Unit, onFile: () -> Unit, onCommand: () -> Unit, onShell: () -> Unit) {
    val c = Fp.colors
    Row(
        Modifier.fillMaxWidth().height(height).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().pressable(Radius.lg, c.surface, onPhotos, label = "Photos").padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(48.dp).pressed(Radius.full, c.accentSoft), contentAlignment = Alignment.Center) {
                Sym(Ic.image, null, size = 24.dp, tint = c.onAccentSoft)
            }
            Column {
                Text("Photos", style = FpType.title, color = c.ink)
                Text("For the model to see", style = FpType.caption, color = c.inkMuted)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrayTile(Ic.file, "File", "@", onFile, Modifier.weight(1f))
            TrayTile(Ic.slash, "Command", "/", onCommand, Modifier.weight(1f))
            TrayTile(Ic.terminal, "Shell", "!", onShell, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrayTile(icon: Int, label: String, key: String, onClick: () -> Unit, modifier: Modifier) {
    val c = Fp.colors
    Row(
        modifier.fillMaxWidth().pressable(Radius.lg, c.surface, onClick, label = label).padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, null, size = 20.dp, tint = c.inkMuted)
        Spacer(Modifier.width(10.dp))
        Text(label, style = FpType.label, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
        Keycap(key)
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
                Modifier.fillMaxWidth().clip(Radius.md).rowPress({ onPick(s.value) }).padding(horizontal = 10.dp, vertical = 9.dp),
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
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(1.dp).background(c.line))
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
        AnimatedVisibility(
            running && hasText,
            enter = expandHorizontally(Motion.settle(), expandFrom = Alignment.End) + fadeIn(Motion.fadeIn()),
            exit = shrinkHorizontally(Motion.settle(), shrinkTowards = Alignment.End) + fadeOut(Motion.fadeOut()),
        ) {
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
