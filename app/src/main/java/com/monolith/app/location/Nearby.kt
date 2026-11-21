package com.monolith.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Friend nearby: when two friends both turn it on for each other, each phone sends its position
 * to the other every few minutes (encrypted, like messages) and you get a notice when you are
 * less than 500 m apart. Nobody else receives anything.
 */
class Nearby(private val ctx: Context, private val scope: CoroutineScope) {
    companion object {
        const val RANGE_M = 500f
    }

    private val sp = ctx.getSharedPreferences("nearby", Context.MODE_PRIVATE)
    private val _mine = MutableStateFlow(sp.getStringSet("mine", emptySet())?.toSet() ?: emptySet())
    /** friends I turned it on for */
    val mine: StateFlow<Set<String>> = _mine.asStateFlow()
    private val _theirs = MutableStateFlow(sp.getStringSet("theirs", emptySet())?.toSet() ?: emptySet())
    /** friends who turned it on for me */
    val theirs: StateFlow<Set<String>> = _theirs.asStateFlow()

    private val positions = HashMap<String, Location>()
    private var myFix: Location? = null
    private var lastSentAt = 0L
    private var lastSentFix: Location? = null
    private var listening = false

    val mutual: Set<String> get() = _mine.value intersect _theirs.value
    val active: Boolean get() = mutual.isNotEmpty()

    fun setMine(pk: String, on: Boolean) {
        val next = if (on) _mine.value + pk else _mine.value - pk
        sp.edit().putStringSet("mine", next).apply()
        _mine.value = next
        scope.launch { Graph.messenger.sendStored(pk, JSONObject().put("t", "nearon").put("on", on)) }
        refresh()
    }

    fun onOn(from: String, p: JSONObject, friend: Friend?) {
        if (friend?.status != FriendStatus.FRIEND) return
        val on = p.optBoolean("on")
        val next = if (on) _theirs.value + from else _theirs.value - from
        sp.edit().putStringSet("theirs", next).apply()
        _theirs.value = next
        if (!on) positions.remove(from)
        refresh()
    }

    fun onPos(from: String, p: JSONObject) {
        if (from !in mutual) return
        val l = Location("near").apply { latitude = p.optDouble("lat"); longitude = p.optDouble("lon"); accuracy = p.optDouble("acc", 100.0).toFloat() }
        if (l.latitude.isNaN() || l.longitude.isNaN()) return
        positions[from] = l
        check(from)
    }

    private fun check(pk: String) {
        val me = myFix ?: Graph.location.me.value ?: return
        val them = positions[pk] ?: return
        val d = me.distanceTo(them)
        if (d > RANGE_M) return
        val key = "alert_$pk"
        if (now() - sp.getLong(key, 0L) < 3 * 3_600_000L) return
        sp.edit().putLong(key, now()).apply()
        scope.launch {
            val f = Graph.db.friends().get(pk) ?: return@launch
            Graph.notifier.nearby(pk, f.displayName, f.emoji, d.toInt())
        }
    }

    private val listener = LocationListener { l -> onFix(l) }

    private fun onFix(l: Location) {
        myFix = l
        mutual.forEach { check(it) }
        val moved = lastSentFix?.distanceTo(l) ?: Float.MAX_VALUE
        if (moved < 150f && now() - lastSentAt < 10 * 60_000L) return
        lastSentAt = now()
        lastSentFix = l
        val payload = JSONObject().put("t", "nearpos").put("lat", l.latitude).put("lon", l.longitude).put("acc", l.accuracy.toDouble())
        scope.launch { mutual.forEach { pk -> Graph.messenger.sendFast(pk, JSONObject(payload.toString())) } }
    }

    /** start or stop listening to the position, depending on whether any friend is mutual */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        if (active && ok && !listening) {
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.PASSIVE_PROVIDER
            runCatching {
                lm.requestLocationUpdates(provider, 4 * 60_000L, 100f, listener, Looper.getMainLooper())
                listening = true
            }
            com.monolith.app.service.MonolithService.start(ctx)
        } else if ((!active || !ok) && listening) {
            runCatching { lm.removeUpdates(listener) }
            listening = false
        }
    }

    fun clear() {
        sp.edit().clear().apply()
        _mine.value = emptySet()
        _theirs.value = emptySet()
        positions.clear()
        refresh()
    }
}
