@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.pairing

import dev.flowpilot.core.sync.catching

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.flowpilot.app.ui.components.CodeBlock
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.graph
import dev.flowpilot.app.ui.rememberLocalNetworkAccess
import dev.flowpilot.core.api.ServerAddress
import dev.flowpilot.app.ui.theme.CodeStyle
import dev.flowpilot.app.ui.theme.Radius

@Composable
fun PairingScreen(onPaired: () -> Unit, onBack: (() -> Unit)? = null) {
    val g = graph
    val vm: PairingViewModel = viewModel { PairingViewModel(g) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lan = rememberLocalNetworkAccess()
    LaunchedEffect(ui.done) { if (ui.done) onPaired() }
    BackHandler(enabled = ui.step == PairStep.Scan || ui.step == PairStep.Manual) { vm.go(PairStep.Welcome) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = ui.step,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "pair",
        ) { step ->
            when (step) {
                // Ask for local network access up front: on Android 17 every LAN request fails without it.
                PairStep.Welcome -> Welcome(onScan = { lan { vm.go(PairStep.Scan) } }, onManual = { vm.go(PairStep.Manual) }, onBack = onBack)
                PairStep.Scan -> Scan(ui.scanError, vm::onScanned, onManual = { vm.go(PairStep.Manual) }, onBack = { vm.go(PairStep.Welcome) })
                PairStep.Manual -> Manual(ui, vm, onConnect = { lan { vm.connectManually() } }, onBack = { vm.go(PairStep.Welcome) })
                PairStep.Connecting -> Center {
                    LoadingIndicator(Modifier.size(72.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Connecting…", style = MaterialTheme.typography.titleLarge)
                }
                PairStep.Success -> Center {
                    Box(
                        Modifier.size(96.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Sym(Ic.check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 48.dp) }
                    Spacer(Modifier.height(24.dp))
                    Text("Connected", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("${ui.connectedName} · OpenCode ${ui.version}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PairStep.OldVersion -> OldVersion(ui.version, onBack = { vm.go(PairStep.Welcome) })
            }
        }
    }
}

private const val INSTALL_COMMAND = "npm i -g @opencode/cli"
private const val RUN_COMMANDS = "opencode service set hostname 0.0.0.0\nopencode service start\nopencode pair"

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun Welcome(onScan: () -> Unit, onManual: () -> Unit, onBack: (() -> Unit)?) {
    var showHelp by remember { mutableStateOf(false) }
    val t = rememberInfiniteTransition(label = "hero")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000)), label = "spin")
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) {
            Row(Modifier.fillMaxWidth()) { IconButton(onClick = onBack) { Sym(Ic.back, "Back") } }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(160.dp).rotate(angle).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Sym(Ic.send, null, Modifier.rotate(-angle), tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 64.dp) }
        Spacer(Modifier.height(40.dp))
        Text("FlowPilot", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Drive OpenCode from your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Sym(Ic.qr, null, size = 22.dp)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Scan QR code", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = onManual) { Text("Enter address") }
            TextButton(onClick = { showHelp = !showHelp }) { Text("How to set up") }
        }
        if (showHelp) SetupHelp()
    }
}

@Composable
private fun SetupHelp() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("On your computer, install OpenCode 2 once:", style = MaterialTheme.typography.bodyMedium)
        CodeBlock(INSTALL_COMMAND, "sh")
        Text("Then let the background service listen on your network, start it, and print a pairing code:", style = MaterialTheme.typography.bodyMedium)
        CodeBlock(RUN_COMMANDS, "sh")
        Text(
            "Scan the QR it shows, or paste its link under Enter address. Your phone and computer need to be on the same Wi-Fi or Tailscale. " +
                "If the service was already running, use opencode service restart after the set command.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Scan(error: String?, onCode: (String) -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    fun hasCamera() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasCamera()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) catching { launcher.launch(Manifest.permission.CAMERA) }.onFailure { asked = true } }
    // Coming back from Android settings with the permission turned on starts the camera.
    LifecycleResumeEffect(Unit) {
        if (!granted && hasCamera()) granted = true
        onPauseOrDispose {}
    }
    // Camera trouble is local to this screen; Try again restarts the camera from scratch.
    var cameraError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) key(attempt) { QrScanner(onCode, onError = { cameraError = it }, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(260.dp).border(4.dp, Color.White.copy(alpha = 0.9f), Radius.xl))
        }
        Row(Modifier.statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = onBack) { Sym(Ic.close, "Close", tint = Color.White) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val msg = when {
                !granted && asked -> "FlowPilot needs the camera to scan. You can type the address instead."
                cameraError != null -> cameraError!!
                error != null -> error
                else -> "Point at the QR code from opencode pair"
            }
            Surface(shape = Radius.full, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (granted && cameraError != null) {
                    Button(onClick = { cameraError = null; attempt++ }) { Text("Try again") }
                }
                FilledTonalButton(onClick = onManual) { Text("Enter address") }
            }
        }
    }
}

@Composable
private fun Manual(ui: PairUi, vm: PairingViewModel, onConnect: () -> Unit, onBack: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Row(Modifier.padding(vertical = 8.dp)) { IconButton(onClick = onBack) { Sym(Ic.back, "Back") } }
        Text("Enter address", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste the link opencode pair prints. Or type your computer's Wi-Fi address with the server password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = ui.address,
            onValueChange = vm::setAddress,
            label = { Text("Pairing link or address") },
            placeholder = { Text("192.168.1.20:49374", style = CodeStyle) },
            singleLine = true,
            isError = ui.addressError != null,
            supportingText = { Text(ui.addressError ?: "Your computer's Wi-Fi or Tailscale address. The port defaults to ${ServerAddress.DEFAULT_PORT}.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = if (ui.addressIsLink) ImeAction.Go else ImeAction.Next),
            keyboardActions = KeyboardActions(onGo = { onConnect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!ui.addressIsLink) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::setPassword,
                label = { Text("Password") },
                singleLine = true,
                isError = ui.passwordError != null,
                supportingText = { Text(ui.passwordError ?: "The server password. Not needed with a pairing link.") },
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) { Sym(if (reveal) Ic.visibilityOff else Ic.visibility, if (reveal) "Hide password" else "Show password") }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapes(),
        ) { Text("Connect", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun OldVersion(version: String, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Center {
        Sym(Ic.warning, null, tint = MaterialTheme.colorScheme.tertiary, size = 48.dp)
        Spacer(Modifier.height(16.dp))
        Text("This computer runs OpenCode $version", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("FlowPilot needs OpenCode 2. Update it on your computer:", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        CodeBlock(INSTALL_COMMAND, "sh")
        Spacer(Modifier.height(16.dp))
        Row {
            TextButton(onClick = { clipboard.setText(AnnotatedString(INSTALL_COMMAND)) }) { Text("Copy command") }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
