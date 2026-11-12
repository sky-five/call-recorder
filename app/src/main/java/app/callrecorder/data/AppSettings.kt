package app.callrecorder.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** All user preferences beyond recording quality (see recorder/RecordSettings). Stored on this phone only. */
object AppSettings {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // ---- Recording rules -------------------------------------------------------------------------
    enum class RecordOnly(val label: String) { EVERYONE("Everyone"), CONTACTS("Contacts only"), UNKNOWN("Unknown numbers only") }

    fun recordOnly(ctx: Context) = enumOr(sp(ctx).getString("recordOnly", null), RecordOnly.EVERYONE)
    fun setRecordOnly(ctx: Context, v: RecordOnly) = sp(ctx).edit().putString("recordOnly", v.name).apply()

    fun internetCalls(ctx: Context) = sp(ctx).getBoolean("internetCalls", true)
    fun setInternetCalls(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("internetCalls", v).apply()

    val MIN_LENGTHS = listOf(0, 5, 10, 30)
    fun minSeconds(ctx: Context) = sp(ctx).getInt("minSeconds", 0)
    fun setMinSeconds(ctx: Context, v: Int) = sp(ctx).edit().putInt("minSeconds", v).apply()

    val START_DELAYS = listOf(0, 1, 2, 3)
    fun startDelay(ctx: Context) = sp(ctx).getInt("startDelay", 0)
    fun setStartDelay(ctx: Context, v: Int) = sp(ctx).edit().putInt("startDelay", v).apply()

    /** "Don't record" list, stored as "digits|name" (digits = last 10 digits of the number). */
    data class Excluded(val digits: String, val name: String)

    fun excluded(ctx: Context): List<Excluded> = (sp(ctx).getStringSet("excluded", emptySet()) ?: emptySet())
        .map { it.split('|', limit = 2).let { p -> Excluded(p[0], p.getOrElse(1) { p[0] }) } }.sortedBy { it.name.lowercase() }

    fun addExcluded(ctx: Context, number: String, name: String) {
        val d = key(number) ?: return
        val set = (sp(ctx).getStringSet("excluded", emptySet()) ?: emptySet()).filterNot { it.startsWith("$d|") }.toMutableSet()
        set += "$d|$name"
        sp(ctx).edit().putStringSet("excluded", set).apply()
    }

    fun removeExcluded(ctx: Context, digits: String) {
        val set = (sp(ctx).getStringSet("excluded", emptySet()) ?: emptySet()).filterNot { it.startsWith("$digits|") }.toSet()
        sp(ctx).edit().putStringSet("excluded", set).apply()
    }

    fun isExcluded(ctx: Context, number: String?) = number != null && key(number)?.let { k -> excluded(ctx).any { it.digits == k } } == true

    /** Compares numbers by their last 10 digits, so "+1 647…" and "647…" match. */
    fun key(number: String): String? = number.filter(Char::isDigit).takeLast(10).takeIf { it.length >= 3 }

    // ---- Library ---------------------------------------------------------------------------------
    fun mainShowsAll(ctx: Context) = sp(ctx).getBoolean("mainShowsAll", false)
    fun setMainShowsAll(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mainShowsAll", v).apply()

    enum class GroupBy(val label: String) { DAY("Day"), PERSON("Person") }
    fun groupBy(ctx: Context) = enumOr(sp(ctx).getString("groupBy", null), GroupBy.DAY)
    fun setGroupBy(ctx: Context, v: GroupBy) = sp(ctx).edit().putString("groupBy", v.name).apply()

    val HIDE_SHORT = listOf(0, 5, 10, 30)
    fun hideShortSeconds(ctx: Context) = sp(ctx).getInt("hideShort", 0)
    fun setHideShortSeconds(ctx: Context, v: Int) = sp(ctx).edit().putInt("hideShort", v).apply()

    // ---- Playback --------------------------------------------------------------------------------
    enum class Boost(val label: String, val gainMb: Int) { LOW("Low", 600), MEDIUM("Medium", 1200), HIGH("High", 2000) }
    fun boostLevel(ctx: Context) = enumOr(sp(ctx).getString("boostLevel", null), Boost.MEDIUM)
    fun setBoostLevel(ctx: Context, v: Boost) = sp(ctx).edit().putString("boostLevel", v.name).apply()

    fun autoPlay(ctx: Context) = sp(ctx).getBoolean("autoPlay", false)
    fun setAutoPlay(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("autoPlay", v).apply()

    // ---- Look ------------------------------------------------------------------------------------
    enum class Theme(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
    /** Observed by the app theme so a change applies immediately. */
    val theme = mutableStateOf(Theme.SYSTEM)
    fun loadTheme(ctx: Context) { theme.value = enumOr(sp(ctx).getString("theme", null), Theme.SYSTEM) }
    fun setTheme(ctx: Context, v: Theme) { sp(ctx).edit().putString("theme", v.name).apply(); theme.value = v }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
