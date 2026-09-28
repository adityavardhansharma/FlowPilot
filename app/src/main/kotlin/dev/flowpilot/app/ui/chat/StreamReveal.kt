package dev.flowpilot.app.ui.chat

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import dev.flowpilot.core.chat.StreamPacer
import kotlinx.coroutines.flow.first

/**
 * How many characters of [text] to draw this frame. While [live], new text is revealed at a steady pace on the
 * display clock instead of in the clumps the network delivers; it keeps easing out the tail after the part
 * completes, so the end of an answer never snaps in. Text that was already there when the row appeared (history,
 * or reopening a chat mid-answer) shows at once. Idle rows cost nothing: the loop sleeps until the text grows.
 */
@Composable
fun rememberStreamReveal(text: String, live: Boolean): Int {
    val context = LocalContext.current
    val animate = remember {
        // "Remove animations" in accessibility settings sets the animator scale to 0: show text as it arrives.
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f
    }
    val start = remember { if (animate && live && text.length <= StreamPacer.REPLAY_LIMIT) 0 else text.length }
    var shown by remember { mutableIntStateOf(start) }
    val target by rememberUpdatedState(text)
    if (animate) LaunchedEffect(Unit) {
        val pacer = StreamPacer(start)
        while (true) {
            snapshotFlow { target.length }.first { it != shown }
            pacer.resume()
            do {
                shown = withFrameNanos { pacer.advance(target, it) }
            } while (shown != target.length)
        }
    }
    return if (animate) shown.coerceAtMost(text.length) else text.length
}
