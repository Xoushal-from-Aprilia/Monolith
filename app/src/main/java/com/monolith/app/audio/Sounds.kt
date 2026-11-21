package com.monolith.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.monolith.app.Graph
import com.monolith.app.R
import com.monolith.app.rtc.CallPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** one sound of the kit; [name] is an Italian key, translated with t() */
data class SoundDef(val id: String, val res: Int, val name: String)

enum class SoundKind { MESSAGE, MEETING, REMINDER }

/** out of the box Monolith is quiet: phone sounds, no key taps, no call tones */
data class SoundSettings(
    val taps: Boolean = false,
    val tap: String = "tap_goccia",
    val ringtone: String = Sounds.SYSTEM,
    val message: String = Sounds.SYSTEM,
    val meeting: String = Sounds.SYSTEM,
    val reminder: String = Sounds.SYSTEM,
    val callTones: Boolean = false,
    /** a tiny vibration when a key is pressed (on by default) */
    val haptics: Boolean = true,
)

/**
 * Monolith's sound kit: calm sounds made for the app, a few kilobytes each.
 * Key taps, ringtone, call tones and the sounds of the notification channels.
 */
object Sounds {
    const val SYSTEM = "system"
    const val NONE = "none"

    val TAPS = listOf(
        SoundDef("tap_goccia", R.raw.tap_goccia, "Goccia"),
        SoundDef("tap_legno", R.raw.tap_legno, "Legno"),
        SoundDef("tap_vetro", R.raw.tap_vetro, "Vetro"),
        SoundDef("tap_soffio", R.raw.tap_soffio, "Soffio"),
    )
    val RINGTONES = listOf(
        SoundDef("ring_alba", R.raw.ring_alba, "Alba"),
        SoundDef("ring_ciotola", R.raw.ring_ciotola, "Ciotola"),
        SoundDef("ring_risacca", R.raw.ring_risacca, "Risacca"),
        SoundDef("ring_carillon", R.raw.ring_carillon, "Carillon"),
        SoundDef("ring_kalimba", R.raw.ring_kalimba, "Kalimba"),
    )
    val MESSAGES = listOf(
        SoundDef("msg_goccia", R.raw.msg_goccia, "Goccia"),
        SoundDef("msg_bolla", R.raw.msg_bolla, "Bolla"),
        SoundDef("msg_campanella", R.raw.msg_campanella, "Campanella"),
        SoundDef("msg_legno", R.raw.msg_legno, "Legno"),
        SoundDef("msg_piuma", R.raw.msg_piuma, "Piuma"),
    )
    val MEETINGS = listOf(
        SoundDef("inc_ritrovo", R.raw.inc_ritrovo, "Ritrovo"),
        SoundDef("inc_benvenuto", R.raw.inc_benvenuto, "Benvenuto"),
        SoundDef("inc_faro", R.raw.inc_faro, "Faro"),
        SoundDef("inc_onda", R.raw.inc_onda, "Onda"),
    )
    val REMINDERS = listOf(
        SoundDef("rem_mattino", R.raw.rem_mattino, "Mattino"),
        SoundDef("rem_carillon", R.raw.rem_carillon, "Carillon"),
        SoundDef("rem_gong", R.raw.rem_gong, "Gong"),
        SoundDef("rem_chiaro", R.raw.rem_chiaro, "Chiaro"),
    )
    private val ALL = TAPS + RINGTONES + MESSAGES + MEETINGS + REMINDERS

    private lateinit var ctx: Context
    private val sp by lazy { ctx.getSharedPreferences("sounds", Context.MODE_PRIVATE) }
    private val _settings = MutableStateFlow(SoundSettings())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private var pool: SoundPool? = null
    private val tapIds = HashMap<String, Int>()
    private var lastTap = 0L
    private var preview: MediaPlayer? = null
    private var ringPlayer: MediaPlayer? = null
    private var systemRing: Ringtone? = null
    private var ringback: MediaPlayer? = null

    fun init(c: Context) {
        ctx = c.applicationContext
        val d = SoundSettings()
        _settings.value = SoundSettings(
            taps = sp.getBoolean("taps", d.taps),
            tap = sp.getString("tap", d.tap) ?: d.tap,
            ringtone = sp.getString("ring", d.ringtone) ?: d.ringtone,
            message = sp.getString("msg", d.message) ?: d.message,
            meeting = sp.getString("meet", d.meeting) ?: d.meeting,
            reminder = sp.getString("rem", d.reminder) ?: d.reminder,
            callTones = sp.getBoolean("calltones", d.callTones),
            haptics = sp.getBoolean("haptics", d.haptics),
        )
        runCatching {
            pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
            TAPS.forEach { s -> pool?.load(ctx, s.res, 1)?.let { tapIds[s.id] = it } }
        }
    }

    /** follow the calls: soft tone while the other side rings, chimes on connect and hang-up */
    fun watchCalls() {
        Graph.scope.launch {
            var prev = CallPhase.IDLE
            Graph.calls.state.collect { st ->
                val ph = st.phase
                if (ph != prev) {
                    if (prev == CallPhase.OUTGOING) stopRingback()
                    if (!_settings.value.callTones) { prev = ph; return@collect }
                    if (ph == CallPhase.OUTGOING) startRingback()
                    if (ph == CallPhase.ACTIVE && prev != CallPhase.ACTIVE) oneShot(R.raw.call_connesso, signalling = true)
                    if (ph == CallPhase.ENDED && (prev == CallPhase.ACTIVE || prev == CallPhase.CONNECTING || prev == CallPhase.OUTGOING)) {
                        oneShot(R.raw.call_fine, signalling = true)
                    }
                    prev = ph
                }
            }
        }
    }

    fun update(f: (SoundSettings) -> SoundSettings) {
        val old = _settings.value
        val s = f(old)
        _settings.value = s
        sp.edit()
            .putBoolean("taps", s.taps).putString("tap", s.tap).putString("ring", s.ringtone)
            .putString("msg", s.message).putString("meet", s.meeting).putString("rem", s.reminder)
            .putBoolean("calltones", s.callTones)
            .putBoolean("haptics", s.haptics)
            .apply()
        // notification channels carry their sound: a new choice means a new channel
        if (s.message != old.message || s.meeting != old.meeting || s.reminder != old.reminder) {
            runCatching { Graph.notifier.createChannels() }
        }
    }

    fun find(id: String): SoundDef? = ALL.firstOrNull { it.id == id }

    fun uri(id: String): Uri? = find(id)?.let { Uri.parse("android.resource://${ctx.packageName}/${it.res}") }

    // ------------------------------------------------------------------ notification channels

    fun soundFor(kind: SoundKind): String = when (kind) {
        SoundKind.MESSAGE -> _settings.value.message
        SoundKind.MEETING -> _settings.value.meeting
        SoundKind.REMINDER -> _settings.value.reminder
    }

    fun channelId(kind: SoundKind): String = prefix(kind) + soundFor(kind)

    fun prefix(kind: SoundKind): String = when (kind) {
        SoundKind.MESSAGE -> "msg2_"
        SoundKind.MEETING -> "meet2_"
        SoundKind.REMINDER -> "rem2_"
    }

    // ------------------------------------------------------------------ taps

    private fun ringerNormal(): Boolean =
        runCatching { ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL }.getOrDefault(true)

    /** a very light tick when something is pressed: a short vibration and, if chosen, a sound */
    fun tap() {
        val s = _settings.value
        if ((!s.taps && !s.haptics) || !::ctx.isInitialized) return
        val t = SystemClock.uptimeMillis()
        if (t - lastTap < 45) return
        lastTap = t
        if (s.haptics) tick()
        if (!s.taps || !ringerNormal()) return
        val id = tapIds[s.tap] ?: return
        runCatching { pool?.play(id, 0.6f, 0.6f, 1, 0, 1f) }
    }

    private var tapVibrator: Vibrator? = null

    /** the lightest vibration the phone can do, like the keyboard */
    fun tick() {
        if (!::ctx.isInitialized) return
        runCatching {
            val v = tapVibrator ?: vibrator()?.also { tapVibrator = it } ?: return
            if (!v.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            else VibrationEffect.createOneShot(18, if (v.hasAmplitudeControl()) 90 else VibrationEffect.DEFAULT_AMPLITUDE)
            v.vibrate(effect)
        }
    }

    // ------------------------------------------------------------------ previews (settings)

    fun preview(id: String) {
        stopPreview()
        val res = find(id)?.res ?: return
        if (TAPS.any { it.id == id }) {
            tapIds[id]?.let { pool?.play(it, 0.8f, 0.8f, 1, 0, 1f) }
            return
        }
        preview = create(res, AudioAttributes.USAGE_MEDIA)?.apply {
            setOnCompletionListener { it.release(); if (preview === it) preview = null }
            start()
        }
    }

    fun stopPreview() {
        runCatching { preview?.stop() }
        runCatching { preview?.release() }
        preview = null
    }

    private fun create(res: Int, usage: Int, loop: Boolean = false, volume: Float = 1f): MediaPlayer? = runCatching {
        val afd = ctx.resources.openRawResourceFd(res)
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            isLooping = loop
            setVolume(volume, volume)
            prepare()
        }
    }.getOrNull()

    private fun oneShot(res: Int, signalling: Boolean) {
        val usage = if (signalling) AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        create(res, usage, volume = 0.7f)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    // ------------------------------------------------------------------ calls

    fun startRingtone() {
        stopRingtone()
        val am = runCatching { ctx.getSystemService(AudioManager::class.java) }.getOrNull()
        val mode = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        vibrateGently()
        if (mode != AudioManager.RINGER_MODE_NORMAL) return
        val choice = _settings.value.ringtone
        if (choice == SYSTEM || find(choice) == null) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                systemRing = RingtoneManager.getRingtone(ctx, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= 28) isLooping = true
                    play()
                }
            }
            return
        }
        ringPlayer = create(find(choice)!!.res, AudioAttributes.USAGE_NOTIFICATION_RINGTONE, loop = true)?.apply { start() }
    }

    fun stopRingtone() {
        runCatching { ringPlayer?.stop() }
        runCatching { ringPlayer?.release() }
        ringPlayer = null
        runCatching { systemRing?.stop() }
        systemRing = null
        runCatching { vibrator()?.cancel() }
    }

    private fun startRingback() {
        stopRingback()
        ringback = create(R.raw.call_attesa, AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING, loop = true, volume = 0.6f)?.apply { start() }
    }

    private fun stopRingback() {
        runCatching { ringback?.stop() }
        runCatching { ringback?.release() }
        ringback = null
    }

    private fun vibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }.getOrNull()

    /** a slow, soft pulse: half a second on, a second and a half off */
    private fun vibrateGently() {
        runCatching {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 450, 1550), intArrayOf(0, 110, 0), 1)
            vibrator()?.vibrate(effect)
        }
    }
}
