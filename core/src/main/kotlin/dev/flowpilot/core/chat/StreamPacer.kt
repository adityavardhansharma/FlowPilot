package dev.flowpilot.core.chat

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max

/**
 * Turns bursty network text into a steady, frame-paced reveal (the approach EchoFlow uses).
 *
 * Deltas arrive in clumps: nothing for 300 ms, then a sentence at once. Drawing them as they land makes the reply
 * lurch. The pacer is fed the full text so far on every display frame and answers how many characters to show:
 * a steady base speed that eases up while a backlog builds and back down when it drains, with fractional
 * progress carried between frames so the cadence is the same at 60, 90 and 120 Hz. Positions are UTF-16 offsets
 * and always land on a grapheme boundary, so an emoji or accented letter is never split.
 *
 * Pure Kotlin with no clock of its own: the caller passes frame times, which keeps it unit-testable.
 */
class StreamPacer(start: Int = 0) {
    private var position = start.toDouble()
    private var lastEmitted = start
    private var speed = BASE_SPEED
    private var lastFrame: Long? = null
    private var bufferedSeconds = 0.0
    private var started = start > 0
    private val graphemes = BreakIterator.getCharacterInstance(Locale.ROOT)
    private var boundaryText = ""

    /** How many characters of [text] to show on the frame at [frameNanos]. Never goes backwards unless [text] shrinks. */
    fun advance(text: String, frameNanos: Long): Int {
        val previous = lastFrame
        lastFrame = frameNanos
        // A dropped frame or a resumed screen must not turn into a large dump of text.
        val dt = if (previous == null) 0.0 else ((frameNanos - previous) / 1_000_000_000.0).coerceIn(0.0, MAX_FRAME_SECONDS)
        if (position > text.length) {
            // The text was replaced by something shorter (a refetch healed a bad delta): start over from its end.
            position = text.length.toDouble()
            lastEmitted = text.length
            speed = BASE_SPEED
        }
        if (position < text.length) {
            if (!started) {
                // Hold the first characters a moment so the opening words come out as a phrase, not a stutter.
                bufferedSeconds += dt
                if (bufferedSeconds < START_BUFFER_SECONDS) return lastEmitted
                started = true
            }
            // Never trail by more than MAX_BEHIND characters: a model that writes faster than MAX_SPEED, or a phone
            // that was asleep, skips ahead instead of replaying a page of stale text.
            position = max(position, (text.length - MAX_BEHIND).toDouble())
            val remaining = text.length - position
            // A small backlog absorbs network jitter; a larger one speeds the reveal up gradually.
            val desired = (BASE_SPEED + remaining / CATCH_UP_SECONDS).coerceAtMost(MAX_SPEED)
            speed += (desired - speed) * (1.0 - exp(-dt / SPEED_RESPONSE_SECONDS))
            position = (position + speed * dt).coerceAtMost(text.length.toDouble())
        } else {
            // No reveal credit piles up during a network pause.
            speed += (BASE_SPEED - speed) * (1.0 - exp(-dt / SPEED_RESPONSE_SECONDS))
        }
        return onBoundary(position.toInt(), text)
    }

    /** Call when frames resume after a pause, so the gap is not counted as elapsed reveal time. */
    fun resume() { lastFrame = null }

    private fun onBoundary(candidate: Int, text: String): Int {
        var at = candidate.coerceAtLeast(lastEmitted).coerceAtMost(text.length)
        if (at in 1 until text.length) {
            if (boundaryText !== text) { boundaryText = text; graphemes.setText(text) }
            if (!graphemes.isBoundary(at)) {
                val back = graphemes.preceding(at)
                at = if (back > lastEmitted) back else graphemes.following(at).takeIf { it != BreakIterator.DONE } ?: text.length
            }
        }
        lastEmitted = at
        return at
    }

    companion object {
        /** Characters per second with no backlog: a brisk reading pace. */
        const val BASE_SPEED = 55.0
        const val MAX_SPEED = 260.0
        const val START_BUFFER_SECONDS = 0.08
        const val CATCH_UP_SECONDS = 0.65
        const val SPEED_RESPONSE_SECONDS = 0.18
        const val MAX_BEHIND = 600
        const val MAX_FRAME_SECONDS = 0.05

        /** Text longer than this that is already streaming when a row first appears is shown as is, not replayed. */
        const val REPLAY_LIMIT = 160
    }
}
