package com.monolith.app.net

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.rtc.CallPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** the song everyone in the call is hearing */
data class SharedTrack(val title: String, val artist: String, val art: String, val url: String, val startAt: Long, val by: String)

/**
 * Listen together: during a call (or in a voice room) someone picks a song preview; everyone
 * starts it at the same moment, set a second and a half in the future.
 */
object ListenTogether {
    private val _track = MutableStateFlow<SharedTrack?>(null)
    val track: StateFlow<SharedTrack?> = _track.asStateFlow()
    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())
    private var watching = false

    private fun watchCall() {
        if (watching) return
        watching = true
        Graph.scope.launch {
            Graph.calls.state.collect { st -> if (st.phase == CallPhase.IDLE || st.phase == CallPhase.ENDED) stopLocal() }
        }
    }

    fun start(title: String, artist: String, art: String, url: String) {
        val me = Graph.identity.keys?.pub ?: return
        val t = SharedTrack(title, artist, art, url, now() + 1500, me)
        Graph.calls.broadcastSignal("listen") {
            put("ti", title); put("ar", artist); put("art", art); put("url", url); put("at", t.startAt)
        }
        play(t)
    }

    fun stop() {
        Graph.calls.broadcastSignal("listenstop") {}
        stopLocal()
    }

    fun onSignal(from: String, type: String, p: JSONObject) {
        main.post {
            if (type == "listenstop") { stopLocal(); return@post }
            val url = p.optString("url")
            if (!url.startsWith("https://")) return@post
            play(SharedTrack(p.optString("ti").take(80), p.optString("ar").take(80), p.optString("art"), url, p.optLong("at", now()), from))
        }
    }

    private fun play(t: SharedTrack) {
        watchCall()
        stopLocal()
        _track.value = t
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(t.url)
                setVolume(0.6f, 0.6f)
                setOnCompletionListener { main.post { if (_track.value == t) stopLocal() } }
                setOnPreparedListener { pl ->
                    val wait = t.startAt - now()
                    if (wait > 0) main.postDelayed({ runCatching { if (player === pl) pl.start() } }, wait)
                    else {
                        val late = (-wait).toInt()
                        if (late < pl.duration) { runCatching { pl.seekTo(late); pl.start() } }
                    }
                }
                prepareAsync()
            }
        }.getOrNull()
        player = mp
    }

    private fun stopLocal() {
        main.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        _track.value = null
    }
}
