package com.monolith.app.rtc

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.monolith.app.Graph
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.service.MonolithService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE, ENDED }

data class CallUi(
    val phase: CallPhase = CallPhase.IDLE,
    val peer: String? = null,
    val since: Long = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val reason: String? = null,
)

/** Peer-to-peer voice calls over WebRTC (Opus audio, DTLS-SRTP), signaled through encrypted live relay events. */
class CallManager(private val ctx: Context, private val scope: CoroutineScope) {
    private val tag = "Calls"
    private val exec = Executors.newSingleThreadScheduledExecutor()
    private val _state = MutableStateFlow(CallUi())
    val state: StateFlow<CallUi> = _state.asStateFlow()

    private var pc: PeerConnection? = null
    private var source: AudioSource? = null
    private var track: AudioTrack? = null
    private var cid: String? = null
    private var pendingOffer: String? = null
    private var sentLocal = false
    private var ringtone: Ringtone? = null
    private var timeout: ScheduledFuture<*>? = null
    private var dropTimer: ScheduledFuture<*>? = null
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val inCall: Boolean
        get() = _state.value.phase.let { it == CallPhase.OUTGOING || it == CallPhase.CONNECTING || it == CallPhase.ACTIVE || it == CallPhase.INCOMING }

    private var iAmCaller = false

    private fun signal(to: String, type: String, callId: String? = cid, extra: JSONObject.() -> Unit = {}) {
        val p = JSONObject().put("t", "call").put("type", type).put("cid", callId ?: "")
        p.extra()
        scope.launch { Graph.messenger.sendLive(to, p) }
    }

    fun startCall(pk: String) {
        if (inCall) return
        cid = newId()
        sentLocal = false
        iAmCaller = true
        _state.value = CallUi(CallPhase.OUTGOING, pk)
        MonolithService.refresh(ctx)
        exec.execute {
            val c = setupPc(pk) ?: return@execute end("Impossibile avviare la chiamata")
            c.createOffer(SimpleSdp(onCreate = { sdp ->
                exec.execute { c.setLocalDescription(SimpleSdp(onSet = { scheduleSend(pk, "offer") }), sdp) }
            }, onError = { exec.execute { end("Errore audio") } }), MediaConstraints())
        }
        timeout = exec.schedule(Runnable {
            if (_state.value.phase == CallPhase.OUTGOING) {
                signal(pk, "end")
                end("Nessuna risposta")
            }
        }, 45, TimeUnit.SECONDS)
    }

    fun onSignal(from: String, p: JSONObject) {
        exec.execute {
            val st = _state.value
            val callId = p.optString("cid")
            when (p.optString("type")) {
                "offer" -> {
                    if (inCall) {
                        if (st.peer == from && callId == cid) return@execute
                        signal(from, "busy", callId)
                        return@execute
                    }
                    cid = callId
                    iAmCaller = false
                    pendingOffer = p.optString("sdp")
                    sentLocal = false
                    _state.value = CallUi(CallPhase.INCOMING, from)
                    startRinging()
                    scope.launch {
                        val f = Graph.db.friends().get(from)
                        Graph.notifier.incomingCall(from, f?.displayName ?: "Amico", f?.emoji ?: "🙂")
                    }
                    timeout = exec.schedule(Runnable {
                        if (_state.value.phase == CallPhase.INCOMING) end("Chiamata persa", missed = true)
                    }, 45, TimeUnit.SECONDS)
                }
                "answer" -> {
                    if (st.peer != from || callId != cid || st.phase != CallPhase.OUTGOING) return@execute
                    timeout?.cancel(false)
                    _state.update { it.copy(phase = CallPhase.CONNECTING) }
                    pc?.setRemoteDescription(SimpleSdp(), SessionDescription(SessionDescription.Type.ANSWER, p.optString("sdp")))
                }
                "decline" -> if (st.peer == from && callId == cid) end("Rifiutata")
                "busy" -> if (st.peer == from && callId == cid) end("Occupato")
                "end" -> if (st.peer == from && callId == cid) {
                    end(if (st.phase == CallPhase.INCOMING) "Chiamata persa" else "Chiamata terminata", missed = st.phase == CallPhase.INCOMING)
                }
            }
        }
    }

    fun accept() {
        val st = _state.value
        val from = st.peer ?: return
        if (st.phase != CallPhase.INCOMING) return
        timeout?.cancel(false)
        stopRinging()
        Graph.notifier.cancelCall()
        _state.value = st.copy(phase = CallPhase.CONNECTING)
        MonolithService.refresh(ctx)
        exec.execute {
            val c = setupPc(from) ?: return@execute end("Impossibile rispondere")
            val offer = SessionDescription(SessionDescription.Type.OFFER, pendingOffer ?: "")
            c.setRemoteDescription(SimpleSdp(onSet = {
                exec.execute {
                    c.createAnswer(SimpleSdp(onCreate = { ans ->
                        exec.execute { c.setLocalDescription(SimpleSdp(onSet = { scheduleSend(from, "answer") }), ans) }
                    }), MediaConstraints())
                }
            }, onError = { exec.execute { end("Errore di connessione") } }), offer)
        }
    }

    fun decline() {
        val st = _state.value
        val from = st.peer ?: return
        signal(from, "decline")
        end("Rifiutata", missed = false, declined = true)
    }

    fun hangup() {
        val st = _state.value
        st.peer?.let { signal(it, "end") }
        end("Chiamata terminata")
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val on = !_state.value.speaker
        audio.isSpeakerphoneOn = on
        _state.update { it.copy(speaker = on) }
    }

    fun toggleMute() {
        val m = !_state.value.muted
        track?.setEnabled(!m)
        _state.update { it.copy(muted = m) }
    }

    private fun setupPc(remote: String): PeerConnection? {
        return try {
            val factory = RtcFactory.get(ctx)
            val c = factory.createPeerConnection(RtcFactory.config(), object : PcObserver() {
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) {
                        exec.execute { sendLocal(remote) }
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    exec.execute {
                        when (newState) {
                            PeerConnection.PeerConnectionState.CONNECTED -> {
                                dropTimer?.cancel(false)
                                if (_state.value.phase != CallPhase.ACTIVE) {
                                    _state.update { it.copy(phase = CallPhase.ACTIVE, since = now()) }
                                }
                            }
                            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                                dropTimer?.cancel(false)
                                dropTimer = exec.schedule(Runnable { end("Connessione persa") }, 8, TimeUnit.SECONDS)
                            }
                            PeerConnection.PeerConnectionState.FAILED -> end("Connessione non riuscita")
                            else -> {}
                        }
                    }
                }
            }) ?: return null
            pc = c
            val s = factory.createAudioSource(MediaConstraints())
            val t = factory.createAudioTrack("monolith-audio", s)
            t.setEnabled(true)
            c.addTrack(t, listOf("monolith"))
            source = s
            track = t
            @Suppress("DEPRECATION")
            run {
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                audio.isSpeakerphoneOn = false
            }
            c
        } catch (e: Throwable) {
            Log.w(tag, "setup failed", e)
            null
        }
    }

    private fun scheduleSend(remote: String, type: String) {
        exec.schedule(Runnable { sendLocal(remote, type) }, 2500, TimeUnit.MILLISECONDS)
    }

    private fun sendLocal(remote: String, type: String? = null) {
        if (sentLocal) return
        val desc = pc?.localDescription ?: return
        sentLocal = true
        val t = type ?: desc.type.canonicalForm()
        signal(remote, t, cid) { put("sdp", desc.description) }
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(ctx, uri)?.apply {
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun end(reason: String, missed: Boolean = false, declined: Boolean = false) {
        val st = _state.value
        if (st.phase == CallPhase.IDLE || st.phase == CallPhase.ENDED) return
        timeout?.cancel(false)
        dropTimer?.cancel(false)
        stopRinging()
        Graph.notifier.cancelCall()
        val c = pc
        pc = null
        runCatching { c?.dispose() }
        runCatching { source?.dispose() }
        source = null
        track = null
        @Suppress("DEPRECATION")
        runCatching {
            audio.isSpeakerphoneOn = false
            audio.mode = AudioManager.MODE_NORMAL
        }
        val peer = st.peer
        val wasActive = st.phase == CallPhase.ACTIVE
        val outgoing = iAmCaller
        _state.value = st.copy(phase = CallPhase.ENDED, reason = reason)
        if (peer != null) {
            scope.launch {
                val text = when {
                    wasActive -> "Chiamata vocale · " + duration(now() - st.since)
                    missed -> "Chiamata persa"
                    declined -> "Chiamata rifiutata"
                    else -> reason
                }
                Graph.messenger.addCallLog(peer, text, outgoing)
                if (missed) {
                    val f = Graph.db.friends().get(peer)
                    Graph.notifier.missedCall(peer, f?.displayName ?: "Amico", f?.emoji ?: "🙂")
                }
            }
        }
        pendingOffer = null
        cid = null
        exec.schedule(Runnable {
            if (_state.value.phase == CallPhase.ENDED) _state.value = CallUi()
            MonolithService.refresh(ctx)
        }, 1600, TimeUnit.MILLISECONDS)
    }

    private fun duration(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
