package com.monolith.app.data

import android.content.Context
import com.monolith.app.core.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** a message written now that leaves at [at] */
data class Scheduled(val id: String, val conv: String, val text: String, val at: Long)

class ScheduledStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("scheduled_msgs", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Scheduled>> = _all.asStateFlow()

    private fun load(): List<Scheduled> = runCatching {
        val a = JSONArray(sp.getString("list", "[]"))
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { Scheduled(it.getString("id"), it.getString("c"), it.getString("x"), it.getLong("at")) }
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Scheduled>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("c", it.conv).put("x", it.text).put("at", it.at)) }
        sp.edit().putString("list", a.toString()).apply()
        _all.value = list
    }

    @Synchronized fun add(s: Scheduled) = save(_all.value + s)
    @Synchronized fun remove(id: String) = save(_all.value.filter { it.id != id })
    fun get(id: String) = _all.value.firstOrNull { it.id == id }
    fun clear() = save(emptyList())
}

/** the message kept at the top of each conversation */
class PinStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("pins", Context.MODE_PRIVATE)
    private val _pins = MutableStateFlow(load())
    val pins: StateFlow<Map<String, String>> = _pins.asStateFlow()

    private fun load(): Map<String, String> = sp.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun set(conv: String, msgId: String?) {
        if (msgId.isNullOrBlank()) sp.edit().remove(conv).apply() else sp.edit().putString(conv, msgId).apply()
        _pins.value = load()
    }

    fun clear() {
        sp.edit().clear().apply()
        _pins.value = emptyMap()
    }
}

/** a 24-hour status: an emoji and a short sentence */
data class Status(val emoji: String, val text: String, val until: Long) {
    val active: Boolean get() = until > now()
}

class StatusStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("statuses", Context.MODE_PRIVATE)
    private val _friends = MutableStateFlow(load())
    val friends: StateFlow<Map<String, Status>> = _friends.asStateFlow()
    private val _mine = MutableStateFlow(parse(sp.getString("__me", null)))
    val mine: StateFlow<Status?> = _mine.asStateFlow()

    private fun parse(s: String?): Status? = runCatching {
        val o = JSONObject(s ?: return null)
        Status(o.optString("e"), o.optString("x"), o.optLong("u"))
    }.getOrNull()

    private fun enc(s: Status) = JSONObject().put("e", s.emoji).put("x", s.text).put("u", s.until).toString()

    private fun load(): Map<String, Status> {
        val out = HashMap<String, Status>()
        for ((k, v) in sp.all) {
            if (k == "__me") continue
            val st = parse(v as? String) ?: continue
            if (st.active) out[k] = st
        }
        return out
    }

    fun setFriend(pk: String, s: Status?) {
        if (s == null || !s.active) sp.edit().remove(pk).apply() else sp.edit().putString(pk, enc(s)).apply()
        _friends.value = load()
    }

    fun setMine(s: Status?) {
        if (s == null) sp.edit().remove("__me").apply() else sp.edit().putString("__me", enc(s)).apply()
        _mine.value = s
    }

    /** drop what expired */
    fun refresh() {
        _friends.value = load()
        if (_mine.value?.active == false) setMine(null)
    }

    fun clear() {
        sp.edit().clear().apply()
        _friends.value = emptyMap()
        _mine.value = null
    }
}
