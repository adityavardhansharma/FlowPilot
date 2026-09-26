package dev.flowpilot.core.chat

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/** Number and time formatting rules from the brand book's content fundamentals. */
object Format {
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    /** "now", "2m", "3h", "Yesterday", "Sep 24". */
    fun relative(epochMs: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val diff = now - epochMs
        if (diff < 60_000) return "now"
        if (diff < 3_600_000) return "${diff / 60_000}m"
        val then = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (then == today) return "${diff / 3_600_000}h"
        if (then == today.minusDays(1)) return "Yesterday"
        return monthDay.format(then)
    }

    /** "0:42", then "12m", then "1h 5m". */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 600 -> "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
            s < 3600 -> "${s / 60}m"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }

    /** "812", "12.4k", "1.2M". */
    fun tokens(n: Double): String = when {
        n < 1000 -> n.roundToLong().toString()
        n < 1_000_000 -> trim(n / 1000) + "k"
        else -> trim(n / 1_000_000) + "M"
    }

    fun cost(usd: Double): String = if (usd < 0.005) "$0.00" else "$" + String.format(Locale.US, "%.2f", usd)

    /** "200k", "1M" for context windows. */
    fun context(n: Long): String = if (n >= 1_000_000) trim(n / 1_000_000.0) + "M" else "${n / 1000}k"

    private fun trim(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

    enum class Bucket(val label: String) { Today("Today"), Yesterday("Yesterday"), Week("This week"), Earlier("Earlier") }

    fun bucket(epochMs: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Bucket {
        val then = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then, today)
        return when {
            days <= 0 -> Bucket.Today
            days == 1L -> Bucket.Yesterday
            days < 7 -> Bucket.Week
            else -> Bucket.Earlier
        }
    }
}
