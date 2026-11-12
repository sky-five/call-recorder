package app.callrecorder.ui

import app.callrecorder.data.Recording
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayOfWeek = DateTimeFormatter.ofPattern("EEEE")
    private val dayThisYear = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val dayFull = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val full = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a")

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(time)
    fun full(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(full)

    fun dayHeader(d: LocalDate): String {
        val today = LocalDate.now(zone)
        return when {
            d == today -> "Today"
            d == today.minusDays(1) -> "Yesterday"
            d.isAfter(today.minusDays(7)) -> d.format(dayOfWeek)
            d.year == today.year -> d.format(dayThisYear)
            else -> d.format(dayFull)
        }
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun title(r: Recording): String = r.contactName ?: r.number?.let(::phone) ?: if (r.source == "OWN" && r.durationMs in 1..15_000) "Mic test" else "Unknown caller"
    fun subtitle(r: Recording): String? = if (r.contactName != null) r.number?.let(::phone) else null

    fun app(r: Recording) = when (r.app) {
        "phone" -> "Phone"
        "whatsapp" -> "WhatsApp"
        else -> r.app.replaceFirstChar { it.uppercase() }
    }

    fun source(r: Recording) = when (r.source) {
        "OWN" -> "Call Recorder"
        "IMPORTED" -> "Another recorder app"
        "SAMSUNG" -> "Samsung Phone"
        else -> "Other"
    }

    /** Light North-American formatting for display only; the stored number is untouched. */
    fun phone(n: String): String {
        val d = n.filter(Char::isDigit)
        return when {
            d.length == 11 && d.startsWith("1") -> "+1 ${d.substring(1, 4)}-${d.substring(4, 7)}-${d.substring(7)}"
            d.length == 10 -> "${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}"
            else -> n
        }
    }
}
