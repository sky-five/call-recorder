package app.callrecorder.recorder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny on-device diagnostic log (app-private file). Never leaves the phone. */
object EventLog {
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private fun file(ctx: Context) = File(ctx.filesDir, "events.log")

    @Synchronized
    fun add(ctx: Context, msg: String) {
        android.util.Log.i("CallRecorder", msg)
        val f = file(ctx)
        f.appendText("${fmt.format(Date())}  $msg\n")
        if (f.length() > 200_000) f.writeText(f.readLines().takeLast(500).joinToString("\n") + "\n")
    }

    fun read(ctx: Context, lines: Int = 60): List<String> =
        file(ctx).takeIf { it.exists() }?.readLines()?.takeLast(lines)?.reversed() ?: emptyList()
}
