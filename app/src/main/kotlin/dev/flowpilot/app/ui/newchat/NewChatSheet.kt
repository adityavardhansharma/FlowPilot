@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.newchat

import dev.flowpilot.app.ui.components.FpSpinner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.components.CodeBlock
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.LoadingRow
import dev.flowpilot.app.ui.components.ProjectShape
import dev.flowpilot.app.ui.components.SectionHeader
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.theme.CodeSmallStyle
import dev.flowpilot.core.api.ProjectOps
import dev.flowpilot.core.chat.ToolDescriber

/**
 * Where should the agent work? One tap on a recent project starts a chat there. [onStart] gets the folder,
 * or null for "No project" (the scratch folder, created on first send).
 */
@Composable
fun NewChatSheet(conn: ServerConnection, onStart: (String?) -> Unit, onAllProjects: () -> Unit, onDismiss: () -> Unit) {
    val vm: NewChatViewModel = viewModel(key = "new-chat:${conn.identity}") { NewChatViewModel(conn) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = ui.step != NewChatStep.Pick)
    BackHandler(enabled = ui.step != NewChatStep.Pick) { vm.go(NewChatStep.Pick) }
    val start: (String?) -> Unit = { dir -> vm.go(NewChatStep.Pick); onStart(dir) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        AnimatedContent(ui.step, label = "step") { step ->
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                when (step) {
                    NewChatStep.Pick -> Pick(ui, start, onAllProjects, vm)
                    NewChatStep.Folder -> NewFolder(ui, vm, start)
                    NewChatStep.Clone -> Clone(ui, vm, start)
                }
            }
        }
    }
    if (ui.browsing) FolderBrowser(ui, vm)
}

@Composable
private fun Pick(ui: NewChatUi, onStart: (String?) -> Unit, onAll: () -> Unit, vm: NewChatViewModel) {
    Text("Where should the agent work?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("Recent projects", Modifier.padding(start = 8.dp))
        when {
            ui.loading -> LoadingRow()
            ui.projects.isEmpty() -> Text(
                "No projects yet. Make a folder or clone a repository.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            else -> ui.projects.take(5).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onStart(p.canonical) }.padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProjectShape(p.displayName, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tilde(p.canonical, ui.home), style = CodeSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        if (ui.projects.size > 5) Action(Ic.folderOpen, "All projects…", null, onAll)
        SectionHeader("Somewhere new", Modifier.padding(start = 8.dp))
        Action(Ic.newFolder, "New folder", "Make a folder, optionally with git") { vm.go(NewChatStep.Folder) }
        Action(Ic.download, "Clone repository", "From GitHub or any git URL") { vm.go(NewChatStep.Clone) }
        Action(Ic.chat, "No project", "A scratch folder for quick questions") { onStart(null) }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun Action(icon: Int, title: String, body: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepHeader(title: String, vm: NewChatViewModel) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.go(NewChatStep.Pick) }) { Sym(Ic.back, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ParentRow(ui: NewChatUi, vm: NewChatViewModel) {
    Row(Modifier.fillMaxWidth().clickable { vm.openBrowser() }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(Ic.folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Inside", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ui.parent?.let { tilde(it, ui.home) } ?: "Loading…", style = CodeSmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { vm.openBrowser() }, enabled = ui.parent != null) { Text("Change") }
    }
}

@Composable
private fun NewFolder(ui: NewChatUi, vm: NewChatViewModel, onStart: (String?) -> Unit) {
    StepHeader("New folder", vm)
    OutlinedTextField(
        value = ui.folderName,
        onValueChange = vm::setName,
        label = { Text("Folder name") },
        singleLine = true,
        isError = ui.error != null,
        supportingText = ui.error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
    ParentRow(ui, vm)
    Row(Modifier.fillMaxWidth().clickable { vm.setGit(!ui.gitInit) }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Start with git", style = MaterialTheme.typography.bodyLarge)
            Text("Runs git init so you can review changes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = ui.gitInit, onCheckedChange = vm::setGit)
    }
    Button(
        onClick = { vm.createFolder { onStart(it) } },
        enabled = !ui.creating && ui.parent != null,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    ) {
        if (ui.creating) FpSpinner(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Create and start chat")
    }
}

@Composable
private fun Clone(ui: NewChatUi, vm: NewChatViewModel, onStart: (String?) -> Unit) {
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        if (ui.repo.isBlank()) {
            val clip = clipboard.getText()?.text?.trim().orEmpty()
            if (clip.startsWith("https://github.com/") || clip.startsWith("git@") || clip.endsWith(".git")) vm.setRepo(clip)
        }
    }
    StepHeader("Clone repository", vm)
    OutlinedTextField(
        value = ui.repo,
        onValueChange = vm::setRepo,
        label = { Text("Repository") },
        placeholder = { Text("owner/repo or https://…") },
        singleLine = true,
        enabled = !ui.clone.running,
        isError = ui.error != null,
        supportingText = { Text(ui.error ?: ProjectOps.normalizeRepoUrl(ui.repo)?.let { "Clones into ${ProjectOps.repoName(it)}" } ?: "GitHub shorthand works") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
    ParentRow(ui, vm)
    val c = ui.clone
    if (c.running) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.percent != null) LinearWavyProgressIndicator(progress = { c.percent / 100f }, modifier = Modifier.fillMaxWidth())
            else LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(ToolDescriber.shortPath(c.line, 60), style = CodeSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
    if (c.failed != null) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clone failed. git said:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            CodeBlock(c.failed, "output", maxLines = 12)
        }
    }
    Button(
        onClick = { vm.clone { onStart(it) } },
        enabled = !c.running && ui.repo.isNotBlank() && ui.parent != null,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    ) { Text(if (c.failed != null) "Retry" else "Clone and start chat") }
}

@Composable
private fun FolderBrowser(ui: NewChatUi, vm: NewChatViewModel) {
    val path = ui.browsePath ?: "/"
    AlertDialog(
        onDismissRequest = vm::closeBrowser,
        title = { Text(tilde(path, ui.home), style = CodeSmallStyle, maxLines = 2) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (path != "/") item("up") {
                    Row(Modifier.fillMaxWidth().clickable { vm.browse(path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" }) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym(Ic.back, null, size = 20.dp); Spacer(Modifier.width(16.dp)); Text("Up one level")
                    }
                }
                if (ui.browseLoading) item("l") { LoadingRow() }
                else items(ui.browseEntries, key = { it.path }) { e ->
                    Row(Modifier.fillMaxWidth().clickable { vm.browse(path.trimEnd('/') + "/" + e.name) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym(Ic.folder, null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp))
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!ui.browseLoading && ui.browseEntries.isEmpty()) item("e") { Text("No folders here", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = vm::useBrowsed) { Text("Use this folder") } },
        dismissButton = { TextButton(onClick = vm::closeBrowser) { Text("Cancel") } },
    )
}

fun tilde(path: String, home: String?): String =
    if (home != null && home != "/" && path.startsWith(home)) "~" + path.removePrefix(home) else path
