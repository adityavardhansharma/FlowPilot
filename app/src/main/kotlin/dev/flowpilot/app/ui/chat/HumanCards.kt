@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.flowpilot.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.components.CodeBlock
import dev.flowpilot.app.ui.components.DiffView
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.core.api.Decision
import dev.flowpilot.core.api.Form
import dev.flowpilot.core.api.FormField
import dev.flowpilot.core.api.PermissionRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Plain words for what the agent wants to do. */
fun permissionTitle(p: PermissionRequest): String {
    val first = p.resources.firstOrNull()
    return when (p.action.substringAfterLast('.')) {
        "shell", "bash" -> "Run a command?"
        "edit", "write", "patch" -> if (first != null) "Edit ${first.substringAfterLast('/')}?" else "Edit files?"
        "read" -> "Read ${first?.substringAfterLast('/') ?: "a file"}?"
        "webfetch" -> "Open a web page?"
        "external_directory" -> "Work outside the project folder?"
        else -> p.message?.takeIf { it.isNotBlank() } ?: "Allow ${p.action.replace('_', ' ')}?"
    }
}

/** An approval: exactly what will happen, then Allow once, Always allow, Deny. */
@Composable
fun PermissionCard(p: PermissionRequest, onDecide: (Decision) -> Unit, modifier: Modifier = Modifier, chatTitle: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.tertiaryContainer.copy(alpha = 0.28f), shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (chatTitle != null) Text(chatTitle, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym(Ic.lock, null, size = 20.dp, tint = scheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Text(permissionTitle(p), style = MaterialTheme.typography.titleMedium)
            }
            if (!p.message.isNullOrBlank() && permissionTitle(p) != p.message) Text(p.message!!, style = MaterialTheme.typography.bodyMedium)
            val action = p.action.substringAfterLast('.')
            val diff = (p.metadata?.get("diff") as? JsonPrimitive)?.contentOrNull
            when {
                diff != null -> DiffView(diff, maxLines = 60)
                p.resources.isNotEmpty() -> CodeBlock(p.resources.joinToString("\n"), if (action == "shell" || action == "bash") "sh" else "text", maxLines = 10)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(Decision.Once) }) { Text("Allow once") }
                FilledTonalButton(onClick = { onDecide(Decision.Always) }) { Text("Always allow") }
                OutlinedButton(onClick = { onDecide(Decision.Reject) }) { Text("Deny") }
            }
        }
    }
}

/** A question from the agent. Submit stays disabled until required fields have answers. */
@Composable
fun FormCard(form: Form, onSubmit: (JsonObject) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, chatTitle: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val answers = remember(form.id) {
        mutableStateMapOf<String, JsonElement>().apply { form.fields.forEach { f -> f.default?.let { put(f.key, it) } } }
    }
    val uri = LocalUriHandler.current
    val fields = form.fields.filterNot { it.hidden }
    val ready = fields.all { f -> !f.required || answers[f.key].isAnswered() }
    Surface(color = scheme.tertiaryContainer.copy(alpha = 0.28f), shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (chatTitle != null) Text(chatTitle, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym(Ic.tool(dev.flowpilot.core.chat.ToolIcon.Question), null, size = 20.dp, tint = scheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Text(form.title, style = MaterialTheme.typography.titleMedium)
            }
            fields.forEach { f ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (f.type != "boolean") {
                        (f.title ?: f.key).let { Text(it + if (f.required) "" else " (optional)", style = MaterialTheme.typography.labelLarge) }
                        f.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    }
                    FieldInput(f, answers[f.key], { answers[f.key] = it }, onOpen = { url -> runCatching { uri.openUri(url) } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSubmit(typed(fields, answers.toMap())) }, enabled = ready) { Text("Submit") }
                TextButton(onClick = onDismiss) { Text("Skip") }
            }
        }
    }
}

/** Number fields are edited as text; convert them to numbers and drop blanks before sending. */
private fun typed(fields: List<FormField>, raw: Map<String, JsonElement>): JsonObject = JsonObject(
    raw.mapNotNull { (k, v) ->
        val f = fields.firstOrNull { it.key == k }
        val p = v as? JsonPrimitive
        when {
            f != null && p != null && p.isString && f.type == "integer" -> p.content.toLongOrNull()?.let { k to JsonPrimitive(it) }
            f != null && p != null && p.isString && f.type == "number" -> p.content.toDoubleOrNull()?.let { k to JsonPrimitive(it) }
            p != null && p.isString && p.content.isBlank() -> null
            else -> k to v
        }
    }.toMap(),
)

private fun JsonElement?.isAnswered(): Boolean = when (this) {
    null -> false
    is JsonPrimitive -> content.isNotBlank()
    is JsonArray -> isNotEmpty()
    else -> true
}

@Composable
private fun FieldInput(f: FormField, value: JsonElement?, set: (JsonElement) -> Unit, onOpen: (String) -> Unit) {
    when (f.type) {
        "boolean" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.title ?: f.key, style = MaterialTheme.typography.bodyLarge)
                f.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Switch(checked = (value as? JsonPrimitive)?.booleanOrNull == true, onCheckedChange = { set(JsonPrimitive(it)) })
        }
        "multiselect" -> {
            val chosen = (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            f.options.forEach { o ->
                Row(Modifier.fillMaxWidth().clickable {
                    val next = if (o.value in chosen) chosen - o.value else chosen + o.value
                    set(JsonArray(next.map { JsonPrimitive(it) }))
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = o.value in chosen, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(o.label, style = MaterialTheme.typography.bodyLarge)
                        o.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        "external" -> OutlinedButton(onClick = { f.url?.let(onOpen) }) {
            Sym(Ic.openInNew, null, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Open")
        }
        else -> {
            val current = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (f.options.isNotEmpty()) {
                f.options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { set(JsonPrimitive(o.value)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == o.value, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(o.label, style = MaterialTheme.typography.bodyLarge)
                            o.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                if (f.custom) OutlinedTextField(
                    value = if (f.options.any { it.value == current }) "" else current,
                    onValueChange = { set(JsonPrimitive(it)) },
                    placeholder = { Text("Something else") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val numeric = f.type == "number" || f.type == "integer"
                OutlinedTextField(
                    value = current,
                    onValueChange = { v -> if (!numeric || v.isEmpty() || v == "-" || v.toDoubleOrNull() != null) set(JsonPrimitive(v)) },
                    placeholder = f.placeholder?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
