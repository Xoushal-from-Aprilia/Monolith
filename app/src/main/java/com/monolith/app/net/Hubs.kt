package com.monolith.app.net

import android.content.Context
import android.util.Base64
import com.monolith.app.BuildConfig
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.nowSec
import com.monolith.app.crypto.Hex
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.nostr.Envelope
import com.monolith.app.nostr.NostrEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shards for everyone. A verified developer makes one and every Monolith user is inside, also whoever
 * signs up later, without anyone sending invitations:
 *  - the shard is described by a public, replaceable event signed by its founder (and by each moderator
 *    for their changes); every app reads it and trusts it only if the founder is verified as a developer;
 *  - every message is ONE event for the whole room (not one envelope per person), sealed with the shard's key
 *    and signed by whoever wrote it. The shard is open to every Monolith user, so it is not private like a chat.
 * Not in Monolith Kids: a child only talks with the people the parent approved.
 */
class Hubs(private val ctx: Context, private val scope: CoroutineScope) {
    companion object {
        const val KIND_MSG = 4078
        const val TAG = "monolith-hub"
        const val D_PREFIX = "monolith-hub-"
        const val TTL = 30L * 86400
        /** the tag of a room (or of the whole shard, room "*") on the relays */
        fun roomTag(shard: String, room: String): String =
            Hex.encode(MessageDigest.getInstance("SHA-256").digest("monolith-hub|$shard|$room".toByteArray())).take(32)
    }

    private val prefs = ctx.getSharedPreferences("hubs", Context.MODE_PRIVATE)
    private val defs = ConcurrentHashMap<String, NostrEvent>()
    private val lock = Mutex()
    @Volatile private var started = false

    private var lastSec: Long
        get() = prefs.getLong("last", 0L)
        set(v) { prefs.edit().putLong("last", v).apply() }

    fun start() {
        if (started || com.monolith.app.kids.Kids.edition) return
        Graph.identity.keys ?: return
        started = true
        Graph.relays.subscribe("hubdefs") {
            listOf(JSONObject().put("kinds", JSONArray().put(Envelope.KIND_PROFILE)).put("#t", JSONArray().put(TAG)))
        }
        refresh()
        scope.launch {
            Graph.relays.events.collect { (sub, ev) ->
                runCatching {
                    when (sub) {
                        "hubdefs" -> onDef(ev)
                        "hub" -> onMsg(ev)
                    }
                }
            }
        }
        // the developer's list of verified accounts can arrive after the shards
        scope.launch { Graph.dev.state.map { it.verified.keys }.distinctUntilChanged().collect { reapply() } }
        // listen to the rooms of the shards I'm in
        scope.launch { Graph.shards.all.map { l -> l.filter { it.open }.flatMap { s -> s.rooms.map { s.id + it.id } } }.distinctUntilChanged().collect { refresh() } }
    }

    private fun openShards() = Graph.shards.all.value.filter { it.open }

    private fun tags(): List<String> = openShards().flatMap { s -> s.rooms.map { roomTag(s.id, it.id) } + roomTag(s.id, "*") }

    /** (re)subscribe to the rooms of my shards for everyone */
    fun refresh() {
        if (!started) return
        if (tags().isEmpty()) { Graph.relays.unsubscribe("hub"); return }
        Graph.relays.subscribe("hub") {
            val since = if (lastSec > 0) lastSec - 900 else nowSec() - 3 * 86400
            listOf(JSONObject().put("kinds", JSONArray().put(KIND_MSG)).put("#h", JSONArray(tags())).put("since", since).put("limit", 500))
        }
    }

    /** just inside: the last days of the rooms, so they are not empty */
    fun backfill(s: Shard) {
        scope.launch {
            val tags = s.rooms.map { roomTag(s.id, it.id) } + roomTag(s.id, "*")
            val f = JSONObject().put("kinds", JSONArray().put(KIND_MSG)).put("#h", JSONArray(tags)).put("since", nowSec() - 3 * 86400).put("limit", 300)
            runCatching { Graph.relays.query(listOf(f), 8000) }.getOrDefault(emptyList()).sortedBy { it.createdAt }.forEach { runCatching { onMsg(it) } }
        }
    }

    // ------------------------------------------------------------------ the shard itself

    private suspend fun onDef(ev: NostrEvent) {
        if (ev.kind != Envelope.KIND_PROFILE) return
        val d = ev.firstTag("d") ?: return
        if (!d.startsWith(D_PREFIX)) return
        if (!withContext(Dispatchers.Default) { ev.verify() }) return
        val key = ev.pubkey + "|" + d
        val prev = defs[key]
        if (prev != null && prev.createdAt >= ev.createdAt) return
        defs[key] = ev
        reapply()
    }

    /** try every known description again: founders first, so the moderators' changes find their shard */
    suspend fun reapply() = lock.withLock {
        val list = defs.values.mapNotNull { ev ->
            val o = runCatching { JSONObject(ev.content) }.getOrNull() ?: return@mapNotNull null
            if (D_PREFIX + o.optString("id") != ev.firstTag("d")) return@mapNotNull null
            Triple(ev.pubkey, o, o.optString("o") == ev.pubkey)
        }.sortedWith(compareByDescending<Triple<String, JSONObject, Boolean>> { it.third }.thenBy { it.second.optLong("u") })
        list.forEach { (author, o, _) -> runCatching { Graph.shards.onHubDef(author, o) } }
    }

    /** founder or moderator: tell everyone how the shard is now */
    suspend fun publishDef(s: Shard) {
        val k = Graph.identity.keys ?: return
        if (!s.open) return
        val content = Graph.shards.toJson(s).toString()
        val ev = withContext(Dispatchers.Default) {
            NostrEvent("", nowSec(), Envelope.KIND_PROFILE, listOf(listOf("d", D_PREFIX + s.id), listOf("t", TAG)), content).sign(k.priv)
        }
        defs[ev.pubkey + "|" + D_PREFIX + s.id] = ev
        Graph.relays.publish(ev)
    }

    // ------------------------------------------------------------------ leaving

    private fun leftSet(): Set<String> = prefs.getStringSet("left", emptySet()) ?: emptySet()
    fun hasLeft(id: String) = id in leftSet()

    /** shards for everyone I left (to get back in): id -> name with emoji */
    fun leftShards(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("leftNames", "{}") ?: "{}")
        leftSet().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())

    fun rememberLeft(s: Shard) {
        val names = runCatching { JSONObject(prefs.getString("leftNames", "{}") ?: "{}") }.getOrDefault(JSONObject()).put(s.id, s.emoji + " " + s.name)
        prefs.edit().putStringSet("left", leftSet() + s.id).putString("leftNames", names.toString()).apply()
    }

    fun forgetLeft(id: String) {
        prefs.edit().putStringSet("left", leftSet() - id).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        defs.clear()
    }

    // ------------------------------------------------------------------ messages

    private fun byTag(tag: String): Pair<Shard, String>? {
        openShards().forEach { s ->
            if (roomTag(s.id, "*") == tag) return s to "*"
            s.rooms.firstOrNull { roomTag(s.id, it.id) == tag }?.let { return s to it.id }
        }
        return null
    }

    /** one event for the whole room, sealed with the shard's key and signed by me */
    suspend fun seal(s: Shard, room: String, payload: JSONObject): NostrEvent? {
        val k = Graph.identity.keys ?: return null
        val prof = Graph.identity.profile.value
        payload.put("av", BuildConfig.VERSION_NAME)
        if (prof != null) payload.put("pf", JSONObject().put("u", prof.username).put("n", prof.displayName).put("e", prof.emoji))
        val sealed = lockBox(Hex.decode(s.key), payload.toString()) ?: return null
        return withContext(Dispatchers.Default) {
            val now = nowSec()
            NostrEvent("", now, KIND_MSG, listOf(listOf("h", roomTag(s.id, room)), listOf("expiration", (now + TTL).toString())), sealed).sign(k.priv)
        }
    }

    private suspend fun onMsg(ev: NostrEvent) {
        if (ev.kind != KIND_MSG) return
        val me = Graph.identity.keys?.pub ?: return
        val (s, room) = byTag(ev.firstTag("h") ?: return) ?: return
        if (ev.pubkey == me || ev.pubkey in s.ban) return
        if (ev.createdAt > nowSec() + 120) return
        if (!withContext(Dispatchers.Default) { ev.verify() }) return
        val p = unlockBox(Hex.decode(s.key), ev.content)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        if (ev.createdAt > lastSec) lastSec = ev.createdAt
        p.optJSONObject("pf")?.let { learn(ev.pubkey, it) }
        // the room comes from the tag, never from the content
        if (room == "*") { p.remove("g"); p.put("s", s.id) } else p.put("g", Shard.groupId(s.id, room))
        Graph.messenger.handleHubPayload(ev.pubkey, p, ev.createdAt * 1000)
    }

    /** the name of someone I don't know yet, so it shows in the rooms */
    private suspend fun learn(pk: String, o: JSONObject) {
        if (!Hex.isHex64(pk)) return
        val name = o.optString("n").take(40).ifBlank { return }
        val cur = Graph.db.friends().get(pk)
        if (cur == null) {
            Graph.db.friends().upsert(Friend(pk, o.optString("u").take(30), name, o.optString("e").take(8).ifBlank { "🙂" }, "", FriendStatus.KNOWN, now()))
        } else if (cur.status == FriendStatus.KNOWN && (cur.displayName != name || cur.emoji != o.optString("e", cur.emoji))) {
            Graph.db.friends().upsert(cur.copy(displayName = name, emoji = o.optString("e").take(8).ifBlank { cur.emoji }, username = o.optString("u").take(30).ifBlank { cur.username }))
        }
    }

    // ------------------------------------------------------------------ sealing

    private fun lockBox(key: ByteArray, text: String): String? = runCatching {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        Base64.encodeToString(iv + c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }.getOrNull()

    private fun unlockBox(key: ByteArray, b64: String): String? = runCatching {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        if (all.size < 29) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12))
    }.getOrNull()
}
