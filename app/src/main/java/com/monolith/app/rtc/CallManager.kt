package com.monolith.app.rtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.strings
import com.monolith.app.core.t
import com.monolith.app.core.toJsonArray
import com.monolith.app.service.MonolithService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE, ENDED }

data class Participant(val pk: String, val connected: Boolean = false, val video: VideoTrack? = null, val camOn: Boolean = true)

data class CallUi(
    val phase: CallPhase = CallPhase.IDLE,
    val cid: String? = null,
    val caller: String? = null,
    /** everybody invited, me included */
    val members: List<String> = emptyList(),
    val group: String? = null,
    /** the other person in a one-to-one call */
    val peer: String? = null,
    val video: Boolean = false,
    val participants: Map<String, Participant> = emptyMap(),
    val localVideo: VideoTrack? = null,
    val since: Long = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val camOn: Boolean = false,
    val frontCam: Boolean = true,
    val reason: String? = null,
)

/**
 * One-to-one and small group calls (up to 4 people), audio or video.
 * Every pair of participants has its own direct connection (full mesh).
 */
class CallManager(private val ctx: Context, private val scope: CoroutineScope) {
    companion object {
        const val MAX_PEOPLE = 4
    }

    private val tag = "Calls"
    private val exec = Executors.newSingleThreadScheduledExecutor()
    private val _state = MutableStateFlow(CallUi())
    val state: StateFlow<CallUi> = _state.asStateFlow()

    private class Link(val pk: String, val pc: PeerConnection, val offerer: Boolean) {
        var sentLocal = false
        var drop: ScheduledFuture<*>? = null
    }

    private val links = HashMap<String, Link>()
    private val declined = HashSet<String>()
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var ringtone: Ringtone? = null
    private var timeout: ScheduledFuture<*>? = null
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val inCall: Boolean
        get() = _state.value.phase.let { it == CallPhase.OUTGOING || it == CallPhase.CONNECTING || it == CallPhase.ACTIVE || it == CallPhase.INCOMING }

    val videoCall: Boolean get() = inCall && _state.value.video

    private fun myPub() = Graph.identity.keys?.pub ?: ""
    private fun others() = _state.value.members.filter { it != myPub() }

    private fun send(to: String, type: String, cid: String? = _state.value.cid, extra: JSONObject.() -> Unit = {}) {
        val p = JSONObject().put("t", "call").put("type", type).put("cid", cid ?: "")
        p.extra()
        scope.launch { Graph.messenger.sendLive(to, p) }
    }

    // ================================================================== starting / answering

    /** [conv] is a friend pubkey or "g:<groupId>" */
    fun startCall(conv: String, video: Boolean) {
        if (inCall) return
        scope.launch {
            val me = myPub()
            val group = if (conv.startsWith("g:")) conv.removePrefix("g:") else null
            val members = if (group != null) {
                Graph.db.groups().get(group)?.memberList() ?: return@launch
            } else listOf(me, conv)
            if (members.size > MAX_PEOPLE) return@launch
            exec.execute { begin(members, group, video) }
        }
    }

    private fun begin(members: List<String>, group: String?, video: Boolean) {
        if (inCall) return
        val me = myPub()
        val cid = newId()
        declined.clear()
        setupLocalMedia(video)
        _state.value = CallUi(
            phase = CallPhase.OUTGOING, cid = cid, caller = me, members = members, group = group,
            peer = if (group == null) members.firstOrNull { it != me } else null,
            video = video, localVideo = videoTrack, camOn = videoTrack != null, speaker = video,
        )
        others().forEach { to ->
            send(to, "ring", cid) {
                put("v", video)
                put("m", members.toJsonArray())
                group?.let { put("g", it) }
            }
        }
        MonolithService.refresh(ctx)
        timeout = exec.schedule(Runnable {
            if (_state.value.cid == cid && _state.value.participants.values.none { it.connected }) {
                others().forEach { send(it, "leave", cid) }
                end(t("Nessuna risposta"))
            }
        }, 45, TimeUnit.SECONDS)
    }

    /**
     * Voice room of a shard: nobody rings. I'm in the room right away; whoever is already there
     * answers my "join" and connects to me.
     */
    fun joinRoom(cid: String, members: List<String>, groupId: String) {
        val task = object : Runnable {
            override fun run() {
                if (_state.value.phase == CallPhase.ENDED) {
                    // the previous call is still letting go of the microphone
                    exec.schedule(this, 700, TimeUnit.MILLISECONDS)
                    return
                }
                if (inCall) return
                declined.clear()
                setupLocalMedia(false)
                _state.value = CallUi(
                    phase = CallPhase.ACTIVE, cid = cid, caller = myPub(), members = members, group = groupId,
                    video = false, since = now(),
                )
                others().forEach { send(it, "join", cid) }
                MonolithService.refresh(ctx)
            }
        }
        exec.execute(task)
    }

    private fun isRoom(st: CallUi = _state.value) = st.cid?.startsWith("room.") == true

    fun accept() {
        exec.execute {
            val st = _state.value
            if (st.phase != CallPhase.INCOMING) return@execute
            timeout?.cancel(false)
            stopRinging()
            Graph.notifier.cancelCall()
            setupLocalMedia(st.video)
            _state.value = st.copy(phase = CallPhase.CONNECTING, localVideo = videoTrack, camOn = videoTrack != null, speaker = st.video)
            others().forEach { send(it, "join") }
            st.caller?.let { addParticipant(it); ensureLink(it) }
            MonolithService.refresh(ctx)
        }
    }

    fun decline() {
        exec.execute {
            val st = _state.value
            if (st.phase != CallPhase.INCOMING) return@execute
            st.caller?.let { send(it, "decline") }
            end(t("Chiamata rifiutata"), declined = true)
        }
    }

    fun hangup() {
        exec.execute {
            if (!inCall) return@execute
            if (_state.value.phase == CallPhase.INCOMING) {
                _state.value.caller?.let { send(it, "decline") }
                end(t("Chiamata rifiutata"), declined = true)
                return@execute
            }
            others().forEach { send(it, "leave") }
            end(t("Chiamata terminata"))
        }
    }

    // ================================================================== signaling

    fun onSignal(from: String, p: JSONObject) {
        exec.execute {
            val st = _state.value
            val cid = p.optString("cid")
            val type = p.optString("type")
            if (type == "ring") {
                onRing(from, p, cid)
                return@execute
            }
            if (!inCall || st.cid != cid) return@execute
            when (type) {
                "join" -> {
                    if (st.phase == CallPhase.INCOMING) return@execute
                    addParticipant(from)
                    send(from, "here")
                    ensureLink(from)
                    if (st.phase == CallPhase.OUTGOING) _state.update { it.copy(phase = CallPhase.CONNECTING) }
                }
                "here" -> {
                    if (st.phase == CallPhase.INCOMING) return@execute
                    addParticipant(from)
                    ensureLink(from)
                }
                "offer" -> {
                    if (st.phase == CallPhase.INCOMING) return@execute
                    addParticipant(from)
                    onOffer(from, p.optString("sdp"))
                }
                "answer" -> links[from]?.pc?.setRemoteDescription(SimpleSdp(), SessionDescription(SessionDescription.Type.ANSWER, p.optString("sdp")))
                "cam" -> _state.update { s ->
                    val part = s.participants[from] ?: return@update s
                    s.copy(participants = s.participants + (from to part.copy(camOn = p.optBoolean("on", true))))
                }
                "leave" -> {
                    if (st.phase == CallPhase.INCOMING && from == st.caller) {
                        end(t("Chiamata persa"), missed = true)
                        return@execute
                    }
                    closeLink(from)
                    _state.update { it.copy(participants = it.participants - from) }
                    checkEmpty()
                }
                "decline" -> {
                    declined += from
                    if (st.group == null) end(t("Chiamata rifiutata"))
                    else if (others().all { it in declined } ) end(t("Nessuna risposta"))
                }
                "busy" -> if (st.group == null) end(t("Occupato"))
            }
        }
    }

    private fun onRing(from: String, p: JSONObject, cid: String) {
        if (inCall) {
            if (_state.value.cid != cid) send(from, "busy", cid)
            return
        }
        val me = myPub()
        val members = (p.optJSONArray("m") ?: JSONArray()).strings()
        if (me !in members || from !in members || members.size > MAX_PEOPLE) return
        val group = p.optString("g").ifBlank { null }
        val video = p.optBoolean("v")
        declined.clear()
        _state.value = CallUi(
            phase = CallPhase.INCOMING, cid = cid, caller = from, members = members, group = group,
            peer = if (group == null) from else null, video = video,
        )
        startRinging()
        scope.launch {
            val f = Graph.db.friends().get(from)
            val g = group?.let { Graph.db.groups().get(it) }
            Graph.notifier.incomingCall(from, f?.displayName ?: t("Amico"), f?.emoji ?: "🙂", video, g?.name)
        }
        timeout = exec.schedule(Runnable {
            if (_state.value.phase == CallPhase.INCOMING && _state.value.cid == cid) end(t("Chiamata persa"), missed = true)
        }, 45, TimeUnit.SECONDS)
    }

    private fun addParticipant(pk: String) {
        _state.update { s -> if (s.participants.containsKey(pk)) s else s.copy(participants = s.participants + (pk to Participant(pk))) }
    }

    private fun checkEmpty() {
        val st = _state.value
        if (isRoom(st)) return   // a voice room stays open even when I'm alone in it
        if (links.isEmpty() && st.participants.isEmpty() && (st.phase == CallPhase.ACTIVE || st.phase == CallPhase.CONNECTING)) {
            end(t("Chiamata terminata"))
        }
    }

    // ================================================================== links

    private fun ensureLink(pk: String) {
        if (links.containsKey(pk)) return
        val link = createLink(pk, offerer = myPub() < pk) ?: return
        if (link.offerer) {
            link.pc.createOffer(SimpleSdp(onCreate = { sdp ->
                exec.execute { link.pc.setLocalDescription(SimpleSdp(onSet = { scheduleSend(link, "offer") }), sdp) }
            }), MediaConstraints())
        }
    }

    private fun onOffer(from: String, sdp: String) {
        val link = links[from] ?: createLink(from, offerer = false) ?: return
        link.pc.setRemoteDescription(SimpleSdp(onSet = {
            exec.execute {
                link.pc.createAnswer(SimpleSdp(onCreate = { ans ->
                    exec.execute { link.pc.setLocalDescription(SimpleSdp(onSet = { scheduleSend(link, "answer") }), ans) }
                }), MediaConstraints())
            }
        }), SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun createLink(pk: String, offerer: Boolean): Link? {
        return try {
            val factory = RtcFactory.get(ctx)
            var linkRef: Link? = null
            val pc = factory.createPeerConnection(RtcFactory.config(), object : PcObserver() {
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) exec.execute { linkRef?.let { sendLocal(it) } }
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        exec.execute {
                            _state.update { s ->
                                val part = s.participants[pk] ?: Participant(pk)
                                s.copy(participants = s.participants + (pk to part.copy(video = track)))
                            }
                        }
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    exec.execute { onLinkState(pk, newState) }
                }
            }) ?: return null
            audioTrack?.let { pc.addTrack(it, listOf("monolith")) }
            val vt = videoTrack
            if (vt != null) {
                pc.addTrack(vt, listOf("monolith"))
            } else if (_state.value.video) {
                pc.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
                )
            }
            val link = Link(pk, pc, offerer)
            linkRef = link
            links[pk] = link
            link
        } catch (e: Throwable) {
            Log.w(tag, "link failed", e)
            null
        }
    }

    private fun onLinkState(pk: String, s: PeerConnection.PeerConnectionState?) {
        val link = links[pk] ?: return
        when (s) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                link.drop?.cancel(false)
                timeout?.cancel(false)
                _state.update { st ->
                    val part = st.participants[pk] ?: Participant(pk)
                    st.copy(
                        participants = st.participants + (pk to part.copy(connected = true)),
                        phase = CallPhase.ACTIVE,
                        since = if (st.since == 0L) now() else st.since,
                    )
                }
            }
            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                link.drop?.cancel(false)
                link.drop = exec.schedule(Runnable {
                    closeLink(pk)
                    _state.update { it.copy(participants = it.participants - pk) }
                    checkEmpty()
                }, 10, TimeUnit.SECONDS)
            }
            PeerConnection.PeerConnectionState.FAILED -> {
                closeLink(pk)
                _state.update { it.copy(participants = it.participants - pk) }
                checkEmpty()
            }
            else -> {}
        }
    }

    private fun scheduleSend(link: Link, type: String) {
        exec.schedule(Runnable { sendLocal(link, type) }, 2500, TimeUnit.MILLISECONDS)
    }

    private fun sendLocal(link: Link, type: String? = null) {
        if (link.sentLocal || links[link.pk] !== link) return
        val desc = link.pc.localDescription ?: return
        link.sentLocal = true
        send(link.pk, type ?: desc.type.canonicalForm()) { put("sdp", desc.description) }
    }

    private fun closeLink(pk: String) {
        val l = links.remove(pk) ?: return
        l.drop?.cancel(false)
        runCatching { l.pc.dispose() }
    }

    // ================================================================== local media

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun setupLocalMedia(video: Boolean) {
        try {
            val factory = RtcFactory.get(ctx)
            if (audioTrack == null) {
                val s = factory.createAudioSource(MediaConstraints())
                audioSource = s
                audioTrack = factory.createAudioTrack("monolith-audio", s).also { it.setEnabled(true) }
            }
            if (video && videoTrack == null && granted(Manifest.permission.CAMERA)) {
                val enumerator = Camera2Enumerator(ctx)
                val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
                if (name != null) {
                    val cap = enumerator.createCapturer(name, null)
                    val helper = SurfaceTextureHelper.create("monolith-camera", RtcFactory.egl.eglBaseContext)
                    val src = factory.createVideoSource(false)
                    cap.initialize(helper, ctx, src.capturerObserver)
                    cap.startCapture(960, 540, 24)
                    capturer = cap
                    surfaceHelper = helper
                    videoSource = src
                    videoTrack = factory.createVideoTrack("monolith-video", src).also { it.setEnabled(true) }
                }
            }
            @Suppress("DEPRECATION")
            run {
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                audio.isSpeakerphoneOn = video
            }
        } catch (e: Throwable) {
            Log.w(tag, "media failed", e)
        }
    }

    private fun releaseLocalMedia() {
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { audioSource?.dispose() }
        capturer = null
        videoSource = null
        surfaceHelper = null
        videoTrack = null
        audioSource = null
        audioTrack = null
        @Suppress("DEPRECATION")
        runCatching {
            audio.isSpeakerphoneOn = false
            audio.mode = AudioManager.MODE_NORMAL
        }
    }

    fun toggleMute() {
        exec.execute {
            val m = !_state.value.muted
            audioTrack?.setEnabled(!m)
            _state.update { it.copy(muted = m) }
        }
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        exec.execute {
            val on = !_state.value.speaker
            audio.isSpeakerphoneOn = on
            _state.update { it.copy(speaker = on) }
        }
    }

    fun toggleCamera() {
        exec.execute {
            val track = videoTrack ?: return@execute
            val on = !_state.value.camOn
            track.setEnabled(on)
            if (on) runCatching { capturer?.startCapture(960, 540, 24) } else runCatching { capturer?.stopCapture() }
            _state.update { it.copy(camOn = on) }
            _state.value.participants.keys.forEach { send(it, "cam") { put("on", on) } }
        }
    }

    fun switchCamera() {
        exec.execute {
            val cap = capturer ?: return@execute
            runCatching { cap.switchCamera(null) }
            _state.update { it.copy(frontCam = !it.frontCam) }
        }
    }

    // ================================================================== ringing / ending

    private fun startRinging() = com.monolith.app.audio.Sounds.startRingtone()

    private fun stopRinging() = com.monolith.app.audio.Sounds.stopRingtone()

    private fun end(reason: String, missed: Boolean = false, declined: Boolean = false) {
        val st = _state.value
        if (st.phase == CallPhase.IDLE || st.phase == CallPhase.ENDED) return
        timeout?.cancel(false)
        stopRinging()
        Graph.notifier.cancelCall()
        val wasActive = st.phase == CallPhase.ACTIVE
        val iCalled = st.caller == myPub()
        // UI first lets go of the video tracks, then we free the native objects
        _state.value = st.copy(phase = CallPhase.ENDED, reason = reason, participants = emptyMap(), localVideo = null)
        exec.schedule(Runnable {
            links.keys.toList().forEach { closeLink(it) }
            releaseLocalMedia()
        }, 400, TimeUnit.MILLISECONDS)
        val conv = if (isRoom(st)) null else st.group?.let { "g:$it" } ?: st.peer
        if (conv != null) {
            scope.launch {
                val kind = if (st.video) t("Videochiamata") else t("Chiamata vocale")
                val text = when {
                    wasActive -> kind + " · " + Fmt.duration(now() - st.since)
                    missed -> t("Chiamata persa")
                    declined -> t("Chiamata rifiutata")
                    else -> reason
                }
                Graph.messenger.addCallLog(conv, text, iCalled)
                if (missed) {
                    val f = st.caller?.let { Graph.db.friends().get(it) }
                    Graph.notifier.missedCall(conv, f?.displayName ?: t("Amico"), f?.emoji ?: "🙂")
                }
            }
        }
        exec.schedule(Runnable {
            if (_state.value.phase == CallPhase.ENDED) _state.value = CallUi()
            MonolithService.refresh(ctx)
        }, 1800, TimeUnit.MILLISECONDS)
    }
}
