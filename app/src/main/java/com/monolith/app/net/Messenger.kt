package com.monolith.app.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.monolith.app.Graph
import com.monolith.app.core.Keys
import com.monolith.app.core.Profile
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.nowSec
import com.monolith.app.core.strings
import com.monolith.app.core.toJsonArray
import com.monolith.app.crypto.Bech32
import com.monolith.app.crypto.Hex
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.AppDatabase
import com.monolith.app.data.Chunk
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Group
import com.monolith.app.data.InviteState
import com.monolith.app.data.Message
import com.monolith.app.data.MsgStatus
import com.monolith.app.data.Processed
import com.monolith.app.nostr.Envelope
import com.monolith.app.nostr.NostrEvent
import com.monolith.app.nostr.RelayPool
import com.monolith.app.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class UserResult(val pubkey: String, val profile: Profile, val createdAt: Long)

/**
 * Heart of the app: speaks the Monolith protocol over relays (store-and-forward, always reachable)
 * and over WebRTC data channels (direct peer-to-peer, when both phones are online).
 */
class Messenger(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val db: AppDatabase,
) {
    private val tag = "Messenger"
    private val relays: RelayPool get() = Graph.relays
    private val identity get() = Graph.identity
    private val prefs get() = Graph.prefs

    companion object {
        const val CHUNK_BYTES = 20 * 1024
        const val TTL_MESSAGE = 30L * 86400
        const val TTL_CHUNK = 14L * 86400
        const val TTL_LIVE = 120L
        const val PRESENCE_WINDOW = 100_000L
    }

    /** the conversation currently on screen (no notifications for it) */
    val activeConv = MutableStateFlow<String?>(null)

    private val _online = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** pubkey -> last presence ping (ms) */
    val online: StateFlow<Map<String, Long>> = _online.asStateFlow()

    private val _typing = MutableStateFlow<Map<String, Pair<String, Long>>>(emptyMap())
    /** convId -> (pubkey, until ms) */
    val typing: StateFlow<Map<String, Pair<String, Long>>> = _typing.asStateFlow()

    private val wrapToMsg = ConcurrentHashMap<String, String>()
    private val liveSeen = object : LinkedHashMap<String, Boolean>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 2000
    }
    private val assembleLock = Mutex()
    private val lastTypingSent = ConcurrentHashMap<String, Long>()
    private val lastPresenceReply = ConcurrentHashMap<String, Long>()

    @Volatile
    private var started = false

    fun me(): Keys? = identity.keys

    fun isOnline(pk: String): Boolean = (now() - (_online.value[pk] ?: 0L)) < PRESENCE_WINDOW

    // ================================================================== lifecycle

    fun start() {
        if (started) return
        val k = identity.keys ?: return
        started = true
        relays.start(prefs.relays.value)
        relays.subscribe("inbox") {
            val since = (if (prefs.lastWrapSec > 0) prefs.lastWrapSec else nowSec() - 30L * 86400) - 900
            listOf(
                JSONObject().put("kinds", JSONArray().put(Envelope.KIND_WRAP))
                    .put("#p", JSONArray().put(k.pub)).put("since", since)
            )
        }
        relays.subscribe("live") {
            listOf(
                JSONObject().put("kinds", JSONArray().put(Envelope.KIND_EPHEMERAL))
                    .put("#p", JSONArray().put(k.pub)).put("since", nowSec() - 20)
            )
        }
        scope.launch {
            relays.events.collect { (_, ev) ->
                runCatching { handleWrap(ev) }.onFailure { Log.w(tag, "wrap failed", it) }
            }
        }
        scope.launch {
            relays.oks.collect { (id, ok) ->
                val msgId = wrapToMsg[id] ?: return@collect
                if (ok) {
                    wrapToMsg.remove(id)
                    db.messages().raiseStatus(msgId, MsgStatus.SENT)
                }
            }
        }
        scope.launch {
            prefs.relays.collect { relays.setRelays(it) }
        }
        scope.launch {
            delay(2500)
            publishProfile()
            resendPending()
            db.misc().pruneProcessed(now() - 45L * 86_400_000)
            db.misc().pruneChunks(now() - 15L * 86_400_000)
        }
        scope.launch {
            while (true) {
                if (Graph.foreground) sendPresenceToAll()
                delay(45_000)
            }
        }
    }

    fun onForeground() {
        if (!started) return
        relays.reconnectNow()
        scope.launch { sendPresenceToAll() }
    }

    // ================================================================== transport

    private fun publishWrapped(to: String, payload: JSONObject, ephemeral: Boolean, ttl: Long?, msgId: String? = null): NostrEvent? {
        val k = me() ?: return null
        if (!Hex.isHex64(to)) return null
        val ev = Envelope.wrap(k, to, payload, ephemeral, ttl)
        if (msgId != null) wrapToMsg[ev.id] = msgId
        relays.publish(ev)
        return ev
    }

    suspend fun sendStored(to: String, payload: JSONObject, ttl: Long = TTL_MESSAGE, msgId: String? = null): NostrEvent? =
        withContext(Dispatchers.Default) { publishWrapped(to, payload, false, ttl, msgId) }

    suspend fun sendLive(to: String, payload: JSONObject) {
        withContext(Dispatchers.Default) { publishWrapped(to, payload, true, TTL_LIVE) }
    }

    /** prefer the direct WebRTC channel, fall back to a live relay event */
    suspend fun sendFast(to: String, payload: JSONObject) {
        if (!Graph.rtc.send(to, payload)) sendLive(to, payload)
    }

    private suspend fun handleWrap(ev: NostrEvent) {
        val k = me() ?: return
        if (ev.kind != Envelope.KIND_WRAP && ev.kind != Envelope.KIND_EPHEMERAL) return
        val inner = withContext(Dispatchers.Default) { Envelope.unwrap(k, ev) } ?: return
        if (inner.from == k.pub) return
        val fresh = synchronized(liveSeen) { liveSeen.put(inner.id, true) == null }
        if (!fresh) return
        if (ev.kind == Envelope.KIND_WRAP) {
            if (db.misc().markProcessed(Processed(inner.id, now())) == -1L) return
            if (ev.createdAt > prefs.lastWrapSec && ev.createdAt <= nowSec() + 60) prefs.lastWrapSec = ev.createdAt
        } else if (nowSec() - inner.createdAt > 90) {
            return // stale live event
        }
        handlePayload(inner.from, inner.payload, inner.createdAt * 1000, viaRtc = false)
    }

    // ================================================================== incoming

    suspend fun handlePayload(from: String, p: JSONObject, sentAt: Long, viaRtc: Boolean) {
        val friend = db.friends().get(from)
        val isFriend = friend?.status == FriendStatus.FRIEND
        if (isFriend && viaRtc) markOnline(from)
        when (p.optString("t")) {
            "freq" -> onFriendRequest(from, p, friend)
            "facc" -> onFriendAccept(from, p, friend)
            "frem" -> if (friend != null && friend.status != FriendStatus.KNOWN) db.friends().delete(from)
            "prof" -> if (friend != null) onProfile(friend, p)
            "msg" -> onMessage(from, p, sentAt, friend)
            "chunk" -> onChunk(from, p)
            "ack" -> onAck(p)
            "grp" -> onGroup(from, p, isFriend)
            "gleave" -> onGroupLeave(from, p)
            "evinv" -> if (isFriend) onEventInvite(from, p)
            "evrsvp" -> onRsvp(from, p)
            "evdel" -> onEventDelete(from, p)
            "loc" -> if (isFriend) onLocation(from, p)
            "locstart" -> if (isFriend) onLocStart(friend!!, p)
            "locstop" -> if (isFriend) db.friends().sharing(from, 0)
            "pres" -> if (isFriend) onPresence(from, p)
            "typing" -> onTyping(from, p, isFriend)
            "rtc" -> if (isFriend) Graph.rtc.onSignal(from, p)
            "call" -> if (isFriend) Graph.calls.onSignal(from, p)
        }
    }

    private fun markOnline(pk: String) {
        _online.update { it + (pk to now()) }
        scope.launch { db.friends().seen(pk, now()) }
    }

    private suspend fun onPresence(from: String, p: JSONObject) {
        markOnline(from)
        // answer so both sides know the other one is around
        if (p.optBoolean("q") && Graph.foreground) {
            val last = lastPresenceReply[from] ?: 0L
            if (now() - last > 20_000) {
                lastPresenceReply[from] = now()
                sendLive(from, JSONObject().put("t", "pres").put("q", false))
            }
        }
    }

    private suspend fun sendPresenceToAll() {
        if (me() == null) return
        db.friends().friendsNow().forEach {
            sendLive(it.pubkey, JSONObject().put("t", "pres").put("q", true))
        }
    }

    private fun onTyping(from: String, p: JSONObject, isFriend: Boolean) {
        val g = p.optString("g")
        val conv = if (g.isNotBlank()) "g:$g" else if (isFriend) from else return
        _typing.update { it + (conv to (from to now() + 4500)) }
        if (isFriend) markOnline(from)
    }

    private suspend fun onFriendRequest(from: String, p: JSONObject, existing: Friend?) {
        val prof = Profile.fromJson(p)
        when (existing?.status) {
            FriendStatus.FRIEND -> sendStored(from, profilePayload("facc"))
            FriendStatus.OUTGOING -> {
                db.friends().upsert(existing!!.copy(status = FriendStatus.FRIEND, username = prof.username, displayName = prof.displayName, emoji = prof.emoji, bio = prof.bio))
                sendStored(from, profilePayload("facc"))
                Graph.notifier.friendAccepted(prof.displayName, prof.emoji)
            }
            else -> {
                db.friends().upsert(
                    Friend(from, prof.username, prof.displayName, prof.emoji, prof.bio, FriendStatus.INCOMING, now())
                )
                Graph.notifier.friendRequest(from, prof.displayName, prof.username, prof.emoji)
            }
        }
    }

    private suspend fun onFriendAccept(from: String, p: JSONObject, existing: Friend?) {
        if (existing == null || existing.status == FriendStatus.INCOMING || existing.status == FriendStatus.KNOWN) return
        val prof = Profile.fromJson(p)
        val wasFriend = existing.status == FriendStatus.FRIEND
        db.friends().upsert(existing.copy(status = FriendStatus.FRIEND, username = prof.username, displayName = prof.displayName, emoji = prof.emoji, bio = prof.bio))
        if (!wasFriend) Graph.notifier.friendAccepted(prof.displayName, prof.emoji)
    }

    private suspend fun onProfile(friend: Friend, p: JSONObject) {
        val prof = Profile.fromJson(p)
        db.friends().upsert(friend.copy(username = prof.username, displayName = prof.displayName, emoji = prof.emoji, bio = prof.bio))
    }

    private suspend fun convFor(from: String, p: JSONObject, friend: Friend?): String? {
        val g = p.optString("g")
        if (g.isNotBlank()) {
            val group = db.groups().get(g) ?: return null
            return if (from in group.memberList()) "g:$g" else null
        }
        return if (friend?.status == FriendStatus.FRIEND) from else null
    }

    private suspend fun onMessage(from: String, p: JSONObject, sentAt: Long, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val id = p.optString("id").ifBlank { return }
        val kind = p.optString("k", "text")
        val isActive = activeConv.value == conv && Graph.foreground
        val extra = p.optJSONObject("ex")
        val msg = Message(
            id = id,
            convId = conv,
            sender = from,
            outgoing = false,
            ts = minOf(p.optLong("ts", sentAt), now()),
            kind = kind,
            text = p.optString("x"),
            extra = extra?.toString(),
            status = if (isActive) MsgStatus.READ else MsgStatus.PENDING,
            progress = if (kind == "photo") 0f else 1f,
        )
        if (db.messages().insert(msg) == -1L) return
        _typing.update { it - conv }
        if (kind == "photo") tryAssemble(id)
        sendStored(from, JSONObject().put("t", "ack").put("ids", JSONArray().put(id)).put("s", if (isActive) MsgStatus.READ else MsgStatus.DELIVERED), 7L * 86400)
        if (!isActive) {
            val title: String
            val group: Group?
            if (conv.startsWith("g:")) {
                group = db.groups().get(conv.removePrefix("g:"))
                title = group?.name ?: "Gruppo"
            } else {
                group = null
                title = friend?.displayName ?: "Monolith"
            }
            val sender = friend ?: db.friends().get(from)
            Graph.notifier.message(
                conv = conv,
                title = title,
                senderName = sender?.displayName ?: "Qualcuno",
                senderEmoji = sender?.emoji ?: "🙂",
                text = previewText(msg),
                isGroup = group != null,
                groupEmoji = group?.emoji,
                nudge = kind == "nudge",
            )
        } else if (kind == "nudge") {
            Graph.notifier.vibrateNudge()
        }
    }

    fun previewText(m: Message): String = when (m.kind) {
        "photo" -> if (m.text.isNotBlank()) "📷 ${m.text}" else "📷 Foto"
        "event" -> "📅 " + (runCatching { JSONObject(m.extra ?: "{}").optString("title") }.getOrNull() ?: "Evento")
        "loc" -> "📍 Posizione"
        "nudge" -> "📳 Trillo!"
        "call" -> "📞 " + m.text
        else -> m.text
    }

    private suspend fun onChunk(from: String, p: JSONObject) {
        val id = p.optString("id").ifBlank { return }
        val g = p.optString("g")
        if (g.isNotBlank()) {
            val group = db.groups().get(g) ?: return
            if (from !in group.memberList()) return
        } else {
            if (db.friends().get(from)?.status != FriendStatus.FRIEND) return
        }
        val i = p.optInt("i")
        val n = p.optInt("n")
        if (n <= 0 || i < 0 || i >= n || n > 400) return
        db.misc().addChunk(Chunk("$id:$i", id, i, n, p.optString("d"), now()))
        tryAssemble(id)
    }

    private suspend fun tryAssemble(id: String) = assembleLock.withLock {
        val msg = db.messages().get(id) ?: return@withLock
        if (msg.outgoing || (msg.progress >= 1f && msg.mediaPath != null)) return@withLock
        val n = runCatching { JSONObject(msg.extra ?: "{}").optInt("n") }.getOrDefault(0)
        if (n <= 0) return@withLock
        val count = db.misc().chunkCount(id)
        if (count < n) {
            db.messages().progress(id, count.toFloat() / n, null)
            return@withLock
        }
        val chunks = db.misc().chunks(id)
        val out = ByteArrayOutputStream()
        val dec = Base64.getDecoder()
        chunks.distinctBy { it.idx }.sortedBy { it.idx }.forEach { out.write(dec.decode(it.data)) }
        val file = File(Media.dir(ctx), "$id.jpg")
        file.writeBytes(out.toByteArray())
        db.messages().progress(id, 1f, file.absolutePath)
        db.misc().clearChunks(id)
    }

    private suspend fun onAck(p: JSONObject) {
        val ids = p.optJSONArray("ids") ?: return
        val s = p.optInt("s", MsgStatus.DELIVERED).coerceIn(MsgStatus.DELIVERED, MsgStatus.READ)
        for (i in 0 until ids.length()) db.messages().raiseStatus(ids.getString(i), s)
    }

    private suspend fun onGroup(from: String, p: JSONObject, isFriend: Boolean) {
        val gid = p.optString("g").ifBlank { return }
        val existing = db.groups().get(gid)
        if (existing != null && existing.admin != from) return
        if (existing == null && !isFriend) return
        val k = me() ?: return
        val members = (p.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) }
        if (k.pub !in members) {
            if (existing != null) db.groups().delete(gid)
            return
        }
        // remember the profiles of members we are not friends with
        p.optJSONObject("mp")?.let { mp ->
            members.filter { it != k.pub }.forEach { pk ->
                val known = db.friends().get(pk)
                val o = mp.optJSONObject(pk) ?: return@forEach
                if (known == null) {
                    val prof = Profile.fromJson(o)
                    db.friends().upsert(Friend(pk, prof.username, prof.displayName, prof.emoji, prof.bio, FriendStatus.KNOWN, now()))
                }
            }
        }
        val group = Group(gid, p.optString("name", "Gruppo"), p.optString("e", "💬"), members.joinToString(","), from, existing?.createdAt ?: now())
        db.groups().upsert(group)
        if (existing == null) {
            val creator = db.friends().get(from)
            db.messages().insert(
                Message(newId(), "g:$gid", from, false, now(), "system", text = "${creator?.displayName ?: "Qualcuno"} ti ha aggiunto al gruppo", status = MsgStatus.READ)
            )
            Graph.notifier.message("g:$gid", group.name, creator?.displayName ?: "", creator?.emoji ?: "🙂", "Ti ha aggiunto al gruppo", true, group.emoji, false)
        }
    }

    private suspend fun onGroupLeave(from: String, p: JSONObject) {
        val gid = p.optString("g")
        val g = db.groups().get(gid) ?: return
        if (from !in g.memberList()) return
        val left = db.friends().get(from)
        db.groups().upsert(g.copy(members = g.memberList().filter { it != from }.joinToString(",")))
        db.messages().insert(Message(newId(), "g:$gid", from, false, now(), "system", text = "${left?.displayName ?: "Qualcuno"} ha lasciato il gruppo", status = MsgStatus.READ))
    }

    private suspend fun onLocation(from: String, p: JSONObject) {
        val until = p.optLong("until", now() + 60_000)
        if (until < now()) return
        db.friends().location(from, p.optDouble("lat"), p.optDouble("lon"), p.optDouble("acc", 0.0).toFloat(), p.optLong("at", now()), until)
        markOnline(from)
    }

    private suspend fun onLocStart(friend: Friend, p: JSONObject) {
        val until = p.optLong("until")
        db.friends().sharing(friend.pubkey, until)
        if (until > now()) Graph.notifier.locationShared(friend.displayName, friend.emoji, until)
    }

    // ------------------------------------------------------------------ agenda sync

    private fun eventJson(e: AgendaEvent) = JSONObject()
        .put("id", e.id).put("title", e.title).put("desc", e.description).put("notes", e.notes)
        .put("start", e.start).put("end", e.end ?: 0L).put("allDay", e.allDay).put("emoji", e.emoji)

    private suspend fun onEventInvite(from: String, p: JSONObject) {
        val o = p.optJSONObject("ev") ?: return
        val id = o.optString("id").ifBlank { return }
        val existing = db.agenda().get(id)
        if (existing != null && existing.owner != from) return
        val friend = db.friends().get(from)
        val ev = AgendaEvent(
            id = id,
            title = o.optString("title"),
            description = o.optString("desc"),
            notes = existing?.notes ?: o.optString("notes"),
            start = o.optLong("start"),
            end = o.optLong("end").takeIf { it > 0 },
            allDay = o.optBoolean("allDay"),
            emoji = o.optString("emoji", "📌"),
            reminderMin = existing?.reminderMin ?: 15,
            owner = from,
            invitees = (p.optJSONArray("inv") ?: JSONArray()).strings().joinToString(","),
            inviteState = existing?.inviteState ?: InviteState.PENDING,
            createdAt = existing?.createdAt ?: now(),
            updatedAt = now(),
        )
        db.agenda().upsert(ev)
        if (ev.inviteState == InviteState.ACCEPTED) ReminderScheduler.schedule(ctx, ev)
        if (existing == null) Graph.notifier.eventInvite(ev, friend?.displayName ?: "Un amico")
    }

    private suspend fun onRsvp(from: String, p: JSONObject) {
        val ev = db.agenda().get(p.optString("id")) ?: return
        if (ev.owner.isNotEmpty() || from !in ev.inviteeList()) return
        val rsvp = runCatching { JSONObject(ev.rsvp) }.getOrDefault(JSONObject())
        val answer = p.optString("s")
        rsvp.put(from, answer)
        db.agenda().upsert(ev.copy(rsvp = rsvp.toString(), updatedAt = now()))
        val f = db.friends().get(from)
        Graph.notifier.rsvp(ev, f?.displayName ?: "Un amico", answer == "yes")
    }

    private suspend fun onEventDelete(from: String, p: JSONObject) {
        val ev = db.agenda().get(p.optString("id")) ?: return
        if (ev.owner != from) return
        db.agenda().delete(ev.id)
        ReminderScheduler.cancel(ctx, ev.id)
        if (ev.inviteState != InviteState.DECLINED) Graph.notifier.eventCancelled(ev)
    }

    suspend fun saveEvent(e: AgendaEvent, notifyInvitees: Boolean = true) {
        db.agenda().upsert(e)
        if (e.inviteState != InviteState.DECLINED && e.inviteState != InviteState.PENDING) ReminderScheduler.schedule(ctx, e)
        if (e.owner.isEmpty() && notifyInvitees) {
            val inv = e.inviteeList()
            val payload = JSONObject().put("t", "evinv").put("ev", eventJson(e)).put("inv", inv.toJsonArray())
            inv.forEach { sendStored(it, payload) }
        }
    }

    suspend fun deleteEvent(e: AgendaEvent) {
        db.agenda().delete(e.id)
        ReminderScheduler.cancel(ctx, e.id)
        if (e.owner.isEmpty()) {
            e.inviteeList().forEach { sendStored(it, JSONObject().put("t", "evdel").put("id", e.id)) }
        } else if (e.inviteState != InviteState.DECLINED) {
            sendStored(e.owner, JSONObject().put("t", "evrsvp").put("id", e.id).put("s", "no"))
        }
    }

    suspend fun respondInvite(id: String, accept: Boolean) {
        val e = db.agenda().get(id) ?: return
        val updated = e.copy(inviteState = if (accept) InviteState.ACCEPTED else InviteState.DECLINED, updatedAt = now())
        db.agenda().upsert(updated)
        if (accept) ReminderScheduler.schedule(ctx, updated) else ReminderScheduler.cancel(ctx, id)
        sendStored(e.owner, JSONObject().put("t", "evrsvp").put("id", id).put("s", if (accept) "yes" else "no"))
    }

    // ================================================================== directory

    fun profilePayload(t: String): JSONObject {
        val p = identity.profile.value ?: Profile("", "", "🙂")
        return p.toJson().put("t", t)
    }

    suspend fun publishProfile() {
        val k = me() ?: return
        val p = identity.profile.value ?: return
        val ev = NostrEvent(
            "", nowSec(), Envelope.KIND_PROFILE,
            listOf(listOf("d", "monolith-profile"), listOf("u", p.username), listOf("t", "monolith")),
            p.toJson().toString()
        )
        withContext(Dispatchers.Default) { ev.sign(k.priv) }
        relays.publish(ev)
    }

    suspend fun updateProfile(p: Profile) {
        identity.updateProfile(p)
        publishProfile()
        val payload = profilePayload("prof")
        db.friends().friendsNow().forEach { sendStored(it.pubkey, payload) }
    }

    /** exact username lookup (or npub) across relays */
    suspend fun searchUsers(query: String): List<UserResult> {
        val q = query.trim()
        val filter = JSONObject().put("kinds", JSONArray().put(Envelope.KIND_PROFILE))
            .put("#d", JSONArray().put("monolith-profile")).put("limit", 30)
        if (q.startsWith("npub1")) {
            val hex = runCatching { Hex.encode(Bech32.decode(q)[1] as ByteArray) }.getOrNull() ?: return emptyList()
            filter.put("authors", JSONArray().put(hex))
        } else if (q.length == 64 && Hex.isHex64(q)) {
            filter.put("authors", JSONArray().put(q.lowercase()))
        } else {
            val u = Profile.normalizeUsername(q).substringBefore('#')
            if (u.length < 3) return emptyList()
            filter.put("#u", JSONArray().put(u))
        }
        val tagFilter = Profile.normalizeUsername(q).substringAfter('#', "")
        val events = relays.query(listOf(filter))
        val myPub = me()?.pub
        return withContext(Dispatchers.Default) {
            events.filter { it.kind == Envelope.KIND_PROFILE && it.verify() }
                .groupBy { it.pubkey }
                .mapNotNull { (pk, evs) ->
                    val latest = evs.maxByOrNull { it.createdAt } ?: return@mapNotNull null
                    val prof = runCatching { Profile.fromJson(JSONObject(latest.content)) }.getOrNull() ?: return@mapNotNull null
                    UserResult(pk, prof, latest.createdAt)
                }
                .filter { it.pubkey != myPub }
                .filter { tagFilter.isEmpty() || it.pubkey.startsWith(tagFilter) }
                .sortedBy { it.createdAt }
        }
    }

    suspend fun isUsernameTaken(username: String): Boolean = searchUsers(username).isNotEmpty()

    // ================================================================== friends

    suspend fun sendFriendRequest(u: UserResult) {
        val existing = db.friends().get(u.pubkey)
        if (existing?.status == FriendStatus.FRIEND) return
        if (existing?.status == FriendStatus.INCOMING) {
            acceptFriend(u.pubkey)
            return
        }
        db.friends().upsert(
            Friend(u.pubkey, u.profile.username, u.profile.displayName, u.profile.emoji, u.profile.bio, FriendStatus.OUTGOING, now())
        )
        sendStored(u.pubkey, profilePayload("freq"))
    }

    suspend fun acceptFriend(pk: String) {
        val f = db.friends().get(pk) ?: return
        db.friends().upsert(f.copy(status = FriendStatus.FRIEND))
        sendStored(pk, profilePayload("facc"))
        Graph.notifier.cancelFriendRequest(pk)
    }

    suspend fun declineFriend(pk: String) {
        db.friends().delete(pk)
        Graph.notifier.cancelFriendRequest(pk)
    }

    suspend fun removeFriend(pk: String) {
        db.friends().delete(pk)
        sendStored(pk, JSONObject().put("t", "frem"))
    }

    // ================================================================== chat

    private suspend fun recipients(conv: String): Pair<List<String>, String?> {
        val myPub = me()?.pub ?: return emptyList<String>() to null
        return if (conv.startsWith("g:")) {
            val gid = conv.removePrefix("g:")
            val g = db.groups().get(gid) ?: return emptyList<String>() to gid
            g.memberList().filter { it != myPub } to gid
        } else listOf(conv) to null
    }

    private suspend fun deliver(m: Message) {
        val (targets, gid) = recipients(m.convId)
        val payload = JSONObject().put("t", "msg").put("id", m.id).put("k", m.kind).put("x", m.text).put("ts", m.ts)
        m.extra?.let { payload.put("ex", JSONObject(it)) }
        gid?.let { payload.put("g", it) }
        targets.forEach { to -> sendStored(to, payload, TTL_MESSAGE, m.id) }
    }

    suspend fun sendText(conv: String, text: String) {
        val k = me() ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        val m = Message(newId(), conv, k.pub, true, now(), "text", text = t)
        db.messages().insert(m)
        deliver(m)
    }

    suspend fun sendNudge(conv: String) {
        val k = me() ?: return
        val m = Message(newId(), conv, k.pub, true, now(), "nudge")
        db.messages().insert(m)
        deliver(m)
    }

    suspend fun shareEventInChat(conv: String, e: AgendaEvent) {
        val k = me() ?: return
        val ex = eventJson(e)
        val m = Message(newId(), conv, k.pub, true, now(), "event", text = e.title, extra = ex.toString())
        db.messages().insert(m)
        deliver(m)
    }

    suspend fun sendLocationPin(conv: String, lat: Double, lon: Double) {
        val k = me() ?: return
        val ex = JSONObject().put("lat", lat).put("lon", lon)
        val m = Message(newId(), conv, k.pub, true, now(), "loc", extra = ex.toString())
        db.messages().insert(m)
        deliver(m)
    }

    suspend fun addCallLog(conv: String, text: String, outgoing: Boolean) {
        val k = me() ?: return
        db.messages().insert(Message(newId(), conv, if (outgoing) k.pub else conv, outgoing, now(), "call", text = text, status = MsgStatus.READ))
    }

    suspend fun sendPhoto(conv: String, uri: Uri, caption: String) {
        val k = me() ?: return
        val compressed = withContext(Dispatchers.IO) { Media.compress(ctx, uri) } ?: return
        val id = newId()
        val file = File(Media.dir(ctx), "$id.jpg")
        withContext(Dispatchers.IO) { file.writeBytes(compressed.bytes) }
        val n = ceil(compressed.bytes.size / CHUNK_BYTES.toDouble()).toInt().coerceAtLeast(1)
        val extra = JSONObject().put("n", n).put("w", compressed.width).put("h", compressed.height)
        val m = Message(id, conv, k.pub, true, now(), "photo", text = caption.trim(), mediaPath = file.absolutePath, extra = extra.toString(), progress = 0f)
        db.messages().insert(m)
        deliver(m)
        sendChunks(m, compressed.bytes)
    }

    private suspend fun sendChunks(m: Message, bytes: ByteArray) {
        val (targets, gid) = recipients(m.convId)
        val n = ceil(bytes.size / CHUNK_BYTES.toDouble()).toInt().coerceAtLeast(1)
        val enc = Base64.getEncoder()
        targets.forEach { to -> if (!Graph.rtc.isConnected(to)) Graph.rtc.ensureConnected(to) }
        // give a direct channel a moment to come up
        if (targets.any { !Graph.rtc.isConnected(it) } && targets.any { isOnline(it) }) delay(2500)
        for (i in 0 until n) {
            val part = bytes.copyOfRange(i * CHUNK_BYTES, minOf(bytes.size, (i + 1) * CHUNK_BYTES))
            val payload = JSONObject().put("t", "chunk").put("id", m.id).put("i", i).put("n", n).put("d", enc.encodeToString(part))
            gid?.let { payload.put("g", it) }
            targets.forEach { to ->
                if (!Graph.rtc.send(to, payload)) {
                    sendStored(to, payload, TTL_CHUNK)
                    delay(150)
                }
            }
            db.messages().progress(m.id, (i + 1f) / n, null)
        }
    }

    private suspend fun resendPending() {
        db.messages().pending(now() - 20_000).forEach { m ->
            deliver(m)
            if (m.kind == "photo" && m.mediaPath != null) {
                val f = File(m.mediaPath)
                if (f.exists()) sendChunks(m, f.readBytes())
            }
        }
    }

    suspend fun markRead(conv: String) {
        val unread = db.messages().unreadList(conv)
        if (unread.isEmpty()) return
        db.messages().markRead(conv)
        Graph.notifier.cancelConversation(conv)
        unread.groupBy { it.sender }.forEach { (sender, list) ->
            sendStored(sender, JSONObject().put("t", "ack").put("ids", list.map { it.id }.toJsonArray()).put("s", MsgStatus.READ), 7L * 86400)
        }
    }

    suspend fun typing(conv: String) {
        val last = lastTypingSent[conv] ?: 0L
        if (now() - last < 3000) return
        lastTypingSent[conv] = now()
        val (targets, gid) = recipients(conv)
        val payload = JSONObject().put("t", "typing")
        gid?.let { payload.put("g", it) }
        targets.forEach { sendFast(it, payload) }
    }

    // ================================================================== groups

    suspend fun createGroup(name: String, emoji: String, members: List<String>): String? {
        val k = me() ?: return null
        val gid = newId()
        val all = (members + k.pub).distinct()
        val g = Group(gid, name.trim().ifBlank { "Gruppo" }, emoji, all.joinToString(","), k.pub, now())
        db.groups().upsert(g)
        db.messages().insert(Message(newId(), "g:$gid", k.pub, true, now(), "system", text = "Hai creato il gruppo", status = MsgStatus.READ))
        broadcastGroup(g)
        return gid
    }

    private suspend fun broadcastGroup(g: Group) {
        val k = me() ?: return
        val mp = JSONObject()
        mp.put(k.pub, identity.profile.value?.toJson() ?: JSONObject())
        g.memberList().forEach { pk ->
            db.friends().get(pk)?.let { f -> mp.put(pk, Profile(f.username, f.displayName, f.emoji, f.bio).toJson()) }
        }
        val payload = JSONObject().put("t", "grp").put("g", g.id).put("name", g.name).put("e", g.emoji)
            .put("m", g.memberList().toJsonArray()).put("mp", mp)
        g.memberList().filter { it != k.pub }.forEach { sendStored(it, payload) }
    }

    suspend fun addGroupMembers(gid: String, newMembers: List<String>) {
        val g = db.groups().get(gid) ?: return
        val updated = g.copy(members = (g.memberList() + newMembers).distinct().joinToString(","))
        db.groups().upsert(updated)
        broadcastGroup(updated)
    }

    suspend fun leaveGroup(gid: String) {
        val k = me() ?: return
        val g = db.groups().get(gid) ?: return
        g.memberList().filter { it != k.pub }.forEach { sendStored(it, JSONObject().put("t", "gleave").put("g", gid)) }
        db.groups().delete(gid)
        db.messages().clear("g:$gid")
    }

    // ================================================================== location

    suspend fun sendLocStart(to: String, until: Long) {
        sendStored(to, JSONObject().put("t", "locstart").put("until", until), 86400)
    }

    suspend fun sendLocStop(to: String) {
        sendStored(to, JSONObject().put("t", "locstop"), 86400)
    }
}

// ====================================================================== media helpers

class Compressed(val bytes: ByteArray, val width: Int, val height: Int)

object Media {
    fun dir(ctx: Context): File = File(ctx.filesDir, "media").apply { mkdirs() }

    fun compress(ctx: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 72): Compressed? = try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("decode failed")
        val rotation = cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = minOf(1f, maxDim.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val bmp = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        Compressed(out.toByteArray(), bmp.width, bmp.height)
    } catch (e: Exception) {
        Log.w("Media", "compress failed", e)
        null
    }

    fun approxSize(w: Int, h: Int, maxDp: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return maxDp to (maxDp * 0.75f).roundToInt()
        val r = h.toFloat() / w
        return if (r > 1f) (maxDp / r).roundToInt().coerceAtLeast(120) to maxDp else maxDp to (maxDp * r).roundToInt().coerceAtLeast(90)
    }
}
