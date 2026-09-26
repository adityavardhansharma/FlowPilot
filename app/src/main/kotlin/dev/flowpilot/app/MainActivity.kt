package dev.flowpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowpilot.app.data.Settings
import dev.flowpilot.app.data.ThemeMode
import dev.flowpilot.app.ui.AppNav
import dev.flowpilot.app.ui.LocalGraph
import dev.flowpilot.app.ui.theme.FlowPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val graph = (application as FlowPilotApp).graph
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !graph.ready.value }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val settings by graph.prefs.settings.collectAsStateWithLifecycle(Settings())
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            CompositionLocalProvider(LocalGraph provides graph) {
                FlowPilotTheme(dark = dark, dynamic = settings.dynamicColor) { AppNav() }
            }
        }
    }
}
