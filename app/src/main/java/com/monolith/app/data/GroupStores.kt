package com.monolith.app.data

import android.content.Context
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.strings
import com.monolith.app.core.t
import com.monolith.app.core.toJsonArray
import com.monolith.app.crypto.Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the table of groups can't hold (its shape never changes): description, administrators,
 * settings and look. Travels with the group to every member.
 */
data class GroupMeta(
    val id: String,
    val desc: String = "",
    /** administrators besides the creator */
    val admins: List<String> = emptyList(),
    /** everyone may change name, picture, description and look */
    val editAll: Boolean = false,
    /** everyone may add people (and share the link) */
    val addAll: Boolean = false,
    /** only the administrators write */
    val sendAdmins: Boolean = false,
    /** whoever uses the link waits for an administrator */
    val approve: Boolean = false,
    /** the secret of the invite link, "" = no link */
    val link: String = "",
    /** a colour theme for the chat (a palette key), "" = the app's colours */
    val color: String = "",
    /** a cover drawn by the app, "" = none */
    val cover: String = "",
    /** the quick reactions of the group, empty = the usual ones */
    val reactions: List<String> = emptyList(),
    /** titles next to names: pk -> title */
    val titles: Map<String, String> = emptyMap(),
    val updatedAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("ds", desc).put("ad", admins.toJsonArray()).put("ea", editAll).put("aa", addAll)
        .put("sa", sendAdmins).put("ap", approve).put("lk", link).put("cl", color).put("cv", cover).put("rx", reactions.toJsonArray())
        .put("tt", JSONObject().also { o -> titles.forEach { (k, v) -> o.put(k, v) } }).put("u", updatedAt)

    companion object {
        fun fromJson(id: String, o: JSONObject) = GroupMeta(
            id = id,
            desc = o.optString("ds").take(500),
            admins = (o.optJSONArray("ad") ?: JSONArray()).strings().filter { Hex.isHex64(it) }.distinct(),
            editAll = o.optBoolean("ea"),
            addAll = o.optBoolean("aa"),
            sendAdmins = o.optBoolean("sa"),
            approve = o.optBoolean("ap"),
            link = o.optString("lk").filter { it.isLetterOrDigit() }.take(32),
            color = o.optString("cl").take(16),
            cover = o.optString("cv").take(16),
            reactions = (o.optJSONArray("rx") ?: JSONArray()).strings().map { it.take(8) }.filter { it.isNotBlank() }.take(7),
            titles = o.optJSONObject("tt")?.let { t -> t.keys().asSequence().filter { Hex.isHex64(it) }.associateWith { t.optString(it).take(24) } } ?: emptyMap(),
            updatedAt = o.optLong("u"),
        )
    }
}

class GroupMetaStore(ctx: Context) {
    private val file = File(ctx.filesDir, "groups_meta.json")
    private val _all = MutableStateFlow<Map<String, GroupMeta>>(emptyMap())
    val all: StateFlow<Map<String, GroupMeta>> = _all.asStateFlow()

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                _all.value = o.keys().asSequence().associateWith { GroupMeta.fromJson(it, o.getJSONObject(it)) }
            }
        }
    }

    private fun save() {
        runCatching { file.writeText(JSONObject().also { o -> _all.value.forEach { (k, v) -> o.put(k, v.toJson()) } }.toString()) }
    }

    fun get(id: String): GroupMeta = _all.value[id] ?: GroupMeta(id)

    @Synchronized
    fun put(m: GroupMeta) {
        _all.value = _all.value + (m.id to m)
        save()
    }

    @Synchronized
    fun remove(id: String) {
        _all.value = _all.value - id
        save()
    }

    fun clear() {
        _all.value = emptyMap()
        runCatching { file.delete() }
    }

    /** the creator and the administrators */
    fun isAdmin(g: Group?, pk: String?): Boolean = g != null && pk != null && (g.admin == pk || pk in get(g.id).admins)

    suspend fun isAdmin(groupId: String, pk: String?) = isAdmin(Graph.db.groups().get(groupId), pk)

    /** a group where only the administrators write */
    suspend fun canWrite(groupId: String, pk: String?): Boolean {
        val m = get(groupId)
        return !m.sendAdmins || isAdmin(groupId, pk)
    }

    /** the title next to a name in a group chat */
    fun titleFor(g: Group?, pk: String): String? {
        g ?: return null
        val m = get(g.id)
        return m.titles[pk] ?: if (g.admin == pk || pk in m.admins) t("Amministratore") else null
    }
}

/** someone who used an invite link and waits for a yes ([target] = "g:<group>" or "s:<shard>") */
data class JoinAsk(val target: String, val pk: String, val name: String, val emoji: String, val at: Long)

class JoinAsks(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("join_asks", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow<List<JoinAsk>>(emptyList())
    val all: StateFlow<List<JoinAsk>> = _all.asStateFlow()

    init {
        runCatching {
            val a = JSONArray(prefs.getString("all", "[]") ?: "[]")
            _all.value = (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { JoinAsk(it.optString("t"), it.optString("p"), it.optString("n"), it.optString("e"), it.optLong("a")) }
            }.filter { now() - it.at < 30L * 86_400_000L }
        }
    }

    private fun save() {
        val a = JSONArray()
        _all.value.forEach { a.put(JSONObject().put("t", it.target).put("p", it.pk).put("n", it.name).put("e", it.emoji).put("a", it.at)) }
        prefs.edit().putString("all", a.toString()).apply()
    }

    fun forTarget(target: String) = _all.value.filter { it.target == target }

    @Synchronized
    fun add(target: String, pk: String, name: String, emoji: String, where: String) {
        val fresh = _all.value.none { it.target == target && it.pk == pk }
        _all.value = _all.value.filter { !(it.target == target && it.pk == pk) } + JoinAsk(target, pk, name, emoji, now())
        save()
        if (fresh) Graph.notifier.joinAsk(target, name, emoji, where)
    }

    @Synchronized
    fun remove(target: String, pk: String) {
        if (_all.value.none { it.target == target && it.pk == pk }) return
        _all.value = _all.value.filter { !(it.target == target && it.pk == pk) }
        save()
    }

    fun clear() {
        _all.value = emptyList()
        prefs.edit().clear().apply()
    }
}

/** conversations with an unread message that tags me (for the @ next to them) */
class Mentions(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("mentions", Context.MODE_PRIVATE)
    private val _convs = MutableStateFlow(prefs.getStringSet("convs", emptySet())?.toSet() ?: emptySet())
    val convs: StateFlow<Set<String>> = _convs.asStateFlow()

    @Synchronized
    fun mark(conv: String) {
        if (conv in _convs.value) return
        _convs.value = _convs.value + conv
        prefs.edit().putStringSet("convs", _convs.value).apply()
    }

    @Synchronized
    fun clear(conv: String) {
        if (conv !in _convs.value) return
        _convs.value = _convs.value - conv
        prefs.edit().putStringSet("convs", _convs.value).apply()
    }

    fun clearAll() {
        _convs.value = emptySet()
        prefs.edit().clear().apply()
    }
}
