package com.monolith.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.service.MonolithService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** "I'm on my way" session towards a destination, shared live with one friend */
data class OnTheWay(val friend: String, val lat: Double, val lon: Double, val label: String, val startedAt: Long) {
    fun encode() = listOf(friend, lat, lon, label.replace("|", " "), startedAt).joinToString("|")

    companion object {
        fun decode(s: String): OnTheWay? = runCatching {
            val p = s.split("|")
            OnTheWay(p[0], p[1].toDouble(), p[2].toDouble(), p[3], p[4].toLong())
        }.getOrNull()
    }
}

/** Live location sharing with friends, plus "on my way" with distance and arrival time. */
class LocationShare(private val ctx: Context, private val scope: CoroutineScope) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _me = MutableStateFlow<Location?>(null)
    val me: StateFlow<Location?> = _me.asStateFlow()

    private val _sharing = MutableStateFlow(false)
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    private val _otw = MutableStateFlow(OnTheWay.decode(Graph.prefs.onTheWay))
    val onTheWay: StateFlow<OnTheWay?> = _otw.asStateFlow()

    @Volatile private var listening = false
    @Volatile private var uiWatchers = 0
    private var lastBroadcast = 0L
    private val lastRelay = ConcurrentHashMap<String, Long>()
    private val speeds = ArrayDeque<Float>()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    init {
        scope.launch {
            delay(3000)
            refresh()
            while (true) {
                delay(30_000)
                if (_sharing.value) refresh()
            }
        }
    }

    fun watch(on: Boolean) {
        uiWatchers = (uiWatchers + if (on) 1 else -1).coerceAtLeast(0)
        scope.launch { applyListening() }
    }

    fun startSharing(friends: List<String>, minutes: Int, remember: Boolean = true) {
        if (remember) {
            Graph.prefs.lastShareFriends = friends
            Graph.prefs.lastShareMinutes = minutes
        }
        scope.launch {
            val until = now() + minutes * 60_000L
            friends.forEach {
                Graph.db.friends().iShare(it, until)
                Graph.messenger.sendLocStart(it, until)
                Graph.rtc.ensureConnected(it)
            }
            refresh()
            _me.value?.let { broadcast(it, force = true) }
        }
    }

    fun startOnTheWay(friend: String, lat: Double, lon: Double, label: String) {
        val s = OnTheWay(friend, lat, lon, label, now())
        _otw.value = s
        Graph.prefs.onTheWay = s.encode()
        scope.launch {
            val until = now() + 4 * 3_600_000L
            Graph.db.friends().iShare(friend, until)
            Graph.messenger.sendLocStart(friend, until, onTheWay = true)
            Graph.rtc.ensureConnected(friend)
            refresh()
            _me.value?.let { broadcast(it, force = true) }
        }
    }

    fun cancelOnTheWay() {
        val s = _otw.value ?: return
        clearOtw()
        stopSharing(s.friend)
    }

    private fun clearOtw() {
        _otw.value = null
        Graph.prefs.onTheWay = ""
        speeds.clear()
    }

    fun stopSharing(friend: String? = null) {
        scope.launch {
            val targets = if (friend != null) listOf(friend) else Graph.db.friends().sharingTargets(now()).map { it.pubkey }
            targets.forEach {
                Graph.db.friends().iShare(it, 0)
                Graph.messenger.sendLocStop(it)
            }
            if (friend == null || _otw.value?.friend == friend) clearOtw()
            refresh()
        }
    }

    suspend fun refresh() {
        val targets = Graph.db.friends().sharingTargets(now())
        val was = _sharing.value
        _sharing.value = targets.isNotEmpty()
        if (targets.isEmpty() && _otw.value != null) clearOtw()
        applyListening()
        if (was != _sharing.value) MonolithService.refresh(ctx)
    }

    @SuppressLint("MissingPermission")
    private suspend fun applyListening() = withContext(Dispatchers.Main) {
        val want = (_sharing.value || uiWatchers > 0) && hasPermission()
        if (want && !listening) {
            listening = true
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
                runCatching {
                    if (lm.isProviderEnabled(p)) {
                        lm.getLastKnownLocation(p)?.let { if (_me.value == null) _me.value = it }
                        lm.requestLocationUpdates(p, 3000L, 2f, listener, Looper.getMainLooper())
                    }
                }
            }
        } else if (!want && listening) {
            listening = false
            runCatching { lm.removeUpdates(listener) }
        }
        Unit
    }

    private fun onFix(loc: Location) {
        val prev = _me.value
        if (prev != null && loc.provider == LocationManager.NETWORK_PROVIDER && prev.provider == LocationManager.GPS_PROVIDER &&
            now() - prev.time < 20_000 && loc.accuracy > prev.accuracy
        ) return
        if (loc.hasSpeed() && loc.speed > 0.4f) {
            speeds.addLast(loc.speed)
            while (speeds.size > 12) speeds.removeFirst()
        }
        _me.value = loc
        Graph.placeWatch.onFix(loc)
        if (_sharing.value) scope.launch { broadcast(loc) }
    }

    private suspend fun broadcast(loc: Location, force: Boolean = false) {
        val tNow = now()
        if (!force && tNow - lastBroadcast < 2500) return
        lastBroadcast = tNow
        val targets = Graph.db.friends().sharingTargets(tNow)
        if (targets.isEmpty()) {
            refresh()
            return
        }
        val otw = _otw.value
        var dist = -1
        var eta = -1
        if (otw != null) {
            val d = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, otw.lat, otw.lon, d)
            dist = d[0].toInt()
            val speed = (if (speeds.isEmpty()) 1.3f else speeds.average().toFloat()).coerceAtLeast(1.2f)
            eta = (d[0] / speed).toInt()
            if (d[0] < 120f) {
                arrived(otw)
                return
            }
        }
        targets.forEach { f ->
            val payload = JSONObject().put("t", "loc")
                .put("lat", loc.latitude).put("lon", loc.longitude)
                .put("acc", loc.accuracy.toDouble()).put("at", tNow).put("until", f.iShareUntil)
            if (otw != null && otw.friend == f.pubkey) payload.put("dist", dist).put("eta", eta)
            if (!Graph.rtc.send(f.pubkey, payload)) {
                Graph.rtc.ensureConnected(f.pubkey)
                val last = lastRelay[f.pubkey] ?: 0L
                if (force || tNow - last > 8000) {
                    lastRelay[f.pubkey] = tNow
                    Graph.messenger.sendLive(f.pubkey, payload)
                }
            }
        }
    }

    private suspend fun arrived(otw: OnTheWay) {
        clearOtw()
        Graph.messenger.sendText(otw.friend, t("Sono arrivato 📍"))
        Graph.db.friends().iShare(otw.friend, 0)
        Graph.messenger.sendLocStop(otw.friend)
        val f = Graph.db.friends().get(otw.friend)
        Graph.notifier.arrivedSelf(f?.displayName ?: "", otw.label)
        refresh()
    }
}
