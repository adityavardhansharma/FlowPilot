@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.pairing

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.flowpilot.app.ui.components.CodeBlock
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.graph
import dev.flowpilot.app.ui.theme.CodeStyle
import dev.flowpilot.app.ui.theme.Radius

@Composable
fun PairingScreen(onPaired: () -> Unit, onBack: (() -> Unit)? = null) {
    val g = graph
    val vm: PairingViewModel = viewModel { PairingViewModel(g) }
    val ui by vm.ui.collectAsStateWithLifecycle()
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
                PairStep.Welcome -> Welcome(onScan = { vm.go(PairStep.Scan) }, onManual = { vm.go(PairStep.Manual) }, onBack = onBack)
                PairStep.Scan -> Scan(ui.scanError, vm::onScanned, onManual = { vm.go(PairStep.Manual) }, onBack = { vm.go(PairStep.Welcome) })
                PairStep.Manual -> Manual(ui, vm, onBack = { vm.go(PairStep.Welcome) })
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
        Text("On your computer, start OpenCode 2 listening on your network, then print a pairing code:", style = MaterialTheme.typography.bodyMedium)
        CodeBlock("npm i -g @opencode/cli\nopencode serve --hostname 0.0.0.0\nopencode pair --url", "sh")
        Text(
            "Scan the QR it shows. Your phone and computer need to be on the same network or Tailscale.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Scan(error: String?, onCode: (String) -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) QrScanner(onCode, Modifier.fillMaxSize())
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
                error != null -> error
                else -> "Point at the QR code from opencode pair"
            }
            Surface(shape = Radius.full, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onManual) { Text("Enter address") }
        }
    }
}

@Composable
private fun Manual(ui: PairUi, vm: PairingViewModel, onBack: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Row(Modifier.padding(vertical = 8.dp)) { IconButton(onClick = onBack) { Sym(Ic.back, "Back") } }
        Text("Enter address", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the address OpenCode prints when it starts, and the server password if you set one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = ui.address,
            onValueChange = vm::setAddress,
            label = { Text("Address") },
            placeholder = { Text("http://100.64.1.2:4096", style = CodeStyle) },
            singleLine = true,
            isError = ui.addressError != null,
            supportingText = { Text(ui.addressError ?: "Your computer's LAN or Tailscale address and port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ui.password,
            onValueChange = vm::setPassword,
            label = { Text("Password") },
            singleLine = true,
            isError = ui.passwordError != null,
            supportingText = { Text(ui.passwordError ?: "OPENCODE_SERVER_PASSWORD, or a pairing token") },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) { Sym(if (reveal) Ic.visibilityOff else Ic.visibility, if (reveal) "Hide password" else "Show password") }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.connectManually() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = vm::connectManually,
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
        CodeBlock("npm i -g @opencode/cli", "sh")
        Spacer(Modifier.height(16.dp))
        Row {
            TextButton(onClick = { clipboard.setText(AnnotatedString("npm i -g @opencode/cli")) }) { Text("Copy command") }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
