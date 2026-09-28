@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package dev.flowpilot.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.flowpilot.app.R

// ---------------------------------------------------------------- type

private fun onest(weight: Int) = Font(
    R.font.onest,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun geist(weight: Int) = Font(
    R.font.geist_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Onest: everything that is read. */
val BrandFamily = FontFamily(onest(400), onest(450), onest(500), onest(600), onest(700))

/** Geist Mono: code, paths, shell and diffs. */
val CodeFamily = FontFamily(geist(400), geist(500), geist(600))

/** The design system's type scale, by token name. */
object FpType {
    val display = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(600), fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em)
    val titleLg = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em)
    val title = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(600), fontSize = 17.sp, lineHeight = 24.sp)
    val bodyLg = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 25.sp)
    val body = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(450), fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val overline = TextStyle(fontFamily = BrandFamily, fontWeight = FontWeight(600), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em)
    val code = TextStyle(fontFamily = CodeFamily, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 20.sp)
    val codeSmall = TextStyle(fontFamily = CodeFamily, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp)
}

val CodeStyle = FpType.code
val CodeSmallStyle = FpType.codeSmall

/** Material roles, for the Material pieces still in use, read the same scale. */
private val FlowPilotTypography = Typography(
    displayLarge = FpType.display.copy(fontSize = 45.sp, lineHeight = 52.sp),
    displayMedium = FpType.display.copy(fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall = FpType.display,
    headlineLarge = FpType.titleLg.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = FpType.titleLg.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = FpType.titleLg.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = FpType.titleLg,
    titleMedium = FpType.title,
    titleSmall = FpType.label.copy(fontWeight = FontWeight(600)),
    bodyLarge = FpType.bodyLg,
    bodyMedium = FpType.body,
    bodySmall = FpType.caption,
    labelLarge = FpType.label,
    labelMedium = FpType.caption.copy(fontWeight = FontWeight(500)),
    labelSmall = FpType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(500)),
)

// ---------------------------------------------------------------- shape and space

/** Radii grow with size: keycaps, buttons, cards, the composer and sheets, pills. */
object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(20.dp)
    val lgIncreased = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(28.dp)
    val full = CircleShape
}

object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp
}

private val FlowPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = Radius.sm,
    medium = Radius.md,
    large = Radius.lg,
    extraLarge = Radius.xl,
)

// ---------------------------------------------------------------- motion

/** Quiet and physical: things settle, nothing bounces. */
object Motion {
    val settleEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val exitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    const val QUICK = 120
    const val SETTLE = 220
    const val SHEET = 320
    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK, easing = settleEasing)
    fun <T> settle(): FiniteAnimationSpec<T> = tween(SETTLE, easing = settleEasing)
    fun <T> sheet(): FiniteAnimationSpec<T> = tween(SHEET, easing = settleEasing)
}

private object SettleMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = Motion.settle()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = Motion.quick()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = Motion.sheet()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = Motion.settle()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = Motion.quick()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = Motion.sheet()
}

/** Every spec snaps, for people who turned animations off. */
private object ReducedMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

// ---------------------------------------------------------------- theme

val LocalFpColors = staticCompositionLocalOf { Stone }
val LocalFlowPilotColors = staticCompositionLocalOf { CodeColorsLight }

/** The design system, read inside [FlowPilotTheme]: `Fp.colors.accent`, `Fp.type.body`. */
object Fp {
    val colors: FpColors
        @Composable @ReadOnlyComposable get() = LocalFpColors.current
    val type: FpType get() = FpType
}

/** Code, diff and terminal colours. */
val MaterialTheme.code: FlowPilotColors
    @Composable @ReadOnlyComposable get() = LocalFlowPilotColors.current

@Composable
fun FlowPilotTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = if (dark) Basalt else Stone
    val reduceMotion = remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalFpColors provides colors,
        LocalFlowPilotColors provides if (dark) CodeColorsDark else CodeColorsLight,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors.toMaterial(),
            motionScheme = if (reduceMotion) ReducedMotion else SettleMotion,
            shapes = FlowPilotShapes,
            typography = FlowPilotTypography,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}
