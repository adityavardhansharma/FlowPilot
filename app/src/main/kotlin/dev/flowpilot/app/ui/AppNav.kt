@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.flowpilot.app.data.CrashLog
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.chat.ChatScreen
import dev.flowpilot.app.ui.components.CenteredLoading
import dev.flowpilot.app.ui.components.Ic
import dev.flowpilot.app.ui.components.Sym
import dev.flowpilot.app.ui.home.HomeScreen
import dev.flowpilot.app.ui.home.HomeViewModel
import dev.flowpilot.app.ui.inbox.InboxScreen
import dev.flowpilot.app.ui.newchat.NewChatSheet
import dev.flowpilot.app.ui.pairing.PairingScreen
import dev.flowpilot.app.ui.projects.ProjectsScreen
import dev.flowpilot.app.ui.settings.ModelsScreen
import dev.flowpilot.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val PAIR = "pair"
    const val PAIR_ANOTHER = "pair/another"
    const val MAIN = "main"
    const val CHAT = "chat?session={session}&dir={dir}"
    const val SETTINGS = "settings"
    const val MODELS = "settings/models"

    fun chat(session: String) = "chat?session=${Uri.encode(session)}"
    fun newChat(dir: String?) = if (dir == null) "chat" else "chat?dir=${Uri.encode(dir)}"
}

@Composable
fun AppNav() {
    val g = graph
    val ready by g.ready.collectAsStateWithLifecycle()
    val conn by g.connection.collectAsStateWithLifecycle()
    if (!ready) { Surface(Modifier.fillMaxSize()) {}; return }

    val nav = rememberNavController()
    var resumed by rememberSaveable { mutableStateOf(false) }
    var previousServer by rememberSaveable { mutableStateOf<String?>(null) }
    val lan = rememberLocalNetworkAccess()

    // Android 17 blocks LAN requests until local network access is granted; ask once per launch, then reconnect.
    LaunchedEffect(conn?.server?.id) {
        val c = conn ?: return@LaunchedEffect
        if (!LocalNetwork.granted(g.context)) lan { c.retryNow() }
    }

    // Show what went wrong last time, so a crash can be reported instead of silently repeating.
    val context = LocalContext.current
    var lastCrash by remember { mutableStateOf(CrashLog.read(context)) }
    lastCrash?.let { report ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { CrashLog.clear(context); lastCrash = null },
            title = { Text("FlowPilot closed unexpectedly") },
            text = { Text("Copy the details to include them in a bug report.") },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)); CrashLog.clear(context); lastCrash = null }) { Text("Copy details") }
            },
            dismissButton = { TextButton(onClick = { CrashLog.clear(context); lastCrash = null }) { Text("Dismiss") } },
        )
    }

    // Losing the last computer sends you back to pairing; pairing the first one lands on Home.
    LaunchedEffect(conn == null) {
        val route = nav.currentDestination?.route
        if (conn == null && route != null && route != Routes.PAIR) nav.navigate(Routes.PAIR) { popUpTo(0) }
    }
    // Resume, don't restart: cold start reopens the last chat.
    LaunchedEffect(conn?.server?.id) {
        val c = conn ?: return@LaunchedEffect
        if (previousServer != null && previousServer != c.server.id) {
            nav.navigate(Routes.MAIN) { popUpTo(0) }
            resumed = false
        }
        previousServer = c.server.id
        if (!resumed) {
            resumed = true
            g.prefs.lastChat(c.server.id).first()?.let { nav.navigate(Routes.chat(it)) }
        }
    }

    val motion = MaterialTheme.motionScheme
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = if (conn == null) Routes.PAIR else Routes.MAIN,
            enterTransition = { slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec()) },
            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
            popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
            popExitTransition = { slideOutHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec()) },
        ) {
            composable(Routes.PAIR) {
                PairingScreen(onPaired = { nav.navigate(Routes.MAIN) { popUpTo(0) } })
            }
            composable(Routes.PAIR_ANOTHER) {
                PairingScreen(onPaired = { nav.popBackStack(Routes.MAIN, inclusive = false) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.MAIN) {
                val c = conn
                if (c == null) CenteredLoading() else MainTabs(c, nav)
            }
            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument("session") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("dir") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val c = conn
                if (c == null) CenteredLoading() else ChatScreen(
                    conn = c,
                    sessionID = entry.arguments?.getString("session"),
                    directory = entry.arguments?.getString("dir"),
                    onBack = {
                        if (!nav.popBackStack()) nav.navigate(Routes.MAIN)
                        val id = c.server.id
                        g.scope.launch { g.prefs.setLastChat(id, null) }
                    },
                    onManageModels = { nav.navigate(Routes.MODELS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onPair = { nav.navigate(Routes.PAIR_ANOTHER) }, onModels = { nav.navigate(Routes.MODELS) })
            }
            composable(Routes.MODELS) {
                val c = conn
                if (c == null) CenteredLoading() else ModelsScreen(c, onBack = { nav.popBackStack() })
            }
        }
    }
}

private enum class Tab(val label: String, val icon: Int) { Chats("Chats", Ic.chat), Projects("Projects", Ic.folder), Inbox("Inbox", Ic.inbox) }

@Composable
private fun MainTabs(conn: ServerConnection, nav: NavHostController) {
    val g = graph
    val home: HomeViewModel = viewModel(key = "home:${conn.identity}") { HomeViewModel(g, conn) }
    var tab by rememberSaveable { mutableStateOf(Tab.Chats) }
    var newChat by remember { mutableStateOf(false) }
    val asks by conn.pending.asks.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val openChat: (String) -> Unit = { nav.navigate(Routes.chat(it)) }
    val startIn: (String?) -> Unit = { dir -> newChat = false; nav.navigate(Routes.newChat(dir)) }

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.Inbox && asks.isNotEmpty()) {
                                BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("${asks.size}") } }) { Sym(t.icon, null) }
                            } else Sym(t.icon, null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.Inbox) {
                ExtendedFloatingActionButton(
                    onClick = { newChat = true },
                    icon = { Sym(Ic.editSquare, null) },
                    text = { Text("New chat") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (tab) {
            Tab.Chats -> HomeScreen(home, padding, onOpenChat = openChat, onNewChat = { newChat = true }, onSettings = { nav.navigate(Routes.SETTINGS) }, showMessage = show)
            Tab.Projects -> ProjectsScreen(home, padding, onStartIn = { startIn(it) }, onNew = { newChat = true })
            Tab.Inbox -> InboxScreen(conn, padding, onOpenChat = openChat, showMessage = show)
        }
    }
    if (newChat) {
        NewChatSheet(conn, onStart = startIn, onAllProjects = { newChat = false; tab = Tab.Projects }, onDismiss = { newChat = false })
    }
}
