package app.callrecorder.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One audio file in the recordings folder. The database is only an index; files are the truth. */
@Entity(
    tableName = "recording",
    indices = [Index(value = ["docId"], unique = true), Index("contentKey"), Index("startedAt")],
)
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SAF document id; stable identity of the file on disk. */
    val docId: String,
    val uri: String,
    val fileName: String,
    /** Folder relative to shared storage, e.g. "Recordings/Call". */
    val folder: String,
    val source: String,
    val sizeBytes: Long,
    val lastModified: Long,
    /** size + hash of the first 64 KB; equal keys = same audio stored twice. */
    val contentKey: String,
    val startedAt: Long,
    val durationMs: Long,
    val number: String? = null,
    val contactName: String? = null,
    val contactPhoto: String? = null,
    val direction: String? = null,
    val app: String = "phone",
    /** Set when this file is a copy of another row. Copies are hidden from the list. */
    val dupOf: Long? = null,
    val hidden: Boolean = false,
    val missing: Boolean = false,
    val starred: Boolean = false,
    /** Where playback was left, to resume. */
    val positionMs: Long = 0,
)

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording WHERE dupOf IS NULL AND hidden = 0 AND missing = 0 ORDER BY startedAt DESC")
    fun library(): Flow<List<Recording>>

    @Query("SELECT * FROM recording WHERE id = :id")
    fun observe(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recording WHERE id = :id")
    suspend fun get(id: Long): Recording?

    @Query("SELECT * FROM recording")
    suspend fun all(): List<Recording>

    @Query("SELECT * FROM recording WHERE dupOf = :id")
    fun copiesOf(id: Long): Flow<List<Recording>>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM recording WHERE missing = 0")
    fun totalBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM recording WHERE missing = 0")
    fun fileCount(): Flow<Int>

    @Query("SELECT * FROM recording WHERE source = 'OWN' AND missing = 0 AND starred = 0 AND startedAt < :before")
    suspend fun ownOlderThan(before: Long): List<Recording>

    @Query("SELECT * FROM recording WHERE source = 'OWN' AND missing = 0")
    suspend fun own(): List<Recording>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(r: Recording): Long

    @Update
    suspend fun update(r: Recording)

    @Query("UPDATE recording SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE recording SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE recording SET positionMs = :pos WHERE id = :id")
    suspend fun setPosition(id: Long, pos: Long)

    @Query("UPDATE recording SET missing = 1 WHERE id = :id")
    suspend fun markMissing(id: Long)
}

@Database(entities = [Recording::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun recordings(): RecordingDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "library.db")
                // The database only indexes files on disk; an older layout is simply rebuilt by the next scan.
                .fallbackToDestructiveMigration(true)
                .build().also { instance = it }
        }
    }
}
