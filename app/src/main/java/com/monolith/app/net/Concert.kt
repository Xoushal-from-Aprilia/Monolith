package com.monolith.app.net

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * A concert of phones. Someone in a group starts it, the others join, and each phone plays one part
 * of the same song: the melody, the chords, the bass, the rhythm. Every phone makes its own sound from
 * the same score, so nothing is streamed: the host only says which song, who plays and when it starts
 * (a few seconds ahead, on the phones' own clocks). While it plays, each screen lights up with its notes
 * and a wave of light runs from phone to phone at every bar.
 */
object Concert {
    enum class Part(val label: String, val color: Long) {
        MELODIA("Melodia", 0xFFFF5A6E),
        ACCORDI("Accordi", 0xFF5AA8FF),
        BASSO("Basso", 0xFFA67CFF),
        RITMO("Ritmo", 0xFFFFC94A),
    }

    /** a note of the score: [start] and [len] in beats; for the rhythm [midi] is the drum */
    data class Note(val part: Part, val start: Float, val len: Float, val midi: Int, val vel: Float = 1f)

    /** an original song: chords bar by bar, a melody, a way of playing */
    class Song(
        val title: String,
        val mood: String,
        val bpm: Int,
        val chords: List<String>,
        val melody: String,
        /** 0 pop, 1 drive, 2 lullaby */
        val style: Int,
        val repeat: Int = 2,
    ) {
        val bars get() = chords.size * repeat + 1
        val beats get() = bars * 4f
        val seconds get() = beats * 60f / bpm
    }

    val SONGS = listOf(
        Song(
            "Monolite", "Allegra", 100,
            listOf("C", "G", "Am", "F", "C", "G", "F", "C"),
            "E5/1 G5/1 E5/1 C5/1  D5/1.5 B4/.5 G4/2  A4/1 C5/1 E5/1 A5/1  G5/1.5 F5/.5 E5/2 " +
                "E5/.5 F5/.5 G5/1 C6/1 G5/1  B5/1 A5/.5 G5/.5 D5/2  F5/1 A5/1 G5/1 F5/1  E5/3 r/1",
            0,
        ),
        Song(
            "Luci della città", "Di sera", 112,
            listOf("Am", "F", "C", "G", "Am", "F", "G", "E"),
            "A4/.5 C5/.5 E5/1 D5/.5 C5/.5 E5/1  F5/1 E5/.5 D5/.5 C5/2  G4/.5 C5/.5 E5/1 G5/1 E5/1  D5/1 B4/1 G4/2 " +
                "A4/.5 C5/.5 E5/1 A5/1 G5/1  F5/.5 E5/.5 F5/1 A5/2  G5/1 F5/1 D5/1 B4/1  G#4/2 B4/1 E5/1",
            1,
        ),
        Song(
            "Ninna dei sassi", "Piano", 76,
            listOf("G", "Em", "C", "D", "G", "Em", "D", "G"),
            "B4/1.5 D5/.5 G5/2  E5/1 G5/1 B4/2  C5/1 E5/1 G5/1 E5/1  D5/1 F#5/1 A5/2 " +
                "B5/1 A5/.5 G5/.5 D5/2  E5/1 D5/.5 B4/.5 G4/2  A4/1 C5/1 B4/1 A4/1  G4/3 r/1",
            2,
        ),
    )

    private const val KICK = 0
    private const val SNARE = 1
    private const val HAT = 2
    private const val SHAKER = 3

    private val SEMI = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)

    private fun midiOf(name: String): Int? {
        val m = Regex("([A-G])(#?)(\\d)").matchEntire(name) ?: return null
        return 12 * (m.groupValues[3].toInt() + 1) + SEMI.getValue(m.groupValues[1]) + if (m.groupValues[2] == "#") 1 else 0
    }

    /** root (0-11) and minor, from "Am", "F#", "C" */
    private fun chordOf(s: String): Pair<Int, Boolean> {
        val root = SEMI.getValue(s.substring(0, 1)) + if (s.getOrNull(1) == '#') 1 else 0
        return root % 12 to s.endsWith("m")
    }

    /** the whole score of a song */
    fun score(song: Song): List<Note> {
        val out = ArrayList<Note>()
        // melody, played [repeat] times, then the last chord held
        val tokens = song.melody.split(Regex("\\s+")).filter { it.isNotBlank() }
        val phrase = tokens.sumOf { it.substringAfter('/').toDouble() }.toFloat()
        for (r in 0 until song.repeat) {
            var b = r * phrase
            for (tk in tokens) {
                val len = tk.substringAfter('/').toFloat()
                midiOf(tk.substringBefore('/'))?.let { out += Note(Part.MELODIA, b, len, it, if (r == 1) 1f else 0.9f) }
                b += len
            }
        }
        val firstRoot = chordOf(song.chords.first()).first
        out += Note(Part.MELODIA, song.repeat * phrase, 4f, 72 + firstRoot - if (firstRoot > 7) 12 else 0, 0.8f)
        val chordBars = (0 until song.repeat).flatMap { song.chords } + song.chords.first()
        chordBars.forEachIndexed { bar, ch ->
            val b0 = bar * 4f
            val last = bar == chordBars.lastIndex
            val (root, minor) = chordOf(ch)
            // chords around middle C
            val tones = listOf(0, if (minor) 3 else 4, 7).map { 60 + (root + it).let { n -> if (root > 6) n - 12 else n } }
            val hits: List<Pair<Float, Float>> = when {
                last -> listOf(0f to 4f)
                song.style == 0 -> listOf(0f to 2f, 2f to 2f)
                song.style == 1 -> listOf(0f to 1.5f, 1.5f to 1f, 2.5f to 0.5f, 3f to 1f)
                else -> listOf(0f to 4f)
            }
            hits.forEach { (s, l) -> tones.forEach { n -> out += Note(Part.ACCORDI, b0 + s, l, n, 0.85f) } }
            // bass, two octaves down
            val bassRoot = 36 + root.let { if (it > 7) it - 12 else it }
            val bass: List<Triple<Float, Float, Int>> = when {
                last -> listOf(Triple(0f, 4f, bassRoot))
                song.style == 0 -> listOf(Triple(0f, 1f, bassRoot), Triple(1f, 1f, bassRoot), Triple(2f, 1f, bassRoot + 7), Triple(3f, 1f, bassRoot + 12))
                song.style == 1 -> (0 until 8).map { Triple(it * 0.5f, 0.45f, if (it == 7) bassRoot + 7 else bassRoot) }
                else -> listOf(Triple(0f, 2f, bassRoot), Triple(2f, 2f, bassRoot + 7))
            }
            bass.forEach { (s, l, n) -> out += Note(Part.BASSO, b0 + s, l, n) }
            // rhythm
            val drums: List<Pair<Float, Int>> = when {
                last -> listOf(0f to KICK, 0f to HAT)
                song.style == 0 -> listOf(0f to KICK, 2f to KICK, 1f to SNARE, 3f to SNARE) + (0 until 8).map { it * 0.5f to HAT }
                song.style == 1 -> listOf(0f to KICK, 1.5f to KICK, 2f to KICK, 1f to SNARE, 3f to SNARE, 3.75f to SNARE) + (0 until 8).map { it * 0.5f to HAT }
                else -> listOf(0f to KICK) + listOf(1f, 2f, 3f, 3.5f).map { it to SHAKER }
            }
            drums.forEach { (s, d) -> out += Note(Part.RITMO, b0 + s, 0.25f, d, if (song.style == 2) 0.7f else 1f) }
        }
        return out.sortedBy { it.start }
    }

    /** which parts the player number [index] of [n] plays */
    fun partsFor(index: Int, n: Int): Set<Part> = when {
        index < 0 -> emptySet()
        n <= 1 -> Part.entries.toSet()
        n == 2 -> if (index == 0) setOf(Part.MELODIA, Part.ACCORDI) else setOf(Part.BASSO, Part.RITMO)
        n == 3 -> listOf(setOf(Part.MELODIA), setOf(Part.ACCORDI), setOf(Part.BASSO, Part.RITMO))[index.coerceAtMost(2)]
        else -> setOf(Part.entries[index % 4])
    }

    // ---------------------------------------------------------------- sound

    private const val SR = 22050
    private const val TWO_PI = 2.0 * PI

    private fun freq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** the sound of [parts] of [song], ready to play */
    fun render(song: Song, parts: Set<Part>): ShortArray {
        val spb = 60.0 / song.bpm
        val total = ((song.seconds + 1.5f) * SR).toInt()
        val buf = FloatArray(total)
        val rnd = java.util.Random(7)
        for (n in score(song)) {
            if (n.part !in parts) continue
            val s0 = (n.start * spb * SR).toInt()
            val dur = n.len * spb
            when (n.part) {
                Part.MELODIA -> tone(buf, s0, dur, freq(n.midi), 0.30 * n.vel, 0)
                Part.ACCORDI -> tone(buf, s0, dur * 0.95, freq(n.midi), 0.10 * n.vel, 1)
                Part.BASSO -> tone(buf, s0, dur * 0.92, freq(n.midi), 0.36 * n.vel, 2)
                Part.RITMO -> drum(buf, s0, n.midi, n.vel, rnd)
            }
        }
        // the same gentle limiter on every phone, so the parts keep their balance
        return ShortArray(total) { (tanh(buf[it] * 1.1) * 30000).toInt().toShort() }
    }

    /** [inst] 0 lead, 1 pad, 2 bass */
    private fun tone(buf: FloatArray, s0: Int, dur: Double, f: Double, amp: Double, inst: Int) {
        val tail = when (inst) { 0 -> 0.15; 1 -> 0.4; else -> 0.15 }
        val n = ((dur + tail) * SR).toInt()
        var ph = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i.toDouble() / SR
            val tt = minOf(t, dur)
            var env = when (inst) {
                0 -> if (tt < 0.012) tt / 0.012 else 0.62 + 0.38 * exp(-(tt - 0.012) / 0.09)
                1 -> if (tt < 0.09) tt / 0.09 else 1.0
                else -> (if (tt < 0.006) tt / 0.006 else 1.0) * exp(-tt / 0.55)
            }
            if (t > dur) env *= exp(-(t - dur) / (if (inst == 1) 0.12 else 0.045))
            val vib = if (inst == 0 && t > 0.2) 1.0 + 0.0045 * sin(TWO_PI * 5.5 * t) else 1.0
            ph += TWO_PI * f * vib / SR
            val w = when (inst) {
                0 -> sin(ph) + 0.3 * sin(2 * ph) + 0.1 * sin(3 * ph)
                1 -> sin(ph) + 0.4 * sin(2 * ph) + 0.15 * sin(3 * ph) + 0.25 * sin(ph * 1.003)
                else -> sin(ph) + 0.45 * sin(2 * ph) + 0.2 * sin(3 * ph)
            }
            buf[idx] += (w * env * amp).toFloat()
        }
    }

    private fun drum(buf: FloatArray, s0: Int, kind: Int, vel: Float, rnd: java.util.Random) {
        val len = when (kind) { KICK -> 0.35; SNARE -> 0.2; HAT -> 0.06; else -> 0.1 }
        val n = (len * SR).toInt()
        var ph = 0.0
        var prev = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i.toDouble() / SR
            val noise = rnd.nextDouble() * 2 - 1
            val hp = noise - prev
            prev = noise
            val v = when (kind) {
                KICK -> { ph += TWO_PI * (45 + 80 * exp(-t / 0.03)) / SR; sin(ph) * exp(-t / 0.13) * 0.55 }
                SNARE -> noise * exp(-t / 0.05) * 0.26 + sin(TWO_PI * 190 * t) * exp(-t / 0.04) * 0.2
                HAT -> hp * exp(-t / 0.015) * 0.09
                else -> hp * (if (t < 0.02) t / 0.02 else exp(-(t - 0.02) / 0.03)) * 0.07
            }
            buf[idx] += (v * vel).toFloat()
        }
    }

    // ---------------------------------------------------------------- the light

    /** how bright this phone is at [sec] seconds into the song (0..1) */
    fun light(song: Song, notes: List<Note>, sec: Float): Float {
        val spb = 60f / song.bpm
        var best = 0f
        for (n in notes) {
            val s = n.start * spb
            if (s > sec) break
            val e = s + n.len * spb
            val d = sec - s
            val v = if (sec <= e) 0.45f + 0.55f * exp(-d / 0.18f) else exp(-(sec - e) / 0.12f) * 0.45f
            if (v * n.vel > best) best = v * n.vel
        }
        return best.coerceIn(0f, 1f)
    }

    /** the wave of light: at every bar it runs across the phones, one after the other */
    fun wave(song: Song, index: Int, n: Int, sec: Float): Float {
        if (index < 0) return 0f
        val bar = 4f * 60f / song.bpm
        val pos = sec % bar - index * 0.11f
        return if (pos in 0f..0.35f) exp(-pos / 0.09f) else 0f
    }

    // ---------------------------------------------------------------- the concert

    data class State(
        val conv: String? = null,
        /** who leads it: null, nobody yet */
        val host: String? = null,
        val song: Int = 0,
        /** players in order (the host first) */
        val players: List<String> = emptyList(),
        /** wall clock time of the first beat, 0 if not started */
        val startAt: Long = 0,
        /** the sound is ready for this phone */
        val ready: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** concerts I was invited to: conv to (host, song) */
    private val invites = HashMap<String, Pair<String, Int>>()

    private var track: AudioTrack? = null
    private var job: Job? = null

    private fun me() = Graph.messenger.me()?.pub

    private fun send(conv: String, p: JSONObject, only: String? = null) {
        Graph.scope.launch { runCatching { Graph.messenger.fastToConv(conv, p.put("t", "concert"), only) } }
    }

    private fun lineup(s: State) = JSONObject().put("k", "lineup").put("song", s.song).put("pl", JSONArray(s.players))

    /** the concert screen opened */
    fun open(conv: String) {
        if (_state.value.conv == conv) return
        stopSound()
        val inv = invites[conv]
        _state.value = State(conv = conv, host = inv?.first, song = inv?.second ?: 0)
        // someone may be playing already: ask
        send(conv, JSONObject().put("k", "who"))
    }

    fun close() {
        val s = _state.value
        val conv = s.conv ?: return
        val mine = me()
        if (s.host != null && s.host == mine) send(conv, JSONObject().put("k", "stop"))
        else if (s.host != null && mine in s.players) send(conv, JSONObject().put("k", "leave"), s.host)
        stopSound()
        _state.value = State()
    }

    /** I start a concert here */
    fun host(song: Int) {
        val s = _state.value
        val conv = s.conv ?: return
        val mine = me() ?: return
        _state.value = s.copy(host = mine, song = song, players = listOf(mine), startAt = 0)
        send(conv, JSONObject().put("k", "invite").put("song", song))
    }

    fun pickSong(song: Int) {
        val s = _state.value
        if (s.host != me() || s.startAt != 0L) return
        _state.value = s.copy(song = song)
        s.conv?.let { send(it, lineup(_state.value)) }
    }

    fun join() {
        val s = _state.value
        val conv = s.conv ?: return
        val host = s.host ?: return
        val mine = me() ?: return
        if (mine !in s.players) _state.value = s.copy(players = s.players + mine)
        send(conv, JSONObject().put("k", "join"), host)
    }

    /** the host starts: everyone in four seconds */
    fun start() {
        val s = _state.value
        val conv = s.conv ?: return
        if (s.host != me()) return
        val at = now() + 4000
        _state.value = s.copy(startAt = at)
        send(conv, JSONObject().put("k", "start").put("at", at).put("song", s.song).put("pl", JSONArray(s.players)))
        play(_state.value)
    }

    fun stop() {
        val s = _state.value
        val conv = s.conv ?: return
        if (s.host == me()) send(conv, JSONObject().put("k", "stop"))
        stopSound()
        _state.value = s.copy(startAt = 0, ready = false)
    }

    private fun stopSound() {
        job?.cancel()
        job = null
        track?.let { tr -> runCatching { tr.stop() }; runCatching { tr.release() } }
        track = null
    }

    private fun play(s: State) {
        stopSound()
        val mine = me() ?: return
        val idx = s.players.indexOf(mine)
        if (idx < 0) return
        val song = SONGS.getOrNull(s.song) ?: return
        val parts = partsFor(idx, s.players.size)
        job = Graph.scope.launch(Dispatchers.Default) {
            val pcm = render(song, parts)
            val tr = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
            }.getOrNull() ?: return@launch
            tr.write(pcm, 0, pcm.size)
            track = tr
            _state.value = _state.value.copy(ready = true)
            val wait = s.startAt - now()
            if (wait > 0) delay(wait) else {
                // joined late: start where the others are
                val skip = (-wait / 1000.0 * SR).toInt()
                if (skip >= pcm.size) { _state.value = _state.value.copy(startAt = 0, ready = false); return@launch }
                runCatching { tr.setPlaybackHeadPosition(skip) }
            }
            runCatching { tr.play() }
            delay(maxOf(0L, s.startAt + (song.seconds * 1000).toLong() + 1200 - now()))
            runCatching { tr.stop(); tr.release() }
            if (track === tr) track = null
            val cur = _state.value
            if (cur.startAt == s.startAt) _state.value = cur.copy(startAt = 0, ready = false)
        }
    }

    private fun allowed() = !(com.monolith.app.kids.Kids.isKid && !com.monolith.app.kids.Kids.rules.value.games)

    suspend fun onSignal(conv: String, from: String, p: JSONObject) {
        if (!allowed()) return
        val s = _state.value
        val here = s.conv == conv
        val mine = me()
        when (p.optString("k")) {
            "invite" -> {
                val song = p.optInt("song")
                invites[conv] = from to song
                if (here && s.startAt == 0L) _state.value = s.copy(host = from, song = song, players = listOf(from))
                else if (!here) {
                    val name = Graph.db.friends().get(from)?.displayName ?: t("Qualcuno")
                    val group = Graph.shards.ofConv(conv)?.let { (sh, r) -> sh.name + " · " + r.name }
                        ?: Graph.db.groups().get(conv.removePrefix("g:"))?.name ?: ""
                    Graph.notifier.concertInvite(conv, name, group)
                }
            }
            "who" -> if (here && s.host == mine && mine != null) {
                send(conv, JSONObject().put("k", "invite").put("song", s.song), from)
                send(conv, lineup(s), from)
                if (s.startAt != 0L) send(conv, JSONObject().put("k", "start").put("at", s.startAt).put("song", s.song).put("pl", JSONArray(s.players)), from)
            }
            "join" -> if (here && s.host == mine && from !in s.players) {
                val n = s.copy(players = s.players + from)
                _state.value = n
                send(conv, lineup(n))
                // already playing: the newcomer comes in where we are and doubles a part
                if (n.startAt != 0L) send(conv, JSONObject().put("k", "start").put("at", n.startAt).put("song", n.song).put("pl", JSONArray(n.players)), from)
            }
            "leave" -> if (here && s.host == mine) {
                val n = s.copy(players = s.players - from)
                _state.value = n
                send(conv, lineup(n))
            }
            "lineup" -> if (here && s.host == from) {
                val pl = p.optJSONArray("pl")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                _state.value = s.copy(song = p.optInt("song"), players = pl)
            }
            "start" -> if (here && s.host == from) {
                val pl = p.optJSONArray("pl")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val n = s.copy(song = p.optInt("song"), players = if (pl.isEmpty()) s.players else pl, startAt = p.optLong("at"))
                _state.value = n
                play(n)
            }
            "stop" -> {
                if (invites[conv]?.first == from) invites.remove(conv)
                if (here && s.host == from) {
                    stopSound()
                    _state.value = State(conv = conv)
                }
            }
        }
    }
}
