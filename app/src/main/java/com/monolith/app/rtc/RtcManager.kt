package com.monolith.app.rtc

import android.content.Context
import android.util.Log
import com.monolith.app.Graph
import com.monolith.app.core.newId
import com.monolith.app.core.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** SdpObserver with lambdas */
open class SimpleSdp(
    private val onCreate: (SessionDescription) -> Unit = {},
    private val onSet: () -> Unit = {},
    private val onError: (String) -> Unit = {},
) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) { if (sdp != null) onCreate(sdp) }
    override fun onSetSuccess() = onSet()
    override fun onCreateFailure(error: String?) = onError(error ?: "create failed")
    override fun onSetFailure(error: String?) = onError(error ?: "set failed")
}

/** PeerConnection.Observer with no-op defaults */
open class PcObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(channel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
}

object RtcFactory {
    @Volatile
    private var factory: PeerConnectionFactory? = null

    val egl: EglBase by lazy { EglBase.create() }

    @Synchronized
    fun get(ctx: Context): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(ctx.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    fun config(): PeerConnection.RTCConfiguration {
        val prefs = Graph.prefs
        val servers = com.monolith.app.core.Prefs.DEFAULT_STUN.map {
            PeerConnection.IceServer.builder(it).createIceServer()
        }.toMutableList()
        if (prefs.turnUrl.isNotBlank()) {
            servers.add(
                PeerConnection.IceServer.builder(prefs.turnUrl.trim())
                    .setUsername(prefs.turnUser)
                    .setPassword(prefs.turnPass)
                    .createIceServer()
            )
        }
        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
    }
}

/**
 * Direct peer-to-peer data channels between friends.
 * Signaling (offer/answer with the gathered ICE candidates) travels inside encrypted live relay events,
 * after that all traffic (live location, photos, typing) flows phone-to-phone over DTLS/SCTP.
 */
class RtcManager(
    private val ctx: Context,
    private val scope: CoroutineScope,
) {
    private val tag = "Rtc"
    private val exec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val peers = ConcurrentHashMap<String, Peer>()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    /** extra conditions that keep an idle channel open (live location, open chat) */
    var keepAlive: (String) -> Boolean = { false }

    private inner class Peer(val remote: String, val sid: String, val initiator: Boolean) {
        var pc: PeerConnection? = null
        var dc: DataChannel? = null
        var sentLocal = false
        val createdAt = now()
        var lastUsed = now()
    }

    init {
        exec.scheduleWithFixedDelay({ sweep() }, 60, 60, TimeUnit.SECONDS)
    }

    private fun myPub(): String = Graph.identity.keys?.pub ?: ""

    private fun isOpen(p: Peer?) = p?.dc?.state() == DataChannel.State.OPEN

    fun isConnected(pk: String): Boolean = isOpen(peers[pk])

    private fun updateConnected() {
        _connected.value = peers.values.filter { isOpen(it) }.map { it.remote }.toSet()
    }

    fun ensureConnected(pk: String) {
        if (pk == myPub()) return
        exec.execute {
            val existing = peers[pk]
            if (existing != null) {
                existing.lastUsed = now()
                if (isOpen(existing) || now() - existing.createdAt < 20_000) return@execute
                closePeer(existing)
            }
            startOffer(pk)
        }
    }

    private fun createPc(peer: Peer): PeerConnection? = try {
        RtcFactory.get(ctx).createPeerConnection(RtcFactory.config(), object : PcObserver() {
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) exec.execute { sendLocal(peer) }
            }

            override fun onDataChannel(channel: DataChannel?) {
                if (channel != null) exec.execute { attach(peer, channel) }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState == PeerConnection.PeerConnectionState.FAILED || newState == PeerConnection.PeerConnectionState.CLOSED) {
                    exec.execute { if (peers[peer.remote] === peer) closePeer(peer) }
                }
                exec.execute { updateConnected() }
            }
        })
    } catch (e: Throwable) {
        Log.w(tag, "pc failed", e)
        null
    }

    private fun startOffer(pk: String) {
        val peer = Peer(pk, newId(), true)
        peers[pk] = peer
        val pc = createPc(peer) ?: run { peers.remove(pk); return }
        peer.pc = pc
        val dc = pc.createDataChannel("monolith", DataChannel.Init().apply { ordered = true })
        attach(peer, dc)
        pc.createOffer(SimpleSdp(onCreate = { sdp ->
            exec.execute {
                pc.setLocalDescription(SimpleSdp(onSet = { scheduleSend(peer) }), sdp)
            }
        }), MediaConstraints())
    }

    private fun scheduleSend(peer: Peer) {
        exec.schedule(Runnable { sendLocal(peer) }, 2500, TimeUnit.MILLISECONDS)
    }

    private fun sendLocal(peer: Peer) {
        if (peer.sentLocal || peers[peer.remote] !== peer) return
        val desc = peer.pc?.localDescription ?: return
        peer.sentLocal = true
        val payload = JSONObject().put("t", "rtc").put("sid", peer.sid)
            .put("type", desc.type.canonicalForm()).put("sdp", desc.description)
        scope.launch { Graph.messenger.sendLive(peer.remote, payload) }
    }

    fun onSignal(from: String, p: JSONObject) {
        exec.execute {
            when (p.optString("type")) {
                "offer" -> {
                    val existing = peers[from]
                    if (existing != null && existing.initiator && !isOpen(existing) && myPub() < from) {
                        return@execute // glare: the smaller pubkey keeps its own offer
                    }
                    existing?.let { closePeer(it) }
                    val peer = Peer(from, p.optString("sid"), false)
                    peers[from] = peer
                    val pc = createPc(peer) ?: run { peers.remove(from); return@execute }
                    peer.pc = pc
                    val offer = SessionDescription(SessionDescription.Type.OFFER, p.optString("sdp"))
                    pc.setRemoteDescription(SimpleSdp(onSet = {
                        exec.execute {
                            pc.createAnswer(SimpleSdp(onCreate = { answer ->
                                exec.execute { pc.setLocalDescription(SimpleSdp(onSet = { scheduleSend(peer) }), answer) }
                            }), MediaConstraints())
                        }
                    }), offer)
                }
                "answer" -> {
                    val peer = peers[from] ?: return@execute
                    if (!peer.initiator || peer.sid != p.optString("sid")) return@execute
                    peer.pc?.setRemoteDescription(SimpleSdp(), SessionDescription(SessionDescription.Type.ANSWER, p.optString("sdp")))
                }
                "bye" -> peers[from]?.let { closePeer(it) }
            }
        }
    }

    private fun attach(peer: Peer, dc: DataChannel) {
        peer.dc = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                exec.execute { updateConnected() }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val text = String(bytes, Charsets.UTF_8)
                peer.lastUsed = now()
                scope.launch {
                    runCatching {
                        Graph.messenger.handlePayload(peer.remote, JSONObject(text), now(), viaRtc = true)
                    }.onFailure { Log.w(tag, "bad dc payload", it) }
                }
            }
        })
    }

    /** sends a payload on the direct channel; false if there is no open channel */
    fun send(pk: String, payload: JSONObject): Boolean {
        val peer = peers[pk] ?: return false
        val dc = peer.dc ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        if (dc.bufferedAmount() > 8L * 1024 * 1024) return false
        peer.lastUsed = now()
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        return try {
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
        } catch (e: Throwable) {
            false
        }
    }

    fun close(pk: String) {
        exec.execute { peers[pk]?.let { closePeer(it) } }
    }

    private fun closePeer(peer: Peer) {
        peers.remove(peer.remote, peer)
        val dc = peer.dc
        val pc = peer.pc
        peer.dc = null
        peer.pc = null
        try {
            dc?.unregisterObserver()
            dc?.close()
            dc?.dispose()
        } catch (_: Throwable) {
        }
        try {
            pc?.dispose()
        } catch (_: Throwable) {
        }
        updateConnected()
    }

    private fun sweep() {
        val t = now()
        peers.values.toList().forEach { p ->
            val idle = t - p.lastUsed > 5 * 60_000
            val stuck = !isOpen(p) && t - p.createdAt > 45_000
            if (stuck || (idle && !keepAlive(p.remote))) closePeer(p)
        }
    }
}
