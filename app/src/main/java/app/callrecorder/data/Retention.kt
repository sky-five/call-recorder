package app.callrecorder.data

import android.content.Context
import android.provider.MediaStore
import app.callrecorder.recorder.EventLog
import java.io.File

/**
 * "Delete after X days". Only touches recordings this app made itself (the only files it is allowed
 * to delete); recordings from other apps are read-only to us and are never removed. Starred
 * recordings are kept. Runs when the app opens and after each call is saved — no background jobs.
 */
object Retention {
    val CHOICES = listOf(0, 30, 90, 180, 365) // 0 = keep forever

    private fun sp(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun days(ctx: Context) = sp(ctx).getInt("keepDays", 0)
    fun setDays(ctx: Context, days: Int) = sp(ctx).edit().putInt("keepDays", days).apply()

    fun cutoff(days: Int) = System.currentTimeMillis() - days * 86_400_000L

    suspend fun wouldDelete(ctx: Context, days: Int): List<Recording> =
        if (days == 0) emptyList() else AppDb.get(ctx).recordings().ownOlderThan(cutoff(days))

    suspend fun runCleanup(ctx: Context) {
        val days = days(ctx)
        if (days == 0) return
        val old = wouldDelete(ctx, days)
        var n = 0
        for (r in old) if (deleteOwn(ctx, r)) n++
        if (old.isNotEmpty()) EventLog.add(ctx, "auto-delete (>$days days): removed $n of ${old.size}")
    }

    /** Deletes one recording this app made. Returns true if the file is gone. */
    suspend fun deleteOwn(ctx: Context, r: Recording): Boolean {
        if (r.source != Source.OWN.name) return false
        val gone = try {
            ctx.contentResolver.delete(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(r.fileName, r.folder.trimEnd('/') + "/"),
            ) > 0
        } catch (e: SecurityException) {
            EventLog.add(ctx, "can't delete ${r.fileName}: not owned by this app"); false
        }
        if (gone) {
            AppDb.get(ctx).recordings().markMissing(r.id)
            EventLog.add(ctx, "deleted ${r.fileName}")
        }
        return gone
    }

    /** One-time removal of files left by older builds that had on-device transcription. */
    fun removeLegacyFiles(ctx: Context) {
        File(ctx.filesDir, "models").takeIf { it.exists() }?.let { it.deleteRecursively(); EventLog.add(ctx, "removed old speech models") }
        File(ctx.filesDir, "my-voice.emb").delete()
    }
}
