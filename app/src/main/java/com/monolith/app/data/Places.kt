package com.monolith.app.data

import android.content.Context
import com.monolith.app.core.strings
import com.monolith.app.core.toJsonArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** where a member of a common place is, as they last reported it */
data class Presence(val inside: Boolean, val at: Long)

/**
 * A common place shared between friends ("Casa", "Palestra"…). When any member walks into its
 * radius, the others get a chat message and a notification.
 */
data class Place(
    val id: String,
    val name: String,
    val emoji: String,
    val lat: Double,
    val lon: Double,
    /** metres, 10…100 */
    val radius: Int,
    /** every member's public key, me included */
    val members: List<String>,
    val createdBy: String,
    val updatedAt: Long,
    /** pubkey -> last reported presence (mine included) */
    val here: Map<String, Presence> = emptyMap(),
) {
    fun insideNow(): List<String> = here.filter { it.value.inside }.keys.toList()

    companion object {
        const val MIN_RADIUS = 10
        const val MAX_RADIUS = 100
    }
}

/** "when I get there, remind me to…": personal, never shared */
data class PlaceNote(val id: String, val placeId: String, val text: String, val createdAt: Long)

/** my own geofence state for one place (never shared) */
data class GeoState(val inside: Boolean? = null, val since: Long = 0L, val lastArrival: Long = 0L)

/** Small JSON-file store: places are few, and this keeps the database schema untouched. */
class PlacesStore(ctx: Context) {
    private val file = File(ctx.filesDir, "places.json")
    private val geo = HashMap<String, GeoState>()
    private val _all = MutableStateFlow<List<Place>>(emptyList())
    val all: StateFlow<List<Place>> = _all.asStateFlow()
    private val _notes = MutableStateFlow<List<PlaceNote>>(emptyList())
    val notes: StateFlow<List<PlaceNote>> = _notes.asStateFlow()

    init {
        runCatching {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                val a = root.optJSONArray("places") ?: JSONArray()
                _all.value = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::placeFrom) }
                root.optJSONArray("notes")?.let { n ->
                    _notes.value = (0 until n.length()).mapNotNull { i ->
                        n.optJSONObject(i)?.let { o -> PlaceNote(o.optString("id"), o.optString("p"), o.optString("x"), o.optLong("c")) }
                    }
                }
                root.optJSONObject("geo")?.let { g ->
                    g.keys().forEach { k ->
                        val o = g.optJSONObject(k) ?: return@forEach
                        geo[k] = GeoState(
                            if (o.has("in")) o.optBoolean("in") else null,
                            o.optLong("since"), o.optLong("arr"),
                        )
                    }
                }
            }
        }
    }

    fun get(id: String): Place? = _all.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(p: Place) {
        _all.value = _all.value.filter { it.id != p.id } + p
        save()
    }

    @Synchronized
    fun delete(id: String) {
        _all.value = _all.value.filter { it.id != id }
        _notes.value = _notes.value.filter { it.placeId != id }
        geo.remove(id)
        save()
    }

    @Synchronized
    fun addNote(placeId: String, text: String) {
        if (text.isBlank()) return
        _notes.value = _notes.value + PlaceNote(java.util.UUID.randomUUID().toString(), placeId, text.trim(), com.monolith.app.core.now())
        save()
    }

    @Synchronized
    fun removeNote(id: String) {
        _notes.value = _notes.value.filter { it.id != id }
        save()
    }

    /** the notes for a place, removed once handed out */
    @Synchronized
    fun takeNotes(placeId: String): List<PlaceNote> {
        val mine = _notes.value.filter { it.placeId == placeId }
        if (mine.isNotEmpty()) {
            _notes.value = _notes.value - mine.toSet()
            save()
        }
        return mine
    }

    /** record where [pk] is for place [id]; older reports never overwrite newer ones */
    @Synchronized
    fun setHere(id: String, pk: String, inside: Boolean, at: Long) {
        val p = get(id) ?: return
        val cur = p.here[pk]
        if (cur != null && cur.at > at) return
        upsert(p.copy(here = p.here + (pk to Presence(inside, at))))
    }

    @Synchronized
    fun geo(id: String): GeoState = geo[id] ?: GeoState()

    @Synchronized
    fun setGeo(id: String, s: GeoState) {
        geo[id] = s
        save()
    }

    /** forget my state, e.g. after the place was moved: the next fix decides again, silently */
    @Synchronized
    fun resetGeo(id: String) {
        geo.remove(id)
        save()
    }

    // ---- backup

    fun exportJson(): JSONArray = JSONArray().also { a -> _all.value.forEach { a.put(placeJson(it)) } }

    @Synchronized
    fun importJson(a: JSONArray) {
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::placeFrom) }
        _all.value = list
        geo.clear()
        save()
    }

    @Synchronized
    fun clear() {
        _notes.value = emptyList()
        _all.value = emptyList()
        geo.clear()
        runCatching { file.delete() }
    }

    private fun save() {
        runCatching {
            val g = JSONObject()
            geo.forEach { (k, s) ->
                val o = JSONObject().put("since", s.since).put("arr", s.lastArrival)
                s.inside?.let { o.put("in", it) }
                g.put(k, o)
            }
            val n = JSONArray()
            _notes.value.forEach { n.put(JSONObject().put("id", it.id).put("p", it.placeId).put("x", it.text).put("c", it.createdAt)) }
            val root = JSONObject().put("places", exportJson()).put("geo", g).put("notes", n)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    private fun placeJson(p: Place) = JSONObject()
        .put("id", p.id).put("n", p.name).put("e", p.emoji).put("la", p.lat).put("lo", p.lon).put("r", p.radius)
        .put("m", p.members.toJsonArray()).put("by", p.createdBy).put("u", p.updatedAt)
        .put("here", JSONObject().also { h -> p.here.forEach { (k, v) -> h.put(k, JSONObject().put("in", v.inside).put("at", v.at)) } })

    private fun placeFrom(o: JSONObject): Place? = runCatching {
        val here = HashMap<String, Presence>()
        o.optJSONObject("here")?.let { h ->
            h.keys().forEach { k -> h.optJSONObject(k)?.let { here[k] = Presence(it.optBoolean("in"), it.optLong("at")) } }
        }
        Place(
            id = o.getString("id"), name = o.optString("n"), emoji = o.optString("e", "📍"),
            lat = o.getDouble("la"), lon = o.getDouble("lo"),
            radius = o.optInt("r", 30).coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS),
            members = (o.optJSONArray("m") ?: JSONArray()).strings(),
            createdBy = o.optString("by"), updatedAt = o.optLong("u"), here = here,
        )
    }.getOrNull()
}
