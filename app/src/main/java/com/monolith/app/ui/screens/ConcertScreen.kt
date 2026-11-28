package com.monolith.app.ui.screens

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.kids.Kids
import com.monolith.app.net.Concert
import com.monolith.app.ui.components.bounceClick

/** the little drawn sign of a part: a wave, three stacked bars, a low curve, a pulse */
@Composable
fun PartGlyph(part: Concert.Part, size: Int = 18, tint: Color = Color(part.color)) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val st = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        when (part) {
            Concert.Part.MELODIA -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.08f, h * 0.55f)
                    cubicTo(w * 0.3f, h * 0.05f, w * 0.45f, h * 0.05f, w * 0.5f, h * 0.5f)
                    cubicTo(w * 0.55f, h * 0.95f, w * 0.7f, h * 0.95f, w * 0.92f, h * 0.45f)
                }
                drawPath(p, tint, style = st)
            }
            Concert.Part.ACCORDI -> for (k in 0 until 3) drawLine(tint, Offset(w * 0.15f, h * (0.25f + 0.25f * k)), Offset(w * 0.85f, h * (0.25f + 0.25f * k)), w * 0.13f, androidx.compose.ui.graphics.StrokeCap.Round)
            Concert.Part.BASSO -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.1f, h * 0.35f)
                    quadraticTo(w * 0.5f, h * 1.15f, w * 0.9f, h * 0.35f)
                }
                drawPath(p, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.16f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Concert.Part.RITMO -> {
                drawCircle(tint, w * 0.16f, Offset(w * 0.25f, h * 0.5f))
                drawCircle(tint, w * 0.1f, Offset(w * 0.55f, h * 0.5f))
                drawCircle(tint, w * 0.06f, Offset(w * 0.8f, h * 0.5f))
            }
        }
    }
}

@Composable
private fun PartChip(part: Concert.Part) {
    Row(
        Modifier.clip(CircleShape).background(Color(part.color).copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PartGlyph(part, 14)
        Spacer(Modifier.width(5.dp))
        Text(t(part.label), style = MaterialTheme.typography.labelMedium, color = Color(part.color))
    }
}

/** phones that play a song together, one part each */
@Composable
fun ConcertScreen(nav: NavHostController, conv: String) {
    val st by Concert.state.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val kidRules by Kids.rules.collectAsState()
    val names = people.associateBy { it.pubkey }
    val mine = Graph.messenger.me()?.pub
    fun nameOf(pk: String) = if (pk == mine) t("Tu") else names[pk]?.displayName ?: t("Qualcuno")
    val group by remember(conv) {
        kotlinx.coroutines.flow.flow {
            emit(Graph.shards.ofConv(conv)?.let { (s, r) -> s.name + " · " + r.name } ?: Graph.db.groups().get(conv.removePrefix("g:"))?.name ?: "")
        }
    }.collectAsState(initial = "")
    LaunchedEffect(conv) { Concert.open(conv) }
    DisposableEffect(conv) { onDispose { Concert.close() } }
    val view = LocalView.current
    val playing = st.conv == conv && st.startAt != 0L
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
    if (Kids.isKid && !kidRules.games) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("I giochi sono spenti"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(t("Chiedi a chi ti segue di accenderli."), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }
    val song = Concert.SONGS.getOrElse(st.song) { Concert.SONGS.first() }
    val myIndex = mine?.let { st.players.indexOf(it) } ?: -1
    val myParts = Concert.partsFor(myIndex, st.players.size)
    if (playing && myIndex >= 0) {
        Stage(song, myIndex, st.players.size, myParts, st.startAt, st.ready, st.host == mine, group) { if (st.host == mine) Concert.stop() else nav.popBackStack() }
        return
    }

    var pick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Column(Modifier.weight(1f)) {
                Text(t("Concerto"), style = MaterialTheme.typography.titleLarge)
                if (group.isNotBlank()) Text(group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val host = st.host
            when {
                host == null -> {
                    Text(t("Ogni telefono suona una parte della stessa canzone: la melodia, gli accordi, il basso, il ritmo. Più siete, più è piena."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    SongList(pick) { pick = it }
                }
                host == mine -> {
                    Text(t("Il concerto è aperto: gli altri ricevono l'invito e toccano Unisciti."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    SongList(st.song) { Concert.pickSong(it) }
                }
                myIndex < 0 -> {
                    Text(
                        if (st.startAt != 0L) t("{0} sta suonando {1}. Entra anche tu.", nameOf(host), t(song.title)) else t("{0} ha aperto un concerto: {1}.", nameOf(host), t(song.title)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                else -> Text(t("Aspettiamo che {0} faccia partire la musica.", nameOf(host)), style = MaterialTheme.typography.titleMedium)
            }
            if (st.players.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(t("Chi suona"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                st.players.forEachIndexed { i, pk ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (pk == mine) Graph.identity.profile.value?.emoji else names[pk]?.emoji) ?: "🙂", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(nameOf(pk), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Concert.partsFor(i, st.players.size).forEach { PartChip(it) } }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t("Alzate il volume e mettete i telefoni vicini."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val host = st.host
        val (label, act) = when {
            host == null -> t("Apri il concerto") to { Concert.host(pick) }
            host == mine -> (if (st.players.size > 1) t("Suona") else t("Suona da solo")) to { Concert.start() }
            myIndex < 0 -> t("Unisciti") to { Concert.join() }
            else -> null to {}
        }
        if (label != null) Surface(
            color = MaterialTheme.colorScheme.primary, shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(20.dp).bounceClick(0.96f) { act() },
        ) {
            Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun SongList(selected: Int, onPick: (Int) -> Unit) {
    Text(t("La canzone"), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Concert.SONGS.forEachIndexed { i, s ->
        val on = i == selected
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                .bounceClick(0.97f) { onPick(i) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Equalizer(i, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t(s.title), style = MaterialTheme.typography.titleMedium)
                Text(t(s.mood) + " · " + s.bpm + " bpm · " + (s.seconds.toInt()) + " s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** bars of different heights: a small drawn sign for each song */
@Composable
private fun Equalizer(seed: Int, tint: Color) {
    val hs = listOf(listOf(0.5f, 0.9f, 0.65f, 0.35f), listOf(0.8f, 0.45f, 1f, 0.6f), listOf(0.3f, 0.55f, 0.4f, 0.25f))[seed % 3]
    Canvas(Modifier.size(28.dp)) {
        val bw = size.width / 7f
        hs.forEachIndexed { k, f ->
            val h = size.height * f
            drawRoundRect(tint, Offset(bw * (2 * k).toFloat() * 0.9f + bw * 0.2f, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
        }
    }
}

/** the screen while playing: it lights up with this phone's notes, and a wave runs across the phones */
@Composable
private fun Stage(song: Concert.Song, index: Int, n: Int, parts: Set<Concert.Part>, startAt: Long, ready: Boolean, iHost: Boolean, group: String, onStop: () -> Unit) {
    val notes = remember(song, parts) { Concert.Part.entries.associateWith { p -> Concert.score(song).filter { it.part == p && p in parts } } }
    var sec by remember { mutableFloatStateOf((now() - startAt) / 1000f) }
    LaunchedEffect(startAt) { while (true) { withInfiniteAnimationFrameNanos { }; sec = (now() - startAt) / 1000f } }
    // the brightest of my parts gives the colour
    var bright = 0f
    var color = Color(parts.firstOrNull()?.color ?: 0xFFFFFFFF)
    if (sec >= 0f) parts.forEach { p ->
        val l = Concert.light(song, notes[p].orEmpty(), sec)
        if (l > bright) { bright = l; color = Color(p.color) }
    }
    val wave = if (sec >= 0f) Concert.wave(song, index, n, sec) else 0f
    val bg = lerp(lerp(Color(0xFF060606), color, bright * 0.85f), Color.White, wave * 0.55f)
    val ink = if (bright * 0.85f + wave * 0.55f > 0.55f) Color(0xFF0A0A0A) else Color(0xFFF2F2F0)
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t(song.title), color = ink, style = MaterialTheme.typography.titleLarge)
            if (group.isNotBlank()) Text(group, color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (sec < 0f) {
                Text(
                    if (!ready) t("Preparo la tua parte…") else (-sec).toInt().plus(1).coerceAtMost(4).toString(),
                    color = ink, fontSize = if (ready) 96.sp else 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { parts.forEach { PartGlyph(it, 64, ink) } }
            }
            Spacer(Modifier.height(18.dp))
            Text(parts.joinToString(" + ") { t(it.label) }, color = ink, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(t("Telefono {0} di {1}", index + 1, n), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            // how far along
            val f = (sec / song.seconds).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(ink.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(f).height(4.dp).background(ink))
            }
            Spacer(Modifier.height(18.dp))
            Surface(color = ink.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.bounceClick(0.95f, onClick = onStop)) {
                Text(if (iHost) t("Ferma per tutti") else t("Esci"), color = ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
            }
        }
    }
}
