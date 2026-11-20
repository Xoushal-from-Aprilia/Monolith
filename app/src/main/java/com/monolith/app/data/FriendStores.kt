package com.monolith.app.data

import android.content.Context
import com.monolith.app.core.now
import com.monolith.app.net.dayCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** the bond badge shows next to a name only from this many days in a row */
const val BOND_BADGE_DAYS = 100

/** days in a row ending today (or yesterday, if you haven't written yet today) */
fun streakOf(days: Set<String>): Int {
    if (days.isEmpty()) return 0
    val c = Calendar.getInstance()
    if (dayCode(c.timeInMillis) !in days) c.add(Calendar.DAY_OF_YEAR, -1)
    var n = 0
    while (dayCode(c.timeInMillis) in days) {
        n++
        c.add(Calendar.DAY_OF_YEAR, -1)
    }
    return n
}

/**
 * Days you and each friend wrote to each other. Kept apart from the messages so that the bond
 * survives disappearing messages and deleted chats.
 */
class BondDays(ctx: Context) {
    private val sp = ctx.getSharedPreferences("bond_days", Context.MODE_PRIVATE)
    private val _days = MutableStateFlow(load())
    val days: StateFlow<Map<String, Set<String>>> = _days.asStateFlow()

    private fun load(): Map<String, Set<String>> =
        sp.all.filterKeys { !it.startsWith("__") }.mapNotNull { (k, v) -> (v as? String)?.let { k to it.split(',').filter { d -> d.isNotBlank() }.toSet() } }.toMap()

    @Synchronized
    fun touch(pk: String, ts: Long) = add(pk, listOf(dayCode(minOf(ts, now()))))

    @Synchronized
    fun add(pk: String, codes: Collection<String>) {
        val cur = _days.value[pk] ?: emptySet()
        if (cur.containsAll(codes)) return
        // a little over a year is plenty for any streak worth showing
        val next = (cur + codes).sorted().takeLast(800).toSet()
        sp.edit().putString(pk, next.joinToString(",")).apply()
        _days.value = _days.value + (pk to next)
    }

    fun streak(pk: String) = streakOf(_days.value[pk] ?: emptySet())

    /** once: rebuild the history from the messages already on the phone */
    suspend fun backfill(db: AppDatabase) {
        if (sp.getBoolean("__filled", false)) return
        db.friends().friendsNow().forEach { f ->
            val codes = db.messages().timestamps(f.pubkey, 0L).map { dayCode(minOf(it, now())) }.toSet()
            if (codes.isNotEmpty()) add(f.pubkey, codes)
        }
        sp.edit().putBoolean("__filled", true).apply()
    }

    fun clear() {
        sp.edit().clear().apply()
        _days.value = emptyMap()
    }
}

/** friends you keep at the top of Clubhouse and of the widget */
class Favorites(ctx: Context) {
    private val sp = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(sp.getStringSet("set", emptySet())?.toSet() ?: emptySet())
    val all: StateFlow<Set<String>> = _all.asStateFlow()

    fun toggle(pk: String) {
        val next = if (pk in _all.value) _all.value - pk else _all.value + pk
        sp.edit().putStringSet("set", next).apply()
        _all.value = next
    }

    fun clear() {
        sp.edit().clear().apply()
        _all.value = emptySet()
    }
}

/** battery of the friends sharing their position with you; kept in memory only */
data class Battery(val level: Int, val charging: Boolean, val at: Long)

object FriendBattery {
    private val _all = MutableStateFlow<Map<String, Battery>>(emptyMap())
    val all: StateFlow<Map<String, Battery>> = _all.asStateFlow()
    private val warned = HashMap<String, Long>()

    /** returns true when it's time to warn that the phone is about to switch off */
    @Synchronized
    fun update(pk: String, level: Int, charging: Boolean): Boolean {
        if (level !in 0..100) return false
        _all.value = _all.value + (pk to Battery(level, charging, now()))
        if (charging || level > 10) return false
        val last = warned[pk] ?: 0L
        if (now() - last < 6 * 3_600_000L) return false
        warned[pk] = now()
        return true
    }

    fun fresh(pk: String): Battery? = _all.value[pk]?.takeIf { now() - it.at < 10 * 60_000L }
}

/** a message or photo you kept in a friend's profile; the photo is copied so it stays */
data class SavedItem(
    val id: String,
    val owner: String,
    val from: String,
    val kind: String,
    val text: String,
    val media: String?,
    val ts: Long,
    val savedAt: Long,
    val msgId: String,
)

class SavedStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "saved.json")
    private val dir = File(ctx.filesDir, "saved").apply { mkdirs() }
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<SavedItem>> = _all.asStateFlow()

    private fun load(): List<SavedItem> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            SavedItem(
                o.getString("id"), o.getString("o"), o.optString("f"), o.optString("k", "text"), o.optString("x"),
                o.optString("m").ifBlank { null }, o.optLong("ts"), o.optLong("at"), o.optString("mid"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<SavedItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("o", it.owner).put("f", it.from).put("k", it.kind).put("x", it.text)
                    .put("m", it.media ?: "").put("ts", it.ts).put("at", it.savedAt).put("mid", it.msgId),
            )
        }
        runCatching { file.writeText(a.toString()) }
        _all.value = list
    }

    fun isSaved(msgId: String) = _all.value.any { it.msgId == msgId }

    @Synchronized
    fun save(owner: String, m: Message) {
        if (isSaved(m.id)) return
        val id = com.monolith.app.core.newId()
        val media = m.mediaPath?.let { p ->
            val src = File(p)
            if (!src.exists()) null else runCatching {
                val dst = File(dir, id + "." + (src.extension.ifBlank { "bin" }))
                src.copyTo(dst, overwrite = true)
                dst.absolutePath
            }.getOrNull()
        }
        save(_all.value + SavedItem(id, owner, m.sender, m.kind, m.text, media, m.ts, now(), m.id))
    }

    @Synchronized
    fun remove(id: String) {
        _all.value.firstOrNull { it.id == id }?.media?.let { runCatching { File(it).delete() } }
        save(_all.value.filter { it.id != id })
    }

    @Synchronized
    fun unsaveMessage(msgId: String) {
        _all.value.filter { it.msgId == msgId }.forEach { remove(it.id) }
    }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        save(emptyList())
    }
}
