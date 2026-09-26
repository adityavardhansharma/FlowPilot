package dev.flowpilot.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.flowpilot.app.data.AppGraph

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph") }

val graph: AppGraph @Composable get() = LocalGraph.current

/** Plain words for any failure, following the microcopy table. */
fun Throwable.friendly(): String = when (this) {
    is dev.flowpilot.core.api.ApiException.Http -> when (code) {
        401 -> "Your pairing expired. Scan a new code to reconnect."
        403 -> "The computer refused that."
        404 -> "That no longer exists on your computer."
        else -> message ?: "Something went wrong."
    }
    is dev.flowpilot.core.api.ApiException -> message ?: "Something went wrong."
    is java.io.IOException -> "Can't reach your computer. Check that OpenCode is running, then retry."
    is kotlinx.serialization.SerializationException -> "Your computer sent something FlowPilot didn't understand. Update OpenCode and FlowPilot, then retry."
    // Name the error so a report says what actually failed, instead of a bare "Something went wrong".
    else -> message?.takeIf { it.isNotBlank() }?.let { "Something went wrong: $it" } ?: "Something went wrong (${this::class.java.simpleName})."
}
