package com.monolith.app.net

import android.location.Location
import com.monolith.app.Graph
import com.monolith.app.core.Profile
import com.monolith.app.core.now
import com.monolith.app.nostr.NostrEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** someone who gave you a high five at the same moment */
data class FiveMatch(val pubkey: String, val profile: Profile)

/**
 * High five to add a friend: both phones feel the bump and say so on a public live channel;
 * two bumps a second apart, a short distance apart, make a match that both people confirm.
 */
object HighFive {
    private const val KIND = 25078
    private const val TAG = "monolith-five"
    private const val SUB = "five"

    private data class Bump(val pk: String, val at: Long, val seenAt: Long, val lat: Double?, val lon: Double?, val profile: Profile)

    private val mine = ArrayList<Long>()
    private val theirs = ArrayList<Bump>()
    private var job: Job? = null
    private var myPos: Location? = null

    private val _matches = MutableStateFlow<List<FiveMatch>>(emptyList())
    val matches: StateFlow<List<FiveMatch>> = _matches.asStateFlow()
    private val _bumps = MutableStateFlow(0)
    /** how many bumps this phone felt: drives the animation */
    val bumps: StateFlow<Int> = _bumps.asStateFlow()

    fun open(pos: Location?) {
        myPos = pos
        _matches.value = emptyList()
        Graph.relays.subscribe(SUB) {
            listOf(JSONObject().put("kinds", JSONArray().put(KIND)).put("#t", JSONArray().put(TAG)).put("since", now() / 1000 - 20))
        }
        job?.cancel()
        job = Graph.scope.launch {
            Graph.relays.events.collect { (sub, ev) ->
                if (sub == SUB && ev.kind == KIND) onEvent(ev)
            }
        }
    }

    fun close() {
        job?.cancel()
        job = null
        Graph.relays.unsubscribe(SUB)
        synchronized(this) { mine.clear(); theirs.clear() }
    }

    fun updatePosition(pos: Location?) { if (pos != null) myPos = pos }

    /** the phone felt a bump (or the button was pressed) */
    fun bump() {
        val k = Graph.identity.keys ?: return
        val p = Graph.identity.profile.value ?: return
        val at = now()
        synchronized(this) {
            mine.add(at)
            mine.removeAll { at - it > 15_000 }
        }
        _bumps.value = _bumps.value + 1
        val body = JSONObject().put("at", at).put("u", p.username).put("n", p.displayName).put("e", p.emoji)
        myPos?.let { body.put("lat", "%.2f".format(java.util.Locale.US, it.latitude).toDouble()).put("lon", "%.2f".format(java.util.Locale.US, it.longitude).toDouble()) }
        Graph.scope.launch {
            val ev = NostrEvent("", now() / 1000, KIND, listOf(listOf("t", TAG)), body.toString())
            withContext(Dispatchers.Default) { ev.sign(k.priv) }
            Graph.relays.publish(ev)
            match()
        }
    }

    private suspend fun onEvent(ev: NostrEvent) {
        val me = Graph.identity.keys?.pub ?: return
        if (ev.pubkey == me) return
        if (!withContext(Dispatchers.Default) { ev.verify() }) return
        val o = runCatching { JSONObject(ev.content) }.getOrNull() ?: return
        val b = Bump(
            ev.pubkey, o.optLong("at"), now(),
            o.optDouble("lat").takeIf { !it.isNaN() }, o.optDouble("lon").takeIf { !it.isNaN() },
            Profile(o.optString("u").take(32), o.optString("n").take(40), o.optString("e", "🙂").take(8)),
        )
        synchronized(this) {
            theirs.add(b)
            theirs.removeAll { now() - it.seenAt > 15_000 }
        }
        match()
    }

    private fun near(b: Bump): Boolean {
        val p = myPos ?: return true
        val lat = b.lat ?: return true
        val lon = b.lon ?: return true
        val r = FloatArray(1)
        Location.distanceBetween(p.latitude, p.longitude, lat, lon, r)
        return r[0] < 3000f
    }

    private fun match() {
        val found = synchronized(this) {
            theirs.filter { b ->
                near(b) && mine.any { m -> abs(b.at - m) < 1500 || (b.seenAt - m) in -500L..4000L }
            }.map { FiveMatch(it.pk, it.profile) }
        }
        if (found.isEmpty()) return
        _matches.value = (_matches.value + found).distinctBy { it.pubkey }
    }

    suspend fun add(m: FiveMatch) {
        Graph.messenger.sendFriendRequest(UserResult(m.pubkey, m.profile, now() / 1000), verified = true)
        Graph.profileExtras.inc("highfive")
    }
}
