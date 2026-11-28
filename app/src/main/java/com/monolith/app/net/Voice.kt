package com.monolith.app.net

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.monolith.app.core.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Records short voice notes (AAC/m4a, mono) */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L

    val isRecording get() = rec != null
    val isPaused get() = pausedAt > 0L
    /** recorded time, without the pauses */
    fun elapsed(): Long = if (rec != null) (if (pausedAt > 0L) pausedAt else now()) - startedAt - pausedTotal else 0L

    /** a pause in a long voice message (hands-free recording) */
    fun pause(): Boolean {
        val r = rec ?: return false
        if (pausedAt > 0L) return true
        return runCatching { r.pause(); pausedAt = now() }.isSuccess
    }

    fun resume(): Boolean {
        val r = rec ?: return false
        if (pausedAt == 0L) return true
        return runCatching { r.resume(); pausedTotal += now() - pausedAt; pausedAt = 0L }.isSuccess
    }

    fun start(): Boolean {
        stop(cancel = true)
        return try {
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(22050)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            startedAt = now()
            pausedAt = 0L
            pausedTotal = 0L
            true
        } catch (e: Exception) {
            rec = null
            false
        }
    }

    /** stops; returns the file and duration unless cancelled or too short */
    fun stop(cancel: Boolean): Pair<File, Long>? {
        val r = rec ?: return null
        val f = file
        val dur = elapsed()
        pausedAt = 0L
        pausedTotal = 0L
        rec = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (cancel || !ok || dur < 700 || f == null) {
            f?.delete()
            return null
        }
        return f to dur
    }
}

/** Single shared player for voice notes, so starting one stops the previous */
object VoicePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /** listening speed for voice messages: 1×, 1.5×, 2× */
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun cycleSpeed() {
        val next = when (_speed.value) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        _speed.value = next
        player?.let { p -> runCatching { p.playbackParams = p.playbackParams.setSpeed(next) } }
    }

    private var currentPath: String? = null
    private var earpiece = false

    fun toggle(id: String, path: String) {
        if (_playing.value == id) {
            stop()
            return
        }
        stop()
        play(id, path, 0)
    }

    private fun play(id: String, path: String, from: Int) {
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (earpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            p.setDataSource(path)
            p.setOnCompletionListener { stop() }
            p.prepare()
            if (from > 0) p.seekTo(from)
            p.start()
            if (_speed.value != 1f) runCatching { p.playbackParams = p.playbackParams.setSpeed(_speed.value) }
            player = p
            currentPath = path
            _playing.value = id
            ticker?.cancel()
            ticker = scope.launch {
                while (isActive) {
                    val pl = player ?: break
                    val d = pl.duration.coerceAtLeast(1)
                    _progress.value = (runCatching { pl.currentPosition }.getOrDefault(0).toFloat() / d).coerceIn(0f, 1f)
                    delay(60)
                }
            }
        } catch (e: Exception) {
            stop()
        }
    }

    /** phone at the ear: the voice message moves to the earpiece, and back to the speaker when it leaves */
    fun setEarpiece(on: Boolean) {
        if (on == earpiece || com.monolith.app.Graph.calls.inCall) return
        earpiece = on
        val am = com.monolith.app.Graph.app.getSystemService(android.media.AudioManager::class.java)
        @Suppress("DEPRECATION")
        runCatching {
            if (on) { am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = false }
            else am.mode = android.media.AudioManager.MODE_NORMAL
        }
        val p = player ?: return
        val id = _playing.value ?: return
        val path = currentPath ?: return
        val pos = runCatching { p.currentPosition }.getOrDefault(0)
        ticker?.cancel()
        runCatching { p.stop() }; runCatching { p.release() }
        player = null
        play(id, path, pos)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        _playing.value = null
        _progress.value = 0f
    }
}
