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

/** [only]: a private room, visible to these members only (plus owner and moderators); null = everyone */
data class ShardRoom(val id: String, val name: String, val voice: Boolean, val only: List<String>? = null)

/** a note pinned on the shard board */
data class ShardNote(val id: String, val text: String, val by: String, val at: Long)

/** an event of the shard: everyone sees it, each one says if they're going */
data class ShardEvent(
    val id: String,
    val title: String,
    val emoji: String,
    val start: Long,
    val allDay: Boolean,
    val by: String,
    val updatedAt: Long,
    val going: List<String> = emptyList(),
    val deleted: Boolean = false,
)

/** a small private space of friends with written and voice rooms */
data class Shard(
    val id: String,
    val name: String,
    val emoji: String,
    val owner: String,
    val members: List<String>,
    val rooms: List<ShardRoom>,
    val updatedAt: Long,
    /** moderators: manage rooms, members (not other moderators) and the board */
    val mods: List<String> = emptyList(),
    val notes: List<ShardNote> = emptyList(),
    val events: List<ShardEvent> = emptyList(),
    /** shown to whoever joins */
    val welcome: String = "",
    /** custom titles next to names: pk -> title */
    val titles: Map<String, String> = emptyMap(),
) {
    fun conv(room: ShardRoom) = "g:" + groupId(id, room.id)
    fun canManage(pk: String?) = pk != null && (pk == owner || pk in mods)
    /** who is inside a room: everyone, or for a private room the chosen ones with owner and moderators */
    fun roomMembers(r: ShardRoom): List<String> = r.only?.let { o -> members.filter { it in o || it == owner || it in mods } } ?: members
    fun canSee(r: ShardRoom, pk: String?) = pk != null && pk in roomMembers(r)

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

    private fun evJson(e: ShardEvent): JSONObject = JSONObject().put("id", e.id).put("x", e.title).put("e", e.emoji).put("s", e.start)
        .put("ad", e.allDay).put("by", e.by).put("u", e.updatedAt).put("go", e.going.toJsonArray()).put("del", e.deleted)

    private fun evFrom(o: JSONObject): ShardEvent? = runCatching {
        ShardEvent(
            o.getString("id").take(32), o.optString("x").take(80), o.optString("e", "📅").take(8), o.optLong("s"), o.optBoolean("ad"),
            o.optString("by"), o.optLong("u"), (o.optJSONArray("go") ?: JSONArray()).strings().filter { Hex.isHex64(it) }, o.optBoolean("del"),
        )
    }.getOrNull()

    private fun toJson(s: Shard): JSONObject = JSONObject()
        .put("id", s.id).put("n", s.name).put("e", s.emoji).put("o", s.owner)
        .put("m", s.members.toJsonArray()).put("u", s.updatedAt).put("md", s.mods.toJsonArray())
        .put("r", JSONArray().also { a -> s.rooms.forEach { r -> a.put(JSONObject().put("id", r.id).put("n", r.name).put("v", r.voice).also { j -> r.only?.let { j.put("pm", it.toJsonArray()) } }) } })
        .put("wl", s.welcome)
        .put("tt", JSONObject().also { o -> s.titles.forEach { (k, v) -> o.put(k, v) } })
        .put("nb", JSONArray().also { a -> s.notes.forEach { n -> a.put(JSONObject().put("id", n.id).put("x", n.text).put("by", n.by).put("at", n.at)) } })
        .put("ev", JSONArray().also { a -> s.events.filter { now() - it.start < 30L * 86_400_000L }.forEach { a.put(evJson(it)) } })

    private fun fromJson(o: JSONObject): Shard? = runCatching {
        val rooms = o.optJSONArray("r") ?: JSONArray()
        Shard(
            id = o.getString("id"), name = o.optString("n"), emoji = o.optString("e", "🪨"), owner = o.optString("o"),
            members = (o.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) },
            rooms = (0 until rooms.length()).mapNotNull { i ->
                rooms.optJSONObject(i)?.let { r ->
                    ShardRoom(
                        r.optString("id").filter { c -> c.isLetterOrDigit() }.take(24), r.optString("n").take(40), r.optBoolean("v"),
                        r.optJSONArray("pm")?.strings()?.filter { Hex.isHex64(it) },
                    )
                }
            }.filter { it.id.isNotBlank() }.take(30),
            updatedAt = o.optLong("u"),
            mods = (o.optJSONArray("md") ?: JSONArray()).strings().filter { Hex.isHex64(it) },
            notes = (o.optJSONArray("nb") ?: JSONArray()).let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { n -> ShardNote(n.optString("id"), n.optString("x").take(500), n.optString("by"), n.optLong("at")) } }
            }.filter { it.id.isNotBlank() }.take(30),
            events = (o.optJSONArray("ev") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::evFrom) } }.take(60),
            welcome = o.optString("wl").take(400),
            titles = o.optJSONObject("tt")?.let { t -> t.keys().asSequence().filter { Hex.isHex64(it) }.associateWith { t.optString(it).take(24) } } ?: emptyMap(),
        )
    }.getOrNull()

    /** events from two copies of the shard: the newest version of each one wins */
    private fun mergeEvents(a: List<ShardEvent>, b: List<ShardEvent>): List<ShardEvent> =
        (a + b).groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedAt } }.sortedBy { it.start }

    /** keep the hidden groups of the rooms in step with the shard */
    private suspend fun syncGroups(s: Shard, old: Shard?) {
        val keep = s.rooms.map { Shard.groupId(s.id, it.id) }.toSet()
        old?.rooms?.forEach { r -> val g = Shard.groupId(s.id, r.id); if (g !in keep) db.groups().delete(g) }
        s.rooms.forEach { r ->
            val gid = Shard.groupId(s.id, r.id)
            val existing = db.groups().get(gid)
            val name = s.name + " · " + (if (r.voice) "🔊 " else "#") + r.name
            db.groups().upsert(Group(gid, name, s.emoji, s.roomMembers(r).joinToString(","), s.owner, existing?.createdAt ?: now()))
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
        // the owner changes everything; moderators rooms, members and board; anyone may add people
        val merged = when {
            old == null || from == old.owner -> incoming.copy(events = mergeEvents(old?.events ?: emptyList(), incoming.events))
            from in old.mods -> old.copy(
                rooms = incoming.rooms,
                members = (incoming.members + old.owner + old.mods.filter { it in old.members }).distinct(),
                notes = incoming.notes,
                welcome = incoming.welcome,
                titles = incoming.titles,
                events = mergeEvents(old.events, incoming.events),
                updatedAt = incoming.updatedAt,
            )
            else -> old.copy(members = (old.members + incoming.members).distinct(), events = mergeEvents(old.events, incoming.events), updatedAt = incoming.updatedAt)
        }
        store(merged, old)
        if (old == null) {
            val who = db.friends().get(from)?.displayName ?: t("Qualcuno")
            merged.rooms.firstOrNull { !it.voice && merged.canSee(it, k) }?.let { r ->
                if (merged.welcome.isNotBlank()) db.messages().insert(Message(newId(), merged.conv(r), merged.owner, false, now() + 1, "system", text = "👋  " + merged.welcome, status = MsgStatus.READ))
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
        if (!s.canManage(me()) || pk == s.owner || (pk in s.mods && s.owner != me())) return
        val n = s.copy(members = s.members - pk, mods = s.mods - pk, updatedAt = now())
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
        if (!s.canManage(me())) return
        val clean = name.trim().ifBlank { if (voice) t("Vocale") else t("stanza") }.take(40)
        val n = s.copy(rooms = s.rooms + ShardRoom(newId().take(8), if (voice) clean else clean.lowercase().replace(' ', '-'), voice), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun removeRoom(id: String, roomId: String) {
        val s = get(id) ?: return
        if (!s.canManage(me()) || s.rooms.size <= 1) return
        val n = s.copy(rooms = s.rooms.filter { it.id != roomId }, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** owner and moderators: a room for some members only */
    suspend fun addPrivateRoom(id: String, name: String, voice: Boolean, only: List<String>) {
        val s = get(id) ?: return
        val k = me() ?: return
        if (!s.canManage(k)) return
        val room = ShardRoom(newId().filter { it.isLetterOrDigit() }.take(12), name.trim().take(40).ifBlank { t("Stanza privata") }, voice, (only + k).distinct())
        val n = s.copy(rooms = s.rooms + room, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun setRoomAccess(id: String, roomId: String, only: List<String>?) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(rooms = s.rooms.map { if (it.id == roomId) it.copy(only = only) else it }, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun setWelcome(id: String, text: String) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(welcome = text.trim().take(400), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun setTitle(id: String, pk: String, title: String) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(titles = if (title.isBlank()) s.titles - pk else s.titles + (pk to title.trim().take(24)), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** the title next to a name in a shard room */
    fun titleFor(conv: String, pk: String): String? {
        val s = _all.value.firstOrNull { sh -> sh.rooms.any { sh.conv(it) == conv } } ?: return null
        return s.titles[pk] ?: when (pk) {
            s.owner -> t("Fondatore")
            in s.mods -> t("Moderatore")
            else -> null
        }
    }

    /** only the owner names moderators */
    suspend fun setMod(id: String, pk: String, on: Boolean) {
        val s = get(id) ?: return
        if (s.owner != me() || pk == s.owner || pk !in s.members) return
        val n = s.copy(mods = if (on) (s.mods + pk).distinct() else s.mods - pk, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun addNote(id: String, text: String) {
        val s = get(id) ?: return
        val k = me() ?: return
        if (!s.canManage(k) || text.isBlank()) return
        val n = s.copy(notes = (listOf(ShardNote(newId(), text.trim().take(500), k, now())) + s.notes).take(30), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun removeNote(id: String, noteId: String) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(notes = s.notes.filter { it.id != noteId }, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    // ------------------------------------------------------------------ events: anyone in the shard

    private suspend fun sendEvent(s: Shard, e: ShardEvent) {
        val k = me() ?: return
        val p = JSONObject().put("t", "shardev").put("s", s.id).put("ev", evJson(e))
        s.members.filter { it != k }.forEach { Graph.messenger.sendStored(it, p) }
    }

    private suspend fun putEvent(s: Shard, e: ShardEvent) {
        val n = s.copy(events = mergeEvents(s.events, listOf(e)))
        _all.value = _all.value.filter { it.id != s.id } + n
        save()
    }

    suspend fun createEvent(id: String, title: String, emoji: String, start: Long, allDay: Boolean) {
        val s = get(id) ?: return
        val k = me() ?: return
        val e = ShardEvent(newId().take(16), title.trim().take(80).ifBlank { t("Evento") }, emoji, start, allDay, k, now(), listOf(k))
        putEvent(s, e)
        sendEvent(s, e)
        addToAgenda(s, e)
        s.rooms.firstOrNull { !it.voice }?.let { r ->
            db.messages().insert(Message(newId(), s.conv(r), k, true, now(), "system", text = t("Hai creato l'evento {0} · {1}", e.emoji + " " + e.title, com.monolith.app.core.Fmt.dayShort(e.start)), status = MsgStatus.READ))
        }
    }

    suspend fun setGoing(id: String, eventId: String, going: Boolean) {
        val s = get(id) ?: return
        val k = me() ?: return
        val e = s.events.firstOrNull { it.id == eventId } ?: return
        val n = e.copy(going = if (going) (e.going + k).distinct() else e.going - k, updatedAt = now())
        putEvent(s, n)
        sendEvent(s, n)
        if (going) addToAgenda(s, n) else db.agenda().delete("shev-" + e.id)
    }

    suspend fun deleteEvent(id: String, eventId: String) {
        val s = get(id) ?: return
        val k = me() ?: return
        val e = s.events.firstOrNull { it.id == eventId } ?: return
        if (e.by != k && !s.canManage(k)) return
        val n = e.copy(deleted = true, updatedAt = now())
        putEvent(s, n)
        sendEvent(s, n)
        db.agenda().delete("shev-" + e.id)
    }

    suspend fun onEvent(from: String, p: JSONObject) {
        val s = get(p.optString("s")) ?: return
        if (from !in s.members) return
        val e = p.optJSONObject("ev")?.let(::evFrom) ?: return
        val old = s.events.firstOrNull { it.id == e.id }
        if (old != null && old.updatedAt >= e.updatedAt) return
        // details are the creator's; anyone else only changes their own "going"
        val fixed = when {
            old == null -> e
            from == old.by || s.canManage(from) -> e
            else -> old.copy(going = if (from in e.going) (old.going + from).distinct() else old.going - from, updatedAt = e.updatedAt)
        }
        putEvent(s, fixed)
        val k = me() ?: return
        if (fixed.deleted) { db.agenda().delete("shev-" + fixed.id); return }
        if (k in fixed.going) addToAgenda(s, fixed)
        if (old == null) {
            val who = db.friends().get(from)?.displayName ?: t("Qualcuno")
            s.rooms.firstOrNull { !it.voice }?.let { r ->
                db.messages().insert(Message(newId(), s.conv(r), from, false, now(), "system", text = t("{0} ha creato l'evento {1} · {2}", who, fixed.emoji + " " + fixed.title, com.monolith.app.core.Fmt.dayShort(fixed.start)), status = MsgStatus.READ))
            }
            Graph.notifier.shardEvent(s.emoji + " " + s.name, fixed.emoji + " " + fixed.title + " · " + com.monolith.app.core.Fmt.dayShort(fixed.start))
        }
    }

    /** the events you're going to are copied into your agenda */
    private suspend fun addToAgenda(s: Shard, e: ShardEvent) {
        val id = "shev-" + e.id
        val cur = db.agenda().get(id)
        val ev = com.monolith.app.data.AgendaEvent(
            id = id, title = e.title, description = s.emoji + " " + s.name, start = e.start, allDay = e.allDay, emoji = e.emoji,
            reminders = cur?.reminders ?: "60", createdAt = cur?.createdAt ?: now(), updatedAt = now(),
        )
        db.agenda().upsert(ev)
        com.monolith.app.service.ReminderScheduler.rescheduleAll(Graph.app)
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
        if (!s.canSee(r, k)) return false
        Graph.calls.joinRoom("room.$shard.${r.id}", s.roomMembers(r), Shard.groupId(shard, r.id))
        return true
    }
}
