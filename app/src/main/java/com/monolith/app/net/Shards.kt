package com.monolith.app.net

import android.content.Context
import com.monolith.app.Graph
import com.monolith.app.core.Profile
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.strings
import com.monolith.app.core.t
import com.monolith.app.core.toJsonArray
import com.monolith.app.crypto.Hex
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Group
import com.monolith.app.data.Message
import com.monolith.app.data.MsgStatus
import com.monolith.app.rtc.CallManager
import com.monolith.app.rtc.CallPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ShardRoom(val id: String, val name: String, val voice: Boolean)

/** a small private space of friends with written and voice rooms */
data class Shard(
    val id: String,
    val name: String,
    val emoji: String,
    val owner: String,
    val members: List<String>,
    val rooms: List<ShardRoom>,
    val updatedAt: Long,
) {
    fun conv(room: ShardRoom) = "g:" + groupId(id, room.id)

    companion object {
        const val PREFIX = "shard."
        fun groupId(shard: String, room: String) = "$PREFIX$shard.$room"
        /** true for the hidden groups that back shard rooms */
        fun isRoomGroup(groupId: String) = groupId.startsWith(PREFIX)
        fun isRoomConv(conv: String) = conv.startsWith("g:$PREFIX")
        const val ALBUM = "album."
        const val STORY = "story."
        /** groups that never appear in the chat list: shard rooms, albums, stories */
        fun isHiddenGroup(groupId: String) = groupId.startsWith(PREFIX) || groupId.startsWith(ALBUM) || groupId.startsWith(STORY)
        /** conversations that add up silently, without notifications */
        fun isQuietConv(conv: String) = conv.startsWith("g:$ALBUM") || conv.startsWith("g:$STORY")
    }
}

/**
 * Shards: every room is backed by a hidden group, so written rooms use the normal chat, and voice
 * rooms use the call engine without ringing anyone (join when you like, leave when you like).
 */
class ShardManager(ctx: Context, private val scope: CoroutineScope) {
    private val file = File(ctx.filesDir, "shards.json")
    private val _all = MutableStateFlow<List<Shard>>(emptyList())
    val all: StateFlow<List<Shard>> = _all.asStateFlow()

    /** "shard.room" -> who is in that voice room (pubkey -> seen at) */
    private val _voice = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val voice: StateFlow<Map<String, Map<String, Long>>> = _voice.asStateFlow()

    private val db get() = Graph.db
    private fun me() = Graph.identity.keys?.pub

    init {
        runCatching {
            if (file.exists()) {
                val a = JSONArray(file.readText())
                _all.value = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) }
            }
        }
    }

    fun start() {
        // while I'm in a voice room, keep telling the others
        scope.launch {
            while (true) {
                delay(25_000)
                currentRoom()?.let { (s, r) -> announce(s, r, true) }
                prune()
            }
        }
        scope.launch {
            var prev: String? = null
            Graph.calls.state.collect { st ->
                val room = st.cid?.takeIf { it.startsWith("room.") && (st.phase == CallPhase.ACTIVE || st.phase == CallPhase.CONNECTING) }
                if (room != prev) {
                    prev?.let { old -> parseRoom(old)?.let { (s, r) -> announce(s, r, false) } }
                    room?.let { parseRoom(it)?.let { (s, r) -> announce(s, r, true) } }
                    prev = room
                }
            }
        }
    }

    fun get(id: String) = _all.value.firstOrNull { it.id == id }

    private fun save() {
        runCatching {
            val a = JSONArray()
            _all.value.forEach { a.put(toJson(it)) }
            file.writeText(a.toString())
        }
    }

    private fun toJson(s: Shard): JSONObject = JSONObject()
        .put("id", s.id).put("n", s.name).put("e", s.emoji).put("o", s.owner)
        .put("m", s.members.toJsonArray()).put("u", s.updatedAt)
        .put("r", JSONArray().also { a -> s.rooms.forEach { r -> a.put(JSONObject().put("id", r.id).put("n", r.name).put("v", r.voice)) } })

    private fun fromJson(o: JSONObject): Shard? = runCatching {
        val rooms = o.optJSONArray("r") ?: JSONArray()
        Shard(
            id = o.getString("id"), name = o.optString("n"), emoji = o.optString("e", "🪨"), owner = o.optString("o"),
            members = (o.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) },
            rooms = (0 until rooms.length()).mapNotNull { i ->
                rooms.optJSONObject(i)?.let { r -> ShardRoom(r.optString("id").filter { c -> c.isLetterOrDigit() }.take(24), r.optString("n").take(40), r.optBoolean("v")) }
            }.filter { it.id.isNotBlank() }.take(30),
            updatedAt = o.optLong("u"),
        )
    }.getOrNull()

    /** keep the hidden groups of the rooms in step with the shard */
    private suspend fun syncGroups(s: Shard, old: Shard?) {
        val keep = s.rooms.map { Shard.groupId(s.id, it.id) }.toSet()
        old?.rooms?.forEach { r -> val g = Shard.groupId(s.id, r.id); if (g !in keep) db.groups().delete(g) }
        s.rooms.forEach { r ->
            val gid = Shard.groupId(s.id, r.id)
            val existing = db.groups().get(gid)
            val name = s.name + " · " + (if (r.voice) "🔊 " else "#") + r.name
            db.groups().upsert(Group(gid, name, s.emoji, s.members.joinToString(","), s.owner, existing?.createdAt ?: now()))
        }
    }

    private suspend fun removeLocal(id: String) {
        val s = get(id) ?: return
        s.rooms.forEach { db.groups().delete(Shard.groupId(s.id, it.id)) }
        _all.value = _all.value.filter { it.id != id }
        save()
    }

    private suspend fun store(s: Shard, old: Shard?) {
        _all.value = _all.value.filter { it.id != s.id } + s
        save()
        syncGroups(s, old)
    }

    private suspend fun payload(s: Shard): JSONObject {
        val k = me()
        val mp = JSONObject()
        if (k != null) mp.put(k, Graph.identity.profile.value?.toJson() ?: JSONObject())
        s.members.forEach { pk -> db.friends().get(pk)?.let { f -> mp.put(pk, Profile(f.username, f.displayName, f.emoji, f.bio).toJson()) } }
        return toJson(s).put("t", "shard").put("mp", mp)
    }

    private suspend fun broadcast(s: Shard, extra: List<String> = emptyList()) {
        val k = me() ?: return
        val p = payload(s)
        (s.members + extra).distinct().filter { it != k }.forEach { Graph.messenger.sendStored(it, p) }
    }

    // ------------------------------------------------------------------ incoming

    suspend fun onShard(from: String, p: JSONObject, isFriend: Boolean) {
        val k = me() ?: return
        val incoming = fromJson(p) ?: return
        val old = get(incoming.id)
        if (old == null && (!isFriend || from !in incoming.members)) return
        if (old != null && from !in old.members) return
        if (old != null && old.updatedAt >= incoming.updatedAt) return
        if (k !in incoming.members) { removeLocal(incoming.id); return }
        // people I don't know yet, so their names show up in the rooms
        p.optJSONObject("mp")?.let { mp ->
            incoming.members.filter { it != k }.forEach { pk ->
                if (db.friends().get(pk) == null) {
                    mp.optJSONObject(pk)?.let { o ->
                        val prof = Profile.fromJson(o)
                        db.friends().upsert(Friend(pk, prof.username, prof.displayName, prof.emoji, prof.bio, FriendStatus.KNOWN, now()))
                    }
                }
            }
        }
        // only the owner renames or changes rooms; anyone may add people
        val merged = if (old != null && from != old.owner) old.copy(members = (old.members + incoming.members).distinct(), updatedAt = incoming.updatedAt) else incoming
        store(merged, old)
        if (old == null) {
            val who = db.friends().get(from)?.displayName ?: t("Qualcuno")
            merged.rooms.firstOrNull { !it.voice }?.let { r ->
                db.messages().insert(Message(newId(), merged.conv(r), from, false, now(), "system", text = t("{0} ti ha invitato in {1}", who, merged.name), status = MsgStatus.READ))
            }
            Graph.notifier.shardInvite(who, merged.emoji + " " + merged.name)
        }
    }

    suspend fun onLeave(from: String, p: JSONObject) {
        val s = get(p.optString("id")) ?: return
        if (from !in s.members) return
        val members = s.members - from
        val owner = if (s.owner == from) members.minOrNull() ?: s.owner else s.owner
        store(s.copy(members = members, owner = owner, updatedAt = maxOf(now(), s.updatedAt + 1)), s)
    }

    fun onVoice(from: String, p: JSONObject) {
        val s = get(p.optString("s")) ?: return
        if (from !in s.members) return
        val key = s.id + "." + p.optString("r")
        val cur = _voice.value.toMutableMap()
        val room = (cur[key] ?: emptyMap()).toMutableMap()
        if (p.optBoolean("in")) room[from] = now() else room.remove(from)
        cur[key] = room
        _voice.value = cur
    }

    /** someone opened the shard: tell them where I am */
    fun onWho(from: String, p: JSONObject) {
        val s = get(p.optString("s")) ?: return
        if (from !in s.members) return
        val (sid, rid) = currentRoom() ?: return
        if (sid != s.id) return
        scope.launch { Graph.messenger.sendLive(from, JSONObject().put("t", "vroom").put("s", sid).put("r", rid).put("in", true)) }
    }

    // ------------------------------------------------------------------ actions

    suspend fun create(name: String, emoji: String, friends: List<String>): String? {
        val k = me() ?: return null
        val s = Shard(
            newId(), name.trim().ifBlank { t("Il mio shard") }, emoji, k, (friends + k).distinct(),
            listOf(ShardRoom(newId().take(8), t("generale"), false), ShardRoom(newId().take(8), t("Vocale"), true)),
            now(),
        )
        store(s, null)
        val first = s.rooms.first()
        db.messages().insert(Message(newId(), s.conv(first), k, true, now(), "system", text = t("Hai creato {0}", s.name), status = MsgStatus.READ))
        broadcast(s)
        return s.id
    }

    suspend fun invite(id: String, friends: List<String>) {
        val s = get(id) ?: return
        val n = s.copy(members = (s.members + friends).distinct(), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun removeMember(id: String, pk: String) {
        val s = get(id) ?: return
        if (s.owner != me() || pk == s.owner) return
        val n = s.copy(members = s.members - pk, updatedAt = now())
        store(n, s)
        broadcast(n, extra = listOf(pk))  // they get the list without themselves and drop it
    }

    suspend fun edit(id: String, name: String, emoji: String) {
        val s = get(id) ?: return
        if (s.owner != me()) return
        val n = s.copy(name = name.trim().ifBlank { s.name }, emoji = emoji, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun addRoom(id: String, name: String, voice: Boolean) {
        val s = get(id) ?: return
        if (s.owner != me()) return
        val clean = name.trim().ifBlank { if (voice) t("Vocale") else t("stanza") }.take(40)
        val n = s.copy(rooms = s.rooms + ShardRoom(newId().take(8), if (voice) clean else clean.lowercase().replace(' ', '-'), voice), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun removeRoom(id: String, roomId: String) {
        val s = get(id) ?: return
        if (s.owner != me() || s.rooms.size <= 1) return
        val n = s.copy(rooms = s.rooms.filter { it.id != roomId }, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun leave(id: String) {
        val k = me() ?: return
        val s = get(id) ?: return
        if (currentRoom()?.first == id) Graph.calls.hangup()
        s.members.filter { it != k }.forEach { Graph.messenger.sendStored(it, JSONObject().put("t", "shardleave").put("id", id)) }
        removeLocal(id)
    }

    // ------------------------------------------------------------------ voice rooms

    private fun parseRoom(cid: String): Pair<String, String>? {
        val p = cid.removePrefix("room.").split(".")
        return if (p.size == 2) p[0] to p[1] else null
    }

    /** (shard, room) of the voice room I'm in */
    fun currentRoom(): Pair<String, String>? {
        val st = Graph.calls.state.value
        if (st.phase != CallPhase.ACTIVE && st.phase != CallPhase.CONNECTING) return null
        return st.cid?.takeIf { it.startsWith("room.") }?.let(::parseRoom)
    }

    fun inRoom(shard: String, room: String): List<String> {
        val cutoff = now() - 70_000L
        return _voice.value["$shard.$room"].orEmpty().filter { it.value > cutoff }.keys.toList()
    }

    private fun prune() {
        val cutoff = now() - 70_000L
        _voice.value = _voice.value.mapValues { (_, m) -> m.filter { it.value > cutoff } }
    }

    private fun announce(shard: String, room: String, inside: Boolean) {
        val s = get(shard) ?: return
        val k = me() ?: return
        onVoice(k, JSONObject().put("s", shard).put("r", room).put("in", inside))
        val p = JSONObject().put("t", "vroom").put("s", shard).put("r", room).put("in", inside)
        scope.launch { s.members.filter { it != k }.forEach { Graph.messenger.sendLive(it, p) } }
    }

    /** ask who is in the voice rooms right now */
    fun ask(shard: String) {
        val s = get(shard) ?: return
        val k = me() ?: return
        val p = JSONObject().put("t", "vwho").put("s", shard)
        scope.launch { s.members.filter { it != k }.forEach { Graph.messenger.sendLive(it, p) } }
    }

    /** false when the room is full */
    fun joinVoice(shard: String, room: String): Boolean {
        val s = get(shard) ?: return false
        val r = s.rooms.firstOrNull { it.id == room && it.voice } ?: return false
        val k = me() ?: return false
        if (inRoom(shard, room).filter { it != k }.size >= CallManager.MAX_PEOPLE) return false
        if (Graph.calls.inCall) {
            if (currentRoom() == shard to room) return true
            Graph.calls.hangup()
        }
        Graph.calls.joinRoom("room.$shard.${r.id}", s.members, Shard.groupId(shard, r.id))
        return true
    }
}
