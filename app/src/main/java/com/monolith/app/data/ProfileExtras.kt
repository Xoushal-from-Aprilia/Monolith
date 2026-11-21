package com.monolith.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * What a profile shows beyond name and bio: the cover (one of the preset designs) and the badges.
 * Mine are sent to friends with the profile; theirs arrive the same way and are kept here.
 * Also the counters behind some badges, kept even when messages disappear.
 */
class ProfileExtras(ctx: Context) {
    private val sp = ctx.getSharedPreferences("profile_extras", Context.MODE_PRIVATE)

    private val _cover = MutableStateFlow(sp.getString("cover", "monoliths") ?: "monoliths")
    val cover: StateFlow<String> = _cover.asStateFlow()

    private val _covers = MutableStateFlow(loadMap("covers"))
    val covers: StateFlow<Map<String, String>> = _covers.asStateFlow()

    private val _badges = MutableStateFlow(loadMap("badges").mapValues { (_, v) -> v.split(',').filter { it.isNotBlank() } })
    /** friends' badges: pk -> badge ids */
    val badges: StateFlow<Map<String, List<String>>> = _badges.asStateFlow()

    private fun loadMap(key: String): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.optString(it) }
    }.getOrDefault(emptyMap())

    private fun saveMap(key: String, m: Map<String, String>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString(key, o.toString()).apply()
    }

    fun setCover(id: String) {
        sp.edit().putString("cover", id).apply()
        _cover.value = id
    }

    /** reads cover and badges from a friend's profile payload */
    @Synchronized
    fun read(pk: String, p: JSONObject) {
        if (p.has("cv")) {
            val c = p.optString("cv").take(24)
            _covers.value = _covers.value + (pk to c)
            saveMap("covers", _covers.value)
        }
        if (p.has("bg")) {
            val list = p.optString("bg").split(',').map { it.trim().take(24) }.filter { it.isNotBlank() }.take(80)
            _badges.value = _badges.value + (pk to list)
            saveMap("badges", _badges.value.mapValues { it.value.joinToString(",") })
        }
    }

    /** a counter that only goes up (stories posted, drawings sent, high fives…) */
    fun inc(key: String, by: Int = 1) = sp.edit().putInt("n_$key", count(key) + by).apply()
    fun count(key: String) = sp.getInt("n_$key", 0)
    /** keeps the highest value ever seen for [key] */
    fun atLeast(key: String, v: Int): Int {
        val cur = count(key)
        if (v > cur) sp.edit().putInt("n_$key", v).apply()
        return maxOf(cur, v)
    }

    fun clear() {
        sp.edit().clear().apply()
        _cover.value = "monoliths"
        _covers.value = emptyMap()
        _badges.value = emptyMap()
    }
}
