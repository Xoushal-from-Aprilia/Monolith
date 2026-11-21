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
    /** identity confirmed in person with the QR code */
    val verified: Boolean = false,
    /** "on my way": distance (m) and eta (s) sent by the friend, -1 when not travelling */
    val distM: Int = -1,
    val etaSec: Int = -1,
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
    /** text, photo, voice, event, loc, nudge, poll, call, system, deleted */
    val kind: String,
    val text: String = "",
    val mediaPath: String? = null,
    val extra: String? = null,
    val status: Int = MsgStatus.PENDING,
    val progress: Float = 1f,
    /** disappearing messages: delete after this time (0 = never) */
    val expiresAt: Long = 0,
    val edited: Boolean = false,
)

data class UnreadCount(val convId: String, val n: Int)

data class ConvTs(val convId: String, val ts: Long)

@Entity(tableName = "reactions", primaryKeys = ["msgId", "sender"], indices = [Index("convId")])
data class Reaction(val msgId: String, val sender: String, val convId: String, val emoji: String, val ts: Long)

@Entity(tableName = "votes", primaryKeys = ["msgId", "voter"], indices = [Index("convId")])
data class Vote(val msgId: String, val voter: String, val convId: String, val options: String, val ts: Long) {
    fun list(): List<Int> = options.split(",").mapNotNull { it.toIntOrNull() }
}

@Entity(tableName = "conv_settings")
data class ConvSetting(@PrimaryKey val convId: String, val ttlSec: Long = 0, val muted: Boolean = false)

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
    /** comma separated minutes before start ("" = no reminder) */
    val reminders: String = "15",
    /** Recur.NONE / DAILY / WEEKLY / MONTHLY / YEARLY */
    val repeat: Int = 0,
    val repeatUntil: Long? = null,
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
    fun reminderList(): List<Int> = reminders.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
}

@Entity(tableName = "lists")
data class SharedList(
    @PrimaryKey val id: String,
    val title: String,
    val emoji: String,
    /** comma separated pubkeys, includes the owner */
    val members: String,
    val owner: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun memberList(): List<String> = members.split(",").filter { it.isNotBlank() }
}

@Entity(tableName = "list_items", indices = [Index("listId")])
data class ListItem(
    @PrimaryKey val id: String,
    val listId: String,
    val text: String,
    val done: Boolean,
    val by: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class ListProgress(val listId: String, val total: Int, val done: Int)

@Entity(tableName = "alerts", indices = [Index("friend")])
data class ArrivalAlert(
    @PrimaryKey val id: String,
    val friend: String,
    val lat: Double,
    val lon: Double,
    val radius: Int,
    val label: String,
    val createdAt: Long,
)

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

    @Query("SELECT * FROM friends")
    suspend fun allNow(): List<Friend>

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

    @Query("UPDATE friends SET lat = :lat, lon = :lon, acc = :acc, locAt = :at, sharingUntil = MAX(sharingUntil, :until), distM = :dist, etaSec = :eta WHERE pubkey = :pk")
    suspend fun location(pk: String, lat: Double, lon: Double, acc: Float, at: Long, until: Long, dist: Int, eta: Int)

    @Query("UPDATE friends SET sharingUntil = :until, distM = -1, etaSec = -1 WHERE pubkey = :pk")
    suspend fun sharing(pk: String, until: Long)

    @Query("UPDATE friends SET iShareUntil = :until WHERE pubkey = :pk")
    suspend fun iShare(pk: String, until: Long)

    @Query("UPDATE friends SET verified = :v WHERE pubkey = :pk")
    suspend fun verified(pk: String, v: Boolean)

    @Query("SELECT * FROM friends WHERE status = 2 AND iShareUntil > :now")
    suspend fun sharingTargets(now: Long): List<Friend>
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM chat_groups ORDER BY createdAt DESC")
    fun all(): Flow<List<Group>>

    @Query("SELECT * FROM chat_groups")
    suspend fun allNow(): List<Group>

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

    @Query("SELECT * FROM messages")
    suspend fun allNow(): List<Message>

    @Query("SELECT * FROM messages WHERE ts > :since AND kind NOT IN ('system', 'deleted')")
    suspend fun since(since: Long): List<Message>

    @Query("SELECT convId, MAX(ts) AS ts FROM messages WHERE kind NOT IN ('system', 'deleted') GROUP BY convId")
    suspend fun lastActivity(): List<ConvTs>

    @Query("SELECT convId, COUNT(*) AS n FROM messages WHERE outgoing = 0 AND status < 3 GROUP BY convId")
    suspend fun unreadNow(): List<UnreadCount>

    @Query("SELECT ts FROM messages WHERE convId = :conv AND kind NOT IN ('system', 'deleted', 'call') AND ts > :since")
    suspend fun timestamps(conv: String, since: Long): List<Long>

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :q || '%' AND kind NOT IN ('deleted', 'system', 'call') ORDER BY ts DESC LIMIT 150")
    suspend fun search(q: String): List<Message>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): Message?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Message): Long

    @Upsert
    suspend fun upsert(m: Message)

    @Query("UPDATE messages SET status = :status WHERE id = :id AND status < :status")
    suspend fun raiseStatus(id: String, status: Int)

    @Query("SELECT * FROM messages WHERE convId = :conv AND outgoing = 0 AND status < 3")
    suspend fun unreadList(conv: String): List<Message>

    @Query("UPDATE messages SET status = 3 WHERE convId = :conv AND outgoing = 0")
    suspend fun markRead(conv: String)

    @Query("SELECT * FROM messages WHERE outgoing = 1 AND status = 0 AND ts < :before")
    suspend fun pending(before: Long): List<Message>

    @Query("UPDATE messages SET progress = :p, mediaPath = COALESCE(:path, mediaPath) WHERE id = :id")
    suspend fun progress(id: String, p: Float, path: String?)

    @Query("UPDATE messages SET extra = :extra WHERE id = :id")
    suspend fun setExtra(id: String, extra: String)

    @Query("UPDATE messages SET mediaPath = NULL WHERE id = :id")
    suspend fun clearMedia(id: String)

    @Query("UPDATE messages SET text = :text, edited = 1 WHERE id = :id")
    suspend fun edit(id: String, text: String)

    @Query("UPDATE messages SET kind = 'deleted', text = '', mediaPath = NULL, extra = NULL WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM messages WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun expired(now: Long): List<Message>

    @Query("SELECT * FROM messages WHERE convId = :conv")
    suspend fun inConversation(conv: String): List<Message>

    @Query("DELETE FROM messages WHERE convId = :conv")
    suspend fun clear(conv: String)
}

@Dao
interface SocialDao {
    @Query("SELECT * FROM reactions WHERE convId = :conv")
    fun reactions(conv: String): Flow<List<Reaction>>

    @Upsert
    suspend fun react(r: Reaction)

    @Query("DELETE FROM reactions WHERE msgId = :msgId AND sender = :sender")
    suspend fun unreact(msgId: String, sender: String)

    @Query("SELECT * FROM votes WHERE convId = :conv")
    fun votes(conv: String): Flow<List<Vote>>

    @Upsert
    suspend fun vote(v: Vote)

    @Query("SELECT * FROM reactions")
    suspend fun allReactions(): List<Reaction>

    @Query("SELECT * FROM votes")
    suspend fun allVotes(): List<Vote>

    @Query("SELECT * FROM conv_settings WHERE convId = :conv")
    fun setting(conv: String): Flow<ConvSetting?>

    @Query("SELECT * FROM conv_settings WHERE convId = :conv")
    suspend fun settingNow(conv: String): ConvSetting?

    @Query("SELECT * FROM conv_settings")
    suspend fun allSettings(): List<ConvSetting>

    @Upsert
    suspend fun saveSetting(s: ConvSetting)

    @Query("SELECT * FROM alerts")
    fun alerts(): Flow<List<ArrivalAlert>>

    @Query("SELECT * FROM alerts WHERE friend = :pk")
    suspend fun alertsFor(pk: String): List<ArrivalAlert>

    @Query("SELECT * FROM alerts")
    suspend fun allAlerts(): List<ArrivalAlert>

    @Upsert
    suspend fun saveAlert(a: ArrivalAlert)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteAlert(id: String)
}

@Dao
interface AgendaDao {
    @Query("SELECT * FROM agenda ORDER BY start")
    fun all(): Flow<List<AgendaEvent>>

    @Query("SELECT * FROM agenda")
    suspend fun allNow(): List<AgendaEvent>

    @Query("SELECT * FROM agenda WHERE id = :id")
    suspend fun get(id: String): AgendaEvent?

    @Upsert
    suspend fun upsert(e: AgendaEvent)

    @Query("DELETE FROM agenda WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY updatedAt DESC")
    fun lists(): Flow<List<SharedList>>

    @Query("SELECT * FROM lists")
    suspend fun listsNow(): List<SharedList>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: String): SharedList?

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observe(id: String): Flow<SharedList?>

    @Upsert
    suspend fun upsert(l: SharedList)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM list_items WHERE listId = :list AND deleted = 0 ORDER BY done, updatedAt DESC")
    fun items(list: String): Flow<List<ListItem>>

    @Query("SELECT * FROM list_items WHERE listId = :list")
    suspend fun itemsNow(list: String): List<ListItem>

    @Query("SELECT * FROM list_items")
    suspend fun allItems(): List<ListItem>

    @Query("SELECT * FROM list_items WHERE id = :id")
    suspend fun item(id: String): ListItem?

    @Upsert
    suspend fun upsertItem(i: ListItem)

    @Query("DELETE FROM list_items WHERE listId = :list")
    suspend fun clearItems(list: String)

    @Query("SELECT listId, COUNT(*) AS total, SUM(CASE WHEN done THEN 1 ELSE 0 END) AS done FROM list_items WHERE deleted = 0 GROUP BY listId")
    fun progress(): Flow<List<ListProgress>>
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
    entities = [
        Friend::class, Group::class, Message::class, AgendaEvent::class, Processed::class, Chunk::class,
        Reaction::class, Vote::class, ConvSetting::class, SharedList::class, ListItem::class, ArrivalAlert::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friends(): FriendDao
    abstract fun groups(): GroupDao
    abstract fun messages(): MessageDao
    abstract fun agenda(): AgendaDao
    abstract fun misc(): MiscDao
    abstract fun social(): SocialDao
    abstract fun lists(): ListDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "monolith.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
