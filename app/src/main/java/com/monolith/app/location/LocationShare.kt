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

/**
 * Live location sharing. Updates go phone-to-phone over WebRTC when a direct channel is up,
 * otherwise as short-lived encrypted live relay events.
 */
class LocationShare(private val ctx: Context, private val scope: CoroutineScope) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _me = MutableStateFlow<Location?>(null)
    val me: StateFlow<Location?> = _me.asStateFlow()

    private val _sharing = MutableStateFlow(false)
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    @Volatile private var listening = false
    @Volatile private var uiWatchers = 0
    private var lastBroadcast = 0L
    private val lastRelay = ConcurrentHashMap<String, Long>()

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

    /** map screen visible: keep my position fresh even when not sharing */
    fun watch(on: Boolean) {
        uiWatchers = (uiWatchers + if (on) 1 else -1).coerceAtLeast(0)
        scope.launch { applyListening() }
    }

    fun startSharing(friends: List<String>, minutes: Int) {
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

    fun stopSharing(friend: String? = null) {
        scope.launch {
            val targets = if (friend != null) listOf(friend) else Graph.db.friends().sharingTargets(now()).map { it.pubkey }
            targets.forEach {
                Graph.db.friends().iShare(it, 0)
                Graph.messenger.sendLocStop(it)
            }
            refresh()
        }
    }

    suspend fun refresh() {
        val targets = Graph.db.friends().sharingTargets(now())
        val was = _sharing.value
        _sharing.value = targets.isNotEmpty()
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
        // prefer accurate fixes: ignore a coarse network fix right after a good GPS one
        if (prev != null && loc.provider == LocationManager.NETWORK_PROVIDER && prev.provider == LocationManager.GPS_PROVIDER &&
            now() - prev.time < 20_000 && loc.accuracy > prev.accuracy
        ) return
        _me.value = loc
        if (_sharing.value) scope.launch { broadcast(loc) }
    }

    private suspend fun broadcast(loc: Location, force: Boolean = false) {
        val t = now()
        if (!force && t - lastBroadcast < 2500) return
        lastBroadcast = t
        val targets = Graph.db.friends().sharingTargets(t)
        if (targets.isEmpty()) {
            refresh()
            return
        }
        targets.forEach { f ->
            val payload = JSONObject().put("t", "loc")
                .put("lat", loc.latitude).put("lon", loc.longitude)
                .put("acc", loc.accuracy.toDouble()).put("at", t).put("until", f.iShareUntil)
            if (!Graph.rtc.send(f.pubkey, payload)) {
                Graph.rtc.ensureConnected(f.pubkey)
                val last = lastRelay[f.pubkey] ?: 0L
                if (force || t - last > 8000) {
                    lastRelay[f.pubkey] = t
                    Graph.messenger.sendLive(f.pubkey, payload)
                }
            }
        }
    }
}
