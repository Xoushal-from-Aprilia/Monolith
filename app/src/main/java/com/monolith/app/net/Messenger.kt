package com.monolith.app.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
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
import com.monolith.app.core.t
import com.monolith.app.core.toJsonArray
import com.monolith.app.crypto.Bech32
import com.monolith.app.crypto.Hex
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.AppDatabase
import com.monolith.app.data.Chunk
import com.monolith.app.data.ConvSetting
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Group
import com.monolith.app.data.InviteState
import com.monolith.app.data.ListItem
import com.monolith.app.data.Message
import com.monolith.app.data.MsgStatus
import com.monolith.app.data.Place
import com.monolith.app.data.Processed
import com.monolith.app.data.Reaction
import com.monolith.app.data.SharedList
import com.monolith.app.data.Vote
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

/** Reply reference stored inside a message's extra json */
data class ReplyRef(val id: String, val sender: String, val preview: String)

/**
 * Heart of the app: the Monolith protocol over the encrypted mailbox (always reachable)
 * and over direct links between phones (when both are online).
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
        val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🔥", "🙏")
    }

    /** the conversation currently on screen (no notifications for it) */
    val activeConv = MutableStateFlow<String?>(null)

    private val _online = MutableStateFlow<Map<String, Long>>(emptyMap())
    val online: StateFlow<Map<String, Long>> = _online.asStateFlow()

    private val _typing = MutableStateFlow<Map<String, Pair<String, Long>>>(emptyMap())
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
        scope.launch { prefs.relays.collect { relays.setRelays(it) } }
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
        scope.launch {
            while (true) {
                sweepExpired()
                delay(30_000)
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

    /** prefer the direct link, fall back to a live mailbox event */
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
            return
        }
        handlePayload(inner.from, inner.payload, inner.createdAt * 1000, viaRtc = false)
    }

    /** friend, or member of a group I'm in (needed for group calls and group messages) */
    suspend fun isKnownContact(pk: String): Boolean {
        val f = db.friends().get(pk)
        if (f?.status == FriendStatus.FRIEND) return true
        return db.groups().allNow().any { pk in it.memberList() }
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
            "react" -> onReact(from, p, friend)
            "pin" -> onPin(from, p, friend)
            "shard" -> Graph.shards.onShard(from, p, isFriend)
            "bill" -> Graph.together.onBill(from, p)
            "bexp" -> Graph.together.onExpense(from, p)
            "habit" -> Graph.together.onHabit(from, p)
            "hcheck" -> Graph.together.onCheck(from, p)
            "shardleave" -> Graph.shards.onLeave(from, p)
            "shardev" -> Graph.shards.onEvent(from, p)
            "vroom" -> Graph.shards.onVoice(from, p)
            "vwho" -> Graph.shards.onWho(from, p)
            "status" -> if (friend?.status == FriendStatus.FRIEND) {
                val u = p.optLong("u")
                Graph.statuses.setFriend(from, if (u > now()) com.monolith.app.data.Status(p.optString("e").take(8), p.optString("x").take(80), minOf(u, now() + 86_400_000L)) else null)
            }
            "edit" -> onEdit(from, p)
            "del" -> onDelete(from, p)
            "vote" -> onVote(from, p, friend)
            "gmove" -> onGameMove(from, p, friend)
            "noshot" -> onNoShot(from, p, friend)
            "shot" -> onShot(from, p, friend)
            "once" -> onOnceOpened(from, p, friend)
            "draw", "drawundo", "drawclr", "drawjoin", "drawall", "drawbye" -> convFor(from, p, friend)?.let { Boards.onLive(it, from, p) }
            "ttl" -> onTtl(from, p, friend)
            "grp" -> onGroup(from, p, isFriend)
            "gleave" -> onGroupLeave(from, p)
            "evinv" -> if (isFriend) onEventInvite(from, p)
            "evrsvp" -> onRsvp(from, p)
            "evdel" -> onEventDelete(from, p)
            "list" -> onList(from, p, isFriend)
            "li" -> onListItem(from, p)
            "lleave" -> onListLeave(from, p)
            "place" -> onPlace(from, p, isFriend)
            "placedel" -> onPlaceDelete(from, p)
            "placehere" -> onPlaceHere(from, p)
            "loc" -> if (isFriend) onLocation(friend!!, p)
            "nearon" -> Graph.nearby.onOn(from, p, friend)
            "nearpos" -> if (isFriend) Graph.nearby.onPos(from, p)
            "locstart" -> if (isFriend) onLocStart(friend!!, p)
            "locstop" -> if (isFriend) { db.friends().sharing(from, 0); Graph.notifier.cancelArriving(from) }
            "pres" -> if (isFriend) onPresence(from, p)
            "typing" -> onTyping(from, p, isFriend)
            "rtc" -> if (isFriend) Graph.rtc.onSignal(from, p)
            "call" -> if (isFriend || isKnownContact(from)) Graph.calls.onSignal(from, p)
        }
    }

    private fun markOnline(pk: String) {
        _online.update { it + (pk to now()) }
        scope.launch { db.friends().seen(pk, now()) }
    }

    private suspend fun onPresence(from: String, p: JSONObject) {
        markOnline(from)
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
                    Friend(from, prof.username, prof.displayName, prof.emoji, prof.bio, FriendStatus.INCOMING, now(), verified = existing?.verified ?: false)
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
        readBirthday(from, p)
        Graph.profileExtras.read(from, p)
        if (!wasFriend) {
            Graph.notifier.friendAccepted(prof.displayName, prof.emoji)
            // the request carried no birthday, cover or badges: send them now that we are friends
            sendStored(from, profilePayload("prof"))
        }
    }

    private suspend fun onProfile(friend: Friend, p: JSONObject) {
        val prof = Profile.fromJson(p)
        readBirthday(friend.pubkey, p)
        Graph.profileExtras.read(friend.pubkey, p)
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
        val ts = minOf(p.optLong("ts", sentAt), now())
        val exp = p.optLong("exp", 0)
        val msg = Message(
            id = id,
            convId = conv,
            sender = from,
            outgoing = false,
            ts = ts,
            kind = kind,
            text = p.optString("x"),
            extra = extra?.toString(),
            status = if (isActive) MsgStatus.READ else MsgStatus.PENDING,
            progress = if (kind == "photo" || kind == "voice") 0f else 1f,
            expiresAt = if (exp > 0) minOf(ts + exp * 1000, now() + exp * 1000) else 0,
        )
        if (db.messages().insert(msg) == -1L) return
        if (!conv.startsWith("g:") && kind != "call") Graph.bondDays.touch(conv, ts)
        _typing.update { it - conv }
        if (kind == "photo" || kind == "voice") tryAssemble(id)
        sendStored(from, JSONObject().put("t", "ack").put("ids", JSONArray().put(id)).put("s", if (isActive) MsgStatus.READ else MsgStatus.DELIVERED), 7L * 86400)
        // an arrival that reaches me hours later is history, not news: keep it in the chat, silently
        val stale = (kind == "place" && now() - ts > 2 * 3_600_000L) || Shard.isQuietConv(conv)
        val myPub = me()?.pub ?: ""
        val mentioned = extra != null && (extra.optBoolean("all") || (extra.optJSONArray("mn")?.let { a -> (0 until a.length()).any { a.optString(it) == myPub } } == true))
        val muted = db.social().settingNow(conv)?.muted == true && !mentioned && kind != "sos"
        if (!isActive && !stale && !muted) {
            val group = if (conv.startsWith("g:")) db.groups().get(conv.removePrefix("g:")) else null
            val sender = friend ?: db.friends().get(from)
            val title = if (mentioned && group != null) t("{0} ti ha menzionato in {1}", sender?.displayName ?: t("Qualcuno"), group.name)
                else group?.name ?: friend?.displayName ?: "Monolith"
            Graph.notifier.message(
                conv = conv,
                title = title,
                senderName = sender?.displayName ?: t("Qualcuno"),
                senderEmoji = sender?.emoji ?: "🙂",
                text = previewText(msg),
                isGroup = group != null,
                groupEmoji = group?.emoji,
                nudge = kind == "nudge",
                meeting = kind == "place",
                sos = kind == "sos",
            )
        } else if (kind == "nudge") {
            Graph.notifier.vibrateNudge()
        }
    }

    fun previewText(m: Message): String = when (m.kind) {
        "photo" -> if (m.extra?.contains("\"once\":true") == true) "📷 " + t("Foto a vista unica")
            else if (m.text.isNotBlank()) "📷 ${m.text}" else "📷 " + t("Foto")
        "voice" -> "🎤 " + t("Messaggio vocale")
        "event" -> "📅 " + (runCatching { JSONObject(m.extra ?: "{}").optString("title") }.getOrNull() ?: t("Evento"))
        "loc" -> "📍 " + t("Posizione")
        "nudge" -> "📳 " + t("Trillo!")
        "poll" -> "📊 " + (runCatching { JSONObject(m.extra ?: "{}").optString("q") }.getOrNull() ?: t("Sondaggio"))
        "call" -> "📞 " + m.text
        "deleted" -> "🚫 " + t("Messaggio eliminato")
        "scratch" -> "✨ " + t("Messaggio da grattare")
        "game" -> runCatching { JSONObject(m.extra ?: "{}").optString("g") }.getOrDefault(Games.TTT).let { "🎮 " + Games.name(it) }
        "dice" -> diceText(m)
        "board" -> "🎨 " + t("Lavagna condivisa")
        "sticker" -> "🖼️ " + t("Sticker")
        "sos" -> "🆘 " + if (m.outgoing) t("Hai chiesto aiuto") else t("Ha bisogno di aiuto")
        "place" -> {
            val name = runCatching { JSONObject(m.extra ?: "{}").optString("n") }.getOrNull().orEmpty().ifBlank { m.text }
            "📍 " + if (m.outgoing) t("Hai raggiunto {0}", name) else t("Ha raggiunto {0}", name)
        }
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

    private fun extFor(kind: String) = when (kind) {
        "voice" -> "m4a"
        "sticker" -> "webp"
        else -> "jpg"
    }

    /** sends a copy of [m] to another chat, marked as forwarded */
    suspend fun forward(m: Message, conv: String) {
        val ex = runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject())
        when (m.kind) {
            "text", "scratch" -> deliver(newOutgoing(conv, "text", m.text, JSONObject().put("fw", true)) ?: return)
            "photo" -> m.mediaPath?.let { path -> sendPhoto(conv, Uri.fromFile(File(path)), m.text, forwarded = true) }
            "voice" -> m.mediaPath?.let { path ->
                val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                sendMedia(conv, "voice", bytes, JSONObject().put("dur", ex.optLong("dur")).put("fw", true), "", null)
            }
            "sticker" -> m.mediaPath?.let { path ->
                val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                sendMedia(conv, "sticker", bytes, JSONObject().put("fw", true), "", null)
            }
        }
    }

    /** a reply (or a reaction) to a friend's story: it arrives in your chat with them, with a small preview */
    suspend fun replyToStory(story: Message, text: String, reaction: Boolean) {
        val owner = story.sender
        if (db.friends().get(owner)?.status != FriendStatus.FRIEND) return
        val ref = JSONObject().put("id", story.id).put("k", story.kind).put("x", story.text.take(80))
        if (story.kind == "photo") story.mediaPath?.let { path ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                    val b = android.graphics.BitmapFactory.decodeFile(path, o) ?: return@withContext null
                    val sc = android.graphics.Bitmap.createScaledBitmap(b, 80, (80f * b.height / b.width).toInt().coerceIn(40, 160), true)
                    val bos = java.io.ByteArrayOutputStream()
                    sc.compress(android.graphics.Bitmap.CompressFormat.JPEG, 55, bos)
                    android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                }
            }.getOrNull()?.let { ref.put("th", it) }
        }
        val ex = JSONObject().put("story", ref)
        if (reaction) ex.put("react", true)
        deliver(newOutgoing(owner, "text", text, ex) ?: return)
    }

    suspend fun sendSticker(conv: String, file: File, reply: ReplyRef? = null) {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        sendMedia(conv, "sticker", bytes, JSONObject(), "", reply)
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
        val file = File(Media.dir(ctx), "$id.${extFor(msg.kind)}")
        file.writeBytes(out.toByteArray())
        db.messages().progress(id, 1f, file.absolutePath)
        db.misc().clearChunks(id)
    }

    private suspend fun onAck(p: JSONObject) {
        val ids = p.optJSONArray("ids") ?: return
        val s = p.optInt("s", MsgStatus.DELIVERED).coerceIn(MsgStatus.DELIVERED, MsgStatus.READ)
        for (i in 0 until ids.length()) db.messages().raiseStatus(ids.getString(i), s)
    }

    private suspend fun onReact(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val id = p.optString("id")
        val m = db.messages().get(id) ?: return
        if (m.convId != conv) return
        val e = p.optString("e")
        if (e.isBlank()) db.social().unreact(id, from) else db.social().react(Reaction(id, from, conv, e, now()))
    }

    private suspend fun onEdit(from: String, p: JSONObject) {
        val m = db.messages().get(p.optString("id")) ?: return
        if (m.sender != from || m.kind == "deleted") return
        db.messages().edit(m.id, p.optString("x"))
    }

    private suspend fun onDelete(from: String, p: JSONObject) {
        val m = db.messages().get(p.optString("id")) ?: return
        if (m.sender != from) return
        m.mediaPath?.let { runCatching { File(it).delete() } }
        db.messages().markDeleted(m.id)
    }

    private suspend fun onVote(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val id = p.optString("id")
        val m = db.messages().get(id) ?: return
        if (m.convId != conv || m.kind != "poll") return
        val opts = (p.optJSONArray("o") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optInt(it) } }
        db.social().vote(Vote(id, from, conv, opts.joinToString(","), now()))
    }

    private suspend fun onTtl(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val sec = p.optLong("s").coerceAtLeast(0)
        val cur = db.social().settingNow(conv) ?: ConvSetting(conv)
        db.social().saveSetting(cur.copy(ttlSec = sec))
        val who = (friend ?: db.friends().get(from))?.displayName ?: t("Qualcuno")
        db.messages().insert(Message(newId(), conv, from, false, now(), "system", text = ttlSystemText(who, sec), status = MsgStatus.READ))
    }

    fun ttlLabel(sec: Long): String = when {
        sec <= 0 -> t("Disattivati")
        sec <= 3600 -> t("1 ora")
        sec <= 86400 -> t("24 ore")
        else -> t("7 giorni")
    }

    private fun ttlSystemText(who: String, sec: Long) =
        if (sec <= 0) t("{0} ha disattivato i messaggi effimeri", who)
        else t("{0} ha attivato i messaggi effimeri: {1}", who, ttlLabel(sec))

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
        val group = Group(gid, p.optString("name", t("Gruppo")), p.optString("e", "💬"), members.joinToString(","), from, existing?.createdAt ?: now())
        db.groups().upsert(group)
        if (existing == null && !gid.startsWith(Shard.STORY)) {
            val creator = db.friends().get(from)
            val name = creator?.displayName ?: t("Qualcuno")
            val album = gid.startsWith(Shard.ALBUM)
            db.messages().insert(Message(newId(), "g:$gid", from, false, now(), "system", text = if (album) t("{0} ti ha invitato nell'album", name) else t("{0} ti ha aggiunto al gruppo", name), status = MsgStatus.READ))
            Graph.notifier.message("g:$gid", group.name, name, creator?.emoji ?: "🙂", if (album) t("Ti ha invitato in un album condiviso") else t("Ti ha aggiunto al gruppo"), true, group.emoji, false)
        }
    }

    private suspend fun onGroupLeave(from: String, p: JSONObject) {
        val gid = p.optString("g")
        val g = db.groups().get(gid) ?: return
        if (from !in g.memberList()) return
        val left = db.friends().get(from)
        db.groups().upsert(g.copy(members = g.memberList().filter { it != from }.joinToString(",")))
        db.messages().insert(Message(newId(), "g:$gid", from, false, now(), "system", text = t("{0} ha lasciato il gruppo", left?.displayName ?: t("Qualcuno")), status = MsgStatus.READ))
    }

    // ------------------------------------------------------------------ location

    private suspend fun onLocation(friend: Friend, p: JSONObject) {
        val until = p.optLong("until", now() + 60_000)
        if (until < now()) return
        val lat = p.optDouble("lat")
        val lon = p.optDouble("lon")
        db.friends().location(
            friend.pubkey, lat, lon, p.optDouble("acc", 0.0).toFloat(), p.optLong("at", now()), until,
            p.optInt("dist", -1), p.optInt("eta", -1),
        )
        val dist = p.optInt("dist", -1)
        val eta = p.optInt("eta", -1)
        if (dist >= 0 && eta >= 0 && dist > 120) Graph.notifier.arrivingLive(friend.pubkey, friend.displayName, friend.emoji, dist, eta)
        else if (dist in 0..120) Graph.notifier.cancelArriving(friend.pubkey)
        if (p.has("bat") && com.monolith.app.data.FriendBattery.update(friend.pubkey, p.optInt("bat", -1), p.optBoolean("chg"))) {
            Graph.notifier.batteryLow(friend.pubkey, friend.displayName, friend.emoji, p.optInt("bat"))
        }
        markOnline(friend.pubkey)
    }

    private suspend fun onLocStart(friend: Friend, p: JSONObject) {
        val until = p.optLong("until")
        db.friends().sharing(friend.pubkey, until)
        if (until > now()) Graph.notifier.locationShared(friend.displayName, friend.emoji, until, p.optBoolean("otw"))
    }

    suspend fun sendLocStart(to: String, until: Long, onTheWay: Boolean = false) {
        sendStored(to, JSONObject().put("t", "locstart").put("until", until).put("otw", onTheWay), 86400)
    }

    suspend fun sendLocStop(to: String) {
        sendStored(to, JSONObject().put("t", "locstop"), 86400)
    }

    // ------------------------------------------------------------------ agenda sync

    private fun eventJson(e: AgendaEvent) = JSONObject()
        .put("id", e.id).put("title", e.title).put("desc", e.description).put("notes", e.notes)
        .put("start", e.start).put("end", e.end ?: 0L).put("allDay", e.allDay).put("emoji", e.emoji)
        .put("rep", e.repeat).put("until", e.repeatUntil ?: 0L)

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
            reminders = existing?.reminders ?: "15",
            repeat = o.optInt("rep", 0),
            repeatUntil = o.optLong("until").takeIf { it > 0 },
            owner = from,
            invitees = (p.optJSONArray("inv") ?: JSONArray()).strings().joinToString(","),
            inviteState = existing?.inviteState ?: InviteState.PENDING,
            createdAt = existing?.createdAt ?: now(),
            updatedAt = now(),
        )
        db.agenda().upsert(ev)
        if (ev.inviteState == InviteState.ACCEPTED) ReminderScheduler.schedule(ctx, ev)
        if (existing == null) Graph.notifier.eventInvite(ev, friend?.displayName ?: t("Un amico"))
        Graph.refreshWidget()
    }

    private suspend fun onRsvp(from: String, p: JSONObject) {
        val ev = db.agenda().get(p.optString("id")) ?: return
        if (ev.owner.isNotEmpty() || from !in ev.inviteeList()) return
        val rsvp = runCatching { JSONObject(ev.rsvp) }.getOrDefault(JSONObject())
        val answer = p.optString("s")
        rsvp.put(from, answer)
        db.agenda().upsert(ev.copy(rsvp = rsvp.toString(), updatedAt = now()))
        val f = db.friends().get(from)
        Graph.notifier.rsvp(ev, f?.displayName ?: t("Un amico"), answer == "yes")
    }

    private suspend fun onEventDelete(from: String, p: JSONObject) {
        val ev = db.agenda().get(p.optString("id")) ?: return
        if (ev.owner != from) return
        db.agenda().delete(ev.id)
        ReminderScheduler.cancel(ctx, ev.id)
        if (ev.inviteState != InviteState.DECLINED) Graph.notifier.eventCancelled(ev)
        Graph.refreshWidget()
    }

    suspend fun saveEvent(e: AgendaEvent, notifyInvitees: Boolean = true) {
        db.agenda().upsert(e)
        if (e.inviteState != InviteState.DECLINED && e.inviteState != InviteState.PENDING) ReminderScheduler.schedule(ctx, e)
        if (e.owner.isEmpty() && notifyInvitees) {
            val inv = e.inviteeList()
            val payload = JSONObject().put("t", "evinv").put("ev", eventJson(e)).put("inv", inv.toJsonArray())
            inv.forEach { sendStored(it, payload) }
        }
        Graph.refreshWidget()
    }

    suspend fun deleteEvent(e: AgendaEvent) {
        db.agenda().delete(e.id)
        ReminderScheduler.cancel(ctx, e.id)
        if (e.owner.isEmpty()) {
            e.inviteeList().forEach { sendStored(it, JSONObject().put("t", "evdel").put("id", e.id)) }
        } else if (e.inviteState != InviteState.DECLINED) {
            sendStored(e.owner, JSONObject().put("t", "evrsvp").put("id", e.id).put("s", "no"))
        }
        Graph.refreshWidget()
    }

    suspend fun respondInvite(id: String, accept: Boolean) {
        val e = db.agenda().get(id) ?: return
        val updated = e.copy(inviteState = if (accept) InviteState.ACCEPTED else InviteState.DECLINED, updatedAt = now())
        db.agenda().upsert(updated)
        if (accept) ReminderScheduler.schedule(ctx, updated) else ReminderScheduler.cancel(ctx, id)
        sendStored(e.owner, JSONObject().put("t", "evrsvp").put("id", id).put("s", if (accept) "yes" else "no"))
        Graph.refreshWidget()
    }

    // ------------------------------------------------------------------ shared lists

    private fun itemJson(i: ListItem) = JSONObject().put("id", i.id).put("x", i.text).put("d", i.done).put("by", i.by).put("ts", i.updatedAt).put("del", i.deleted)

    private fun itemFrom(listId: String, o: JSONObject) = ListItem(
        id = o.optString("id"), listId = listId, text = o.optString("x"), done = o.optBoolean("d"),
        by = o.optString("by"), updatedAt = o.optLong("ts"), deleted = o.optBoolean("del"),
    )

    private suspend fun mergeItem(i: ListItem) {
        if (i.id.isBlank()) return
        val cur = db.lists().item(i.id)
        if (cur == null || cur.updatedAt < i.updatedAt) db.lists().upsertItem(i)
    }

    private suspend fun onList(from: String, p: JSONObject, isFriend: Boolean) {
        val id = p.optString("id").ifBlank { return }
        val k = me() ?: return
        val existing = db.lists().get(id)
        val members = (p.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) }
        if (existing == null && (!isFriend || k.pub !in members)) return
        if (existing != null && from !in existing.memberList()) return
        if (k.pub !in members) {
            db.lists().delete(id); db.lists().clearItems(id); return
        }
        val l = SharedList(id, p.optString("title", t("Lista")), p.optString("e", "📝"), members.joinToString(","), existing?.owner ?: p.optString("owner", from), existing?.createdAt ?: now(), now())
        db.lists().upsert(l)
        p.optJSONArray("items")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { mergeItem(itemFrom(id, it)) } }
        if (existing == null) {
            val f = db.friends().get(from)
            Graph.notifier.listShared(f?.displayName ?: t("Un amico"), l.emoji + " " + l.title)
        }
    }

    private suspend fun onListItem(from: String, p: JSONObject) {
        val l = db.lists().get(p.optString("l")) ?: return
        if (from !in l.memberList()) return
        val o = p.optJSONObject("it") ?: return
        mergeItem(itemFrom(l.id, o))
        db.lists().upsert(l.copy(updatedAt = now()))
    }

    private suspend fun onListLeave(from: String, p: JSONObject) {
        val l = db.lists().get(p.optString("l")) ?: return
        if (from !in l.memberList()) return
        db.lists().upsert(l.copy(members = l.memberList().filter { it != from }.joinToString(",")))
    }

    private suspend fun broadcastList(l: SharedList, includeItems: Boolean) {
        val k = me() ?: return
        val payload = JSONObject().put("t", "list").put("id", l.id).put("title", l.title).put("e", l.emoji)
            .put("m", l.memberList().toJsonArray()).put("owner", l.owner)
        if (includeItems) payload.put("items", JSONArray().also { a -> db.lists().itemsNow(l.id).forEach { a.put(itemJson(it)) } })
        l.memberList().filter { it != k.pub }.forEach { sendStored(it, payload) }
    }

    suspend fun saveList(id: String?, title: String, emoji: String, members: List<String>): String? {
        val k = me() ?: return null
        val existing = id?.let { db.lists().get(it) }
        val all = (members + k.pub).distinct()
        val l = SharedList(existing?.id ?: newId(), title.trim().ifBlank { t("Lista") }, emoji, all.joinToString(","), existing?.owner ?: k.pub, existing?.createdAt ?: now(), now())
        db.lists().upsert(l)
        broadcastList(l, includeItems = true)
        // members removed from the list get a final update without themselves
        existing?.memberList()?.filter { it !in all }?.forEach {
            sendStored(it, JSONObject().put("t", "list").put("id", l.id).put("m", all.toJsonArray()))
        }
        return l.id
    }

    suspend fun saveItem(listId: String, itemId: String?, text: String? = null, done: Boolean? = null, deleted: Boolean = false) {
        val k = me() ?: return
        val l = db.lists().get(listId) ?: return
        val cur = itemId?.let { db.lists().item(it) }
        val item = ListItem(
            id = cur?.id ?: newId(), listId = listId, text = text ?: cur?.text ?: "", done = done ?: cur?.done ?: false,
            by = k.pub, updatedAt = now(), deleted = deleted,
        )
        if (item.text.isBlank() && !deleted) return
        db.lists().upsertItem(item)
        db.lists().upsert(l.copy(updatedAt = now()))
        val payload = JSONObject().put("t", "li").put("l", listId).put("it", itemJson(item))
        l.memberList().filter { it != k.pub }.forEach { sendStored(it, payload) }
    }

    suspend fun leaveList(listId: String) {
        val k = me() ?: return
        val l = db.lists().get(listId) ?: return
        l.memberList().filter { it != k.pub }.forEach { sendStored(it, JSONObject().put("t", "lleave").put("l", listId)) }
        db.lists().delete(listId)
        db.lists().clearItems(listId)
    }

    // ================================================================== directory

    fun profilePayload(type: String): JSONObject {
        val p = identity.profile.value ?: Profile("", "", "🙂")
        val j = p.toJson().put("t", type)
        // the birthday only goes to friends, never to a stranger with a request
        if (type != "freq") j.put("bd", Graph.birthdays.mine.value?.code() ?: "")
        j.put("cv", Graph.profileExtras.cover.value).put("bg", Badges.ids())
        return j
    }

    /** cover or badges changed: tell the friends */
    suspend fun sendProfileToFriends() {
        val payload = profilePayload("prof")
        db.friends().friendsNow().forEach { sendStored(it.pubkey, payload) }
    }

    private fun readBirthday(pk: String, p: JSONObject) {
        if (!p.has("bd")) return
        Graph.birthdays.setFriend(pk, com.monolith.app.data.Birthday.parse(p.optString("bd")))
    }

    /** my birthday (day and month), shared privately with my friends */
    suspend fun setMyBirthday(b: com.monolith.app.data.Birthday?) {
        Graph.birthdays.setMine(b)
        val payload = profilePayload("prof")
        db.friends().friendsNow().forEach { sendStored(it.pubkey, payload) }
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
        val tagFilter = if (q.length == 64) "" else Profile.normalizeUsername(q).substringAfter('#', "")
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

    // ================================================================== friends

    suspend fun sendFriendRequest(u: UserResult, verified: Boolean = false) {
        val existing = db.friends().get(u.pubkey)
        if (existing?.status == FriendStatus.FRIEND) {
            if (verified) db.friends().verified(u.pubkey, true)
            return
        }
        if (existing?.status == FriendStatus.INCOMING) {
            if (verified) db.friends().verified(u.pubkey, true)
            acceptFriend(u.pubkey)
            return
        }
        db.friends().upsert(
            Friend(u.pubkey, u.profile.username, u.profile.displayName, u.profile.emoji, u.profile.bio, FriendStatus.OUTGOING, now(), verified = verified)
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

    // ------------------------------------------------------------------ common places

    private val places get() = Graph.places

    private fun placePayload(p: Place) = JSONObject().put("t", "place").put("id", p.id).put("n", p.name).put("e", p.emoji)
        .put("la", p.lat).put("lo", p.lon).put("r", p.radius).put("m", p.members.toJsonArray()).put("by", p.createdBy).put("u", p.updatedAt)

    private suspend fun onPlace(from: String, p: JSONObject, isFriend: Boolean) {
        val id = p.optString("id").ifBlank { return }
        val k = me() ?: return
        val existing = places.get(id)
        val members = (p.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) }
        if (existing == null && (!isFriend || k.pub !in members || from !in members)) return
        if (existing != null && from !in existing.members) return
        if (k.pub !in members) { places.delete(id); return }
        val u = p.optLong("u")
        if (existing != null && existing.updatedAt >= u) return
        val lat = p.optDouble("la")
        val lon = p.optDouble("lo")
        if (lat.isNaN() || lon.isNaN()) return
        val place = Place(
            id = id, name = p.optString("n").ifBlank { t("Luogo") }, emoji = p.optString("e", "📍"),
            lat = lat, lon = lon, radius = p.optInt("r", 30).coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS),
            members = members, createdBy = p.optString("by", from), updatedAt = u,
            here = existing?.here?.filterKeys { it in members } ?: emptyMap(),
        )
        places.upsert(place)
        if (existing != null && (existing.lat != place.lat || existing.lon != place.lon || existing.radius != place.radius)) places.resetGeo(id)
        if (existing == null) {
            val f = db.friends().get(from)
            Graph.notifier.placeShared(f?.displayName ?: t("Un amico"), place.emoji + " " + place.name)
        }
    }

    private fun onPlaceDelete(from: String, p: JSONObject) {
        val existing = places.get(p.optString("id")) ?: return
        if (from !in existing.members) return
        places.delete(existing.id)
    }

    private fun onPlaceHere(from: String, p: JSONObject) {
        val existing = places.get(p.optString("id")) ?: return
        if (from !in existing.members) return
        places.setHere(existing.id, from, p.optBoolean("in"), minOf(p.optLong("at", now()), now()))
    }

    /** create or edit a common place and share it with [friends] */
    suspend fun savePlace(id: String?, name: String, emoji: String, lat: Double, lon: Double, radius: Int, friends: List<String>): String? {
        val k = me() ?: return null
        val existing = id?.let { places.get(it) }
        val all = (friends + k.pub).distinct()
        val p = Place(
            id = existing?.id ?: newId(), name = name.trim().ifBlank { t("Luogo") }, emoji = emoji,
            lat = lat, lon = lon, radius = radius.coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS),
            members = all, createdBy = existing?.createdBy ?: k.pub, updatedAt = now(),
            here = existing?.here?.filterKeys { it in all } ?: emptyMap(),
        )
        places.upsert(p)
        if (existing == null || existing.lat != lat || existing.lon != lon || existing.radius != p.radius) places.resetGeo(p.id)
        val payload = placePayload(p)
        all.filter { it != k.pub }.forEach { sendStored(it, payload) }
        // people taken off the place get a last update without themselves, so it disappears for them
        existing?.members?.filter { it !in all && it != k.pub }?.forEach {
            sendStored(it, JSONObject().put("t", "place").put("id", p.id).put("m", all.toJsonArray()).put("u", p.updatedAt))
        }
        Graph.placeWatch.refresh()
        return p.id
    }

    /**
     * SOS: every friend gets an alarm-like message with where I am, and my live position for an hour.
     * Returns how many friends were reached.
     */
    suspend fun sendSos(lat: Double?, lon: Double?, accuracy: Float?): Int {
        val friends = db.friends().friendsNow().filter { it.status == FriendStatus.FRIEND }
        val ex = JSONObject()
        if (lat != null && lon != null) ex.put("lat", lat).put("lon", lon).put("acc", (accuracy ?: 0f).toDouble())
        Graph.medical.card.value.takeIf { !it.isEmpty }?.let { ex.put("med", it.toJson()) }
        friends.forEach { f ->
            deliver(newOutgoing(f.pubkey, "sos", t("SOS"), JSONObject(ex.toString())) ?: return@forEach)
        }
        if (friends.isNotEmpty()) Graph.location.startSharing(friends.map { it.pubkey }, 60, remember = false)
        return friends.size
    }

    /** a common place is common: deleting it removes it for everyone */
    suspend fun deletePlace(id: String) {
        val k = me() ?: return
        val p = places.get(id) ?: return
        places.delete(id)
        p.members.filter { it != k.pub }.forEach { sendStored(it, JSONObject().put("t", "placedel").put("id", id)) }
        Graph.placeWatch.refresh()
    }

    /**
     * I walked into (or out of) a common place: everyone else in it learns where I am, and on arrival
     * they also get a chat message, which brings the usual notification.
     */
    suspend fun placePresence(p: Place, inside: Boolean, notify: Boolean) {
        val k = me() ?: return
        val at = now()
        places.setHere(p.id, k.pub, inside, at)
        val others = p.members.filter { it != k.pub }
        others.forEach { sendStored(it, JSONObject().put("t", "placehere").put("id", p.id).put("in", inside).put("at", at), 2L * 86400) }
        if (inside && notify) {
            // friends already there: that's a meeting
            val fresh = places.get(p.id) ?: p
            val with = fresh.here.filter { (pk, pr) -> pk != k.pub && pr.inside && at - pr.at < 12 * 3_600_000L }.keys
                .mapNotNull { db.friends().get(it)?.displayName }
            if (with.isNotEmpty()) Graph.notifier.together(with.joinToString(", "), p.name, p.emoji)
            val ex = JSONObject().put("id", p.id).put("n", p.name).put("e", p.emoji).put("la", p.lat).put("lo", p.lon)
            others.forEach { pk ->
                if (db.friends().get(pk)?.status == FriendStatus.FRIEND) {
                    deliver(newOutgoing(pk, "place", p.name, JSONObject(ex.toString())) ?: return@forEach)
                }
            }
        }
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

    private suspend fun ttlFor(conv: String): Long = db.social().settingNow(conv)?.ttlSec ?: 0L

    private suspend fun deliver(m: Message) {
        val (targets, gid) = recipients(m.convId)
        val payload = JSONObject().put("t", "msg").put("id", m.id).put("k", m.kind).put("x", m.text).put("ts", m.ts)
        m.extra?.let { payload.put("ex", JSONObject(it)) }
        gid?.let { payload.put("g", it) }
        if (m.expiresAt > 0) payload.put("exp", ((m.expiresAt - m.ts) / 1000).coerceAtLeast(1))
        targets.forEach { to -> sendStored(to, payload, TTL_MESSAGE, m.id) }
    }

    private suspend fun newOutgoing(conv: String, kind: String, text: String = "", extra: JSONObject? = null, reply: ReplyRef? = null, mediaPath: String? = null, progress: Float = 1f): Message? {
        val k = me() ?: return null
        val ex = extra ?: if (reply != null) JSONObject() else null
        if (reply != null) ex?.put("re", JSONObject().put("id", reply.id).put("s", reply.sender).put("x", reply.preview.take(140)))
        val ts = now()
        val ttl = ttlFor(conv)
        val m = Message(newId(), conv, k.pub, true, ts, kind, text = text, mediaPath = mediaPath, extra = ex?.toString(), progress = progress, expiresAt = if (ttl > 0) ts + ttl * 1000 else 0)
        db.messages().insert(m)
        if (!conv.startsWith("g:")) Graph.bondDays.touch(conv, ts)
        return m
    }

    suspend fun sendText(conv: String, text: String, reply: ReplyRef? = null, music: JSONObject? = null) {
        val tx = text.trim()
        if (tx.isEmpty()) return
        val mentions = if (conv.startsWith("g:") && music == null) mentionsIn(conv, tx) else null
        val ex = music?.let { JSONObject().put("mu", it) } ?: mentions
        deliver(newOutgoing(conv, "text", tx, extra = ex, reply = reply) ?: return)
    }

    /** a story made of a piece of song from the phone */
    suspend fun sendAudioStory(conv: String, file: File, durationMs: Long, music: JSONObject, caption: String) {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        sendMedia(conv, "voice", bytes, JSONObject().put("dur", durationMs).put("mu", music), caption, null)
        runCatching { file.delete() }
    }

    /** members of the group named with @ in the text (or @tutti), for notifications that get through silence */
    private suspend fun mentionsIn(conv: String, tx: String): JSONObject? {
        if ('@' !in tx) return null
        val k = me() ?: return null
        val g = db.groups().get(conv.removePrefix("g:")) ?: return null
        val low = tx.lowercase()
        val all = Regex("@(tutti|all|everyone|tous|alle|все|wszyscy|všichni)\\b").containsMatchIn(low)
        val pks = g.memberList().filter { it != k.pub }.filter { pk ->
            val name = db.friends().get(pk)?.displayName ?: return@filter false
            val first = name.substringBefore(' ')
            low.contains("@" + name.lowercase()) || Regex("@" + Regex.escape(first.lowercase()) + "\\b").containsMatchIn(low)
        }
        if (!all && pks.isEmpty()) return null
        return JSONObject().put("mn", JSONArray().also { a -> pks.forEach { a.put(it) } }).put("all", all)
    }

    suspend fun setMuted(conv: String, muted: Boolean) {
        val cur = db.social().settingNow(conv) ?: ConvSetting(conv)
        db.social().saveSetting(cur.copy(muted = muted))
    }

    // ---------------------------------------------------------------- games, dice, view once, board

    suspend fun sendGame(conv: String, type: String, word: String = "") {
        val k = me() ?: return
        val ex = JSONObject().put("g", type).put("mv", JSONArray())
        if (!conv.startsWith("g:")) ex.put("o", conv)
        if (type == Games.HANG) ex.put("w", Games.cleanWord(word))
        if (k.pub.isEmpty()) return
        deliver(newOutgoing(conv, "game", Games.name(type), ex) ?: return)
    }

    suspend fun gameMove(id: String, v: String) {
        val k = me() ?: return
        val m = db.messages().get(id) ?: return
        val ex = runCatching { JSONObject(m.extra ?: "{}") }.getOrNull() ?: return
        val st = Games.parse(m.sender, ex)
        if (!Games.canMove(st, k.pub, v)) return
        val n = st.moves.size
        db.messages().setExtra(id, Games.withMove(ex, k.pub, v).toString())
        toConv(m.convId, JSONObject().put("t", "gmove").put("id", id).put("n", n).put("v", v))
    }

    private suspend fun onGameMove(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val m = db.messages().get(p.optString("id")) ?: return
        if (m.convId != conv || m.kind != "game") return
        val ex = runCatching { JSONObject(m.extra ?: "{}") }.getOrNull() ?: return
        val st = Games.parse(m.sender, ex)
        val v = p.optString("v")
        if (p.optInt("n", -1) != st.moves.size || !Games.canMove(st, from, v)) return
        val next = Games.parse(m.sender, Games.withMove(ex, from, v))
        db.messages().setExtra(m.id, ex.toString())
        if (activeConv.value == conv && Graph.foreground) return
        val who = (friend ?: db.friends().get(from))?.displayName ?: t("Qualcuno")
        val myPub = me()?.pub ?: return
        val res = Games.result(next)
        val text = when {
            res == 3 && next.type != Games.HANG -> t("Pareggio")
            res != 0 -> t("Partita finita")
            Games.canMoveAny(next, myPub) -> t("Tocca a te")
            else -> t("{0} ha fatto la sua mossa", who)
        }
        Graph.notifier.game(conv, Games.emoji(next.type) + " " + Games.name(next.type), who + " · " + text)
    }

    suspend fun sendDice(conv: String, type: String) {
        val r = java.security.SecureRandom()
        val res = when (type) {
            "coin" -> listOf(r.nextInt(2))
            "dice2" -> listOf(r.nextInt(6) + 1, r.nextInt(6) + 1)
            else -> listOf(r.nextInt(6) + 1)
        }
        val ex = JSONObject().put("d", type).put("r", JSONArray().also { a -> res.forEach { a.put(it) } })
        val text = if (type == "coin") (if (res[0] == 0) t("Testa") else t("Croce")) else res.joinToString(" + ") + if (res.size > 1) " = " + res.sum() else ""
        deliver(newOutgoing(conv, "dice", text, ex) ?: return)
    }

    fun diceText(m: Message): String {
        val type = runCatching { JSONObject(m.extra ?: "{}").optString("d") }.getOrDefault("dice")
        return (if (type == "coin") "🪙 " else "🎲 ") + m.text
    }

    /** a view-once photo was opened: delete it here and tell the sender */
    suspend fun openedOnce(m: Message) {
        val k = me() ?: return
        m.mediaPath?.let { runCatching { File(it).delete() } }
        db.messages().clearMedia(m.id)
        val ex = runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject())
        val by = ex.optJSONArray("ob") ?: JSONArray()
        by.put(k.pub)
        db.messages().setExtra(m.id, ex.put("ob", by).toString())
        toConv(m.convId, JSONObject().put("t", "once").put("id", m.id))
    }

    private suspend fun onOnceOpened(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val m = db.messages().get(p.optString("id")) ?: return
        if (m.convId != conv || m.kind != "photo") return
        val ex = runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject())
        val by = ex.optJSONArray("ob") ?: JSONArray()
        if ((0 until by.length()).any { by.optString(it) == from }) return
        by.put(from)
        db.messages().setExtra(m.id, ex.put("ob", by).toString())
        // in a one-to-one chat nobody can open it any more: the sender's copy goes too
        if (m.outgoing && !conv.startsWith("g:")) {
            m.mediaPath?.let { runCatching { File(it).delete() } }
            db.messages().clearMedia(m.id)
        }
    }

    // ---------------------------------------------------------------- screenshots

    private suspend fun systemNote(conv: String, from: String, outgoing: Boolean, text: String) {
        db.messages().insert(Message(newId(), conv, from, outgoing, now(), "system", text = text, status = MsgStatus.READ))
    }

    /** block screenshots in this chat, on my phone and on theirs */
    suspend fun setScreenBlock(conv: String, on: Boolean) {
        val k = me() ?: return
        Graph.guard.set(conv, on)
        toConv(conv, JSONObject().put("t", "noshot").put("on", on))
        systemNote(conv, k.pub, true, if (on) t("Hai bloccato gli screenshot in questa chat") else t("Hai riattivato gli screenshot in questa chat"))
    }

    private suspend fun onNoShot(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val on = p.optBoolean("on")
        Graph.guard.set(conv, on)
        val who = (friend ?: db.friends().get(from))?.displayName ?: t("Qualcuno")
        systemNote(conv, from, false, if (on) t("{0} ha bloccato gli screenshot in questa chat", who) else t("{0} ha riattivato gli screenshot in questa chat", who))
    }

    /** I took a screenshot of this chat: the others are told */
    suspend fun screenshotTaken(conv: String) {
        val k = me() ?: return
        toConv(conv, JSONObject().put("t", "shot"))
        systemNote(conv, k.pub, true, "📸 " + t("Hai fatto uno screenshot"))
    }

    private suspend fun onShot(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val who = (friend ?: db.friends().get(from))?.displayName ?: t("Qualcuno")
        systemNote(conv, from, false, "📸 " + t("{0} ha fatto uno screenshot della chat", who))
        if (activeConv.value != conv || !Graph.foreground) Graph.notifier.screenshot(conv, who, (friend ?: db.friends().get(from))?.emoji ?: "📸")
    }

    suspend fun sendBoardInvite(conv: String) {
        deliver(newOutgoing(conv, "board", t("Lavagna condivisa")) ?: return)
    }

    /** a live update to everybody in the chat (or only to one of them) */
    suspend fun fastToConv(conv: String, payload: JSONObject, only: String? = null) {
        val (targets, gid) = recipients(conv)
        gid?.let { payload.put("g", it) }
        targets.filter { only == null || it == only }.forEach { sendFast(it, payload) }
    }

    /** a message to scratch off with a finger */
    suspend fun sendScratch(conv: String, text: String) {
        val tx = text.trim()
        if (tx.isEmpty()) return
        deliver(newOutgoing(conv, "scratch", tx) ?: return)
    }

    suspend fun sendNudge(conv: String) {
        deliver(newOutgoing(conv, "nudge") ?: return)
    }

    suspend fun shareEventInChat(conv: String, e: AgendaEvent) {
        deliver(newOutgoing(conv, "event", e.title, eventJson(e)) ?: return)
    }

    suspend fun sendLocationPin(conv: String, lat: Double, lon: Double) {
        deliver(newOutgoing(conv, "loc", extra = JSONObject().put("lat", lat).put("lon", lon)) ?: return)
    }

    suspend fun sendPoll(conv: String, question: String, options: List<String>, multi: Boolean) {
        val ex = JSONObject().put("q", question.trim()).put("o", options.map { it.trim() }.filter { it.isNotEmpty() }.toJsonArray()).put("m", multi)
        deliver(newOutgoing(conv, "poll", question.trim(), ex) ?: return)
    }

    suspend fun addCallLog(conv: String, text: String, outgoing: Boolean) {
        val k = me() ?: return
        db.messages().insert(Message(newId(), conv, if (outgoing) k.pub else conv, outgoing, now(), "call", text = text, status = MsgStatus.READ))
    }

    suspend fun sendPhoto(conv: String, uri: Uri, caption: String, reply: ReplyRef? = null, once: Boolean = false, music: JSONObject? = null, forwarded: Boolean = false) {
        val compressed = withContext(Dispatchers.IO) { Media.compress(ctx, uri) } ?: return
        val extra = JSONObject().put("w", compressed.width).put("h", compressed.height)
        if (forwarded) extra.put("fw", true)
        if (once) extra.put("once", true)
        if (music != null) extra.put("mu", music)
        sendMedia(conv, "photo", compressed.bytes, extra, caption, reply)
    }

    suspend fun sendVoice(conv: String, file: File, durationMs: Long, reply: ReplyRef? = null) {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        sendMedia(conv, "voice", bytes, JSONObject().put("dur", durationMs), "", reply)
        runCatching { file.delete() }
    }

    private suspend fun sendMedia(conv: String, kind: String, bytes: ByteArray, extra: JSONObject, caption: String, reply: ReplyRef?) {
        val n = ceil(bytes.size / CHUNK_BYTES.toDouble()).toInt().coerceAtLeast(1)
        extra.put("n", n)
        val m = newOutgoing(conv, kind, caption.trim(), extra, reply, progress = 0f) ?: return
        val file = File(Media.dir(ctx), "${m.id}.${extFor(kind)}")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        db.messages().progress(m.id, 0f, file.absolutePath)
        deliver(m)
        sendChunks(m, bytes)
    }

    private suspend fun sendChunks(m: Message, bytes: ByteArray) {
        val (targets, gid) = recipients(m.convId)
        val n = ceil(bytes.size / CHUNK_BYTES.toDouble()).toInt().coerceAtLeast(1)
        val enc = Base64.getEncoder()
        targets.forEach { to -> if (!Graph.rtc.isConnected(to)) Graph.rtc.ensureConnected(to) }
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
            if ((m.kind == "photo" || m.kind == "voice") && m.mediaPath != null) {
                val f = File(m.mediaPath)
                if (f.exists()) sendChunks(m, f.readBytes())
            }
        }
    }

    private suspend fun toConv(conv: String, payload: JSONObject, ttl: Long = TTL_MESSAGE) {
        val (targets, gid) = recipients(conv)
        gid?.let { payload.put("g", it) }
        targets.forEach { sendStored(it, payload, ttl) }
    }

    private suspend fun onPin(from: String, p: JSONObject, friend: Friend?) {
        val conv = convFor(from, p, friend) ?: return
        val id = p.optString("id")
        if (id.isNotBlank()) {
            val m = db.messages().get(id) ?: return
            if (m.convId != conv) return
        }
        Graph.pins.set(conv, id.ifBlank { null })
    }

    /** keep a message at the top of the conversation, for everyone in it (null = unpin) */
    suspend fun pin(conv: String, msgId: String?) {
        Graph.pins.set(conv, msgId)
        toConv(conv, JSONObject().put("t", "pin").put("id", msgId ?: ""))
    }

    /** my 24-hour status, sent to every friend (null clears it) */
    suspend fun setStatus(emoji: String?, text: String?) {
        val st = if (emoji.isNullOrBlank() && text.isNullOrBlank()) null
        else com.monolith.app.data.Status(emoji?.ifBlank { "💬" } ?: "💬", text.orEmpty().trim().take(80), now() + 86_400_000L)
        Graph.statuses.setMine(st)
        val payload = JSONObject().put("t", "status").put("e", st?.emoji ?: "").put("x", st?.text ?: "").put("u", st?.until ?: 0L)
        db.friends().friendsNow().filter { it.status == FriendStatus.FRIEND }.forEach { sendStored(it.pubkey, payload, 86_400L) }
    }

    suspend fun react(m: Message, emoji: String) {
        val k = me() ?: return
        if (emoji.isBlank()) db.social().unreact(m.id, k.pub) else db.social().react(Reaction(m.id, k.pub, m.convId, emoji, now()))
        toConv(m.convId, JSONObject().put("t", "react").put("id", m.id).put("e", emoji))
    }

    suspend fun editMessage(m: Message, text: String) {
        val tx = text.trim()
        if (!m.outgoing || tx.isEmpty()) return
        db.messages().edit(m.id, tx)
        toConv(m.convId, JSONObject().put("t", "edit").put("id", m.id).put("x", tx))
    }

    suspend fun deleteForEveryone(m: Message) {
        if (!m.outgoing) return
        m.mediaPath?.let { runCatching { File(it).delete() } }
        db.messages().markDeleted(m.id)
        toConv(m.convId, JSONObject().put("t", "del").put("id", m.id))
    }

    suspend fun deleteForMe(m: Message) {
        m.mediaPath?.let { runCatching { File(it).delete() } }
        db.messages().delete(m.id)
    }

    suspend fun vote(m: Message, options: List<Int>) {
        val k = me() ?: return
        db.social().vote(Vote(m.id, k.pub, m.convId, options.joinToString(","), now()))
        toConv(m.convId, JSONObject().put("t", "vote").put("id", m.id).put("o", JSONArray().also { a -> options.forEach { a.put(it) } }))
    }

    suspend fun setDisappearing(conv: String, sec: Long) {
        val k = me() ?: return
        val cur = db.social().settingNow(conv) ?: ConvSetting(conv)
        db.social().saveSetting(cur.copy(ttlSec = sec))
        db.messages().insert(Message(newId(), conv, k.pub, true, now(), "system", text = ttlSystemText(t("Tu"), sec), status = MsgStatus.READ))
        toConv(conv, JSONObject().put("t", "ttl").put("s", sec))
    }

    private suspend fun sweepExpired() {
        db.messages().expired(now()).forEach { m ->
            m.mediaPath?.let { runCatching { File(it).delete() } }
            db.messages().delete(m.id)
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

    suspend fun createGroup(name: String, emoji: String, members: List<String>, idPrefix: String = ""): String? {
        val k = me() ?: return null
        val gid = idPrefix + newId()
        val all = (members + k.pub).distinct()
        val g = Group(gid, name.trim().ifBlank { t("Gruppo") }, emoji, all.joinToString(","), k.pub, now())
        db.groups().upsert(g)
        db.messages().insert(Message(newId(), "g:$gid", k.pub, true, now(), "system", text = t("Hai creato il gruppo"), status = MsgStatus.READ))
        broadcastGroup(g)
        return gid
    }

    suspend fun broadcastGroup(g: Group) {
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

    suspend fun leaveGroup(gid: String) {
        val k = me() ?: return
        val g = db.groups().get(gid) ?: return
        g.memberList().filter { it != k.pub }.forEach { sendStored(it, JSONObject().put("t", "gleave").put("g", gid)) }
        db.groups().delete(gid)
        db.messages().clear("g:$gid")
    }
}

fun replyOf(m: Message): ReplyRef? {
    val raw = m.extra ?: return null
    val o = runCatching { JSONObject(raw).optJSONObject("re") }.getOrNull() ?: return null
    return ReplyRef(o.optString("id"), o.optString("s"), o.optString("x"))
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
