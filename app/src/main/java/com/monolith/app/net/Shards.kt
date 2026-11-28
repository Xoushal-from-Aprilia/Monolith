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

/**
 * [only]: a private room, visible to these members only (plus owner and moderators); null = everyone.
 * [ro]: only the founder and the moderators write in it (announcements).
 */
data class ShardRoom(
    val id: String,
    val name: String,
    val voice: Boolean,
    val only: List<String>? = null,
    val ro: Boolean = false,
    /** a forum: posts with a title, each with its own replies */
    val forum: Boolean = false,
    /** the category it is listed under ("" = none) */
    val cat: String = "",
    /** slow mode: seconds between two messages of the same person (0 = off) */
    val slow: Int = 0,
)

/** a role of the shard: a name, a colour (a theme key) and what it allows */
data class ShardRole(val id: String, val name: String, val color: String, val mod: Boolean = false, val ann: Boolean = false)

/** an entry of the moderation log: [k] what happened (del, ban, unban, kick, mod, unmod, role, slow, words, spam, rules), to [who] */
data class ModEntry(val id: String, val at: Long, val by: String, val k: String, val who: String = "", val x: String = "")

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
    /** a few words on what the shard is about */
    val desc: String = "",
    /**
     * a shard for everyone: made by a verified developer, every Monolith user is inside without being invited
     * (also whoever signs up later). [members] then holds only the founder and the moderators.
     */
    val open: Boolean = false,
    /** the key that seals the messages of a shard for everyone (it is open to every Monolith user) */
    val key: String = "",
    /** people kept out of a shard for everyone */
    val ban: List<String> = emptyList(),
    /** the secret of the invite link ("" = no link) and whether whoever uses it waits for a yes */
    val link: String = "",
    val approve: Boolean = false,
    /** roles, and who has them: pk -> role ids */
    val roles: List<ShardRole> = emptyList(),
    val roleOf: Map<String, List<String>> = emptyMap(),
    /** the rules to accept before writing ("" = none) */
    val rules: String = "",
    /** words that are not allowed, and the automatic stop to repeated messages */
    val words: List<String> = emptyList(),
    val spam: Boolean = false,
    /** what the founder and the moderators did, newest first */
    val log: List<ModEntry> = emptyList(),
) {
    fun conv(room: ShardRoom) = "g:" + groupId(id, room.id)
    fun rolesOf(pk: String?): List<ShardRole> = pk?.let { p -> roleOf[p].orEmpty().mapNotNull { rid -> roles.firstOrNull { it.id == rid } } } ?: emptyList()
    fun canManage(pk: String?) = pk != null && (pk == owner || pk in mods || rolesOf(pk).any { it.mod })
    /** who is inside a room: everyone, or for a private room the chosen ones with owner and moderators */
    fun roomMembers(r: ShardRoom): List<String> = r.only?.let { o -> members.filter { it in o || it == owner || it in mods } } ?: members
    fun canSee(r: ShardRoom, pk: String?) = pk != null && (if (open) r.only == null && pk !in ban else pk in roomMembers(r))
    /** may this person write in the room? */
    fun canWrite(r: ShardRoom, pk: String?) = canSee(r, pk) && (!r.ro || canManage(pk) || rolesOf(pk).any { it.ann })
    /** is this person part of the shard (for a shard for everyone: anyone who is not kept out) */
    fun has(pk: String?) = pk != null && (if (open) pk !in ban else pk in members)

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
        /** "g:shard.<shard>.<room>" -> (shard, room) */
        fun parseConv(conv: String): Pair<String, String>? {
            if (!isRoomConv(conv)) return null
            val p = conv.removePrefix("g:$PREFIX").split(".")
            return if (p.size == 2) p[0] to p[1] else null
        }
        fun parseGroup(groupId: String) = parseConv("g:$groupId")
        /** accounts with this title, given by the developer, may make shards for everyone */
        fun isDeveloperTitle(title: String?) = title?.trim()?.lowercase() in setOf("sviluppatore", "sviluppatrice", "developer", "sviluppatori")
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

    // ------------------------------------------------------------------ lookups

    /** the shard behind a room conversation ("g:shard.<id>.<room>") */
    fun ofConv(conv: String): Pair<Shard, ShardRoom>? {
        val (sid, rid) = Shard.parseConv(conv) ?: return null
        val s = get(sid) ?: return null
        val r = s.rooms.firstOrNull { it.id == rid } ?: return null
        return s to r
    }
    fun ofGroup(groupId: String) = ofConv("g:$groupId")
    /** the shard for everyone behind a room conversation, if it is one */
    fun openFor(conv: String): Shard? = ofConv(conv)?.first?.takeIf { it.open }
    fun isOpenConv(conv: String) = openFor(conv) != null
    /** founder or moderator of the shard of this room */
    fun canModerate(conv: String, pk: String?) = ofConv(conv)?.first?.canManage(pk) == true

    private fun evJson(e: ShardEvent): JSONObject = JSONObject().put("id", e.id).put("x", e.title).put("e", e.emoji).put("s", e.start)
        .put("ad", e.allDay).put("by", e.by).put("u", e.updatedAt).put("go", e.going.toJsonArray()).put("del", e.deleted)

    private fun evFrom(o: JSONObject): ShardEvent? = runCatching {
        ShardEvent(
            o.getString("id").take(32), o.optString("x").take(80), o.optString("e", "📅").take(8), o.optLong("s"), o.optBoolean("ad"),
            o.optString("by"), o.optLong("u"), (o.optJSONArray("go") ?: JSONArray()).strings().filter { Hex.isHex64(it) }, o.optBoolean("del"),
        )
    }.getOrNull()

    internal fun toJson(s: Shard): JSONObject = JSONObject()
        .put("id", s.id).put("n", s.name).put("e", s.emoji).put("o", s.owner)
        .put("m", s.members.toJsonArray()).put("u", s.updatedAt).put("md", s.mods.toJsonArray())
        .put("r", JSONArray().also { a ->
            s.rooms.forEach { r ->
                a.put(JSONObject().put("id", r.id).put("n", r.name).put("v", r.voice).put("ro", r.ro).put("fo", r.forum).put("cat", r.cat).put("sl", r.slow)
                    .also { j -> r.only?.let { j.put("pm", it.toJsonArray()) } })
            }
        })
        .put("rl", JSONArray().also { a -> s.roles.forEach { r -> a.put(JSONObject().put("id", r.id).put("n", r.name).put("c", r.color).put("m", r.mod).put("a", r.ann)) } })
        .put("rof", JSONObject().also { o -> s.roleOf.forEach { (k, v) -> o.put(k, v.toJsonArray()) } })
        .put("rules", s.rules).put("wd", s.words.toJsonArray()).put("sp", s.spam)
        .put("lg", JSONArray().also { a -> s.log.take(100).forEach { e -> a.put(JSONObject().put("id", e.id).put("at", e.at).put("by", e.by).put("k", e.k).put("w", e.who).put("x", e.x)) } })
        .put("ds", s.desc).put("op", s.open).put("k", s.key).put("bn", s.ban.toJsonArray()).put("lk", s.link).put("ap", s.approve)
        .put("wl", s.welcome)
        .put("tt", JSONObject().also { o -> s.titles.forEach { (k, v) -> o.put(k, v) } })
        .put("nb", JSONArray().also { a -> s.notes.forEach { n -> a.put(JSONObject().put("id", n.id).put("x", n.text).put("by", n.by).put("at", n.at)) } })
        .put("ev", JSONArray().also { a -> s.events.filter { now() - it.start < 30L * 86_400_000L }.forEach { a.put(evJson(it)) } })

    internal fun fromJson(o: JSONObject): Shard? = runCatching {
        val rooms = o.optJSONArray("r") ?: JSONArray()
        Shard(
            id = o.getString("id"), name = o.optString("n"), emoji = o.optString("e", "🪨"), owner = o.optString("o"),
            members = (o.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) },
            rooms = (0 until rooms.length()).mapNotNull { i ->
                rooms.optJSONObject(i)?.let { r ->
                    ShardRoom(
                        r.optString("id").filter { c -> c.isLetterOrDigit() }.take(24), r.optString("n").take(40), r.optBoolean("v"),
                        r.optJSONArray("pm")?.strings()?.filter { Hex.isHex64(it) }, r.optBoolean("ro"),
                        r.optBoolean("fo"), r.optString("cat").take(30), r.optInt("sl").coerceIn(0, 3600),
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
            desc = o.optString("ds").take(500),
            open = o.optBoolean("op"),
            key = o.optString("k").takeIf { Hex.isHex64(it) } ?: "",
            ban = (o.optJSONArray("bn") ?: JSONArray()).strings().filter { Hex.isHex64(it) },
            link = o.optString("lk").filter { it.isLetterOrDigit() }.take(32),
            approve = o.optBoolean("ap"),
            roles = (o.optJSONArray("rl") ?: JSONArray()).let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { r -> ShardRole(r.optString("id").take(16), r.optString("n").take(24), r.optString("c").take(16), r.optBoolean("m"), r.optBoolean("a")) } }
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }.take(20),
            roleOf = o.optJSONObject("rof")?.let { m -> m.keys().asSequence().filter { Hex.isHex64(it) }.associateWith { (m.optJSONArray(it) ?: JSONArray()).strings().take(10) } } ?: emptyMap(),
            rules = o.optString("rules").take(2000),
            words = (o.optJSONArray("wd") ?: JSONArray()).strings().map { it.trim().lowercase().take(40) }.filter { it.isNotBlank() }.take(200),
            spam = o.optBoolean("sp"),
            log = (o.optJSONArray("lg") ?: JSONArray()).let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { e -> ModEntry(e.optString("id"), e.optLong("at"), e.optString("by"), e.optString("k").take(12), e.optString("w"), e.optString("x").take(120)) } }
            }.filter { it.id.isNotBlank() }.take(100),
        )
    }.getOrNull()?.let { if (it.open && it.key.isBlank()) null else it }

    /** the moderation log of two copies of the shard, newest first */
    private fun mergeLog(a: List<ModEntry>, b: List<ModEntry>): List<ModEntry> = (a + b).distinctBy { it.id }.sortedByDescending { it.at }.take(100)

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
        // a shard for everyone is described in public by its founder and moderators
        if (s.open) { if (s.canManage(k)) Graph.hubs.publishDef(s); return }
        val p = payload(s)
        (s.members + extra).distinct().filter { it != k }.forEach { Graph.messenger.sendStored(it, p) }
        // the card behind the invite link follows the shard
        if (s.link.isNotBlank() && s.canManage(k)) Invites.publishShard(s)
    }

    // ------------------------------------------------------------------ incoming

    suspend fun onShard(from: String, p: JSONObject, isFriend: Boolean) {
        val k = me() ?: return
        val incoming = fromJson(p) ?: return
        // a shard for everyone never travels in envelopes
        if (incoming.open) return
        val old = get(incoming.id)
        if (old?.open == true) return
        // from a friend, or the answer to my request with the invite link
        val asked = Invites.isPending(incoming.id)
        if (old == null && (!(isFriend || asked) || from !in incoming.members)) return
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
            old == null || from == old.owner -> incoming.copy(events = mergeEvents(old?.events ?: emptyList(), incoming.events), log = mergeLog(old?.log ?: emptyList(), incoming.log))
            old.canManage(from) -> old.copy(
                rooms = incoming.rooms,
                members = (incoming.members + old.owner + old.mods.filter { it in old.members }).distinct(),
                notes = incoming.notes,
                welcome = incoming.welcome,
                titles = incoming.titles,
                rules = incoming.rules,
                words = incoming.words,
                spam = incoming.spam,
                log = mergeLog(old.log, incoming.log),
                events = mergeEvents(old.events, incoming.events),
                updatedAt = incoming.updatedAt,
            )
            else -> old.copy(members = (old.members + incoming.members).distinct(), events = mergeEvents(old.events, incoming.events), updatedAt = incoming.updatedAt)
        }
        store(merged, old)
        if (old == null && asked) Invites.done(incoming.id)
        // whoever was waiting with the link and is now inside
        merged.members.forEach { Graph.joinAsks.remove("s:" + merged.id, it) }
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
        if (s.open || from !in s.members) return
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

    suspend fun create(name: String, emoji: String, friends: List<String>, desc: String = ""): String? {
        val k = me() ?: return null
        val s = Shard(
            newId(), name.trim().ifBlank { t("Il mio shard") }, emoji, k, (friends + k).distinct(),
            listOf(ShardRoom(newId().take(8), t("generale"), false), ShardRoom(newId().take(8), t("Vocale"), true)),
            now(), desc = desc.trim().take(500),
        )
        store(s, null)
        val first = s.rooms.first()
        db.messages().insert(Message(newId(), s.conv(first), k, true, now(), "system", text = t("Hai creato {0}", s.name), status = MsgStatus.READ))
        broadcast(s)
        return s.id
    }

    /** am I allowed to make shards for everyone? (verified by the developer with the title "Sviluppatore") */
    fun canMakeOpen(): Boolean = !com.monolith.app.kids.Kids.edition && Shard.isDeveloperTitle(me()?.let { Graph.dev.verifiedOf(it)?.title })

    /**
     * A shard for everyone: every Monolith user is inside, also whoever signs up later.
     * It starts with #annunci (only founder and moderators write) and #generale.
     */
    suspend fun createOpen(name: String, emoji: String, desc: String): String? {
        val k = me() ?: return null
        if (!canMakeOpen()) return null
        val key = Hex.encode(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        val s = Shard(
            newId(), name.trim().ifBlank { "Monolith" }, emoji, k, listOf(k),
            listOf(ShardRoom(newId().take(8), t("annunci"), false, ro = true), ShardRoom(newId().take(8), t("generale"), false)),
            now(), desc = desc.trim().take(500), open = true, key = key,
        )
        store(s, null)
        db.messages().insert(Message(newId(), s.conv(s.rooms.first()), k, true, now(), "system", text = t("Hai creato {0}: tutti gli utenti di Monolith sono dentro, anche chi arriva dopo", s.name), status = MsgStatus.READ))
        Graph.hubs.publishDef(s)
        Graph.hubs.refresh()
        return s.id
    }

    /** a shard for everyone described in public by [author] (founder or moderator); false = try again later */
    suspend fun onHubDef(author: String, o: JSONObject): Boolean {
        val k = me() ?: return true
        if (com.monolith.app.kids.Kids.edition) return true
        val incoming = fromJson(o) ?: return true
        if (!incoming.open) return true
        val old = get(incoming.id)
        if (old != null && !old.open) return true
        if (old == null) {
            // a moderator's copy waits for the founder's; the developer's list of verified accounts may come later
            if (author != incoming.owner || !Shard.isDeveloperTitle(Graph.dev.verifiedOf(author)?.title)) return false
            if (Graph.hubs.hasLeft(incoming.id)) { Graph.hubs.rememberLeft(incoming); return true }
            if (k in incoming.ban) return true
            val s = incoming.copy(members = (listOf(incoming.owner) + incoming.mods).distinct())
            store(s, null)
            joinedOpen(s)
            return true
        }
        if (old.updatedAt >= incoming.updatedAt) return true
        val merged = when (author) {
            old.owner -> incoming.copy(members = (listOf(incoming.owner) + incoming.mods).distinct(), events = mergeEvents(old.events, incoming.events), log = mergeLog(old.log, incoming.log))
            in old.mods, in old.roleOf.keys.filter { old.canManage(it) } -> old.copy(
                rooms = incoming.rooms, notes = incoming.notes, welcome = incoming.welcome, titles = incoming.titles, desc = incoming.desc,
                ban = incoming.ban.filter { it != old.owner && it !in old.mods }, rules = incoming.rules, words = incoming.words, spam = incoming.spam,
                log = mergeLog(old.log, incoming.log), updatedAt = incoming.updatedAt,
            )
            else -> return true
        }
        // kept out of it
        if (k in merged.ban) { removeLocal(merged.id); return true }
        store(merged, old)
        // what the people kept out wrote disappears
        val newly = merged.ban - old.ban.toSet()
        if (newly.isNotEmpty()) merged.rooms.forEach { r -> newly.forEach { pk -> db.messages().deleteFrom(merged.conv(r), pk) } }
        merged.rooms.filter { r -> old.rooms.none { it.id == r.id } && !r.ro && !r.voice }.forEach { Graph.messenger.setMuted(merged.conv(it), true) }
        if (merged.rooms.map { it.id } != old.rooms.map { it.id }) Graph.hubs.refresh()
        return true
    }

    /** I'm in a shard for everyone: say so once, quietly */
    private suspend fun joinedOpen(s: Shard) {
        val k = me() ?: return
        // a big room: no notification for every message; announcements and @mentions still arrive
        s.rooms.filter { !it.voice && !it.ro }.forEach { Graph.messenger.setMuted(s.conv(it), true) }
        s.rooms.firstOrNull { !it.voice && s.canSee(it, k) }?.let { r ->
            if (s.welcome.isNotBlank()) db.messages().insert(Message(newId(), s.conv(r), s.owner, false, now() + 1, "system", text = "👋  " + s.welcome, status = MsgStatus.READ))
            db.messages().insert(Message(newId(), s.conv(r), s.owner, false, now(), "system", text = t("Sei dentro {0}: lo Shard di tutti gli utenti di Monolith", s.name), status = MsgStatus.READ))
        }
        Graph.notifier.hubJoined(s.emoji + " " + s.name)
        Graph.hubs.refresh()
        Graph.hubs.backfill(s)
    }

    /** a shard for everyone I had left: back inside */
    suspend fun rejoin(id: String) {
        Graph.hubs.forgetLeft(id)
        Graph.hubs.reapply()
    }

    suspend fun invite(id: String, friends: List<String>) {
        val s = get(id) ?: return
        if (s.open) return
        val n = s.copy(members = (s.members + friends).distinct(), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun removeMember(id: String, pk: String) {
        val s = get(id) ?: return
        if (!s.canManage(me()) || pk == s.owner || (pk in s.mods && s.owner != me())) return
        if (s.open) { setBanned(id, pk, true); return }
        val n = s.copy(members = s.members - pk, mods = s.mods - pk, roleOf = s.roleOf - pk, log = (listOf(entry("kick", pk)) + s.log).take(100), updatedAt = now())
        store(n, s)
        broadcast(n, extra = listOf(pk))  // they get the list without themselves and drop it
    }

    /** founder and moderators of a shard for everyone: keep someone out (or let them back) */
    suspend fun setBanned(id: String, pk: String, on: Boolean) {
        val s = get(id) ?: return
        val k = me() ?: return
        if (!s.open || !s.canManage(k) || pk == s.owner || (pk in s.mods && s.owner != k)) return
        val n = s.copy(
            ban = if (on) (s.ban + pk).distinct() else s.ban - pk, mods = s.mods - pk, members = if (on) s.members - pk else s.members,
            roleOf = if (on) s.roleOf - pk else s.roleOf, log = (listOf(entry(if (on) "ban" else "unban", pk)) + s.log).take(100), updatedAt = now(),
        )
        store(n, s)
        if (on) s.rooms.forEach { r -> db.messages().deleteFrom(s.conv(r), pk) }
        broadcast(n)
    }

    suspend fun edit(id: String, name: String, emoji: String, desc: String? = null) {
        val s = get(id) ?: return
        if (s.owner != me()) return
        val n = s.copy(name = name.trim().ifBlank { s.name }, emoji = emoji, desc = desc?.trim()?.take(500) ?: s.desc, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun addRoom(id: String, name: String, voice: Boolean, forum: Boolean = false, ro: Boolean = false, cat: String = "") {
        val s = get(id) ?: return
        if (!s.canManage(me()) || (s.open && voice)) return
        val clean = name.trim().ifBlank { if (voice) t("Vocale") else t("stanza") }.take(40)
        val room = ShardRoom(newId().take(8), if (voice) clean else clean.lowercase().replace(' ', '-'), voice, forum = forum && !voice, ro = ro && !voice, cat = cat.trim().take(30))
        val n = s.copy(rooms = s.rooms + room, updatedAt = now())
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

    // ------------------------------------------------------------------ roles, categories, rules, moderation

    private fun entry(k: String, who: String = "", x: String = ""): ModEntry = ModEntry(newId().take(12), now(), me() ?: "", k, who, x.take(120))

    /** the founder: the roles of the shard */
    suspend fun setRoles(id: String, roles: List<ShardRole>) {
        val s = get(id) ?: return
        if (s.owner != me()) return
        val ids = roles.map { it.id }.toSet()
        val n = s.copy(roles = roles.take(20), roleOf = s.roleOf.mapValues { (_, v) -> v.filter { it in ids } }.filterValues { it.isNotEmpty() }, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** the founder: which roles someone has */
    suspend fun assignRoles(id: String, pk: String, roleIds: List<String>) {
        val s = get(id) ?: return
        if (s.owner != me() || !Hex.isHex64(pk)) return
        val valid = roleIds.filter { r -> s.roles.any { it.id == r } }.distinct()
        val n = s.copy(
            roleOf = if (valid.isEmpty()) s.roleOf - pk else s.roleOf + (pk to valid),
            members = if (s.open && valid.isNotEmpty()) (s.members + pk).distinct() else s.members,
            log = (listOf(entry("role", pk, valid.mapNotNull { r -> s.roles.firstOrNull { it.id == r }?.name }.joinToString(", "))) + s.log).take(100),
            updatedAt = now(),
        )
        store(n, s)
        broadcast(n)
    }

    /** founder and moderators: the category of a room */
    suspend fun setRoomCategory(id: String, roomId: String, cat: String) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(rooms = s.rooms.map { if (it.id == roomId) it.copy(cat = cat.trim().take(30)) else it }, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** founder and moderators: the order of the rooms */
    suspend fun reorderRooms(id: String, order: List<String>) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val byId = s.rooms.associateBy { it.id }
        val sorted = order.mapNotNull { byId[it] } + s.rooms.filter { it.id !in order }
        val n = s.copy(rooms = sorted, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** founder and moderators: seconds between two messages of the same person in a room (0 = off) */
    suspend fun setSlow(id: String, roomId: String, sec: Int) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val r = s.rooms.firstOrNull { it.id == roomId } ?: return
        val n = s.copy(
            rooms = s.rooms.map { if (it.id == roomId) it.copy(slow = sec.coerceIn(0, 3600)) else it },
            log = (listOf(entry("slow", "", "#" + r.name + " · " + sec)) + s.log).take(100),
            updatedAt = now(),
        )
        store(n, s)
        broadcast(n)
    }

    suspend fun setRules(id: String, text: String) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(rules = text.trim().take(2000), log = (listOf(entry("rules")) + s.log).take(100), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    suspend fun setAutomod(id: String, words: List<String>, spam: Boolean) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val clean = words.map { it.trim().lowercase().take(40) }.filter { it.isNotBlank() }.distinct().take(200)
        val n = s.copy(words = clean, spam = spam, log = (listOf(entry("words", "", clean.size.toString() + (if (spam) " · spam" else ""))) + s.log).take(100), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** a moderator deleted someone's message in a room: written in the log */
    suspend fun logDelete(conv: String, target: String) {
        val (s, r) = ofConv(conv) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(log = (listOf(entry("del", target, "#" + r.name)) + s.log).take(100), updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    // the rules: accepted on this phone, once for every version of the text
    private val rulesPrefs by lazy { Graph.app.getSharedPreferences("shard_rules", Context.MODE_PRIVATE) }
    private fun rulesKey(s: Shard) = s.id + ":" + s.rules.hashCode()
    fun mustAcceptRules(s: Shard) = s.rules.isNotBlank() && !s.canManage(me()) && !rulesPrefs.getBoolean(rulesKey(s), false)
    fun acceptRules(s: Shard) { rulesPrefs.edit().putBoolean(rulesKey(s), true).apply() }

    /** a word of the shard's list in this text, if any */
    fun badWord(s: Shard, text: String): String? {
        if (s.words.isEmpty()) return null
        val low = text.lowercase()
        return s.words.firstOrNull { w -> Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(w) + "($|[^\\p{L}\\p{N}])").containsMatchIn(low) }
    }

    private val lastBy = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<Pair<Long, String>>>()

    /** what the shard's rules let through, on every phone: slow mode, words, repeated messages */
    fun allowIncoming(s: Shard, r: ShardRoom, from: String, text: String, sentAt: Long): Boolean {
        if (s.canManage(from)) return true
        if (badWord(s, text) != null) return false
        val key = s.conv(r) + "|" + from
        val q = lastBy.getOrPut(key) { ArrayDeque() }
        synchronized(q) {
            val last = q.lastOrNull()
            if (r.slow > 0 && last != null && sentAt - last.first < r.slow * 800L) return false
            if (s.spam) {
                if (text.isNotBlank() && q.any { it.second == text && sentAt - it.first < 60_000L }) return false
                if (q.count { sentAt - it.first < 10_000L } >= 5) return false
            }
            q.addLast(sentAt to text)
            while (q.size > 12) q.removeFirst()
        }
        return true
    }

    /** founder and moderators: a room where only they write, or a room for everyone again */
    suspend fun setRoomReadOnly(id: String, roomId: String, ro: Boolean) {
        val s = get(id) ?: return
        if (!s.canManage(me())) return
        val n = s.copy(rooms = s.rooms.map { if (it.id == roomId) it.copy(ro = ro) else it }, updatedAt = now())
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
            else -> s.rolesOf(pk).firstOrNull()?.name
        }
    }

    /** the colour of someone's first role in a shard room (a theme key), if any */
    fun roleColorFor(conv: String, pk: String): String? = ofConv(conv)?.first?.rolesOf(pk)?.firstOrNull { it.color.isNotBlank() }?.color

    /** only the owner names moderators (in a shard for everyone, anyone who is not kept out) */
    suspend fun setMod(id: String, pk: String, on: Boolean) {
        val s = get(id) ?: return
        if (s.owner != me() || pk == s.owner || (if (s.open) pk in s.ban else pk !in s.members)) return
        val n = s.copy(
            mods = if (on) (s.mods + pk).distinct() else s.mods - pk,
            members = if (s.open) (if (on) (s.members + pk).distinct() else s.members - pk) else s.members,
            log = (listOf(entry(if (on) "mod" else "unmod", pk)) + s.log).take(100),
            updatedAt = now(),
        )
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
        if (s.open) { Graph.messenger.publishHub(s, "*", p, null); return }
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
        if (!s.has(from)) return
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
        if (s.open) {
            Graph.hubs.rememberLeft(s)
            removeLocal(id)
            Graph.hubs.refresh()
            return
        }
        s.members.filter { it != k }.forEach { Graph.messenger.sendStored(it, JSONObject().put("t", "shardleave").put("id", id)) }
        removeLocal(id)
    }

    // ------------------------------------------------------------------ invite link

    /** founder and moderators: make a new link (the old one stops working) or turn it off */
    suspend fun setLink(id: String, on: Boolean) {
        val s = get(id) ?: return
        val k = me() ?: return
        if (s.open || !s.canManage(k)) return
        if (s.link.isNotBlank()) Invites.revoke(s.link)
        val n = s.copy(link = if (on) Invites.newSecret() else "", updatedAt = now())
        store(n, s)
        broadcast(n)
        if (on) Invites.publishShard(n)
    }

    suspend fun setApprove(id: String, on: Boolean) {
        val s = get(id) ?: return
        if (s.open || !s.canManage(me())) return
        val n = s.copy(approve = on, updatedAt = now())
        store(n, s)
        broadcast(n)
    }

    /** someone used the link: in at once, or a request for the founder and the moderators */
    suspend fun onJoinAsk(from: String, p: JSONObject) {
        val s = get(p.optString("id")) ?: return
        val k = me() ?: return
        if (s.open || !s.canManage(k) || s.link.isBlank() || p.optString("s") != s.link || from in s.members) return
        val prof = p.optJSONObject("pf")?.let { Profile.fromJson(it) }
        if (prof != null && db.friends().get(from) == null) db.friends().upsert(Friend(from, prof.username, prof.displayName, prof.emoji, prof.bio, FriendStatus.KNOWN, now()))
        val name = db.friends().get(from)?.displayName ?: prof?.displayName ?: t("Qualcuno")
        if (s.approve) {
            Graph.joinAsks.add("s:" + s.id, from, name, prof?.emoji ?: "🙂", s.emoji + " " + s.name)
            return
        }
        admit(s.id, from, viaLink = true)
    }

    /** let in someone who asked with the link */
    suspend fun admit(id: String, pk: String, viaLink: Boolean = false) {
        val s = get(id) ?: return
        val k = me() ?: return
        Graph.joinAsks.remove("s:$id", pk)
        if (!s.canManage(k) || pk in s.members) return
        val n = s.copy(members = s.members + pk, updatedAt = now())
        store(n, s)
        broadcast(n)
        val name = db.friends().get(pk)?.displayName ?: t("Qualcuno")
        n.rooms.firstOrNull { !it.voice }?.let { r ->
            db.messages().insert(Message(newId(), n.conv(r), pk, false, now(), "system", text = if (viaLink) t("{0} è entrato con il link", name) else t("{0} è entrato", name), status = MsgStatus.READ))
        }
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
        val s = get(shard)?.takeIf { !it.open } ?: return
        val k = me() ?: return
        onVoice(k, JSONObject().put("s", shard).put("r", room).put("in", inside))
        val p = JSONObject().put("t", "vroom").put("s", shard).put("r", room).put("in", inside)
        scope.launch { s.members.filter { it != k }.forEach { Graph.messenger.sendLive(it, p) } }
    }

    /** ask who is in the voice rooms right now */
    fun ask(shard: String) {
        val s = get(shard)?.takeIf { !it.open } ?: return
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
