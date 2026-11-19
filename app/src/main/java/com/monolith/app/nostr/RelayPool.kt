package com.monolith.app.nostr

import android.util.Log
import com.monolith.app.core.Keys
import com.monolith.app.core.newId
import com.monolith.app.core.nowSec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

fun parseEvent(o: JSONObject): NostrEvent = NostrEvent().apply {
    id = o.getString("id")
    pubkey = o.getString("pubkey")
    createdAt = o.getLong("created_at")
    kind = o.getInt("kind")
    content = o.optString("content", "")
    sig = o.optString("sig", "")
    val ta = o.optJSONArray("tags") ?: JSONArray()
    val list = ArrayList<List<String>>()
    for (i in 0 until ta.length()) {
        val t = ta.optJSONArray(i) ?: continue
        list.add((0 until t.length()).map { t.optString(it) })
    }
    tags = list
}

enum class RelayState { CONNECTING, CONNECTED, DISCONNECTED }

data class RelayInfo(val url: String, val state: RelayState, val error: String? = null)

/**
 * Connection pool to public Nostr relays. Relays are only used as dumb, untrusted mailboxes:
 * everything private travels end-to-end encrypted inside gift-wrapped events.
 */
class RelayPool(
    private val scope: CoroutineScope,
    private val keysProvider: () -> Keys?,
) {
    private val tag = "RelayPool"
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val relays = ConcurrentHashMap<String, Relay>()
    private val subs = ConcurrentHashMap<String, () -> List<JSONObject>>()
    private val queryCollectors = ConcurrentHashMap<String, (NostrEvent) -> Unit>()
    private val eoseWaiters = ConcurrentHashMap<String, () -> Unit>()

    private val _info = MutableStateFlow<Map<String, RelayInfo>>(emptyMap())
    val info: StateFlow<Map<String, RelayInfo>> = _info.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, NostrEvent>>(extraBufferCapacity = 1024)
    val events: SharedFlow<Pair<String, NostrEvent>> = _events.asSharedFlow()

    private val _oks = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 512)
    val oks: SharedFlow<Pair<String, Boolean>> = _oks.asSharedFlow()

    private val _connectedEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val connectedEvents: SharedFlow<String> = _connectedEvents.asSharedFlow()

    private val seen = object : LinkedHashMap<String, Boolean>(4096, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 6000
    }

    @Volatile
    private var active = false

    val connectedCount: Int get() = relays.values.count { it.state == RelayState.CONNECTED }

    fun start(urls: List<String>) {
        active = true
        setRelays(urls)
    }

    fun stop() {
        active = false
        relays.values.forEach { it.shutdown() }
        relays.clear()
        publishInfo()
    }

    fun setRelays(urls: List<String>) {
        if (!active) return
        val wanted = urls.toSet()
        relays.keys.filter { it !in wanted }.forEach { relays.remove(it)?.shutdown() }
        wanted.forEach { url -> relays.getOrPut(url) { Relay(url).also { it.connect() } } }
        publishInfo()
    }

    /** reconnect relays that are down (e.g. after the network comes back) */
    fun reconnectNow() {
        if (!active) return
        relays.values.filter { it.state == RelayState.DISCONNECTED }.forEach { it.connect(immediate = true) }
    }

    fun subscribe(id: String, filters: () -> List<JSONObject>) {
        subs[id] = filters
        relays.values.forEach { it.sendReq(id, filters()) }
    }

    fun unsubscribe(id: String) {
        subs.remove(id)
        relays.values.forEach { it.send("[\"CLOSE\",\"$id\"]") }
    }

    fun publish(ev: NostrEvent) {
        val msg = "[\"EVENT\",${ev.toJson()}]"
        relays.values.forEach { it.publish(ev.id, msg) }
    }

    /** One-shot query across connected relays; waits for EOSE from all of them or the timeout. */
    suspend fun query(filters: List<JSONObject>, timeoutMs: Long = 6000): List<NostrEvent> {
        if (connectedCount == 0) {
            withTimeoutOrNull(4000) {
                while (connectedCount == 0) delay(150)
            }
        }
        val connected = relays.values.filter { it.state == RelayState.CONNECTED }
        if (connected.isEmpty()) return emptyList()
        val id = "q" + newId().take(12)
        val results = ConcurrentHashMap<String, NostrEvent>()
        val remaining = AtomicInteger(connected.size)
        val done = CompletableDeferred<Unit>()
        queryCollectors[id] = { ev -> results[ev.id] = ev }
        eoseWaiters[id] = { if (remaining.decrementAndGet() <= 0) done.complete(Unit) }
        connected.forEach { it.sendReq(id, filters) }
        withTimeoutOrNull(timeoutMs) { done.await() }
        queryCollectors.remove(id)
        eoseWaiters.remove(id)
        connected.forEach { it.send("[\"CLOSE\",\"$id\"]") }
        return results.values.toList()
    }

    private fun publishInfo() {
        _info.value = relays.values.associate { it.url to RelayInfo(it.url, it.state, it.lastError) }
    }

    private fun route(subId: String, ev: NostrEvent) {
        val collector = queryCollectors[subId]
        if (collector != null) {
            collector(ev)
            return
        }
        val fresh = synchronized(seen) { seen.put(ev.id, true) == null }
        if (fresh) _events.tryEmit(subId to ev)
    }

    private inner class Relay(val url: String) {
        @Volatile var state = RelayState.DISCONNECTED
        @Volatile var lastError: String? = null
        @Volatile private var ws: WebSocket? = null
        @Volatile private var closed = false
        private var attempts = 0
        private var reconnectJob: Job? = null
        private val pending = LinkedHashMap<String, String>()
        private val recent = object : LinkedHashMap<String, String>(64, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
        }

        fun connect(immediate: Boolean = false) {
            if (closed || !active) return
            if (state != RelayState.DISCONNECTED) return
            if (immediate) {
                reconnectJob?.cancel()
                attempts = 0
            }
            state = RelayState.CONNECTING
            publishInfo()
            try {
                ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
            } catch (e: Exception) {
                lastError = e.message
                state = RelayState.DISCONNECTED
                publishInfo()
                scheduleReconnect()
            }
        }

        fun shutdown() {
            closed = true
            reconnectJob?.cancel()
            runCatching { ws?.close(1000, "bye") }
            ws = null
            state = RelayState.DISCONNECTED
        }

        private fun scheduleReconnect() {
            if (closed || !active) return
            reconnectJob?.cancel()
            val backoff = (1000L shl attempts.coerceAtMost(6)).coerceAtMost(60_000L) + Random.nextLong(0, 1000)
            attempts++
            reconnectJob = scope.launch {
                delay(backoff)
                connect()
            }
        }

        fun send(text: String): Boolean {
            val socket = ws ?: return false
            if (state != RelayState.CONNECTED) return false
            return socket.send(text)
        }

        fun sendReq(id: String, filters: List<JSONObject>) {
            val sb = StringBuilder("[\"REQ\",\"").append(id).append('"')
            filters.forEach { sb.append(',').append(it.toString()) }
            sb.append(']')
            send(sb.toString())
        }

        fun publish(eventId: String, msg: String) {
            synchronized(recent) { recent[eventId] = msg }
            if (!send(msg)) {
                synchronized(pending) {
                    pending[eventId] = msg
                    while (pending.size > 400) pending.remove(pending.keys.first())
                }
            }
        }

        private fun onConnected() {
            subs.forEach { (id, f) -> runCatching { sendReq(id, f()) } }
            val toSend = synchronized(pending) { pending.values.toList().also { pending.clear() } }
            toSend.forEach { send(it) }
            _connectedEvents.tryEmit(url)
        }

        private fun auth(challenge: String) {
            val keys = keysProvider() ?: return
            val ev = NostrEvent(
                "", nowSec(), 22242,
                listOf(listOf("relay", url), listOf("challenge", challenge)), ""
            ).sign(keys.priv)
            send("[\"AUTH\",${ev.toJson()}]")
            scope.launch {
                delay(700)
                subs.forEach { (id, f) -> runCatching { sendReq(id, f()) } }
            }
        }

        private fun handle(text: String) {
            val arr = JSONArray(text)
            when (arr.optString(0)) {
                "EVENT" -> {
                    val sub = arr.optString(1)
                    val obj = arr.optJSONObject(2) ?: return
                    route(sub, parseEvent(obj))
                }
                "EOSE" -> eoseWaiters[arr.optString(1)]?.invoke()
                "OK" -> {
                    val id = arr.optString(1)
                    val ok = arr.optBoolean(2)
                    val msg = arr.optString(3)
                    _oks.tryEmit(id to ok)
                    if (!ok && msg.startsWith("auth-required")) {
                        val again = synchronized(recent) { recent[id] }
                        if (again != null) scope.launch { delay(1200); send(again) }
                    }
                }
                "AUTH" -> auth(arr.optString(1))
                "CLOSED" -> {
                    val sub = arr.optString(1)
                    eoseWaiters[sub]?.invoke()
                }
                "NOTICE" -> Log.d(tag, "$url notice: ${arr.optString(1)}")
            }
        }

        private val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return
                state = RelayState.CONNECTED
                attempts = 0
                lastError = null
                publishInfo()
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { handle(text) }.onFailure { Log.w(tag, "bad message from $url", it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== ws) return
                state = RelayState.DISCONNECTED
                publishInfo()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== ws) return
                lastError = t.message
                state = RelayState.DISCONNECTED
                publishInfo()
                scheduleReconnect()
            }
        }
    }
}
