package dev.flowpilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressable
import dev.flowpilot.app.ui.theme.pressed
import kotlinx.coroutines.launch
import dev.flowpilot.app.ui.theme.raised
import dev.flowpilot.core.sync.catching

/**
 * A dialog, for decisions only: surface-overlay on elevation-4 with radius-xl, an optional icon in a soft disc,
 * the title, one paragraph, and the actions end-aligned. The buttons are pills inside a 24dp inset, so their
 * corners sit concentric with the dialog's.
 */
@Composable
fun FpDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    body: String? = null,
    icon: Int? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    dismiss: String = "Cancel",
    content: (@Composable () -> Unit)? = null,
) {
    val c = Fp.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().raised(Radius.xl, c.surfaceRaised, lift = true).padding(24.dp)) {
            if (icon != null) {
                Box(
                    Modifier.size(44.dp).pressed(Radius.full, if (danger) c.dangerSoft else c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { Sym(icon, null, size = 22.dp, tint = if (danger) c.onDangerSoft else c.onAccentSoft) }
                Spacer(Modifier.height(16.dp))
            }
            Text(title, style = FpType.title, color = c.ink)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = FpType.body, color = c.inkMuted)
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                FpButton(dismiss, onDismiss, variant = FpButtonVariant.Ghost)
                Spacer(Modifier.width(8.dp))
                FpButton(confirm, onConfirm, variant = if (danger) FpButtonVariant.Danger else FpButtonVariant.Primary, enabled = confirmEnabled)
            }
        }
    }
}

/** A dialog asking for one line of text, such as a chat's new name. The field is a well and takes focus at once. */
@Composable
fun FpTextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = Fp.colors
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { catching { focus.requestFocus() } }
    val ok = value.text.isNotBlank()
    FpDialog(
        title = title, onDismiss = onDismiss, confirm = confirm, confirmEnabled = ok,
        onConfirm = { if (ok) onConfirm(value.text.trim()) },
    ) {
        BasicTextField(
            value, { value = it },
            singleLine = true,
            textStyle = FpType.bodyLg.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text.trim()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().height(48.dp).pressed(Radius.md, c.surfaceSunken).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
    }
}

/** One action in an actions sheet: an icon and a label, danger-tinted when destructive. */
data class SheetAction(val icon: Int, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * A short list of actions for one thing (a chat's Pin, Rename, Delete), in a sheet that rises on spring-glide.
 * Rows are 56dp with the icon in ink-muted; a destructive one is danger. Picking one closes the sheet first.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FpActionsSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val c = Fp.colors
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.surfaceRaised,
        scrimColor = c.scrim,
        tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 5.dp).pressed(Radius.full, c.line)) },
    ) {
        Text(
            title, style = FpType.title, color = c.ink, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            actions.forEach { a ->
                val ink = if (a.danger) c.danger else c.ink
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .pressable(Radius.lg, androidx.compose.ui.graphics.Color.Transparent, {
                            scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); a.onClick() }
                        }, flat = true)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym(a.icon, null, size = 22.dp, tint = if (a.danger) c.danger else c.inkMuted)
                    Spacer(Modifier.width(16.dp))
                    Text(a.label, style = FpType.bodyLg, color = ink)
                }
            }
        }
    }
}
