package com.monolith.app.kids

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.monolith.app.BuildConfig
import com.monolith.app.Graph
import com.monolith.app.core.Profile
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.net.UserResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar

enum class KidRole { NONE, PARENT, KID }

/** what the parent lets the child do */
data class KidRules(
    val chat: Boolean = true,
    val photos: Boolean = true,
    val voice: Boolean = true,
    val games: Boolean = true,
    val calls: Boolean = true,
    val video: Boolean = true,
    val agendaEdit: Boolean = true,
    val location: Boolean = true,
    /** rest time, minutes of the day; -1 = none */
    val quietFrom: Int = -1,
    val quietTo: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject().put("chat", chat).put("photos", photos).put("voice", voice).put("games", games)
        .put("calls", calls).put("video", video).put("agenda", agendaEdit).put("loc", location).put("qf", quietFrom).put("qt", quietTo)

    /** true now, during the rest time */
    fun quietNow(): Boolean {
        if (quietFrom < 0 || quietTo < 0 || quietFrom == quietTo) return false
        val c = Calendar.getInstance()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (quietFrom < quietTo) m in quietFrom until quietTo else m >= quietFrom || m < quietTo
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) KidRules() else KidRules(
            o.optBoolean("chat", true), o.optBoolean("photos", true), o.optBoolean("voice", true), o.optBoolean("games", true),
            o.optBoolean("calls", true), o.optBoolean("video", true), o.optBoolean("agenda", true), o.optBoolean("loc", true),
            o.optInt("qf", -1), o.optInt("qt", -1),
        )
    }
}

/** a person in the child's list (approved or waiting) */
data class KContact(val pk: String, val name: String, val emoji: String, val username: String = "", val how: String = "") {
    fun toJson(): JSONObject = JSONObject().put("pk", pk).put("n", name).put("e", emoji).put("u", username).put("how", how)
    companion object {
        fun fromJson(o: JSONObject) = KContact(o.optString("pk"), o.optString("n"), o.optString("e").ifBlank { "🙂" }, o.optString("u"), o.optString("how"))
    }
}

/** a message of the child, as the parent sees it */
data class MirrorMsg(
    val id: String, val conv: String, val convName: String, val from: String, val out: Boolean, val ts: Long,
    val kind: String, val text: String, val thumb: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("c", conv).put("cn", convName).put("f", from).put("o", out).put("ts", ts)
        .put("k", kind).put("x", text).apply { if (thumb.isNotEmpty()) put("th", thumb) }
    companion object {
        fun fromJson(o: JSONObject) = MirrorMsg(o.optString("id"), o.optString("c"), o.optString("cn"), o.optString("f"), o.optBoolean("o"), o.optLong("ts"),
            o.optString("k"), o.optString("x"), o.optString("th"))
    }
}

/** an event of the child's agenda, as the parent sees it */
data class KidEvent(val id: String, val title: String, val emoji: String, val start: Long, val end: Long?, val allDay: Boolean, val notes: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("t", title).put("e", emoji).put("s", start).put("en", end ?: 0).put("ad", allDay).put("n", notes)
    companion object {
        fun fromJson(o: JSONObject) = KidEvent(o.optString("id"), o.optString("t"), o.optString("e").ifBlank { "📌" }, o.optLong("s"),
            o.optLong("en").takeIf { it > 0 }, o.optBoolean("ad"), o.optString("n"))
        fun of(e: AgendaEvent) = KidEvent(e.id, e.title, e.emoji, e.start, e.end, e.allDay, e.notes)
    }
}

/** someone who asked to become this child's parent: the child's phone asks for a yes first */
data class PairAsk(val pk: String, val name: String, val emoji: String, val username: String)

/** a child followed by this (parent) phone */
data class Child(
    val pk: String,
    val name: String,
    val emoji: String,
    val pairedAt: Long,
    val rules: KidRules = KidRules(),
    val contacts: List<KContact> = emptyList(),
    val pending: List<KContact> = emptyList(),
    val events: List<KidEvent> = emptyList(),
    val battery: Int = -1,
    val charging: Boolean = false,
    val seenAt: Long = 0,
    val version: String = "",
)

/**
 * Monolith Kids. The same app is installed twice: on the parent's phone (PARENT) and on the child's (KID).
 * They pair in person with a QR code. From then on the child's phone sends the parent a copy of every message,
 * its agenda and its position, and follows the parent's rules: approved contacts only, features on or off,
 * rest time. Everything travels encrypted between the two phones like any message. The child always sees
 * who follows the account. When the parent deletes the account, the child's is deleted too.
 */
object Kids {
    private const val tag = "Kids"
    /** screenshots of the standard build can draw the Kids screens */
    @Volatile internal var forced = false
    val edition: Boolean get() = BuildConfig.KIDS || forced

    private lateinit var ctx: Context
    private val sp by lazy { ctx.getSharedPreferences("kids", Context.MODE_PRIVATE) }

    private val _role = MutableStateFlow(KidRole.NONE)
    val role: StateFlow<KidRole> = _role.asStateFlow()

    // ------------------------------------------------ child's phone
    private val _parent = MutableStateFlow<KContact?>(null)
    val parent: StateFlow<KContact?> = _parent.asStateFlow()
    private val _rules = MutableStateFlow(KidRules())
    val rules: StateFlow<KidRules> = _rules.asStateFlow()
    private val _pendingPair = MutableStateFlow<PairAsk?>(null)
    /** a parent's request to connect, waiting for a yes on the child's phone */
    val pendingPair: StateFlow<PairAsk?> = _pendingPair.asStateFlow()
    private val _asks = MutableStateFlow<List<KContact>>(emptyList())
    /** people the child asked to add, waiting for the parent */
    val asks: StateFlow<List<KContact>> = _asks.asStateFlow()
    private var allowed = setOf<String>()

    // ------------------------------------------------ parent's phone
    private val _children = MutableStateFlow<List<Child>>(emptyList())
    val children: StateFlow<List<Child>> = _children.asStateFlow()
    private val mirrors = HashMap<String, MutableStateFlow<List<MirrorMsg>>>()

    private var started = false

    fun init(c: Context) {
        ctx = c.applicationContext
        if (!edition) return
        _role.value = runCatching { KidRole.valueOf(sp.getString("role", "NONE") ?: "NONE") }.getOrDefault(KidRole.NONE)
        load()
    }

    val isKid get() = edition && _role.value == KidRole.KID
    val isParent get() = edition && _role.value == KidRole.PARENT

    fun setRole(r: KidRole) {
        _role.value = r
        sp.edit().putString("role", r.name).apply()
    }

    /** a code for the child's QR: only who sees the child's screen can pair */
    val pairCode: String
        get() = sp.getString("pair_code", null) ?: (100000 + java.security.SecureRandom().nextInt(900000)).toString().also { sp.edit().putString("pair_code", it).apply() }

    fun pairQr(): String {
        val me = Graph.identity.keys?.pub ?: ""
        val p = Graph.identity.profile.value
        return "monolithkids:$me:$pairCode:" + URLEncoder.encode(p?.displayName ?: "", "UTF-8") + ":" + URLEncoder.encode(p?.emoji ?: "🙂", "UTF-8")
    }

    /** a random name that never goes in the public directory */
    fun hiddenUsername(prefix: String) = prefix + "_" + newId().take(8).lowercase()

    // ================================================================== engine

    fun start() {
        if (!edition || started) return
        started = true
        if (isKid) {
            Graph.scope.launch { mirrorLoop() }
            Graph.scope.launch { stateLoop() }
            Graph.scope.launch { Graph.db.agenda().all().debounce(1500).collect { sendEvents(it) } }
        }
    }

    private suspend fun sendEvents(list: List<AgendaEvent>? = null) {
        val l = list ?: Graph.db.agenda().allNow()
        sendToParent(JSONObject().put("k", "events").put("list", JSONArray(l.map { KidEvent.of(it).toJson() })))
    }

    private suspend fun sendToParent(p: JSONObject) {
        val to = _parent.value?.pk ?: return
        runCatching { Graph.messenger.sendStored(to, p.put("t", "kid")) }.onFailure { Log.w(tag, "send", it) }
    }

    private suspend fun sendToChild(pk: String, p: JSONObject) {
        runCatching { Graph.messenger.sendStored(pk, p.put("t", "kid")) }.onFailure { Log.w(tag, "send", it) }
    }

    private val mirrored by lazy { LinkedHashSet((sp.getString("mirrored", "") ?: "").split(",").filter { it.isNotBlank() }) }

    /** the child's copy of new messages, a few at a time */
    private suspend fun mirrorLoop() {
        while (true) {
            runCatching {
                val parentPk = _parent.value?.pk
                if (parentPk != null) {
                    val last = sp.getLong("mirror_ts", 0)
                    // messages can arrive late with an older time: look back a little and skip the ones already sent
                    val list = Graph.db.messages().since((last - 20 * 60_000L).coerceAtLeast(0))
                        .filter { it.convId != parentPk && !it.convId.startsWith("story.") && !it.convId.startsWith("g:story.") && it.id !in mirrored }
                        .sortedBy { it.ts }.take(30)
                    if (list.isNotEmpty()) {
                        val friends = Graph.db.friends().allNow().associateBy { it.pubkey }
                        val groups = Graph.db.groups().allNow().associateBy { "g:" + it.id }
                        val arr = JSONArray(list.map { m ->
                            MirrorMsg(
                                m.id, m.convId, friends[m.convId]?.displayName ?: groups[m.convId]?.name ?: "", m.sender, m.outgoing, m.ts, m.kind,
                                m.text, if (m.kind == "photo" || m.kind == "sticker") thumb(m.mediaPath) else "",
                            ).toJson()
                        })
                        sendToParent(JSONObject().put("k", "msgs").put("list", arr))
                        mirrored.addAll(list.map { it.id })
                        while (mirrored.size > 800) mirrored.remove(mirrored.first())
                        sp.edit().putLong("mirror_ts", maxOf(last, list.maxOf { it.ts })).putString("mirrored", mirrored.joinToString(",")).apply()
                    }
                }
            }.onFailure { Log.w(tag, "mirror", it) }
            delay(5000)
        }
    }

    private fun thumb(path: String?): String = runCatching {
        if (path.isNullOrBlank()) return ""
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / s > 320 || o.outHeight / s > 320) s *= 2
        val bm = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return ""
        val k = 200f / maxOf(bm.width, bm.height)
        val small = if (k < 1f) Bitmap.createScaledBitmap(bm, (bm.width * k).toInt(), (bm.height * k).toInt(), true) else bm
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 60, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")

    /** battery, contacts and version for the parent; the position goes with the normal live location */
    private suspend fun stateLoop() {
        while (true) {
            runCatching {
                val p = _parent.value
                if (p != null) {
                    sendState()
                    applyLocation()
                }
            }.onFailure { Log.w(tag, "state", it) }
            delay(10 * 60_000L)
        }
    }

    /** the position goes to the parent all day long, unless the parent switched it off */
    private suspend fun applyLocation() {
        val p = _parent.value ?: return
        val f = Graph.db.friends().get(p.pk) ?: return
        if (_rules.value.location) {
            if (f.iShareUntil < now() + 2 * 3_600_000L && Graph.location.hasPermission()) Graph.location.startSharing(listOf(p.pk), 24 * 60, remember = false)
        } else if (f.iShareUntil > now()) Graph.location.stopSharing(p.pk)
    }

    private suspend fun sendState() {
        val bm = ctx.getSystemService(android.os.BatteryManager::class.java)
        val bat = runCatching { bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)
        val chg = runCatching { bm.isCharging }.getOrDefault(false)
        val parentPk = _parent.value?.pk
        val friends = Graph.db.friends().friendsNow().filter { it.pubkey != parentPk }
        sendToParent(
            JSONObject().put("k", "state").put("bat", bat).put("chg", chg).put("v", BuildConfig.VERSION_NAME)
                .put("friends", JSONArray(friends.map { KContact(it.pubkey, it.displayName, it.emoji, it.username).toJson() }))
                .put("pending", JSONArray(_asks.value.map { it.toJson() })),
        )
    }

    // ================================================================== incoming

    /**
     * Called first for every payload. Returns true when Kids took care of it (or dropped it): on the child's
     * phone nobody but the parent and the approved contacts gets through.
     */
    suspend fun intercept(from: String, p: JSONObject): Boolean {
        if (!edition) return false
        val t = p.optString("t")
        return when (_role.value) {
            KidRole.KID -> {
                val parentPk = _parent.value?.pk
                when {
                    t == "kid" -> { if (from == parentPk || (parentPk == null && p.optString("k") == "pair")) onParent(from, p); true }
                    from == parentPk -> false
                    from in allowed || Graph.db.friends().get(from)?.status == FriendStatus.FRIEND -> {
                        // calls the parent switched off, or during the rest time, do not ring
                        val r = _rules.value
                        t == "call" && p.optString("type") == "ring" && (!r.calls || (p.optBoolean("v") && !r.video) || r.quietNow())
                    }
                    t == "freq" -> { onStrangerRequest(from, p); true }
                    else -> true
                }
            }
            KidRole.PARENT -> if (t == "kid" && _children.value.any { it.pk == from }) { onChild(from, p); true } else false
            else -> false
        }
    }

    private suspend fun onStrangerRequest(from: String, p: JSONObject) {
        val prof = Profile.fromJson(p)
        val c = KContact(from, prof.displayName, prof.emoji, prof.username, "incoming")
        sendToParent(JSONObject().put("k", "ask").put("c", c.toJson()))
    }

    // ------------------------------------------------ child's phone: commands from the parent

    private suspend fun onParent(from: String, p: JSONObject) {
        when (p.optString("k")) {
            "pair" -> {
                if (_parent.value != null || p.optString("code") != pairCode) return
                // the code is right: the child's phone still asks whether this is really the parent
                _pendingPair.value = PairAsk(from, p.optString("n").take(40), p.optString("e").ifBlank { "🙂" }, p.optString("u"))
            }
            "rules" -> { _rules.value = KidRules.fromJson(p.optJSONObject("r")); save(); applyLocation() }
            "allow" -> {
                val c = KContact.fromJson(p.optJSONObject("c") ?: return)
                allowed = allowed + c.pk
                _asks.value = _asks.value.filter { it.pk != c.pk }
                save()
                val existing = Graph.db.friends().get(c.pk)
                if (existing?.status != FriendStatus.FRIEND) {
                    Graph.messenger.sendFriendRequest(UserResult(c.pk, Profile(c.username.ifBlank { "amico" }, c.name, c.emoji), now()))
                }
                sendState()
            }
            "deny" -> {
                val pk = p.optString("pk")
                _asks.value = _asks.value.filter { it.pk != pk }
                save(); sendState()
            }
            "remove" -> {
                val pk = p.optString("pk")
                allowed = allowed - pk
                save()
                Graph.messenger.removeFriend(pk)
                sendState()
            }
            "delmsg" -> Graph.db.messages().delete(p.optString("id"))
            "event" -> {
                val e = KidEvent.fromJson(p.optJSONObject("ev") ?: return)
                if (p.optString("op") == "del") {
                    Graph.db.agenda().get(e.id)?.let { Graph.messenger.deleteEvent(it) }
                } else {
                    val old = Graph.db.agenda().get(e.id)
                    val ev = (old ?: AgendaEvent(id = e.id, title = e.title, start = e.start, createdAt = now(), updatedAt = now()))
                        .copy(title = e.title, emoji = e.emoji, start = e.start, end = e.end, allDay = e.allDay, notes = e.notes, updatedAt = now())
                    Graph.messenger.saveEvent(ev, notifyInvitees = false)
                }
            }
            "sync" -> {
                mirrored.clear()
                sp.edit().putLong("mirror_ts", 0).putString("mirrored", "").apply()
                sendState()
                sendEvents()
            }
            "wipe" -> Graph.wipeEverything(ctx)
        }
    }

    /** the child (or the parent holding the child's phone) answers the request to connect */
    suspend fun answerPair(yes: Boolean) {
        val a = _pendingPair.value ?: return
        _pendingPair.value = null
        if (!yes) {
            runCatching { Graph.messenger.sendStored(a.pk, JSONObject().put("t", "kid").put("k", "pairno")) }
            return
        }
        if (_parent.value != null) return
        val parent = KContact(a.pk, a.name, a.emoji, a.username)
        _parent.value = parent
        // the parent's commands must arrive even with the app closed
        Graph.prefs.alwaysOnline = true
        Graph.db.friends().upsert(Friend(a.pk, parent.username, parent.name, parent.emoji, "", FriendStatus.FRIEND, now(), verified = true))
        save()
        sendToParent(JSONObject().put("k", "paired").put("n", Graph.identity.profile.value?.displayName ?: "").put("e", Graph.identity.profile.value?.emoji ?: "🙂"))
        Graph.messenger.sendStored(a.pk, Graph.messenger.profilePayload("facc"))
        sendState()
        sendEvents()
        applyLocation()
    }

    // ------------------------------------------------ pairing with the code, when the camera cannot read the QR

    private fun codeTag(code: String) =
        com.monolith.app.crypto.Hex.encode(java.security.MessageDigest.getInstance("SHA-256").digest("monolith-kidpair|${code.trim()}".toByteArray())).take(32)

    /** while it waits for a parent, the child's phone says where it can be found from its code */
    suspend fun publishBeacon() {
        val k = Graph.identity.keys ?: return
        val prof = Graph.identity.profile.value ?: return
        val ev = com.monolith.app.nostr.NostrEvent(
            "", com.monolith.app.core.nowSec(), com.monolith.app.nostr.Envelope.KIND_PROFILE,
            listOf(listOf("d", "monolith-kidpair"), listOf("c", codeTag(pairCode)), listOf("expiration", (com.monolith.app.core.nowSec() + 3600).toString())),
            JSONObject().put("n", prof.displayName).put("e", prof.emoji).toString(),
        )
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ev.sign(k.priv) }
        Graph.relays.publish(ev)
    }

    /** the parent typed the code shown on the child's phone */
    suspend fun findByCode(code: String): PairAsk? {
        val c = code.filter(Char::isDigit)
        if (c.length != 6) return null
        val tag = codeTag(c)
        val f = JSONObject().put("kinds", JSONArray().put(com.monolith.app.nostr.Envelope.KIND_PROFILE))
            .put("#c", JSONArray().put(tag)).put("since", com.monolith.app.core.nowSec() - 2 * 3600).put("limit", 10)
        val list = runCatching { Graph.relays.query(listOf(f)) }.getOrDefault(emptyList())
            .filter { it.firstTag("d") == "monolith-kidpair" && it.firstTag("c") == tag }
            .sortedByDescending { it.createdAt }
        for (ev in list) {
            if (!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ev.verify() }) continue
            val o = runCatching { JSONObject(ev.content) }.getOrDefault(JSONObject())
            return PairAsk(ev.pubkey, o.optString("n").take(40).ifBlank { "Bambino" }, o.optString("e").ifBlank { "🙂" }, "")
        }
        return null
    }

    // ------------------------------------------------ parent's phone: what the children send

    private suspend fun onChild(from: String, p: JSONObject) {
        when (p.optString("k")) {
            "paired" -> {
                updateChild(from) { it.copy(name = p.optString("n").ifBlank { it.name }, emoji = p.optString("e").ifBlank { it.emoji }, seenAt = now()) }
                // the rules sent with the request arrived before the yes: send them again
                _children.value.firstOrNull { it.pk == from }?.let { sendToChild(from, JSONObject().put("k", "rules").put("r", it.rules.toJson())) }
            }
            "pairno" -> {
                val name = _children.value.firstOrNull { it.pk == from }?.name ?: ""
                if (_children.value.firstOrNull { it.pk == from }?.seenAt == 0L) {
                    forgetChild(from)
                    Graph.notifier.kidPairNo(name)
                }
            }
            "state" -> {
                val fr = p.optJSONArray("friends") ?: JSONArray()
                val pe = p.optJSONArray("pending") ?: JSONArray()
                updateChild(from) { c ->
                    val pending = ((0 until pe.length()).map { KContact.fromJson(pe.getJSONObject(it)) } + c.pending.filter { it.how == "incoming" }).distinctBy { it.pk }
                    val contacts = (0 until fr.length()).map { KContact.fromJson(fr.getJSONObject(it)) }
                    c.copy(battery = p.optInt("bat", -1), charging = p.optBoolean("chg"), version = p.optString("v"), seenAt = now(),
                        contacts = contacts, pending = pending.filter { q -> contacts.none { it.pk == q.pk } })
                }
            }
            "ask" -> {
                val c = KContact.fromJson(p.optJSONObject("c") ?: return)
                updateChild(from) { ch -> if (ch.contacts.any { it.pk == c.pk }) ch else ch.copy(pending = (ch.pending.filter { it.pk != c.pk } + c)) }
                val name = _children.value.firstOrNull { it.pk == from }?.name ?: ""
                Graph.notifier.kidAsk(name, c.name, c.emoji)
            }
            "events" -> {
                val arr = p.optJSONArray("list") ?: JSONArray()
                updateChild(from) { it.copy(events = (0 until arr.length()).map { i -> KidEvent.fromJson(arr.getJSONObject(i)) }.sortedBy { e -> e.start }) }
            }
            "msgs" -> {
                val arr = p.optJSONArray("list") ?: JSONArray()
                val add = (0 until arr.length()).map { MirrorMsg.fromJson(arr.getJSONObject(it)) }
                val flow = mirrorOf(from)
                flow.value = (flow.value.filter { o -> add.none { it.id == o.id } } + add).sortedBy { it.ts }.takeLast(3000)
                saveMirror(from)
                updateChild(from) { it.copy(seenAt = now()) }
            }
        }
    }

    private fun updateChild(pk: String, f: (Child) -> Child) {
        _children.value = _children.value.map { if (it.pk == pk) f(it) else it }
        save()
    }

    // ================================================================== parent actions

    /** the parent scanned the child's QR code */
    suspend fun pairWith(qr: String?): Boolean {
        val s = qr?.trim() ?: return false
        if (!s.startsWith("monolithkids:")) return false
        val parts = s.removePrefix("monolithkids:").split(":")
        val pk = parts.getOrNull(0)?.lowercase() ?: return false
        val code = parts.getOrNull(1) ?: return false
        if (pk.length != 64 || !com.monolith.app.crypto.Hex.isHex64(pk)) return false
        val name = runCatching { URLDecoder.decode(parts.getOrNull(2) ?: "", "UTF-8") }.getOrDefault("")
        val emoji = runCatching { URLDecoder.decode(parts.getOrNull(3) ?: "", "UTF-8") }.getOrDefault("🙂")
        pair(pk, code, name, emoji)
        return true
    }

    /** sends the request to connect; the child's phone asks for a yes, then answers with "paired" */
    suspend fun pair(pk: String, code: String, name: String, emoji: String) {
        if (_children.value.none { it.pk == pk }) _children.value = _children.value + Child(pk, name.ifBlank { "Bambino" }, emoji.ifBlank { "🙂" }, now())
        save()
        Graph.db.friends().upsert(Friend(pk, "", name, emoji, "", FriendStatus.FRIEND, now(), verified = true))
        val me = Graph.identity.profile.value
        sendToChild(pk, JSONObject().put("k", "pair").put("code", code).put("n", me?.displayName ?: "").put("e", me?.emoji ?: "🙂").put("u", me?.username ?: ""))
        sendToChild(pk, JSONObject().put("k", "rules").put("r", (_children.value.first { it.pk == pk }.rules).toJson()))
    }

    /** a child that never answered: forgotten on this phone only */
    suspend fun forgetChild(child: String) {
        _children.value = _children.value.filter { it.pk != child }
        mirrors.remove(child)
        File(ctx.filesDir, "kids_mirror_$child.json").delete()
        Graph.db.friends().delete(child)
        save()
    }

    suspend fun setRules(child: String, r: KidRules) {
        updateChild(child) { it.copy(rules = r) }
        sendToChild(child, JSONObject().put("k", "rules").put("r", r.toJson()))
    }

    suspend fun allow(child: String, c: KContact) {
        updateChild(child) { ch -> ch.copy(pending = ch.pending.filter { it.pk != c.pk }, contacts = (ch.contacts.filter { it.pk != c.pk } + c)) }
        sendToChild(child, JSONObject().put("k", "allow").put("c", c.toJson()))
    }

    suspend fun deny(child: String, pk: String) {
        updateChild(child) { ch -> ch.copy(pending = ch.pending.filter { it.pk != pk }) }
        sendToChild(child, JSONObject().put("k", "deny").put("pk", pk))
    }

    suspend fun removeContact(child: String, pk: String) {
        updateChild(child) { ch -> ch.copy(contacts = ch.contacts.filter { it.pk != pk }) }
        sendToChild(child, JSONObject().put("k", "remove").put("pk", pk))
    }

    suspend fun deleteMessage(child: String, m: MirrorMsg) {
        val flow = mirrorOf(child)
        flow.value = flow.value.filter { it.id != m.id }
        saveMirror(child)
        sendToChild(child, JSONObject().put("k", "delmsg").put("id", m.id).put("c", m.conv))
    }

    suspend fun putEvent(child: String, e: KidEvent) {
        updateChild(child) { ch -> ch.copy(events = (ch.events.filter { it.id != e.id } + e).sortedBy { it.start }) }
        sendToChild(child, JSONObject().put("k", "event").put("op", "put").put("ev", e.toJson()))
    }

    suspend fun deleteEvent(child: String, e: KidEvent) {
        updateChild(child) { ch -> ch.copy(events = ch.events.filter { it.id != e.id }) }
        sendToChild(child, JSONObject().put("k", "event").put("op", "del").put("ev", e.toJson()))
    }

    suspend fun askSync(child: String) = sendToChild(child, JSONObject().put("k", "sync"))

    /** deletes the child's account on the child's phone, and forgets the child here */
    suspend fun wipeChild(child: String) {
        sendToChild(child, JSONObject().put("k", "wipe"))
        delay(1500)
        _children.value = _children.value.filter { it.pk != child }
        mirrors.remove(child)
        File(ctx.filesDir, "kids_mirror_$child.json").delete()
        Graph.db.friends().delete(child)
        save()
    }

    /** before the parent's own account goes away, every child's account goes with it */
    suspend fun wipeAllChildren() {
        if (!isParent) return
        _children.value.forEach { sendToChild(it.pk, JSONObject().put("k", "wipe")) }
        delay(2500)
    }

    // ================================================================== child actions

    /** the child wants to add someone: the parent decides */
    suspend fun askToAdd(c: KContact) {
        if (Graph.db.friends().get(c.pk)?.status == FriendStatus.FRIEND) return
        _asks.value = _asks.value.filter { it.pk != c.pk } + c
        save()
        sendToParent(JSONObject().put("k", "ask").put("c", c.toJson()))
    }

    // ================================================================== storage

    fun mirrorOf(child: String): MutableStateFlow<List<MirrorMsg>> = mirrors.getOrPut(child) {
        val list = runCatching {
            val a = JSONArray(File(ctx.filesDir, "kids_mirror_$child.json").readText())
            (0 until a.length()).map { MirrorMsg.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        MutableStateFlow(list)
    }

    private fun saveMirror(child: String) {
        val list = mirrors[child]?.value ?: return
        runCatching { File(ctx.filesDir, "kids_mirror_$child.json").writeText(JSONArray(list.map { it.toJson() }).toString()) }
    }

    private fun save() {
        val o = JSONObject()
            .put("rules", _rules.value.toJson())
            .put("allowed", JSONArray(allowed.toList()))
            .put("asks", JSONArray(_asks.value.map { it.toJson() }))
            .put("children", JSONArray(_children.value.map { c ->
                JSONObject().put("pk", c.pk).put("n", c.name).put("e", c.emoji).put("at", c.pairedAt).put("r", c.rules.toJson())
                    .put("contacts", JSONArray(c.contacts.map { it.toJson() })).put("pending", JSONArray(c.pending.map { it.toJson() }))
                    .put("events", JSONArray(c.events.map { it.toJson() }))
                    .put("bat", c.battery).put("chg", c.charging).put("seen", c.seenAt).put("v", c.version)
            }))
        _parent.value?.let { o.put("parent", it.toJson()) }
        sp.edit().putString("state", o.toString()).apply()
    }

    private fun load() {
        runCatching {
            val o = JSONObject(sp.getString("state", null) ?: return)
            _rules.value = KidRules.fromJson(o.optJSONObject("rules"))
            allowed = o.optJSONArray("allowed")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            _asks.value = o.optJSONArray("asks")?.let { a -> (0 until a.length()).map { KContact.fromJson(a.getJSONObject(it)) } } ?: emptyList()
            _parent.value = o.optJSONObject("parent")?.let { KContact.fromJson(it) }
            _children.value = o.optJSONArray("children")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
                    fun list(k: String) = c.optJSONArray(k)?.let { x -> (0 until x.length()).map { KContact.fromJson(x.getJSONObject(it)) } } ?: emptyList()
                    Child(
                        c.optString("pk"), c.optString("n"), c.optString("e"), c.optLong("at"), KidRules.fromJson(c.optJSONObject("r")),
                        list("contacts"), list("pending"),
                        c.optJSONArray("events")?.let { x -> (0 until x.length()).map { KidEvent.fromJson(x.getJSONObject(it)) } } ?: emptyList(),
                        c.optInt("bat", -1), c.optBoolean("chg"), c.optLong("seen"), c.optString("v"),
                    )
                }
            } ?: emptyList()
        }
    }

    /** everything about Kids on this phone goes (the account is being deleted) */
    fun clear() {
        if (!edition) return
        sp.edit().clear().apply()
        ctx.filesDir.listFiles()?.filter { it.name.startsWith("kids_mirror_") }?.forEach { it.delete() }
        _role.value = KidRole.NONE; _parent.value = null; _children.value = emptyList(); _asks.value = emptyList(); allowed = emptySet(); _pendingPair.value = null
    }

    /** for the screenshots */
    internal fun preview(role: KidRole, parent: KContact? = null, children: List<Child> = emptyList(), rules: KidRules = KidRules(), asks: List<KContact> = emptyList(), mirror: Map<String, List<MirrorMsg>> = emptyMap(), pending: PairAsk? = null) {
        forced = role != KidRole.NONE
        _pendingPair.value = pending
        _role.value = role; _parent.value = parent; _children.value = children; _rules.value = rules; _asks.value = asks
        mirror.forEach { (k, v) -> mirrors.getOrPut(k) { MutableStateFlow(emptyList()) }.value = v }
    }
}
