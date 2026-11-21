package com.monolith.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.newId
import com.monolith.app.core.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** a stop of at least ten minutes somewhere */
data class Visit(val id: String, val lat: Double, val lon: Double, val start: Long, val end: Long, val name: String)

/**
 * The private diary of places: where you stopped and for how long. Saved only on this phone,
 * never sent to anyone. Uses the low-power network position every few minutes.
 */
class PlaceDiary(private val ctx: Context, private val scope: CoroutineScope) {
    private val sp = ctx.getSharedPreferences("place_diary", Context.MODE_PRIVATE)
    private val file = File(ctx.filesDir, "diary.json")
    private val _visits = MutableStateFlow(load())
    val visits: StateFlow<List<Visit>> = _visits.asStateFlow()
    private val _enabled = MutableStateFlow(sp.getBoolean("on", false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var listening = false
    // the stop being measured: where it started, first and last time we saw the phone there
    private var anchor: Location? = null
    private var anchorFrom = 0L
    private var anchorTo = 0L
    private var openId: String? = null

    private fun load(): List<Visit> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { Visit(it.getString("id"), it.getDouble("a"), it.getDouble("o"), it.getLong("s"), it.getLong("e"), it.optString("n")) }
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Visit>) {
        val keep = list.filter { now() - it.end < 90L * 86_400_000L }.sortedBy { it.start }
        val a = JSONArray()
        keep.forEach { a.put(JSONObject().put("id", it.id).put("a", it.lat).put("o", it.lon).put("s", it.start).put("e", it.end).put("n", it.name)) }
        runCatching { file.writeText(a.toString()) }
        _visits.value = keep
    }

    fun setEnabled(on: Boolean) {
        sp.edit().putBoolean("on", on).apply()
        _enabled.value = on
        if (on) start() else stop()
    }

    fun remove(id: String) = save(_visits.value.filter { it.id != id })

    fun clear() {
        save(emptyList())
        anchor = null
        openId = null
    }

    private val listener = LocationListener { l -> onFix(l) }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!_enabled.value || listening) return
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.PASSIVE_PROVIDER
        runCatching {
            lm.requestLocationUpdates(provider, 3 * 60_000L, 40f, listener, Looper.getMainLooper())
            if (provider != LocationManager.PASSIVE_PROVIDER) lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000L, 40f, listener, Looper.getMainLooper())
            listening = true
        }
    }

    fun stop() {
        if (!listening) return
        runCatching { ctx.getSystemService(LocationManager::class.java)?.removeUpdates(listener) }
        listening = false
        anchor = null
        openId = null
    }

    private fun onFix(l: Location) {
        if (l.accuracy > 250f) return
        val t = now()
        val a = anchor
        if (a == null || a.distanceTo(l) > 150f) {
            // moved away: the previous stop (if any) is closed as it is
            anchor = l
            anchorFrom = t
            anchorTo = t
            openId = null
            return
        }
        anchorTo = t
        if (anchorTo - anchorFrom < 10 * 60_000L) return
        val id = openId
        if (id == null) {
            val v = Visit(newId(), a.latitude, a.longitude, anchorFrom, anchorTo, "")
            openId = v.id
            save(_visits.value + v)
            scope.launch(Dispatchers.IO) { name(v.id, a.latitude, a.longitude) }
        } else {
            save(_visits.value.map { if (it.id == id) it.copy(end = anchorTo) else it })
        }
    }

    /** a common place if we are in one, otherwise the street from the phone's own geocoder */
    @Suppress("DEPRECATION")
    private fun name(id: String, lat: Double, lon: Double) {
        val res = FloatArray(1)
        val inPlace = Graph.places.all.value.firstOrNull { p ->
            Location.distanceBetween(lat, lon, p.lat, p.lon, res)
            res[0] <= p.radius + 30
        }
        val n = inPlace?.let { it.emoji + " " + it.name } ?: runCatching {
            if (!Geocoder.isPresent()) null
            else Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { ad ->
                listOfNotNull(ad.thoroughfare?.let { s -> s + (ad.subThoroughfare?.let { " $it" } ?: "") }, ad.locality).joinToString(", ")
            }
        }.getOrNull()
        if (!n.isNullOrBlank()) save(_visits.value.map { if (it.id == id) it.copy(name = n) else it })
    }
}
