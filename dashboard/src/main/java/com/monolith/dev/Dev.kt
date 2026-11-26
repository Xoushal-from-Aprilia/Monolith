package com.monolith.dev

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.monolith.app.core.Keys
import com.monolith.app.crypto.Bech32
import com.monolith.app.crypto.Hex
import com.monolith.app.crypto.Secp256k1
import com.monolith.app.nostr.Envelope
import com.monolith.app.nostr.NostrEvent
import com.monolith.app.nostr.RelayPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** an account of the public directory */
data class User(
    val pk: String,
    val username: String,
    val name: String,
    val emoji: String,
    val bio: String,
    /** app version, empty before 2.4.0 */
    val version: String,
    /** sign-up time in seconds, 0 when unknown */
    val joined: Long,
    /** last time the app published the profile, i.e. last opening (seconds) */
    val lastSeen: Long,
) {
    val tag get() = pk.take(4)
}

/** the symbol of a verified account */
data class Ver(val title: String, val shape: String = "seal", val color: String = "blue")

/** the document every Monolith app reads and follows */
data class Doc(
    val newsId: String = "",
    val newsTitle: String = "",
    val newsBody: String = "",
    val newsLink: String = "",
    val latest: String = "",
    val latestUrl: String = "",
    val latestNotes: String = "",
    val min: String = "",
    val blocked: Set<String> = emptySet(),
    val verified: Map<String, Ver> = emptyMap(),
    /** when it was published (seconds), 0 = never */
    val at: Long = 0,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        if (newsId.isNotBlank() && (newsTitle.isNotBlank() || newsBody.isNotBlank())) {
            o.put("news", JSONObject().put("id", newsId).put("title", newsTitle).put("body", newsBody).put("link", newsLink))
        }
        if (latest.isNotBlank()) o.put("latest", JSONObject().put("version", latest).put("url", latestUrl).put("notes", latestNotes))
        if (min.isNotBlank()) o.put("min", min)
        o.put("blocked", JSONArray(blocked.sorted()))
        o.put("verified", JSONArray(verified.toSortedMap().map { (pk, v) -> JSONObject().put("pk", pk).put("t", v.title).put("s", v.shape).put("c", v.color) }))
        return o
    }

    companion object {
        fun parse(o: JSONObject, at: Long): Doc {
            val n = o.optJSONObject("news")
            val l = o.optJSONObject("latest")
            val b = o.optJSONArray("blocked") ?: JSONArray()
            val vs = o.optJSONArray("verified") ?: JSONArray()
            return Doc(
                newsId = n?.optString("id").orEmpty(), newsTitle = n?.optString("title").orEmpty(),
                newsBody = n?.optString("body").orEmpty(), newsLink = n?.optString("link").orEmpty(),
                latest = l?.optString("version").orEmpty(), latestUrl = l?.optString("url").orEmpty(), latestNotes = l?.optString("notes").orEmpty(),
                min = o.optString("min"),
                blocked = (0 until b.length()).map { b.optString(it).lowercase() }.filter { it.length == 64 }.toSet(),
                verified = (0 until vs.length()).mapNotNull { i ->
                    val e = vs.optJSONObject(i) ?: return@mapNotNull null
                    val pk = e.optString("pk").lowercase().takeIf { it.length == 64 } ?: return@mapNotNull null
                    pk to Ver(e.optString("t"), e.optString("s").ifBlank { "seal" }, e.optString("c").ifBlank { "blue" })
                }.toMap(),
                at = at,
            )
        }
    }
}

/** a report or a message that reached the developer */
data class Msg(
    val id: String,
    val from: String,
    val at: Long,
    val kind: String,
    val about: String,
    val aboutName: String,
    val why: String,
    val text: String,
    val version: String,
    val read: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("from", from).put("at", at).put("kind", kind).put("about", about)
        .put("aboutName", aboutName).put("why", why).put("text", text).put("version", version).put("read", read)

    companion object {
        fun fromJson(o: JSONObject) = Msg(
            o.optString("id"), o.optString("from"), o.optLong("at"), o.optString("kind"), o.optString("about"),
            o.optString("aboutName"), o.optString("why"), o.optString("text"), o.optString("version"), o.optBoolean("read"),
        )
    }
}

data class Release(val tag: String, val version: String, val build: Int, val url: String, val date: String)

/** everything the dashboard knows and does */
object Dev {
    const val REPO = "Xoushal-from-Aprilia/Monolith"
    const val D_TAG = "monolith-dev"
    private const val KIND_DOC = 30078
    val RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net", "wss://nostr.mom", "wss://offchain.pub")

    private const val tag = "Dev"
    lateinit var app: Application
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var relays: RelayPool
    private lateinit var vault: KeyVault

    private val _keys = MutableStateFlow<Keys?>(null)
    val keys: StateFlow<Keys?> = _keys.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _usersAt = MutableStateFlow(0L)
    val usersAt: StateFlow<Long> = _usersAt.asStateFlow()

    private val _doc = MutableStateFlow(Doc())
    /** the document as last published */
    val doc: StateFlow<Doc> = _doc.asStateFlow()

    private val _inbox = MutableStateFlow<List<Msg>>(emptyList())
    val inbox: StateFlow<List<Msg>> = _inbox.asStateFlow()

    private var started = false

    fun init(a: Application) {
        app = a
        vault = KeyVault(a)
        relays = RelayPool(scope) { _keys.value }
        _keys.value = vault.load()
        loadLocal()
        if (_keys.value != null) start()
    }

    // ================================================================== key

    fun createKey() {
        val sk = Secp256k1.generatePrivateKey()
        vault.save(sk)
        _keys.value = Keys(sk, Hex.encode(Secp256k1.publicKey(sk)))
        start()
    }

    /** accepts the 64-character secret code (or an nsec) */
    fun importKey(text: String): Boolean {
        val s = text.trim().replace(" ", "").replace("\n", "").lowercase()
        val sk = when {
            s.length == 64 && Hex.isHex64(s) -> Hex.decode(s)
            s.startsWith("nsec1") -> runCatching { Bech32.decode(s)[1] as ByteArray }.getOrNull()
            else -> null
        } ?: return false
        if (!Secp256k1.isValidPrivateKey(sk)) return false
        vault.save(sk)
        _keys.value = Keys(sk, Hex.encode(Secp256k1.publicKey(sk)))
        start()
        return true
    }

    fun secretCode(): String = _keys.value?.let { Hex.encode(it.priv) } ?: ""

    fun forgetKey() {
        vault.clear()
        _keys.value = null
        relays.stop()
        started = false
        _doc.value = Doc()
        _inbox.value = emptyList()
        saveLocal()
    }

    // ================================================================== relays

    private fun start() {
        if (started) return
        val k = _keys.value ?: return
        started = true
        relays.start(RELAYS)
        relays.subscribe("inbox") {
            listOf(JSONObject().put("kinds", JSONArray().put(Envelope.KIND_WRAP)).put("#p", JSONArray().put(k.pub)).put("since", System.currentTimeMillis() / 1000 - 90L * 86400))
        }
        scope.launch {
            relays.events.collect { (_, ev) -> runCatching { onWrap(ev) }.onFailure { Log.w(tag, "wrap", it) } }
        }
        scope.launch {
            loadDoc()
            refreshUsers()
        }
    }

    private fun onWrap(ev: NostrEvent) {
        val k = _keys.value ?: return
        if (ev.kind != Envelope.KIND_WRAP) return
        val inner = Envelope.unwrap(k, ev) ?: return
        val p = inner.payload
        if (p.optString("t") != "dev") return
        if (_inbox.value.any { it.id == inner.id }) return
        val kind = p.optString("k")
        val m = if (kind == "verify") Msg(
            inner.id, inner.from, inner.createdAt, kind, inner.from, p.optString("n") + if (p.optString("u").isNotBlank()) " (@" + p.optString("u") + ")" else "",
            "", p.optString("text"), p.optString("v"), false,
        ) else Msg(
            inner.id, inner.from, inner.createdAt, kind, p.optString("about"), p.optString("name"),
            p.optString("why"), p.optString("text"), p.optString("v"), false,
        )
        _inbox.value = (_inbox.value + m).sortedByDescending { it.at }
        saveLocal()
    }

    fun markRead(id: String) {
        _inbox.value = _inbox.value.map { if (it.id == id) it.copy(read = true) else it }
        saveLocal()
    }

    fun deleteMsg(id: String) {
        _inbox.value = _inbox.value.filter { it.id != id }
        saveLocal()
    }

    /** reads the last published document from the relays */
    suspend fun loadDoc() {
        val k = _keys.value ?: return
        val f = JSONObject().put("kinds", JSONArray().put(KIND_DOC)).put("authors", JSONArray().put(k.pub)).put("#d", JSONArray().put(D_TAG))
        val ev = relays.query(listOf(f), 8000).filter { it.pubkey == k.pub && it.firstTag("d") == D_TAG }.maxByOrNull { it.createdAt } ?: return
        if (ev.createdAt <= _doc.value.at) return
        if (!withContext(Dispatchers.Default) { ev.verify() }) return
        _doc.value = Doc.parse(runCatching { JSONObject(ev.content) }.getOrDefault(JSONObject()), ev.createdAt)
        saveLocal()
    }

    /** signs and sends the whole document; returns how many relays accepted it */
    suspend fun publish(d: Doc): Int {
        val k = _keys.value ?: return 0
        val now = System.currentTimeMillis() / 1000
        val at = if (now <= _doc.value.at) _doc.value.at + 1 else now
        val ev = NostrEvent("", at, KIND_DOC, listOf(listOf("d", D_TAG)), d.toJson().toString())
        withContext(Dispatchers.Default) { ev.sign(k.priv) }
        val accepted = java.util.concurrent.atomic.AtomicInteger(0)
        val counter = scope.launch { relays.oks.filter { it.first == ev.id }.collect { if (it.second) accepted.incrementAndGet() } }
        kotlinx.coroutines.delay(100)
        relays.publish(ev)
        withTimeoutOrNull(6000) { while (accepted.get() < relays.connectedCount.coerceAtLeast(1)) kotlinx.coroutines.delay(150) }
        counter.cancel()
        val ok = accepted.get()
        if (ok > 0) {
            _doc.value = d.copy(at = at)
            saveLocal()
        }
        return ok
    }

    /** gives (or with null takes away) the verified symbol */
    suspend fun setVerified(pk: String, v: Ver?): Int {
        val d = _doc.value
        return publish(d.copy(verified = if (v == null) d.verified - pk else d.verified + (pk to v)))
    }

    suspend fun setBlocked(pk: String, blocked: Boolean): Int {
        val d = _doc.value
        return publish(d.copy(blocked = if (blocked) d.blocked + pk else d.blocked - pk))
    }

    // ================================================================== directory

    suspend fun refreshUsers() {
        if (_loading.value) return
        _loading.value = true
        try {
            val all = HashMap<String, NostrEvent>()
            var until: Long? = null
            for (page in 0 until 12) {
                val f = JSONObject().put("kinds", JSONArray().put(KIND_DOC)).put("#d", JSONArray().put("monolith-profile")).put("limit", 500)
                until?.let { f.put("until", it) }
                val evs = relays.query(listOf(f), 9000)
                    .filter { it.kind == KIND_DOC && it.firstTag("d") == "monolith-profile" }
                if (evs.isEmpty()) break
                evs.forEach { e -> val cur = all[e.pubkey]; if (cur == null || e.createdAt > cur.createdAt) all[e.pubkey] = e }
                val oldest = evs.minOf { it.createdAt }
                if (until != null && oldest >= until) break
                if (evs.size < 50) break
                until = oldest - 1
            }
            val list = withContext(Dispatchers.Default) {
                all.values.filter { it.verify() }.mapNotNull { e ->
                    val o = runCatching { JSONObject(e.content) }.getOrNull() ?: return@mapNotNull null
                    User(e.pubkey, o.optString("u"), o.optString("n").ifBlank { o.optString("u") }, o.optString("e").ifBlank { "🙂" }, o.optString("b"),
                        o.optString("v"), o.optLong("j"), e.createdAt)
                }.sortedByDescending { it.lastSeen }
            }
            if (list.isNotEmpty() || _users.value.isEmpty()) _users.value = list
            _usersAt.value = System.currentTimeMillis()
            saveLocal()
        } catch (e: Throwable) {
            Log.w(tag, "users", e)
        } finally {
            _loading.value = false
        }
    }

    /** for the screenshots: fill the dashboard without going online */
    internal fun preview(k: Keys, users: List<User>, doc: Doc, inbox: List<Msg>) {
        _keys.value = k; _users.value = users; _doc.value = doc; _inbox.value = inbox; _usersAt.value = System.currentTimeMillis()
    }

    fun userOf(pk: String): User? = _users.value.firstOrNull { it.pk == pk }

    /** the builds of the app on GitHub, newest first */
    suspend fun releases(): List<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases?per_page=20").openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            (0 until arr.length()).mapNotNull { i ->
                val r = arr.getJSONObject(i)
                val tagName = r.optString("tag_name")
                if (r.optBoolean("prerelease") || !tagName.startsWith("build-")) return@mapNotNull null
                val apk = r.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
                    ?: return@mapNotNull null
                val version = Regex("Monolith ([0-9]+\\.[0-9]+\\.[0-9]+)").find(r.optString("name"))?.groupValues?.get(1) ?: ""
                Release(tagName, version, tagName.removePrefix("build-").toIntOrNull() ?: 0, apk.optString("browser_download_url"), r.optString("published_at").take(10))
            }
        }.onFailure { Log.w(tag, "releases", it) }.getOrDefault(emptyList())
    }

    // ================================================================== local copy

    private fun file() = File(app.filesDir, "dashboard.json")

    private fun saveLocal() {
        runCatching {
            val d = _doc.value
            val o = JSONObject()
                .put("doc", d.toJson().put("at", d.at))
                .put("inbox", JSONArray(_inbox.value.map { it.toJson() }))
                .put("users", JSONArray(_users.value.map {
                    JSONObject().put("pk", it.pk).put("u", it.username).put("n", it.name).put("e", it.emoji).put("b", it.bio)
                        .put("v", it.version).put("j", it.joined).put("s", it.lastSeen)
                }))
                .put("usersAt", _usersAt.value)
            file().writeText(o.toString())
        }
    }

    private fun loadLocal() {
        runCatching {
            val o = JSONObject(file().readText())
            o.optJSONObject("doc")?.let { _doc.value = Doc.parse(it, it.optLong("at")) }
            o.optJSONArray("inbox")?.let { a -> _inbox.value = (0 until a.length()).map { Msg.fromJson(a.getJSONObject(it)) } }
            o.optJSONArray("users")?.let { a ->
                _users.value = (0 until a.length()).map { a.getJSONObject(it) }.map {
                    User(it.optString("pk"), it.optString("u"), it.optString("n"), it.optString("e"), it.optString("b"), it.optString("v"), it.optLong("j"), it.optLong("s"))
                }
            }
            _usersAt.value = o.optLong("usersAt")
        }
    }
}

/** keeps the developer key encrypted with a key of the phone's keystore */
class KeyVault(ctx: Context) {
    private val sp = ctx.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val alias = "monolith_dev_key"

    fun load(): Keys? = runCatching {
        val enc = sp.getString("sk", null) ?: return null
        val sk = decrypt(enc)
        Keys(sk, Hex.encode(Secp256k1.publicKey(sk)))
    }.getOrNull()

    fun save(sk: ByteArray) { sp.edit().putString("sk", encrypt(sk)).apply() }
    fun clear() { sp.edit().clear().apply() }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(c.iv + c.doFinal(data), Base64.NO_WRAP)
    }

    private fun decrypt(s: String): ByteArray {
        val all = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return c.doFinal(all.copyOfRange(12, all.size))
    }
}

class DevApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Dev.init(this)
    }
}
