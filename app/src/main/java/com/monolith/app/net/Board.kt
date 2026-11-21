package com.monolith.app.net

import com.monolith.app.Graph
import com.monolith.app.core.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** one line on the shared board; points are 0..1 across the board, w is a fraction of its width */
data class BoardStroke(val id: String, val by: String, val w: Float, val shade: Int, val erase: Boolean, val pts: List<Float>)

/**
 * The shared board of each chat, kept in memory while people draw together.
 * Strokes travel as live updates; whoever joins late asks the others for the whole drawing.
 */
object Boards {
    private val _strokes = MutableStateFlow<Map<String, List<BoardStroke>>>(emptyMap())
    val strokes: StateFlow<Map<String, List<BoardStroke>>> = _strokes.asStateFlow()

    /** who is drawing right now, per chat: pk -> last seen */
    private val _here = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val here: StateFlow<Map<String, Map<String, Long>>> = _here.asStateFlow()

    private fun put(conv: String, s: BoardStroke) {
        _strokes.value = _strokes.value + (conv to ((_strokes.value[conv] ?: emptyList()).filter { it.id != s.id } + s))
    }

    @Synchronized
    fun addPoints(conv: String, id: String, by: String, w: Float, shade: Int, erase: Boolean, from: Int, pts: List<Float>) {
        val cur = _strokes.value[conv]?.firstOrNull { it.id == id }
        val base = cur?.pts ?: emptyList()
        val merged = if (from >= base.size) base + pts else base.take(from) + pts + base.drop(from + pts.size)
        put(conv, BoardStroke(id, by, w, shade, erase, merged))
    }

    @Synchronized
    fun undo(conv: String, id: String) {
        _strokes.value = _strokes.value + (conv to (_strokes.value[conv] ?: emptyList()).filter { it.id != id })
    }

    @Synchronized
    fun clear(conv: String) {
        _strokes.value = _strokes.value + (conv to emptyList())
    }

    private fun touch(conv: String, pk: String) {
        val m = (_here.value[conv] ?: emptyMap()) + (pk to now())
        _here.value = _here.value + (conv to m)
    }

    fun leave(conv: String, pk: String) {
        _here.value = _here.value + (conv to ((_here.value[conv] ?: emptyMap()) - pk))
    }

    fun json(s: BoardStroke): JSONObject = JSONObject().put("s", s.id).put("i", 0).put("w", s.w.toDouble()).put("h", s.shade)
        .put("e", s.erase).put("by", s.by).put("p", JSONArray().also { a -> s.pts.forEach { a.put(it.toDouble()) } })

    private fun points(a: JSONArray?): List<Float> = if (a == null) emptyList() else (0 until a.length()).map { a.optDouble(it).toFloat() }

    /** live updates from the others: draw, undo, clear, join, the whole board */
    suspend fun onLive(conv: String, from: String, p: JSONObject) {
        touch(conv, from)
        when (p.optString("t")) {
            "draw" -> addPoints(conv, p.optString("s"), from, p.optDouble("w", 0.01).toFloat(), p.optInt("h"), p.optBoolean("e"), p.optInt("i"), points(p.optJSONArray("p")))
            "drawundo" -> undo(conv, p.optString("s"))
            "drawclr" -> clear(conv)
            "drawbye" -> leave(conv, from)
            "drawjoin" -> {
                val all = _strokes.value[conv] ?: emptyList()
                all.chunked(25).forEach { part ->
                    Graph.messenger.fastToConv(conv, JSONObject().put("t", "drawall").put("l", JSONArray().also { a -> part.forEach { a.put(json(it)) } }), only = from)
                }
            }
            "drawall" -> {
                val l = p.optJSONArray("l") ?: return
                for (i in 0 until l.length()) {
                    val o = l.optJSONObject(i) ?: continue
                    val id = o.optString("s")
                    if ((_strokes.value[conv] ?: emptyList()).any { it.id == id }) continue
                    put(conv, BoardStroke(id, o.optString("by", from), o.optDouble("w", 0.01).toFloat(), o.optInt("h"), o.optBoolean("e"), points(o.optJSONArray("p"))))
                }
            }
        }
    }
}
