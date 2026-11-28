package com.monolith.app.data

import android.content.Context
import com.monolith.app.core.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** chats pinned at the top, archived, or marked as unread by hand */
class ChatOrg(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("chat_org", Context.MODE_PRIVATE)
    private fun read(key: String) = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private val _pinned = MutableStateFlow(read("pinned"))
    private val _archived = MutableStateFlow(read("archived"))
    private val _unread = MutableStateFlow(read("unread"))
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()
    val archived: StateFlow<Set<String>> = _archived.asStateFlow()
    val unread: StateFlow<Set<String>> = _unread.asStateFlow()

    private fun flip(flow: MutableStateFlow<Set<String>>, key: String, conv: String, on: Boolean) {
        flow.value = if (on) flow.value + conv else flow.value - conv
        prefs.edit().putStringSet(key, flow.value).apply()
    }

    @Synchronized fun setPinned(conv: String, on: Boolean) = flip(_pinned, "pinned", conv, on)
    @Synchronized fun setArchived(conv: String, on: Boolean) = flip(_archived, "archived", conv, on)
    @Synchronized fun setUnread(conv: String, on: Boolean) { if ((conv in _unread.value) != on) flip(_unread, "unread", conv, on) }

    fun clear() {
        _pinned.value = emptySet(); _archived.value = emptySet(); _unread.value = emptySet()
        prefs.edit().clear().apply()
    }
}

/** what you started writing in a chat and did not send yet */
class Drafts(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("drafts", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap())
    val all: StateFlow<Map<String, String>> = _all.asStateFlow()

    fun get(conv: String) = _all.value[conv].orEmpty()

    @Synchronized
    fun set(conv: String, text: String) {
        val t = text.take(4000)
        if (t.isBlank()) {
            if (conv !in _all.value) return
            _all.value = _all.value - conv
            prefs.edit().remove(conv).apply()
        } else {
            if (_all.value[conv] == t) return
            _all.value = _all.value + (conv to t)
            prefs.edit().putString(conv, t).apply()
        }
    }

    fun clear() {
        _all.value = emptyMap()
        prefs.edit().clear().apply()
    }
}

/** who received and read my messages in a group: message -> person -> (status, when) */
class Receipts(ctx: Context) {
    private val file = File(ctx.filesDir, "receipts.json")
    private val _all = MutableStateFlow<Map<String, Map<String, Pair<Int, Long>>>>(emptyMap())
    val all: StateFlow<Map<String, Map<String, Pair<Int, Long>>>> = _all.asStateFlow()

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                _all.value = o.keys().asSequence().associateWith { id ->
                    val m = o.getJSONObject(id)
                    m.keys().asSequence().associateWith { pk -> m.getJSONArray(pk).let { it.getInt(0) to it.getLong(1) } }
                }
            }
        }
    }

    private fun save() {
        runCatching {
            val o = JSONObject()
            _all.value.forEach { (id, m) -> o.put(id, JSONObject().also { j -> m.forEach { (pk, v) -> j.put(pk, JSONArray().put(v.first).put(v.second)) } }) }
            file.writeText(o.toString())
        }
    }

    @Synchronized
    fun add(id: String, pk: String, status: Int) {
        val cur = _all.value[id].orEmpty()
        val old = cur[pk]
        if (old != null && old.first >= status) return
        var next = _all.value + (id to (cur + (pk to (status to now()))))
        // keep the last 800 messages
        if (next.size > 800) next = next.entries.sortedByDescending { e -> e.value.values.maxOfOrNull { it.second } ?: 0L }.take(800).associate { it.key to it.value }
        _all.value = next
        save()
    }

    fun clear() {
        _all.value = emptyMap()
        runCatching { file.delete() }
    }
}

/** notifications of a group or a room: 0 everything, 1 only when someone tags me, 2 nothing */
class RoomNotify(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("room_notify", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap())
    val all: StateFlow<Map<String, Int>> = _all.asStateFlow()
    fun get(conv: String) = _all.value[conv] ?: 0

    @Synchronized
    fun set(conv: String, mode: Int) {
        _all.value = _all.value + (conv to mode)
        prefs.edit().putInt(conv, mode).apply()
    }

    fun clear() {
        _all.value = emptyMap()
        prefs.edit().clear().apply()
    }
}

/** a message that comes back as a reminder at a chosen time */
data class MsgReminder(val id: String, val conv: String, val msgId: String, val preview: String, val at: Long)

class MsgReminders(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("msg_reminders", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow<List<MsgReminder>>(emptyList())
    val all: StateFlow<List<MsgReminder>> = _all.asStateFlow()

    init {
        runCatching {
            val a = JSONArray(prefs.getString("all", "[]") ?: "[]")
            _all.value = (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { MsgReminder(it.getString("id"), it.getString("c"), it.getString("m"), it.optString("p"), it.getLong("at")) }
            }
        }
    }

    private fun save() {
        val a = JSONArray()
        _all.value.forEach { a.put(JSONObject().put("id", it.id).put("c", it.conv).put("m", it.msgId).put("p", it.preview).put("at", it.at)) }
        prefs.edit().putString("all", a.toString()).apply()
    }

    fun get(id: String) = _all.value.firstOrNull { it.id == id }

    @Synchronized fun add(r: MsgReminder) { _all.value = _all.value.filter { it.msgId != r.msgId } + r; save() }
    @Synchronized fun remove(id: String) { _all.value = _all.value.filter { it.id != id }; save() }

    fun clear() {
        _all.value = emptyList()
        prefs.edit().clear().apply()
    }
}
