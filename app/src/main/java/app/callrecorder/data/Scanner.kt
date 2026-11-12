package app.callrecorder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.ContactsContract
import android.provider.DocumentsContract
import org.json.JSONObject
import java.security.MessageDigest

/** Remembers the folder the user granted (read-only) via the Storage Access Framework. */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun treeUri(ctx: Context): Uri? = sp(ctx).getString("tree", null)?.let(Uri::parse)
    fun setTreeUri(ctx: Context, uri: Uri) = sp(ctx).edit().putString("tree", uri.toString()).apply()
}

/**
 * Walks the granted folder (and every subfolder) and keeps the
 * database in step with what is on disk. Read-only: it never writes, moves or deletes files.
 */
class Scanner(private val ctx: Context) {
    private val dao = AppDb.get(ctx).recordings()
    private val cr = ctx.contentResolver

    data class Result(val found: Int, val added: Int, val missing: Int)

    private data class FileRow(
        val docId: String, val name: String, val size: Long, val modified: Long,
        val folder: String, val propsDocId: String?,
    )

    suspend fun scan(): Result {
        val tree = Prefs.treeUri(ctx) ?: return Result(0, 0, 0)
        val files = mutableListOf<FileRow>()
        walk(tree, DocumentsContract.getTreeDocumentId(tree), files)

        val known = dao.all().associateBy { it.docId }
        val seen = HashSet<String>()
        var added = 0
        for (f in files) {
            seen += f.docId
            val old = known[f.docId]
            if (old != null && old.sizeBytes == f.size && old.lastModified == f.modified && !old.missing) {
                // contacts permission may have been granted after the first scan
                if ((old.contactName == null || old.contactPhoto == null) && old.number != null)
                    lookupContact(old.number)?.let { (n, ph) -> if (n != old.contactName || ph != old.contactPhoto) dao.update(old.copy(contactName = old.contactName ?: n, contactPhoto = ph)) }
                if (old.number == null && old.contactName != null && old.contactPhoto == null)
                    lookupByName(old.contactName)?.let { (num, ph) -> dao.update(old.copy(number = num, contactPhoto = ph)) }
                continue
            }
            val row = build(tree, f, old)
            if (old == null) { dao.insert(row); added++ } else dao.update(row.copy(id = old.id))
        }
        var missing = 0
        for (r in known.values) if (r.docId !in seen && !r.missing) { dao.update(r.copy(missing = true)); missing++ }
        dedupe()
        return Result(files.size, added, missing)
    }

    private fun walk(tree: Uri, parentId: String, out: MutableList<FileRow>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val dirs = mutableListOf<Pair<String, String>>()
        val audio = mutableListOf<FileRow>()
        var propsDir: String? = null
        cr.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (name == ".props") propsDir = id else if (!name.startsWith(".")) dirs += id to name
                } else if (name.substringAfterLast('.', "").lowercase() in AUDIO_EXT) {
                    audio += FileRow(id, name, c.getLong(3), c.getLong(4), folderOf(parentId), null)
                }
            }
        }
        val props = propsDir?.let { listProps(tree, it) } ?: emptyMap()
        audio.forEach { out += it.copy(propsDocId = props[FileNames.baseName(it.name)]) }
        dirs.forEach { (id, _) -> walk(tree, id, out) }
    }

    private fun listProps(tree: Uri, dirId: String): Map<String, String> {
        val m = HashMap<String, String>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
        cr.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.endsWith(".json")) m[name.removeSuffix(".json")] = c.getString(0)
            }
        }
        return m
    }

    private fun build(tree: Uri, f: FileRow, old: Recording?): Recording {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, f.docId)
        val info = FileNames.parse(f.name, hasSidecar = f.propsDocId != null)

        var number = info.number
        var direction: String? = null
        var duration = 0L
        f.propsDocId?.let { pid ->
            try {
                cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, pid))?.use { s ->
                    val j = JSONObject(s.readBytes().decodeToString())
                    duration = j.optString("duration").toLongOrNull() ?: 0L
                    if (number == null) number = j.optString("callee").ifBlank { null }
                    direction = j.optString("direction").ifBlank { null }?.lowercase()
                }
            } catch (_: Exception) {}
        }
        if (duration <= 0) duration = probeDuration(uri)
        val started = info.startedAt ?: (f.modified - duration)
        if (number == null && info.name != null) lookupByName(info.name)?.let { (num, _) -> number = num }
        val c = number?.let { lookupContact(it) }
        val contact = info.name ?: c?.first

        return Recording(
            id = old?.id ?: 0, docId = f.docId, uri = uri.toString(), fileName = f.name, folder = f.folder,
            source = info.source.name, sizeBytes = f.size, lastModified = f.modified,
            contentKey = contentKey(uri, f.size), startedAt = started, durationMs = duration,
            number = number, contactName = contact, contactPhoto = c?.second, direction = direction, app = info.app,
            dupOf = null, hidden = old?.hidden ?: false, missing = false,
            starred = old?.starred ?: false, positionMs = old?.positionMs ?: 0,
        )
    }

    /** Same audio in two places: keep one card, prefer the copy with the richest metadata. */
    private suspend fun dedupe() {
        val rows = dao.all().filter { !it.missing }
        for ((_, group) in rows.groupBy { it.contentKey }) {
            val primary = group.minWith(compareBy<Recording>({ RANK.indexOf(it.source) }, { it.folder.length }, { it.id }))
            for (r in group) {
                val want = if (r.id == primary.id) null else primary.id
                if (r.dupOf != want) dao.update(r.copy(dupOf = want))
            }
        }
    }

    private fun probeDuration(uri: Uri): Long = try {
        MediaMetadataRetriever().use { m ->
            m.setDataSource(ctx, uri)
            m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    } catch (_: Exception) { 0L }

    private fun contentKey(uri: Uri, size: Long): String = try {
        val md = MessageDigest.getInstance("SHA-1")
        cr.openInputStream(uri)?.use { s ->
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (total < buf.size) { val n = s.read(buf, total, buf.size - total); if (n <= 0) break; total += n }
            md.update(buf, 0, total)
        }
        "$size-" + md.digest().joinToString("") { "%02x".format(it) }.take(16)
    } catch (_: Exception) { "$size-?" }

    /** (number, photo) for a contact saved under exactly this name. */
    private fun lookupByName(name: String): Pair<String, String?>? = try {
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(name), null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }
    } catch (_: Exception) { null }

    /** (display name, photo thumbnail uri) from the phone's own contacts. */
    private fun lookupContact(number: String): Pair<String, String?>? {
        val digits = number.filter(Char::isDigit)
        val tries = buildList {
            add(number)
            if (!number.startsWith("+") && digits.length == 11 && digits.startsWith("1")) add("+$digits")
            if (digits.length == 10) add("+1$digits")
        }
        return tries.firstNotNullOfOrNull(::lookupOne)
    }

    private fun lookupOne(number: String): Pair<String, String?>? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        cr.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
        }
    } catch (_: Exception) { null }

    private fun folderOf(docId: String) = docId.substringAfter(':', docId)

    companion object {
        private val AUDIO_EXT = setOf("m4a", "mp3", "amr", "aac", "wav", "ogg", "opus", "3gp", "flac")
        private val RANK = listOf("IMPORTED", "OWN", "SAMSUNG", "OTHER")
    }
}
