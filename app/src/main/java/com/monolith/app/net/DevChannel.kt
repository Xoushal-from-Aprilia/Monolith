package com.monolith.app.net

import android.content.Context
import android.util.Log
import com.monolith.app.BuildConfig
import com.monolith.app.Graph
import com.monolith.app.crypto.Hex
import com.monolith.app.nostr.Envelope
import com.monolith.app.nostr.NostrEvent
import com.monolith.app.core.nowSec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DevNews(val id: String, val title: String, val body: String, val link: String)

/** a verified account: the title and the look of the symbol are chosen by the developer */
data class Verified(val title: String, val shape: String, val color: String)

data class DevState(
    /** the developer key is known: reports and feedback can be sent */
    val active: Boolean = false,
    val news: DevNews? = null,
    val latest: String? = null,
    val latestUrl: String = "",
    val latestNotes: String = "",
    val min: String? = null,
    val blocked: Set<String> = emptySet(),
    val verified: Map<String, Verified> = emptyMap(),
)

/**
 * The developer's channel. The developer dashboard publishes one signed document (news, newest version,
 * minimum version, blocked accounts); every app reads it and follows it. The public half of the developer
 * key comes from the app's own repository, the same place the app is built from. Reports and feedback go
 * the other way, encrypted to that key like any message.
 */
enum class Sent { SENT, QUEUED, FAILED }

class DevChannel(private val ctx: Context, private val scope: CoroutineScope) {
    companion object {
        const val KEY_URL = "https://raw.githubusercontent.com/Xoushal-from-Aprilia/Monolith/main/dev/developer.pub"
        const val RELEASES = "https://github.com/Xoushal-from-Aprilia/Monolith/releases/latest"
        const val D_TAG = "monolith-dev"
        private const val KEY_TTL = 12 * 3_600_000L
        /** while there is no key, look again soon: the developer may add it at any moment */
        private const val NO_KEY_TTL = 15 * 60_000L
        private const val DOC_EVERY = 3 * 3_600_000L

        /** -1, 0, 1 comparing dotted versions like 2.4.0 */
        fun compare(a: String, b: String): Int {
            val x = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }

    private val tag = "DevChannel"
    private val sp = ctx.getSharedPreferences("dev_channel", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DevState> = _state.asStateFlow()
    private val _seenNews = MutableStateFlow(sp.getString("seen_news", "") ?: "")
    val seenNews: StateFlow<String> = _seenNews.asStateFlow()
    private val _seenUpdate = MutableStateFlow(sp.getString("seen_update", "") ?: "")
    val seenUpdate: StateFlow<String> = _seenUpdate.asStateFlow()
    private var started = false
    private var lastDoc = 0L

    val devKey: String get() = sp.getString("key", "") ?: ""
    fun isBlocked(pk: String) = pk in _state.value.blocked
    fun verifiedOf(pk: String): Verified? = _state.value.verified[pk]
    val blockedMe: Boolean get() = Graph.identity.keys?.pub?.let { isBlocked(it) } == true
    // the version numbers are Monolith's: Monolith Kids has its own and is updated from the site
    val updateRequired: Boolean get() = !BuildConfig.KIDS && _state.value.min?.let { compare(BuildConfig.VERSION_NAME, it) < 0 } == true
    val updateAvailable: Boolean get() = !BuildConfig.KIDS && _state.value.latest?.let { compare(BuildConfig.VERSION_NAME, it) < 0 } == true
    val updateUrl: String get() = _state.value.latestUrl.ifBlank { RELEASES }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            Graph.relays.events.collect { (_, ev) ->
                if (ev.kind == Envelope.KIND_PROFILE && ev.firstTag("d") == D_TAG) runCatching { onDoc(ev) }
            }
        }
        scope.launch {
            while (true) {
                refresh()
                delay(DOC_EVERY)
            }
        }
    }

    /** when the app comes back to the screen, at most once an hour */
    fun onForeground() {
        if (started && System.currentTimeMillis() - lastDoc > 3_600_000L) scope.launch { refresh() }
    }

    /** for the screenshots */
    internal fun preview(s: DevState) { _state.value = s }

    fun markNewsSeen(id: String) { sp.edit().putString("seen_news", id).apply(); _seenNews.value = id }
    fun markUpdateSeen(v: String) { sp.edit().putString("seen_update", v).apply(); _seenUpdate.value = v }

    /** looks for the developer key right now, without waiting for the cache; true when the channel is on */
    suspend fun recheck(): Boolean {
        refresh(force = true)
        return _state.value.active
    }

    private suspend fun refresh(force: Boolean = false) {
        val key = loadKey(force)
        if (key.isBlank()) {
            if (_state.value.active) save(DevState())
            return
        }
        if (!_state.value.active) _state.value = _state.value.copy(active = true)
        sendPending()
        lastDoc = System.currentTimeMillis()
        val filter = JSONObject().put("kinds", JSONArray().put(Envelope.KIND_PROFILE))
            .put("authors", JSONArray().put(key)).put("#d", JSONArray().put(D_TAG)).put("limit", 5)
        // stay subscribed too: a block or a verification published now arrives at once
        if (liveFor != key) {
            liveFor = key
            Graph.relays.subscribe("devdoc") { listOf(JSONObject(filter.toString()).put("since", nowSec() - 60).apply { remove("limit") }) }
        }
        val ev = runCatching { Graph.relays.query(listOf(filter)) }.getOrDefault(emptyList())
            .filter { it.pubkey == key && it.kind == Envelope.KIND_PROFILE && it.firstTag("d") == D_TAG }
            .maxByOrNull { it.createdAt } ?: return
        onDoc(ev)
    }

    private var liveFor = ""

    /** a document of the developer, from a query or from the live subscription */
    private suspend fun onDoc(ev: NostrEvent) {
        val key = devKey
        if (key.isBlank() || ev.pubkey != key || ev.kind != Envelope.KIND_PROFILE || ev.firstTag("d") != D_TAG) return
        if (ev.createdAt <= sp.getLong("doc_at", 0)) return
        if (!withContext(Dispatchers.Default) { ev.verify() }) return
        val doc = runCatching { JSONObject(ev.content) }.getOrNull() ?: return
        sp.edit().putLong("doc_at", ev.createdAt).apply()
        save(parse(doc).copy(active = true))
    }

    /** the developer key from the repository, cached for 12 hours (and kept when offline) */
    private suspend fun loadKey(force: Boolean = false): String {
        val cached = sp.getString("key", "") ?: ""
        val ttl = if (cached.isBlank()) NO_KEY_TTL else KEY_TTL
        if (!force && System.currentTimeMillis() - sp.getLong("key_at", 0) < ttl) return cached
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(KEY_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Monolith/${BuildConfig.VERSION_NAME}")
                try {
                    when (c.responseCode) {
                        200 -> c.inputStream.bufferedReader().readText().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: ""
                        404 -> ""
                        else -> null
                    }
                } finally { c.disconnect() }
            }.getOrNull()
        } ?: return cached
        val key = fetched.lowercase().takeIf { it.length == 64 && Hex.isHex64(it) } ?: ""
        sp.edit().putString("key", key).putLong("key_at", System.currentTimeMillis()).apply()
        return key
    }

    private fun parse(o: JSONObject): DevState {
        val n = o.optJSONObject("news")
        val l = o.optJSONObject("latest")
        val b = o.optJSONArray("blocked")
        val v = o.optJSONArray("verified")
        return DevState(
            news = n?.let { DevNews(it.optString("id"), it.optString("title"), it.optString("body"), it.optString("link")) }?.takeIf { it.id.isNotBlank() && (it.title.isNotBlank() || it.body.isNotBlank()) },
            latest = l?.optString("version")?.ifBlank { null },
            latestUrl = l?.optString("url").orEmpty(),
            latestNotes = l?.optString("notes").orEmpty(),
            min = o.optString("min").ifBlank { null },
            blocked = (0 until (b?.length() ?: 0)).mapNotNull { b?.optString(it)?.lowercase()?.takeIf { s -> s.length == 64 } }.toSet(),
            verified = (0 until (v?.length() ?: 0)).mapNotNull { i ->
                val e = v?.optJSONObject(i) ?: return@mapNotNull null
                val pk = e.optString("pk").lowercase().takeIf { it.length == 64 } ?: return@mapNotNull null
                pk to Verified(e.optString("t").take(40), e.optString("s").ifBlank { "seal" }, e.optString("c").ifBlank { "blue" })
            }.toMap(),
        )
    }

    private fun save(s: DevState) {
        _state.value = s
        val o = JSONObject().put("active", s.active)
            .put("min", s.min ?: "").put("latest", s.latest ?: "").put("url", s.latestUrl).put("notes", s.latestNotes)
            .put("blocked", JSONArray(s.blocked.toList()))
            .put("verified", JSONArray(s.verified.map { (pk, v) -> JSONObject().put("pk", pk).put("t", v.title).put("s", v.shape).put("c", v.color) }))
        s.news?.let { o.put("news", JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("link", it.link)) }
        sp.edit().putString("state", o.toString()).apply()
    }

    private fun load(): DevState = runCatching {
        val o = JSONObject(sp.getString("state", null) ?: return DevState())
        parse(JSONObject().put("news", o.optJSONObject("news")).put("latest", JSONObject().put("version", o.optString("latest")).put("url", o.optString("url")).put("notes", o.optString("notes")))
            .put("min", o.optString("min")).put("blocked", o.optJSONArray("blocked")).put("verified", o.optJSONArray("verified"))).copy(active = o.optBoolean("active"))
    }.getOrDefault(DevState())

    // ================================================================== to the developer

    private suspend fun sendToDev(payload: JSONObject): Boolean {
        val key = devKey.ifBlank { return false }
        payload.put("t", "dev").put("v", BuildConfig.VERSION_NAME).put("lang", com.monolith.app.core.I18n.lang)
        return runCatching { Graph.messenger.sendStored(key, payload, ttl = 30L * 86400) != null }.onFailure { Log.w(tag, "send failed", it) }.getOrDefault(false)
    }

    /** reports an account (and optionally a Shard) to the developer */
    suspend fun report(about: String, name: String, reason: String, text: String): Boolean =
        sendToDev(JSONObject().put("k", "report").put("about", about).put("name", name).put("why", reason).put("text", text))

    suspend fun feedback(text: String): Boolean = sendToDev(JSONObject().put("k", "feedback").put("text", text))

    /** when I asked to be verified (ms), 0 if never */
    val verifyAskedAt: Long get() = sp.getLong("verify_at", 0)

    /** a request written while verifications were not open yet: it leaves as soon as they open */
    val verifyPending: Boolean get() = !sp.getString("verify_pending", null).isNullOrBlank()

    private suspend fun sendVerify(text: String): Boolean {
        val p = Graph.identity.profile.value
        val ok = sendToDev(JSONObject().put("k", "verify").put("u", p?.username ?: "").put("n", p?.displayName ?: "").put("text", text))
        if (ok) sp.edit().putLong("verify_at", System.currentTimeMillis()).remove("verify_pending").apply()
        return ok
    }

    private suspend fun sendPending() {
        val text = sp.getString("verify_pending", null)
        if (!text.isNullOrBlank()) sendVerify(text)
    }

    /** asks the developer to verify this account; [text] says who I am */
    suspend fun requestVerification(text: String): Sent {
        if (devKey.isBlank()) recheck()
        if (devKey.isBlank()) {
            sp.edit().putString("verify_pending", text).apply()
            return Sent.QUEUED
        }
        return if (sendVerify(text)) Sent.SENT else Sent.FAILED
    }
}
