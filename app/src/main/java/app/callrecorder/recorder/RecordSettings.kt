package app.callrecorder.recorder

import android.content.Context

/** Recording preferences (Settings › Recording). */
object RecordSettings {
    enum class Quality(val label: String, val bitrate: Int) {
        STANDARD("Standard", 64_000), HIGH("High", 128_000), BEST("Best", 192_000)
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun autoRecord(ctx: Context) = sp(ctx).getBoolean("autoRecord", true)
    fun setAutoRecord(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("autoRecord", v).apply()

    fun quality(ctx: Context) = runCatching { Quality.valueOf(sp(ctx).getString("quality", Quality.HIGH.name)!!) }.getOrDefault(Quality.HIGH)
    fun setQuality(ctx: Context, q: Quality) = sp(ctx).edit().putString("quality", q.name).apply()

    fun notifyAfter(ctx: Context) = sp(ctx).getBoolean("notifyAfter", true)
    fun setNotifyAfter(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("notifyAfter", v).apply()
}
