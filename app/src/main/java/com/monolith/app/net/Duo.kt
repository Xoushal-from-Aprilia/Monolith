package com.monolith.app.net

import com.monolith.app.Graph
import com.monolith.app.core.newId
import com.monolith.app.core.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Two phones side by side become one screen.
 * Each phone knows its own size in millimetres. A finger slides off the edge of one phone and onto the
 * edge of the other: from where it left and where it arrived, both phones work out where the other one
 * is (left or right, and how much higher or lower). Then a photo spans both screens, and a photo
 * thrown off one screen lands on the other. Signals go over the direct link (or the live mailbox).
 */
object Duo {
    /** where a finger crossed an edge: [exit] it left this screen, otherwise it arrived; [side] 'L' or 'R'; [y] in mm from the top */
    data class Edge(val exit: Boolean, val side: Char, val y: Float, val at: Long)

    /** where the other phone is, in this phone's millimetres: its top-left corner and its size */
    data class Layout(val x: Float, val y: Float, val w: Float, val h: Float, val right: Boolean)

    /** a photo thrown across: [y] where it crosses the edge, [vy] its vertical speed (mm/s), [thumb] a small JPEG */
    data class Throw(val id: String, val incoming: Boolean, val y: Float, val vy: Float, val thumb: String, val at: Long)

    data class State(
        val peer: String? = null,
        /** the other phone has this screen open too */
        val here: Boolean = false,
        val peerW: Float = 0f,
        val peerH: Float = 0f,
        val layout: Layout? = null,
        /** the photo of the chat shown across both screens (message id) */
        val photo: String? = null,
        val throws: List<Throw> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var myW = 0f
    private var myH = 0f
    private var mine: Edge? = null
    private var theirs: Edge? = null

    /** for the screenshots: two phones already joined */
    fun preview(s: State) { _state.value = s }

    private fun send(p: JSONObject) {
        val peer = _state.value.peer ?: return
        Graph.scope.launch { Graph.messenger.sendFast(peer, p.put("t", "duo")) }
    }

    /** the screen opened, [w]×[h] millimetres */
    fun open(peer: String, w: Float, h: Float) {
        myW = w; myH = h
        if (_state.value.peer != peer) _state.value = State(peer = peer)
        send(JSONObject().put("k", "hi").put("w", w.toDouble()).put("h", h.toDouble()))
    }

    fun close() {
        send(JSONObject().put("k", "bye"))
        _state.value = State()
        mine = null; theirs = null
    }

    /** start again: the phones were moved */
    fun realign() {
        mine = null; theirs = null
        _state.value = _state.value.copy(layout = null)
        send(JSONObject().put("k", "reset"))
    }

    /** a finger crossed one of my edges */
    fun edge(exit: Boolean, side: Char, y: Float) {
        val e = Edge(exit, side, y, now())
        mine = e
        send(JSONObject().put("k", "edge").put("x", exit).put("s", side.toString()).put("y", y.toDouble()))
        match()
    }

    /** both edges known: where is the other phone? */
    private fun match() {
        val a = mine ?: return
        val b = theirs ?: return
        if (a.exit == b.exit || a.side == b.side || kotlin.math.abs(a.at - b.at) > 3000L) return
        val st = _state.value
        if (st.peerW <= 0f) return
        val right = a.side == 'R'
        // the same point of the glass is (edge, a.y) here and (other edge, b.y) there
        val l = Layout(if (right) myW else -st.peerW, a.y - b.y, st.peerW, st.peerH, right)
        _state.value = st.copy(layout = l)
        mine = null; theirs = null
    }

    /** show a photo of our chat across both screens (or stop, with null) */
    fun showPhoto(msgId: String?) {
        _state.value = _state.value.copy(photo = msgId)
        send(JSONObject().put("k", "photo").put("id", msgId ?: ""))
    }

    /** a photo thrown off my screen: it goes to the chat too */
    fun throwPhoto(uri: android.net.Uri, y: Float, vy: Float, thumb: String) {
        val peer = _state.value.peer ?: return
        val id = newId()
        _state.value = _state.value.copy(throws = (_state.value.throws + Throw(id, false, y, vy, thumb, now())).takeLast(6))
        Graph.scope.launch {
            Graph.messenger.sendFast(peer, JSONObject().put("t", "duo").put("k", "throw").put("id", id).put("y", y.toDouble()).put("vy", vy.toDouble()).put("tb", thumb))
            Graph.messenger.sendPhoto(peer, uri, "")
        }
    }

    suspend fun onSignal(from: String, p: JSONObject) {
        val st = _state.value
        when (p.optString("k")) {
            "hi" -> {
                if (st.peer != from) {
                    // not on this screen: an invitation
                    val f = Graph.db.friends().get(from)
                    Graph.notifier.duoInvite(from, f?.displayName ?: "", f?.emoji ?: "🙂")
                    return
                }
                val first = !st.here
                _state.value = st.copy(here = true, peerW = p.optDouble("w").toFloat(), peerH = p.optDouble("h").toFloat())
                if (first) send(JSONObject().put("k", "hi").put("w", myW.toDouble()).put("h", myH.toDouble()))
                match()
            }
            "bye" -> if (st.peer == from) _state.value = st.copy(here = false, layout = null, photo = null)
            "reset" -> if (st.peer == from) { mine = null; theirs = null; _state.value = st.copy(layout = null) }
            "edge" -> if (st.peer == from) {
                theirs = Edge(p.optBoolean("x"), p.optString("s").firstOrNull() ?: 'R', p.optDouble("y").toFloat(), now())
                match()
            }
            "photo" -> if (st.peer == from) _state.value = st.copy(photo = p.optString("id").ifBlank { null })
            "throw" -> if (st.peer == from) {
                val l = st.layout
                // their y, in my millimetres
                val y = p.optDouble("y").toFloat() + (l?.y ?: 0f)
                val t = Throw(p.optString("id"), true, y, p.optDouble("vy").toFloat(), p.optString("tb"), now())
                _state.value = st.copy(throws = (st.throws + t).takeLast(6))
            }
        }
    }
}
