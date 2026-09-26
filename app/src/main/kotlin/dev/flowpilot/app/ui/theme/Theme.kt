@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package dev.flowpilot.app.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flowpilot.app.R

val LocalFlowPilotColors = staticCompositionLocalOf { CodeColorsLight }

/** Code, diff and terminal colors. They never follow dynamic color. */
val MaterialTheme.code: FlowPilotColors
    @Composable @ReadOnlyComposable get() = LocalFlowPilotColors.current

private fun flex(weight: Int) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", 40f)),
)

/** Google Sans Flex with a touch of roundness, the brand face. */
val BrandFamily = FontFamily(flex(400), flex(500), flex(600), flex(700))

private fun mono(weight: Int) = Font(
    R.font.google_sans_code,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val CodeFamily = FontFamily(mono(400), mono(500), mono(600))

/** `code-medium` from the type scale: 13/20 Google Sans Code. */
val CodeStyle = TextStyle(fontFamily = CodeFamily, fontSize = 13.sp, lineHeight = 20.sp)
val CodeSmallStyle = TextStyle(fontFamily = CodeFamily, fontSize = 12.sp, lineHeight = 16.sp)

private fun TextStyle.brand() = copy(fontFamily = BrandFamily)

private val FlowPilotTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.brand(),
        displayMedium = t.displayMedium.brand(),
        displaySmall = t.displaySmall.brand(),
        headlineLarge = t.headlineLarge.brand(),
        headlineMedium = t.headlineMedium.brand(),
        headlineSmall = t.headlineSmall.brand(),
        titleLarge = t.titleLarge.brand(),
        titleMedium = t.titleMedium.brand(),
        titleSmall = t.titleSmall.brand(),
        bodyLarge = t.bodyLarge.brand(),
        bodyMedium = t.bodyMedium.brand(),
        bodySmall = t.bodySmall.brand(),
        labelLarge = t.labelLarge.brand(),
        labelMedium = t.labelMedium.brand(),
        labelSmall = t.labelSmall.brand(),
        displayLargeEmphasized = t.displayLargeEmphasized.brand(),
        displayMediumEmphasized = t.displayMediumEmphasized.brand(),
        displaySmallEmphasized = t.displaySmallEmphasized.brand(),
        headlineLargeEmphasized = t.headlineLargeEmphasized.brand(),
        headlineMediumEmphasized = t.headlineMediumEmphasized.brand(),
        headlineSmallEmphasized = t.headlineSmallEmphasized.brand(),
        titleLargeEmphasized = t.titleLargeEmphasized.brand(),
        titleMediumEmphasized = t.titleMediumEmphasized.brand(),
        titleSmallEmphasized = t.titleSmallEmphasized.brand(),
        bodyLargeEmphasized = t.bodyLargeEmphasized.brand(),
        bodyMediumEmphasized = t.bodyMediumEmphasized.brand(),
        bodySmallEmphasized = t.bodySmallEmphasized.brand(),
        labelLargeEmphasized = t.labelLargeEmphasized.brand(),
        labelMediumEmphasized = t.labelMediumEmphasized.brand(),
        labelSmallEmphasized = t.labelSmallEmphasized.brand(),
    )
}

private val FlowPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Radii the system names that [Shapes] doesn't carry. */
object Radius {
    val lg = RoundedCornerShape(16.dp)
    val lgIncreased = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(28.dp)
    val full = RoundedCornerShape(50)
}

@Composable
fun FlowPilotTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FlowPilotDark
        else -> FlowPilotLight
    }
    val reduceMotion = remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val motion = if (reduceMotion) ReducedMotion else MotionScheme.expressive()
    CompositionLocalProvider(LocalFlowPilotColors provides if (dark) CodeColorsDark else CodeColorsLight) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = motion,
            shapes = FlowPilotShapes,
            typography = FlowPilotTypography,
            content = content,
        )
    }
}

/** Every spec snaps, for people who turned animations off. */
private object ReducedMotion : MotionScheme {
    override fun <T> defaultSpatialSpec() = snap<T>()
    override fun <T> fastSpatialSpec() = snap<T>()
    override fun <T> slowSpatialSpec() = snap<T>()
    override fun <T> defaultEffectsSpec() = snap<T>()
    override fun <T> fastEffectsSpec() = snap<T>()
    override fun <T> slowEffectsSpec() = snap<T>()
}
