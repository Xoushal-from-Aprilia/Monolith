package com.monolith.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.data.GeoState
import com.monolith.app.data.Place
import com.monolith.app.service.MonolithService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Watches my position against the common places and tells the other members when I arrive.
 *
 * Battery-aware: coarse, rare fixes when far from every place, GPS only while moving towards one,
 * and calm network fixes while I'm inside or standing still (just enough to notice a change).
 */
class PlaceWatcher(private val ctx: Context, private val scope: CoroutineScope) {
    /** FAR: rare coarse fixes · NEAR: GPS while approaching · CALM: inside a place, or standing still nearby */
    private enum class Mode { OFF, FAR, NEAR, CALM }

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val lock = Mutex()
    @Volatile private var mode = Mode.OFF
    @Volatile private var last: Location? = null
    private val streak = HashMap<String, Int>()
    // "am I moving?": the last point I was at, and since when
    private var anchor: Location? = null
    private var anchorAt = 0L
    private var wasActive = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time": needed to notice arrivals while Monolith is closed */
    fun hasBackground(): Boolean = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** there is something to watch and the permission to do it */
    val active: Boolean get() = Graph.places.all.value.isNotEmpty() && hasLocation() && Graph.identity.hasIdentity()

    fun start() {
        scope.launch { Graph.places.all.collect { refresh() } }
        scope.launch {
            while (true) {
                delay(120_000)
                refresh()
            }
        }
    }

    /** re-evaluate what to listen to (places changed, permission granted…) */
    fun refresh() {
        scope.launch {
            val on = active
            if (on != wasActive) {
                wasActive = on
                MonolithService.refresh(ctx)
            }
            listen(if (!on) Mode.OFF else desired(last))
        }
    }

    /** fixes coming from elsewhere (map open, live sharing) are welcome too */
    fun onFix(loc: Location) {
        if (Graph.places.all.value.isEmpty()) return
        last = loc
        val a = anchor
        if (a == null || a.distanceTo(loc) > 40f) {
            anchor = loc
            anchorAt = now()
        }
        scope.launch {
            evaluate(loc)
            listen(if (!active) Mode.OFF else desired(loc))
        }
    }

    private fun distance(loc: Location, p: Place): Float {
        val d = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lon, d)
        return d[0]
    }

    private fun desired(loc: Location?): Mode {
        val places = Graph.places.all.value
        if (places.isEmpty()) return Mode.OFF
        loc ?: return Mode.FAR
        if (places.any { Graph.places.geo(it.id).inside == true }) return Mode.CALM
        val edge = places.minOf { distance(loc, it) - it.radius }
        val still = anchor != null && now() - anchorAt > 3 * 60_000L
        return when {
            edge < 400f && !still -> Mode.NEAR
            edge < 400f -> Mode.CALM
            else -> Mode.FAR
        }
    }

    @Volatile private var appliedAt = 0L

    @SuppressLint("MissingPermission")
    private suspend fun listen(wanted: Mode) = withContext(Dispatchers.Main) {
        val want = if (hasLocation()) wanted else Mode.OFF
        // same mode: keep it, but re-register now and then (GPS switched on later, provider restarts…)
        if (want == mode && (want == Mode.OFF || now() - appliedAt < 10 * 60_000L)) return@withContext
        runCatching { lm.removeUpdates(listener) }
        mode = want
        appliedAt = now()
        if (want == Mode.OFF) return@withContext
        fun req(provider: String, minMs: Long, minM: Float) = runCatching {
            if (provider == LocationManager.PASSIVE_PROVIDER || lm.isProviderEnabled(provider)) {
                lm.requestLocationUpdates(provider, minMs, minM, listener, Looper.getMainLooper())
                true
            } else false
        }.getOrDefault(false)
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        req(LocationManager.PASSIVE_PROVIDER, 15_000L, 0f)
        when (want) {
            Mode.FAR -> if (!req(LocationManager.NETWORK_PROVIDER, 120_000L, 40f) && fine) req(LocationManager.GPS_PROVIDER, 120_000L, 40f)
            Mode.NEAR -> {
                req(LocationManager.NETWORK_PROVIDER, 30_000L, 5f)
                if (fine) req(LocationManager.GPS_PROVIDER, 8_000L, 4f)
            }
            Mode.CALM -> if (!req(LocationManager.NETWORK_PROVIDER, 60_000L, 10f) && fine) req(LocationManager.GPS_PROVIDER, 60_000L, 10f)
            Mode.OFF -> {}
        }
        // ask once for something recent so we don't wait for the first interval
        if (last == null) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER).forEach { p ->
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { l -> if (last == null || l.time > last!!.time) last = l }
            }
        }
    }

    private suspend fun evaluate(loc: Location) = lock.withLock {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
        if (ageMs > 3 * 60_000L) return@withLock          // stale fix
        if (!loc.hasAccuracy() || loc.accuracy > 120f) return@withLock
        val acc = min(loc.accuracy, 50f)
        Graph.places.all.value.forEach { p ->
            val d = distance(loc, p)
            val st = Graph.places.geo(p.id)
            val inside = d <= p.radius + acc * 0.5f
            val clearlyOut = d > p.radius + max(20f, p.radius * 0.5f) + acc * 0.5f
            when {
                // first look at a new place: just learn where I am, no message
                st.inside == null -> when {
                    inside -> {
                        Graph.places.setGeo(p.id, GeoState(true, now(), st.lastArrival))
                        Graph.messenger.placePresence(p, true, notify = false)
                    }
                    clearlyOut -> Graph.places.setGeo(p.id, GeoState(false, now(), st.lastArrival))
                }
                st.inside == false && inside -> {
                    // a precise fix is enough; with a vague one, wait for a second confirmation
                    val n = (streak[p.id] ?: 0) + 1
                    if (loc.accuracy <= max(p.radius.toFloat(), 25f) || n >= 2) {
                        streak.remove(p.id)
                        // no new message if I just stepped out and back in
                        val notify = now() - st.lastArrival > 10 * 60_000L
                        Graph.places.setGeo(p.id, GeoState(true, now(), if (notify) now() else st.lastArrival))
                        Graph.messenger.placePresence(p, true, notify)
                    } else streak[p.id] = n
                }
                st.inside == true && clearlyOut -> {
                    streak.remove(p.id)
                    Graph.places.setGeo(p.id, GeoState(false, now(), st.lastArrival))
                    Graph.messenger.placePresence(p, false, notify = false)
                }
                else -> if (!inside) streak.remove(p.id)
            }
        }
    }
}
