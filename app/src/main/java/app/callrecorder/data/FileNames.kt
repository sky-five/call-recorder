package app.callrecorder.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a recording's file name tells us. All fields optional. */
data class NameInfo(
    val source: Source,
    val startedAt: Long? = null,
    val number: String? = null,
    val name: String? = null,
    val app: String = "phone",
)

enum class Source { OWN, IMPORTED, SAMSUNG, OTHER }

/**
 * Pure parsers for call-recording file names found on the phone:
 *  - ours:    <name or number> – 2026-11-11 8.21 PM.m4a      (older builds: CallRec_…)
 *  - common:  <app>_<yyyyMMdd-HHmmss>_<name>_<number>.m4a      e.g. phone_20261111-180119__15550102030
 *  - Samsung: Call <name or number>_<yyMMdd>_<HHmmss>.m4a      e.g. Call +15550104040_261105_191703
 */
object FileNames {
    private const val VOIP = "WhatsApp|Signal|Telegram|Meet|Messenger|Viber|Skype|Teams"
    private val READABLE = Regex("""^(.+?)(?: \(($VOIP)\))? – (\d{4}-\d{2}-\d{2} \d{1,2}\.\d{2} [AP]M)(?: \(\d+\))?$""")
    private val readableFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd h.mm a", java.util.Locale.US)
    private val OWN = Regex("""^CallRec_(?:(.+)_)?(\d{6}_\d{6})$""")
    private val APP_STYLE = Regex("""^([A-Za-z0-9]+)_(\d{8}-\d{6})_(.*)_([^_]*)$""")
    private val SAMSUNG = Regex("""^Call (.+)_(\d{6})_(\d{6})$""")
    private val PHONE_LIKE = Regex("""^\+?[\d\s()-]{3,}$""")

    private val ownFmt = DateTimeFormatter.ofPattern("yyMMdd_HHmmss")
    private val appStyleFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val samsungFmt = DateTimeFormatter.ofPattern("yyMMdd_HHmmss")

    fun baseName(fileName: String) = fileName.substringBeforeLast('.')

    fun parse(fileName: String, hasSidecar: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): NameInfo {
        val base = baseName(fileName)

        READABLE.matchEntire(base)?.let { m ->
            val who = m.groupValues[1].trim()
            val known = who != UNKNOWN && who != INTERNET && who != MIC_TEST
            val voip = m.groupValues[2].ifEmpty { null }
            return NameInfo(
                Source.OWN, time(m.groupValues[3], readableFmt, zone),
                number = who.takeIf { known && looksLikeNumber(it) },
                name = if (who == MIC_TEST) MIC_TEST else who.takeIf { known && !looksLikeNumber(it) },
                app = voip?.lowercase() ?: if (who == INTERNET) "internet" else "phone",
            )
        }
        OWN.matchEntire(base)?.let { m ->
            val who = m.groupValues[1].ifBlank { null }
            return NameInfo(
                Source.OWN, time(m.groupValues[2], ownFmt, zone),
                number = who?.takeIf { looksLikeNumber(it) },
                name = who?.takeUnless { looksLikeNumber(it) },
            )
        }
        SAMSUNG.matchEntire(base)?.let { m ->
            val who = m.groupValues[1]
            return NameInfo(
                Source.SAMSUNG, time("${m.groupValues[2]}_${m.groupValues[3]}", samsungFmt, zone),
                number = who.takeIf { looksLikeNumber(it) },
                name = who.takeUnless { looksLikeNumber(it) },
            )
        }
        APP_STYLE.matchEntire(base)?.let { m ->
            val t = time(m.groupValues[2], appStyleFmt, zone)
            if (t != null) {
                return NameInfo(
                    Source.IMPORTED, t,
                    number = m.groupValues[4].ifBlank { null },
                    name = m.groupValues[3].ifBlank { null },
                    app = appName(m.groupValues[1]),
                )
            }
        }
        return NameInfo(if (hasSidecar) Source.IMPORTED else Source.OTHER)
    }

    fun looksLikeNumber(s: String) = PHONE_LIKE.matches(s.trim())

    const val UNKNOWN = "Unknown caller"
    const val INTERNET = "Internet call"
    const val MIC_TEST = "Mic test"

    /** Human-readable name for a recording: "Jane Doe – 2026-11-11 8.21 PM.m4a". */
    fun readable(who: String, startedAt: Long, zone: ZoneId = ZoneId.systemDefault(), app: String? = null): String {
        val clean = who.replace(Regex("""[\\/:*?"<>|]"""), "").replace(Regex("""\s+"""), " ").trim().ifEmpty { UNKNOWN }.take(60)
        val t = java.time.Instant.ofEpochMilli(startedAt).atZone(zone).format(readableFmt)
        return if (app != null) "$clean ($app) – $t.m4a" else "$clean – $t.m4a"
    }

    private fun appName(prefix: String) = when (prefix.lowercase()) {
        "phone" -> "phone"
        "whatsapp", "whatsappbusiness" -> "whatsapp"
        else -> prefix.lowercase()
    }

    private fun time(s: String, f: DateTimeFormatter, zone: ZoneId): Long? = try {
        LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli()
    } catch (_: Exception) { null }
}
