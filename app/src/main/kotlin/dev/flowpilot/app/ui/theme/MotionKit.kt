package dev.flowpilot.app.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Fade-through: the old content leaves on duration-exit, then the new one fades in on duration-fade from a 0.985
 * scale, settling on spring-settle. The two never overlap, so nothing ghosts. Used for tab switches and for any
 * content that is replaced in place.
 */
fun fadeThrough(): ContentTransform =
    (fadeIn(Motion.fadeIn(delay = Motion.EXIT)) + scaleIn(Motion.settle(), initialScale = 0.985f))
        .togetherWith(fadeOut(Motion.fadeOut()))
        .using(SizeTransform(clip = false) { _, _ -> Motion.settle() })

@Composable
fun <S> FadeThrough(
    targetState: S,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "fadeThrough",
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { fadeThrough() },
        contentAlignment = contentAlignment,
        label = label,
        content = content,
    )
}
