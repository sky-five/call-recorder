package app.callrecorder.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.callrecorder.recorder.EventLog

/**
 * Renames recordings made by this app (older CallRec_ names) to the readable format.
 * Only runs when the user taps "Tidy up file names". Files made by other apps are never touched.
 */
object Tidy {
    data class Plan(val uri: Uri, val from: String, val to: String)

    private val OLD = Regex("""^CallRec_.*""")

    suspend fun plan(ctx: Context): List<Plan> {
        val rows = AppDb.get(ctx).recordings().own().filter { OLD.matches(it.fileName) }
        return rows.mapNotNull { r ->
            val uri = mediaUri(ctx, r) ?: return@mapNotNull null
            val who = r.contactName ?: r.number?.let { app.callrecorder.ui.Fmt.phone(it) }
                ?: if (r.durationMs in 1..15_000) FileNames.MIC_TEST else FileNames.UNKNOWN
            Plan(uri, r.fileName, FileNames.readable(who, r.startedAt))
        }
    }

    /** Applies renames. Returns the ones Android needs the user's OK for (files from an older install). */
    fun apply(ctx: Context, plans: List<Plan>): List<Plan> {
        val needConsent = mutableListOf<Plan>()
        for (p in plans) {
            try {
                ctx.contentResolver.update(p.uri, ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, p.to) }, null, null)
                EventLog.add(ctx, "renamed ${p.from} -> ${p.to}")
            } catch (_: SecurityException) {
                needConsent += p
            } catch (e: Exception) {
                EventLog.add(ctx, "rename failed for ${p.from}: ${e.message}")
            }
        }
        return needConsent
    }

    private fun mediaUri(ctx: Context, r: Recording): Uri? {
        val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return ctx.contentResolver.query(
            coll, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(r.fileName, r.folder.trimEnd('/') + "/"), null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(coll, c.getLong(0)) else null }
    }
}
