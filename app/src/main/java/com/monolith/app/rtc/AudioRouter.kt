package com.monolith.app.rtc

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.monolith.app.core.t
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RouteKind { EARPIECE, SPEAKER, WIRED, BLUETOOTH, USB, HEARING;
    val isHeadset get() = this == WIRED || this == BLUETOOTH || this == USB || this == HEARING
}

/** where the call is heard (or, for microphones, where it is spoken into) */
data class AudioRoute(val key: String, val kind: RouteKind, val name: String)

/**
 * Chooses the speaker and the microphone of calls and voice rooms: phone earpiece, loudspeaker,
 * wired or USB headphones, Bluetooth headsets. Headphones that connect during a call take the audio
 * right away; when they leave, the call goes back to the phone.
 */
class AudioRouter(private val ctx: Context) {
    private val tag = "AudioRouter"
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _outputs = MutableStateFlow<List<AudioRoute>>(emptyList())
    val outputs: StateFlow<List<AudioRoute>> = _outputs.asStateFlow()
    private val _output = MutableStateFlow<AudioRoute?>(null)
    val output: StateFlow<AudioRoute?> = _output.asStateFlow()
    private val _mics = MutableStateFlow<List<AudioRoute>>(emptyList())
    val mics: StateFlow<List<AudioRoute>> = _mics.asStateFlow()
    /** null: the microphone that goes with the chosen output */
    private val _mic = MutableStateFlow<AudioRoute?>(null)
    val mic: StateFlow<AudioRoute?> = _mic.asStateFlow()
    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** short messages for the call screen ("audio in the headphones…") */
    val notice: SharedFlow<String> = _notice.asSharedFlow()

    @Volatile private var active = false
    private var video = false
    private var legacyScoOn = false

    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = later()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = later()
    }
    private var commListener: Any? = null
    private val refreshTask = Runnable { refresh(announce = true) }

    // ================================================================== lifecycle

    /** called when the call's microphone opens; [video] calls start on the loudspeaker */
    fun start(video: Boolean) {
        main.post {
            if (active) return@post
            active = true
            this.video = video
            @Suppress("DEPRECATION")
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.registerAudioDeviceCallback(deviceCb, main)
            if (Build.VERSION.SDK_INT >= 31) {
                val l = AudioManager.OnCommunicationDeviceChangedListener { dev -> if (active) _output.value = dev?.let { toRoute(it) } ?: _output.value }
                am.addOnCommunicationDeviceChangedListener(ctx.mainExecutor, l)
                commListener = l
            }
            refresh(announce = false)
            select(bestDefault())
        }
    }

    fun stop() {
        main.post {
            if (!active) return@post
            active = false
            main.removeCallbacks(refreshTask)
            runCatching { am.unregisterAudioDeviceCallback(deviceCb) }
            if (Build.VERSION.SDK_INT >= 31) {
                (commListener as? AudioManager.OnCommunicationDeviceChangedListener)?.let { runCatching { am.removeOnCommunicationDeviceChangedListener(it) } }
                commListener = null
                runCatching { am.clearCommunicationDevice() }
            }
            @Suppress("DEPRECATION")
            runCatching {
                if (legacyScoOn) { am.stopBluetoothSco(); am.isBluetoothScoOn = false }
                legacyScoOn = false
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
            }
            runCatching { RtcFactory.preferInput(null) }
            _output.value = null
            _mic.value = null
            _outputs.value = emptyList()
            _mics.value = emptyList()
        }
    }

    /** the call turned into a video call: from the earpiece to the loudspeaker */
    fun onVideo() {
        main.post {
            video = true
            if (active && _output.value?.kind == RouteKind.EARPIECE) _outputs.value.firstOrNull { it.kind == RouteKind.SPEAKER }?.let { select(it) }
        }
    }

    // ================================================================== choosing

    fun select(route: AudioRoute?) {
        if (route == null) return
        main.post {
            if (!active) return@post
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    val dev = am.availableCommunicationDevices.firstOrNull { toRoute(it)?.key == route.key }
                    if (dev != null) am.setCommunicationDevice(dev)
                } else legacySelect(route)
                _output.value = route
                // a microphone chosen by hand that belongs to headphones no longer in use goes back to automatic
                if (_mic.value != null && _mics.value.none { it.key == _mic.value?.key }) selectMic(null)
            } catch (e: Throwable) {
                Log.w(tag, "route failed", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySelect(route: AudioRoute) {
        if (route.kind != RouteKind.BLUETOOTH && legacyScoOn) {
            am.stopBluetoothSco(); am.isBluetoothScoOn = false; legacyScoOn = false
        }
        when (route.kind) {
            RouteKind.SPEAKER -> am.isSpeakerphoneOn = true
            RouteKind.BLUETOOTH -> {
                am.isSpeakerphoneOn = false
                am.startBluetoothSco(); am.isBluetoothScoOn = true; legacyScoOn = true
            }
            else -> am.isSpeakerphoneOn = false
        }
    }

    /** loudspeaker on or off: off goes to the headphones if there are any, else to the earpiece */
    fun toggleSpeaker() {
        val list = _outputs.value
        if (_output.value?.kind == RouteKind.SPEAKER) select(list.firstOrNull { it.kind.isHeadset } ?: list.firstOrNull { it.kind == RouteKind.EARPIECE })
        else select(list.firstOrNull { it.kind == RouteKind.SPEAKER })
    }

    fun selectMic(route: AudioRoute?) {
        main.post {
            val dev = route?.let { r -> inputs().firstOrNull { micRoute(it)?.key == r.key } }
            runCatching { RtcFactory.preferInput(dev) }.onFailure { Log.w(tag, "mic failed", it) }
            _mic.value = if (dev == null) null else route
        }
    }

    private fun bestDefault(): AudioRoute? {
        val list = _outputs.value
        return list.firstOrNull { it.kind == RouteKind.BLUETOOTH }
            ?: list.firstOrNull { it.kind == RouteKind.WIRED }
            ?: list.firstOrNull { it.kind == RouteKind.USB }
            ?: list.firstOrNull { it.kind == RouteKind.HEARING }
            ?: if (video) list.firstOrNull { it.kind == RouteKind.SPEAKER } else list.firstOrNull { it.kind == RouteKind.EARPIECE }
            ?: list.firstOrNull { it.kind == RouteKind.SPEAKER }
    }

    // ================================================================== devices

    private fun later() {
        // Bluetooth headsets show up for calls a moment after they connect
        main.removeCallbacks(refreshTask)
        main.postDelayed(refreshTask, 700)
    }

    private fun refresh(announce: Boolean) {
        if (!active) return
        val before = _outputs.value
        val now = currentOutputs()
        _outputs.value = now
        _mics.value = currentMics()
        if (!announce) return
        val arrived = now.filter { r -> r.kind.isHeadset && before.none { it.key == r.key } }
        val cur = _output.value
        when {
            arrived.isNotEmpty() -> {
                val r = arrived.first()
                select(r)
                _notice.tryEmit(t("Audio in {0}", r.name))
            }
            cur != null && now.none { it.key == cur.key } -> {
                val r = bestDefault()
                select(r)
                if (r != null) _notice.tryEmit(t("{0} scollegate: audio su {1}", cur.name, r.name))
            }
        }
    }

    private fun currentOutputs(): List<AudioRoute> {
        val devs = if (Build.VERSION.SDK_INT >= 31) am.availableCommunicationDevices
        else am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val list = devs.mapNotNull { toRoute(it) }.distinctBy { it.key }.toMutableList()
        if (Build.VERSION.SDK_INT < 31 && list.none { it.kind == RouteKind.SPEAKER }) list += AudioRoute("SPEAKER", RouteKind.SPEAKER, t("Vivavoce"))
        return list.sortedBy { order(it.kind) }
    }

    private fun inputs(): List<AudioDeviceInfo> = am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()

    private fun currentMics(): List<AudioRoute> = inputs().mapNotNull { micRoute(it) }.distinctBy { it.key }.sortedBy { order(it.kind) }

    private fun order(k: RouteKind) = when (k) {
        RouteKind.BLUETOOTH -> 0
        RouteKind.WIRED -> 1
        RouteKind.USB -> 2
        RouteKind.HEARING -> 3
        RouteKind.EARPIECE -> 4
        RouteKind.SPEAKER -> 5
    }

    private fun product(d: AudioDeviceInfo): String = d.productName?.toString()?.trim().orEmpty()

    private fun toRoute(d: AudioDeviceInfo): AudioRoute? = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute("EARPIECE", RouteKind.EARPIECE, t("Telefono"))
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute("SPEAKER", RouteKind.SPEAKER, t("Vivavoce"))
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioRoute("WIRED", RouteKind.WIRED, t("Cuffie con filo"))
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
            AudioRoute("USB:" + product(d), RouteKind.USB, product(d).ifBlank { t("Cuffie USB") })
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER ->
            AudioRoute("BT:" + product(d), RouteKind.BLUETOOTH, product(d).ifBlank { t("Cuffie Bluetooth") })
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioRoute("HEARING", RouteKind.HEARING, t("Apparecchio acustico"))
        else -> null
    }

    private fun micRoute(d: AudioDeviceInfo): AudioRoute? = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> AudioRoute("MIC", RouteKind.EARPIECE, t("Microfono del telefono"))
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioRoute("WIRED", RouteKind.WIRED, t("Microfono delle cuffie"))
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
            AudioRoute("USB:" + product(d), RouteKind.USB, product(d).ifBlank { t("Microfono USB") })
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET ->
            AudioRoute("BT:" + product(d), RouteKind.BLUETOOTH, t("Microfono di {0}", product(d).ifBlank { t("Cuffie Bluetooth") }))
        else -> null
    }

    /** for the screenshots: pretend to be in a call with these devices */
    internal fun preview(outputs: List<AudioRoute>, output: AudioRoute?, mics: List<AudioRoute>) {
        _outputs.value = outputs; _output.value = output; _mics.value = mics
    }
}
