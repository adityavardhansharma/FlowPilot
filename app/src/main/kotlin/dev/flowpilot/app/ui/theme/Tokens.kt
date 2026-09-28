// The FlowPilot design system's tokens (Stone and Basalt). The reference is the design system artifact's
// tokens.json: change a value there first, then here, under the same name.
package dev.flowpilot.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class FpColors(
    /** Page background behind every surface. */
    val ground: Color,
    /** Default raised surface: cards, the composer, list rows. */
    val surface: Color,
    /** Above surfaces: sheets, menus, toasts, the selected segment. */
    val surfaceRaised: Color,
    /** Wells pressed into a surface: inputs, code, segmented tracks. */
    val surfaceSunken: Color,
    /** Decorative hairlines only. */
    val line: Color,
    /** Control outlines and dividers that must be seen: 3:1 on every surface. */
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Disabled labels only. */
    val inkFaint: Color,
    /** Lagoon: send, primary buttons, the working state, links, focus. */
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    /** Needs you. Nothing else is ever amber. */
    val amber: Color,
    val onAmber: Color,
    val amberSoft: Color,
    val onAmberSoft: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    val onDangerSoft: Color,
    /** Always paired with a check icon. */
    val success: Color,
    val scrim: Color,
    val isDark: Boolean,
) {
    val focus: Color get() = accent
}

val Stone = FpColors(
    ground = Color(0xFFEBEAE6), surface = Color(0xFFF6F5F2), surfaceRaised = Color(0xFFFDFCFA), surfaceSunken = Color(0xFFE2E0DB),
    line = Color(0xFFD5D3CD), lineStrong = Color(0xFF838079),
    ink = Color(0xFF1A1B1D), inkMuted = Color(0xFF5A5B5F), inkFaint = Color(0xFF7B7C80),
    accent = Color(0xFF1D6B66), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFD3E6E2), onAccentSoft = Color(0xFF134A46),
    amber = Color(0xFF8F5409), onAmber = Color(0xFFFFFFFF), amberSoft = Color(0xFFF3E2C3), onAmberSoft = Color(0xFF5A3405),
    danger = Color(0xFFA8321C), onDanger = Color(0xFFFFFFFF), dangerSoft = Color(0xFFF5DBD4), onDangerSoft = Color(0xFF6E1F10),
    success = Color(0xFF2F6B2B), scrim = Color(0x5C121315),
    isDark = false,
)

val Basalt = FpColors(
    ground = Color(0xFF121315), surface = Color(0xFF1A1B1E), surfaceRaised = Color(0xFF232428), surfaceSunken = Color(0xFF0D0E0F),
    line = Color(0xFF2C2D31), lineStrong = Color(0xFF76777C),
    ink = Color(0xFFECEBE7), inkMuted = Color(0xFFA4A4A8), inkFaint = Color(0xFF7D7E83),
    accent = Color(0xFF6CC9BE), onAccent = Color(0xFF0B2522), accentSoft = Color(0xFF173230), onAccentSoft = Color(0xFFAEE6DE),
    amber = Color(0xFFF0B45C), onAmber = Color(0xFF2A1A02), amberSoft = Color(0xFF34270F), onAmberSoft = Color(0xFFF7D7A4),
    danger = Color(0xFFFF8B73), onDanger = Color(0xFF3A0B02), dangerSoft = Color(0xFF3A1C16), onDangerSoft = Color(0xFFFFC3B5),
    success = Color(0xFF8CCB84), scrim = Color(0x99000000),
    isDark = true,
)

/**
 * Material components that are still in use (text fields, dialogs, the date of a snackbar host) read these roles.
 * They map onto the system's surfaces so nothing Material paints its own tonal tint: `surfaceTint` is transparent.
 */
internal fun FpColors.toMaterial(): ColorScheme =
    if (isDark) darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = onAccentSoft,
        inversePrimary = if (isDark) Stone.accent else Basalt.accent,
        secondary = accent, onSecondary = onAccent, secondaryContainer = accentSoft, onSecondaryContainer = onAccentSoft,
        tertiary = amber, onTertiary = onAmber, tertiaryContainer = amberSoft, onTertiaryContainer = onAmberSoft,
        background = ground, onBackground = ink, surface = ground, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted, surfaceTint = Color.Transparent,
        inverseSurface = ink, inverseOnSurface = ground,
        error = danger, onError = onDanger, errorContainer = dangerSoft, onErrorContainer = onDangerSoft,
        outline = lineStrong, outlineVariant = line, scrim = Color.Black,
        surfaceBright = surfaceRaised, surfaceDim = surfaceSunken,
        surfaceContainerLowest = surfaceRaised, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surfaceSunken,
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = onAccentSoft,
        inversePrimary = if (isDark) Stone.accent else Basalt.accent,
        secondary = accent, onSecondary = onAccent, secondaryContainer = accentSoft, onSecondaryContainer = onAccentSoft,
        tertiary = amber, onTertiary = onAmber, tertiaryContainer = amberSoft, onTertiaryContainer = onAmberSoft,
        background = ground, onBackground = ink, surface = ground, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted, surfaceTint = Color.Transparent,
        inverseSurface = ink, inverseOnSurface = ground,
        error = danger, onError = onDanger, errorContainer = dangerSoft, onErrorContainer = onDangerSoft,
        outline = lineStrong, outlineVariant = line, scrim = Color.Black,
        surfaceBright = surfaceRaised, surfaceDim = surfaceSunken,
        surfaceContainerLowest = surfaceRaised, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surfaceSunken,
    )

@Immutable
data class FlowPilotColors(
    val diffAddBg: Color,
    val diffAddInk: Color,
    val diffRemoveBg: Color,
    val diffRemoveInk: Color,
    val codeBg: Color,
    val codeInk: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeNumber: Color,
    val codeComment: Color,
    val codeFunction: Color,
    val terminalPrompt: Color,
)

/** Code sits in a sunken well; syntax colours stay muted so the accent keeps its meaning. */
internal val CodeColorsLight = FlowPilotColors(
    diffAddBg = Color(0xFFDCEAD6),
    diffAddInk = Color(0xFF24541F),
    diffRemoveBg = Color(0xFFF5DBD4),
    diffRemoveInk = Color(0xFF6E1F10),
    codeBg = Stone.surfaceSunken,
    codeInk = Stone.ink,
    codeKeyword = Color(0xFF6B3F8F),
    codeString = Color(0xFF2F6B2B),
    codeNumber = Color(0xFF8F5409),
    codeComment = Color(0xFF6A6B6F),
    codeFunction = Color(0xFF1D5C8A),
    terminalPrompt = Stone.accent,
)

internal val CodeColorsDark = FlowPilotColors(
    diffAddBg = Color(0xFF1A2C18),
    diffAddInk = Color(0xFFA9DCA2),
    diffRemoveBg = Color(0xFF3A1C16),
    diffRemoveInk = Color(0xFFFFC3B5),
    codeBg = Basalt.surfaceSunken,
    codeInk = Basalt.ink,
    codeKeyword = Color(0xFFCDB2EE),
    codeString = Color(0xFFA9DCA2),
    codeNumber = Color(0xFFF0C48A),
    codeComment = Color(0xFF8C8D92),
    codeFunction = Color(0xFF9CC7EE),
    terminalPrompt = Basalt.accent,
)
