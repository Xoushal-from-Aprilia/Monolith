package com.monolith.app.data

import android.content.Context
import androidx.room.ColumnInfo
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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

object FriendStatus {
    const val INCOMING = 0
    const val OUTGOING = 1
    const val FRIEND = 2
    /** profile known from a shared group, not a friend */
    const val KNOWN = 3
}

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val pubkey: String,
    val username: String,
    val displayName: String,
    val emoji: String,
    val bio: String = "",
    val status: Int,
    val createdAt: Long,
    val lastSeen: Long = 0,
    /** friend shares their location with me until (ms) */
    val sharingUntil: Long = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val acc: Float? = null,
    val locAt: Long = 0,
    /** I share my location with this friend until (ms) */
    val iShareUntil: Long = 0,
)

@Entity(tableName = "chat_groups")
data class Group(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    /** comma separated pubkeys, includes the admin */
    val members: String,
    val admin: String,
    val createdAt: Long,
) {
    fun memberList(): List<String> = members.split(",").filter { it.isNotBlank() }
}

object MsgStatus {
    const val FAILED = -1
    const val PENDING = 0
    const val SENT = 1
    const val DELIVERED = 2
    const val READ = 3
}

@Entity(tableName = "messages", indices = [Index("convId"), Index("ts")])
data class Message(
    @PrimaryKey val id: String,
    /** friend pubkey for direct chats, "g:<groupId>" for groups */
    val convId: String,
    val sender: String,
    val outgoing: Boolean,
    val ts: Long,
    /** text, photo, event, loc, nudge, system */
    val kind: String,
    val text: String = "",
    val mediaPath: String? = null,
    val extra: String? = null,
    val status: Int = MsgStatus.PENDING,
    val progress: Float = 1f,
)

data class UnreadCount(val convId: String, val n: Int)

object InviteState {
    const val MINE = 0
    const val PENDING = 1
    const val ACCEPTED = 2
    const val DECLINED = 3
}

@Entity(tableName = "agenda", indices = [Index("start")])
data class AgendaEvent(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val start: Long,
    @ColumnInfo(name = "endAt") val end: Long? = null,
    val allDay: Boolean = false,
    val emoji: String = "📌",
    /** minutes before start, -1 = no reminder */
    val reminderMin: Int = 15,
    /** pubkey of the creator; empty = me */
    val owner: String = "",
    /** comma separated invited pubkeys */
    val invitees: String = "",
    /** json object pubkey -> "yes"/"no"/"?" */
    val rsvp: String = "{}",
    val inviteState: Int = InviteState.MINE,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun inviteeList(): List<String> = invitees.split(",").filter { it.isNotBlank() }
}

@Entity(tableName = "processed")
data class Processed(@PrimaryKey val id: String, val ts: Long)

@Entity(tableName = "chunks", indices = [Index("msgId")])
data class Chunk(
    @PrimaryKey @ColumnInfo(name = "chunkKey") val key: String,
    val msgId: String,
    val idx: Int,
    val total: Int,
    val data: String,
    val ts: Long,
)

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY displayName COLLATE NOCASE")
    fun all(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 2 ORDER BY displayName COLLATE NOCASE")
    fun friends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 2")
    suspend fun friendsNow(): List<Friend>

    @Query("SELECT * FROM friends WHERE pubkey = :pk")
    suspend fun get(pk: String): Friend?

    @Query("SELECT * FROM friends WHERE pubkey = :pk")
    fun observe(pk: String): Flow<Friend?>

    @Upsert
    suspend fun upsert(f: Friend)

    @Query("DELETE FROM friends WHERE pubkey = :pk")
    suspend fun delete(pk: String)

    @Query("UPDATE friends SET lastSeen = :ts WHERE pubkey = :pk AND lastSeen < :ts")
    suspend fun seen(pk: String, ts: Long)

    @Query("UPDATE friends SET lat = :lat, lon = :lon, acc = :acc, locAt = :at, sharingUntil = MAX(sharingUntil, :until) WHERE pubkey = :pk")
    suspend fun location(pk: String, lat: Double, lon: Double, acc: Float, at: Long, until: Long)

    @Query("UPDATE friends SET sharingUntil = :until WHERE pubkey = :pk")
    suspend fun sharing(pk: String, until: Long)

    @Query("UPDATE friends SET iShareUntil = :until WHERE pubkey = :pk")
    suspend fun iShare(pk: String, until: Long)

    @Query("SELECT * FROM friends WHERE status = 2 AND iShareUntil > :now")
    suspend fun sharingTargets(now: Long): List<Friend>
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM chat_groups ORDER BY createdAt DESC")
    fun all(): Flow<List<Group>>

    @Query("SELECT * FROM chat_groups WHERE id = :id")
    suspend fun get(id: String): Group?

    @Query("SELECT * FROM chat_groups WHERE id = :id")
    fun observe(id: String): Flow<Group?>

    @Upsert
    suspend fun upsert(g: Group)

    @Query("DELETE FROM chat_groups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE convId = :conv ORDER BY ts DESC LIMIT :limit")
    fun conversation(conv: String, limit: Int = 500): Flow<List<Message>>

    @Query(
        "SELECT m.* FROM messages m INNER JOIN (SELECT convId AS c, MAX(ts) AS mts FROM messages GROUP BY convId) x " +
            "ON m.convId = x.c AND m.ts = x.mts ORDER BY m.ts DESC"
    )
    fun lastMessages(): Flow<List<Message>>

    @Query("SELECT convId, COUNT(*) AS n FROM messages WHERE outgoing = 0 AND status < 3 GROUP BY convId")
    fun unread(): Flow<List<UnreadCount>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): Message?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Message): Long

    @Upsert
    suspend fun upsert(m: Message)

    @Query("UPDATE messages SET status = :status WHERE id = :id AND status < :status")
    suspend fun raiseStatus(id: String, status: Int)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: Int)

    @Query("SELECT id FROM messages WHERE convId = :conv AND outgoing = 0 AND status < 3")
    suspend fun unreadIds(conv: String): List<String>

    @Query("SELECT * FROM messages WHERE convId = :conv AND outgoing = 0 AND status < 3")
    suspend fun unreadList(conv: String): List<Message>

    @Query("UPDATE messages SET status = 3 WHERE convId = :conv AND outgoing = 0")
    suspend fun markRead(conv: String)

    @Query("SELECT * FROM messages WHERE outgoing = 1 AND status = 0 AND ts < :before")
    suspend fun pending(before: Long): List<Message>

    @Query("UPDATE messages SET progress = :p, mediaPath = COALESCE(:path, mediaPath) WHERE id = :id")
    suspend fun progress(id: String, p: Float, path: String?)

    @Query("DELETE FROM messages WHERE convId = :conv")
    suspend fun clear(conv: String)
}

@Dao
interface AgendaDao {
    @Query("SELECT * FROM agenda ORDER BY start")
    fun all(): Flow<List<AgendaEvent>>

    @Query("SELECT * FROM agenda WHERE inviteState != 3 AND start >= :from ORDER BY start")
    suspend fun upcoming(from: Long): List<AgendaEvent>

    @Query("SELECT * FROM agenda WHERE id = :id")
    suspend fun get(id: String): AgendaEvent?

    @Query("SELECT * FROM agenda WHERE id = :id")
    fun observe(id: String): Flow<AgendaEvent?>

    @Upsert
    suspend fun upsert(e: AgendaEvent)

    @Query("DELETE FROM agenda WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MiscDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markProcessed(p: Processed): Long

    @Query("DELETE FROM processed WHERE ts < :before")
    suspend fun pruneProcessed(before: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addChunk(c: Chunk): Long

    @Query("SELECT * FROM chunks WHERE msgId = :msgId ORDER BY idx")
    suspend fun chunks(msgId: String): List<Chunk>

    @Query("SELECT COUNT(*) FROM chunks WHERE msgId = :msgId")
    suspend fun chunkCount(msgId: String): Int

    @Query("DELETE FROM chunks WHERE msgId = :msgId")
    suspend fun clearChunks(msgId: String)

    @Query("DELETE FROM chunks WHERE ts < :before")
    suspend fun pruneChunks(before: Long)
}

@Database(
    entities = [Friend::class, Group::class, Message::class, AgendaEvent::class, Processed::class, Chunk::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friends(): FriendDao
    abstract fun groups(): GroupDao
    abstract fun messages(): MessageDao
    abstract fun agenda(): AgendaDao
    abstract fun misc(): MiscDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "monolith.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
