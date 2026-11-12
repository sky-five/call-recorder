package app.callrecorder.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Finds a recording in Android's media index (needed to trash it or hand it to other apps). */
object Media {
    fun uriOf(ctx: Context, r: Recording): Uri? {
        val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return runCatching {
            ctx.contentResolver.query(
                coll, arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(r.fileName, r.folder.trimEnd('/') + "/"), null,
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(coll, c.getLong(0)) else null }
        }.getOrNull()
    }
}
