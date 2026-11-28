package com.monolith.app.ui.screens

import androidx.compose.foundation.layout.heightIn

import androidx.compose.ui.draw.drawBehind

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.app.Graph
import com.monolith.app.core.I18n
import com.monolith.app.core.t
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The film of the features, shown once before creating the profile and again from Settings.
 *
 * KEEP IT UP TO DATE: at every release, add the new features to [FILM] (a new scene or new lines
 * in the right scene). The owner of the app asked for this explicitly.
 */
private data class Scene(val kicker: String, val title: String, val lines: List<String>, val art: String)

private val FILM = listOf(
    Scene("", "Monolith" /*i18n*/, listOf("Tutto quello che conta, tra amici." /*i18n*/), "intro"),
    Scene("Agenda" /*i18n*/, "Il tuo tempo, condiviso" /*i18n*/, listOf("Eventi e inviti agli amici" /*i18n*/, "Conto alla rovescia e meteo" /*i18n*/, "Compleanni e anniversari d'amicizia" /*i18n*/), "agenda"),
    Scene("Chat" /*i18n*/, "Chat che sanno giocare" /*i18n*/, listOf("Giochi, dadi e lavagna condivisa" /*i18n*/, "Sticker, inoltri e foto a vista unica" /*i18n*/, "Date nei messaggi che diventano eventi" /*i18n*/), "chat"),
    Scene("Chat" /*i18n*/, "Tutto in ordine" /*i18n*/, listOf("Thread, file e video" /*i18n*/, "Cartelle, chat fissate e note per te" /*i18n*/, "Grassetto, spoiler e Ricordamelo" /*i18n*/, "Letto da, nei gruppi" /*i18n*/), "chatorg"),
    Scene("Storie" /*i18n*/, "Racconta la giornata" /*i18n*/, listOf("Filtri in bianco e nero" /*i18n*/, "Musica nelle storie" /*i18n*/, "Risposte che arrivano in chat" /*i18n*/), "stories"),
    Scene("Chiamate" /*i18n*/, "Voce, video e schermo" /*i18n*/, listOf("Fino a 4 persone" /*i18n*/, "Cuffie Bluetooth e microfono a scelta" /*i18n*/, "Condividi lo schermo" /*i18n*/, "Ascolta musica insieme" /*i18n*/), "calls"),
    Scene("Mappa" /*i18n*/, "Sai sempre dove trovarvi" /*i18n*/, listOf("Posizione in diretta e arrivo in diretta" /*i18n*/, "Luoghi comuni e amici nei paraggi" /*i18n*/, "Bussola verso l'amico" /*i18n*/), "map"),
    Scene("Profilo" /*i18n*/, "Il tuo spazio" /*i18n*/, listOf("Copertine da scegliere" /*i18n*/, "Distintivi disegnati, tre in vetrina" /*i18n*/, "Account verificati, con il simbolo accanto al nome" /*i18n*/), "profile"),
    Scene("Colori" /*i18n*/, "Del colore che vuoi" /*i18n*/, listOf("Dodici temi da scegliere" /*i18n*/, "Chiaro, scuro o dal tuo sfondo" /*i18n*/, "E l'icona che preferisci" /*i18n*/), "colors"),
    Scene("Legame" /*i18n*/, "Più vi scrivete, più cresce" /*i18n*/, listOf("Da Sasso a Monolite" /*i18n*/, "Serie di giorni insieme" /*i18n*/, "Il simbolo accanto al nome dopo 100 giorni" /*i18n*/), "bond"),
    Scene("Shard" /*i18n*/, "Spazi per il tuo gruppo" /*i18n*/, listOf("Stanze scritte, vocali e forum" /*i18n*/, "Ruoli con nome, colore e permessi" /*i18n*/, "Regole, modalità lenta e moderazione automatica" /*i18n*/, "Lo Shard per tutti gli utenti di Monolith" /*i18n*/), "shard"),
    Scene("Gruppi" /*i18n*/, "Il gruppo è vostro" /*i18n*/, listOf("Aggiungi persone quando vuoi" /*i18n*/, "Un link per entrare, anche con il codice" /*i18n*/, "I tag si vedono subito" /*i18n*/, "Il monolite del gruppo cresce con voi" /*i18n*/), "groups"),
    Scene("Schermo unito" /*i18n*/, "Due telefoni, uno schermo" /*i18n*/, listOf("Una foto su tutti e due gli schermi" /*i18n*/, "Lancia una foto da un telefono all'altro" /*i18n*/), "duo"),
    Scene("Concerto" /*i18n*/, "Ogni telefono suona una parte" /*i18n*/, listOf("Melodia, accordi, basso e ritmo" /*i18n*/, "Un'onda di luce da un telefono all'altro" /*i18n*/), "concert"),
    Scene("Insieme" /*i18n*/, "Spese, album, abitudini" /*i18n*/, listOf("Chi deve quanto, in un tocco" /*i18n*/, "Le foto di tutti in un posto" /*i18n*/, "Abitudini da tenere insieme" /*i18n*/), "together"),
    Scene("Sicurezza" /*i18n*/, "Al sicuro, sempre" /*i18n*/, listOf("Tasto SOS con scheda medica" /*i18n*/, "Screenshot bloccati, account molesti fermati" /*i18n*/, "Backup cifrato di tutto, anche automatico" /*i18n*/), "safe"),
    Scene("Il tuo telefono" /*i18n*/, "Si muove con te" /*i18n*/, listOf("Batti cinque per aggiungere amici" /*i18n*/, "Vocali all'orecchio" /*i18n*/, "Sfondo animato e widget" /*i18n*/, "Una piccola vibrazione a ogni tocco" /*i18n*/), "phone"),
    Scene("Domenica" /*i18n*/, "La tua settimana in slide" /*i18n*/, listOf("Persone, emoji e ritmo" /*i18n*/, "Un titolo tutto tuo" /*i18n*/), "wrapped"),
    Scene("Monolith Kids" /*i18n*/, "Anche per i più piccoli" /*i18n*/, listOf("Un'app a parte, sul telefono del bambino" /*i18n*/, "Il genitore vede messaggi, agenda e posizione" /*i18n*/, "Solo contatti approvati e ora di riposo" /*i18n*/), "kids"),
    Scene("", "Pronto?" /*i18n*/, listOf("Il resto lo scopri usandola." /*i18n*/, "Sempre l'ultima versione su monolith.support" /*i18n*/), "end"),
)

private const val SCENE_MS = 4300

object FilmPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("film", Context.MODE_PRIVATE)
    fun seen(ctx: Context) = sp(ctx).getBoolean("seen", false)
    fun markSeen(ctx: Context) = sp(ctx).edit().putBoolean("seen", true).apply()
}

/** the film itself; [onDone] when it ends or is skipped */
@Composable
fun FeatureFilm(onDone: () -> Unit, doneLabel: String = t("Inizia"), preview: Int = -1) {
    // [preview]: for the screenshots, one scene held still near its end
    var index by remember { mutableIntStateOf(preview.coerceAtLeast(0)) }
    var paused by remember { mutableStateOf(preview >= 0) }
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(-1) }
    val last = FILM.lastIndex
    LaunchedEffect(index, paused) {
        if (shown != index) { shown = index; progress.snapTo(if (preview >= 0) 0.95f else 0f) }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SCENE_MS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (index < last) index++
    }
    val dark = index % 2 == 0
    val bg by animateColorAsState(if (dark) Color(0xFF050505) else Color(0xFFF1F1EF), tween(600), label = "bg")
    val ink by animateColorAsState(if (dark) Color(0xFFF1F1EF) else Color(0xFF0A0A0A), tween(600), label = "ink")
    val inf = rememberInfiniteTransition(label = "film")
    val clock by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "clock")

    Box(
        Modifier.fillMaxSize().background(bg).pointerInput(Unit) {
            detectTapGestures(
                onPress = { paused = true; tryAwaitRelease(); paused = false },
                onTap = { o -> if (o.x < size.width / 3f) { if (index > 0) index-- } else if (index < last) index++ },
            )
        },
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = { (fadeIn(tween(420)) + scaleIn(tween(520, easing = Motion.EmphasizedDecel), initialScale = 0.94f)) togetherWith fadeOut(tween(260)) },
            label = "scene",
        ) { i ->
            val sc = FILM[i]
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 64.dp, bottom = 24.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SceneArt(sc.art, ink, bg, progress.value, clock)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    if (sc.kicker.isNotBlank()) Reveal(0) {
                        Text(t(sc.kicker).uppercase(I18n.locale), color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Reveal(120) {
                        Text(t(sc.title), color = ink, fontSize = if (sc.art == "intro") 54.sp else 36.sp, fontWeight = FontWeight.Black, lineHeight = 40.sp, letterSpacing = (-1).sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    sc.lines.forEachIndexed { k, l ->
                        Reveal(380 + k * 260) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Box(Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(ink))
                                Spacer(Modifier.width(10.dp))
                                Text(t(l), color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (i == last) {
                        Spacer(Modifier.height(24.dp))
                        Reveal(700) {
                            Button(
                                onClick = onDone, shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = bg),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text(doneLabel, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                FILM.indices.forEach { i ->
                    // the progress is read while drawing: no recomposition at every frame
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.22f)).drawBehind {
                            val f = when {
                                i < index -> 1f
                                i == index -> progress.value
                                else -> 0f
                            }
                            drawRect(ink, size = size.copy(width = size.width * f))
                        },
                    )
                }
            }
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MONOLITH", color = ink.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                if (index < last) TextButton(onClick = onDone) { Text(t("Salta"), color = ink) }
            }
        }
    }
}

@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(650, easing = Motion.EmphasizedDecel))
    }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36.dp.toPx() }) { content() }
}

// ====================================================================== the animated pictures

private fun ease(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).let { it * it * it }
private fun stage(p: Float, from: Float, to: Float) = ease((p - from) / (to - from))

private fun DrawScope.partialPath(path: Path, f: Float, color: Color, width: Float) {
    val m = PathMeasure()
    m.setPath(path, false)
    val out = Path()
    m.getSegment(0f, m.length * f.coerceIn(0f, 1f), out, true)
    drawPath(out, color, style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.monolith(cx: Float, baseY: Float, w: Float, h: Float, ink: Color, rise: Float = 1f) {
    val hh = h * rise
    val d = w * 0.26f
    drawPath(Path().apply { moveTo(cx - w / 2, baseY - hh); lineTo(cx - w / 2 + d, baseY - hh - d * 0.8f); lineTo(cx + w / 2 + d, baseY - hh - d * 0.8f); lineTo(cx + w / 2, baseY - hh); close() }, ink.copy(alpha = 0.55f))
    drawPath(Path().apply { moveTo(cx + w / 2, baseY - hh); lineTo(cx + w / 2 + d, baseY - hh - d * 0.8f); lineTo(cx + w / 2 + d, baseY - d * 0.8f); lineTo(cx + w / 2, baseY); close() }, ink.copy(alpha = 0.3f))
    drawRect(ink, Offset(cx - w / 2, baseY - hh), Size(w, hh))
}

@Composable
private fun SceneArt(art: String, ink: Color, bg: Color, p: Float, clock: Float) {
    Canvas(Modifier.fillMaxWidth(0.86f).fillMaxHeight88()) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2, h / 2)
        val tau = (2 * PI).toFloat()
        when (art) {
            "intro", "end" -> {
                for (k in 0 until 4) {
                    val f = (clock * 2 + k / 4f) % 1f
                    drawCircle(ink.copy(alpha = 0.14f * (1 - f)), min(w, h) * (0.15f + f * 0.5f), Offset(c.x, h * 0.78f), style = Stroke(2.5f))
                }
                if (art == "end") {
                    val r = java.util.Random(3)
                    repeat(60) {
                        val tw = 0.5f + 0.5f * sin(clock * tau * 2 + it)
                        drawCircle(ink.copy(alpha = 0.5f * tw), 1.5f + r.nextFloat() * 2f, Offset(r.nextFloat() * w, r.nextFloat() * h * 0.6f))
                    }
                }
                drawRect(ink.copy(alpha = 0.3f), Offset(w * 0.12f, h * 0.78f), Size(w * 0.76f, 2.5f))
                monolith(c.x, h * 0.78f, w * 0.2f, h * 0.55f, ink, stage(p, 0f, 0.35f))
            }
            "groups" -> {
                // three monoliths together, the link that joins them, tags that light up
                val base = h * 0.8f
                drawRect(ink.copy(alpha = 0.3f), Offset(w * 0.12f, base), Size(w * 0.76f, 2.5f))
                monolith(w * 0.3f, base, w * 0.12f, h * 0.34f, ink.copy(alpha = 0.75f), stage(p, 0f, 0.3f))
                monolith(w * 0.5f, base, w * 0.15f, h * 0.46f, ink, stage(p, 0.1f, 0.4f))
                monolith(w * 0.7f, base, w * 0.12f, h * 0.3f, ink.copy(alpha = 0.75f), stage(p, 0.2f, 0.5f))
                val s = stage(p, 0.42f, 0.78f)
                val lw = w * 0.17f
                val lh = w * 0.085f
                val cy = h * 0.14f
                rotate(-28f, Offset(c.x, cy)) {
                    drawRoundRect(ink.copy(alpha = s), Offset(c.x - lw * 0.85f, cy - lh / 2), Size(lw, lh), CornerRadius(lh / 2), style = Stroke(w * 0.018f))
                    drawRoundRect(ink.copy(alpha = s), Offset(c.x - lw * 0.15f, cy - lh / 2), Size(lw, lh), CornerRadius(lh / 2), style = Stroke(w * 0.018f))
                }
                listOf(Offset(w * 0.2f, h * 0.36f), Offset(w * 0.8f, h * 0.44f)).forEachIndexed { k, o ->
                    val a = stage(p, 0.6f + k * 0.1f, 0.9f + k * 0.05f)
                    val glow = 0.6f + 0.4f * sin(clock * tau * 2 + k * 2f)
                    drawRoundRect(ink.copy(alpha = 0.22f * a * glow), Offset(o.x - w * 0.08f, o.y - h * 0.028f), Size(w * 0.16f, h * 0.056f), CornerRadius(h * 0.028f))
                    drawCircle(ink.copy(alpha = a), h * 0.012f, Offset(o.x - w * 0.045f, o.y))
                    drawRect(ink.copy(alpha = 0.8f * a), Offset(o.x - w * 0.02f, o.y - h * 0.005f), Size(w * 0.07f, h * 0.01f))
                }
            }
            "chatorg" -> {
                // folder chips, a pinned chat, a thread branching off a message, a spoiler
                val chips = listOf(0.2f, 0.16f, 0.18f)
                var x = w * 0.04f
                chips.forEachIndexed { k, cw ->
                    val s = stage(p, k * 0.06f, k * 0.06f + 0.2f)
                    drawRoundRect(ink.copy(alpha = if (k == 0) 0.9f * s else 0.2f * s), Offset(x, h * 0.05f), Size(w * cw, h * 0.08f), CornerRadius(h * 0.04f))
                    x += w * cw + w * 0.03f
                }
                for (k in 0 until 3) {
                    val s = stage(p, 0.15f + k * 0.08f, 0.35f + k * 0.08f)
                    val y = h * (0.2f + k * 0.13f)
                    drawCircle(ink.copy(alpha = 0.25f * s), h * 0.045f, Offset(w * 0.1f, y + h * 0.045f))
                    drawRoundRect(ink.copy(alpha = 0.6f * s), Offset(w * 0.2f, y + h * 0.015f), Size(w * 0.45f * s, h * 0.025f), CornerRadius(h * 0.012f))
                    drawRoundRect(ink.copy(alpha = 0.2f * s), Offset(w * 0.2f, y + h * 0.055f), Size(w * 0.6f * s, h * 0.02f), CornerRadius(h * 0.01f))
                    if (k == 0) {
                        // the pin, drawn: a head and a needle
                        val px = w * 0.9f
                        val py = y + h * 0.04f
                        drawCircle(ink.copy(alpha = s), h * 0.018f, Offset(px, py))
                        drawLine(ink.copy(alpha = s), Offset(px, py), Offset(px - h * 0.03f, py + h * 0.04f), 3f, StrokeCap.Round)
                    }
                }
                // a message with its thread
                val ty = h * 0.64f
                val sm = stage(p, 0.45f, 0.6f)
                drawRoundRect(ink.copy(alpha = 0.9f * sm), Offset(w * 0.04f, ty), Size(w * 0.5f, h * 0.09f), CornerRadius(h * 0.045f))
                val st = stage(p, 0.6f, 0.8f)
                drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.12f, ty + h * 0.09f), Offset(w * 0.12f, ty + h * 0.09f + h * 0.17f * st), 3f, StrokeCap.Round)
                for (k in 0 until 2) {
                    val a = stage(p, 0.68f + k * 0.08f, 0.85f + k * 0.06f)
                    val yy = ty + h * (0.13f + k * 0.08f)
                    drawLine(ink.copy(alpha = 0.5f * a), Offset(w * 0.12f, yy + h * 0.025f), Offset(w * 0.17f, yy + h * 0.025f), 3f, StrokeCap.Round)
                    drawRoundRect(ink.copy(alpha = 0.22f * a), Offset(w * 0.18f, yy), Size(w * 0.32f, h * 0.05f), CornerRadius(h * 0.025f))
                }
                // a spoiler: dots that shimmer, then clear
                val sp = stage(p, 0.5f, 0.65f)
                val clear = stage(p, 0.85f, 1f)
                val ox = w * 0.6f
                val oy = h * 0.66f
                drawRoundRect(ink.copy(alpha = 0.18f * sp), Offset(ox, oy), Size(w * 0.36f, h * 0.09f), CornerRadius(h * 0.045f))
                val rnd = java.util.Random(5)
                repeat(36) {
                    val dx = rnd.nextFloat()
                    val dy = rnd.nextFloat()
                    val tw = 0.5f + 0.5f * sin(clock * tau * 3 + it)
                    drawCircle(ink.copy(alpha = 0.8f * tw * sp * (1 - clear)), 2.2f, Offset(ox + w * 0.03f + dx * w * 0.3f, oy + h * 0.015f + dy * h * 0.06f))
                }
                drawRoundRect(ink.copy(alpha = 0.85f * clear), Offset(ox + w * 0.04f, oy + h * 0.035f), Size(w * 0.26f * clear, h * 0.02f), CornerRadius(h * 0.01f))
            }
            "duo" -> {
                // two phones side by side; one picture runs across both; a photo flies from one to the other
                val pw = w * 0.36f
                val ph = h * 0.62f
                val gap = w * 0.03f
                val y0 = h * 0.12f
                val l = Offset(c.x - gap / 2 - pw, y0)
                val r = Offset(c.x + gap / 2, y0 + h * 0.03f)
                val s = stage(p, 0f, 0.3f)
                listOf(l, r).forEachIndexed { k, o ->
                    val slide = (1 - s) * (if (k == 0) -w * 0.2f else w * 0.2f)
                    drawRoundRect(ink.copy(alpha = 0.9f), Offset(o.x + slide, o.y), Size(pw, ph), CornerRadius(w * 0.04f), style = Stroke(4f))
                }
                // the shared circle, cut by the gap between the screens
                val ring = stage(p, 0.3f, 0.6f)
                if (ring > 0f) {
                    val cc = Offset(c.x, y0 + ph * 0.5f)
                    clipRect(l.x + 6f, l.y + 6f, l.x + pw - 6f, l.y + ph - 6f) { drawCircle(ink.copy(alpha = 0.85f), pw * 0.7f * ring, cc, style = Stroke(w * 0.02f)) }
                    clipRect(r.x + 6f, r.y + 6f, r.x + pw - 6f, r.y + ph - 6f) { drawCircle(ink.copy(alpha = 0.85f), pw * 0.7f * ring, cc, style = Stroke(w * 0.02f)) }
                }
                // the throw
                val f = stage(p, 0.62f, 0.92f)
                if (f > 0f && f < 1f) {
                    val x = l.x + pw * 0.5f + (r.x + pw * 0.5f - (l.x + pw * 0.5f)) * f
                    val y = y0 + ph * 0.78f - sin(f * PI.toFloat()) * h * 0.18f
                    rotate(-20f + 40f * f, Offset(x, y)) { drawRoundRect(ink, Offset(x - w * 0.07f, y - w * 0.05f), Size(w * 0.14f, w * 0.1f), CornerRadius(6f)) }
                }
                if (f >= 1f) drawRoundRect(ink, Offset(r.x + pw * 0.5f - w * 0.07f, y0 + ph * 0.78f - w * 0.05f), Size(w * 0.14f, w * 0.1f), CornerRadius(6f))
                // the finger crossing the edge
                val sw = stage(p, 0.2f, 0.45f)
                if (sw in 0.01f..0.99f) drawCircle(ink.copy(alpha = 0.5f), w * 0.025f, Offset(l.x + pw * (0.6f + 0.8f * sw), y0 + ph * 0.3f))
            }
            "concert" -> {
                // four phones, each lit in turn by the wave, with notes rising from them
                val n = 4
                val pw = w * 0.17f
                val ph = h * 0.36f
                val gap = (w - n * pw) / (n + 1)
                val y0 = h * 0.45f
                val beat = (clock * 4f) % 1f
                for (k in 0 until n) {
                    val s = stage(p, k * 0.08f, k * 0.08f + 0.25f)
                    val x = gap + k * (pw + gap)
                    val wave = ((beat - k * 0.12f).let { if (it < 0) it + 1 else it }).let { if (it < 0.3f) 1f - it / 0.3f else 0f } * stage(p, 0.4f, 0.55f)
                    drawRoundRect(ink.copy(alpha = (0.12f + 0.75f * wave) * s), Offset(x, y0 + (1 - s) * h * 0.2f), Size(pw, ph), CornerRadius(w * 0.03f))
                    drawRoundRect(ink.copy(alpha = 0.9f * s), Offset(x, y0 + (1 - s) * h * 0.2f), Size(pw, ph), CornerRadius(w * 0.03f), style = Stroke(3f))
                    // the part, drawn small on each phone
                    val gx = x + pw / 2
                    val gy = y0 + ph * 0.5f
                    val gi = if (wave > 0.5f) bg else ink
                    when (k) {
                        0 -> drawPath(Path().apply { moveTo(gx - pw * 0.3f, gy); cubicTo(gx - pw * 0.1f, gy - ph * 0.15f, gx, gy - ph * 0.15f, gx, gy); cubicTo(gx, gy + ph * 0.15f, gx + pw * 0.1f, gy + ph * 0.15f, gx + pw * 0.3f, gy) }, gi.copy(alpha = s), style = Stroke(4f, cap = StrokeCap.Round))
                        1 -> for (q in 0 until 3) drawLine(gi.copy(alpha = s), Offset(gx - pw * 0.25f, gy - ph * 0.08f + q * ph * 0.08f), Offset(gx + pw * 0.25f, gy - ph * 0.08f + q * ph * 0.08f), 4f, StrokeCap.Round)
                        2 -> drawPath(Path().apply { moveTo(gx - pw * 0.28f, gy - ph * 0.05f); quadraticTo(gx, gy + ph * 0.2f, gx + pw * 0.28f, gy - ph * 0.05f) }, gi.copy(alpha = s), style = Stroke(5f, cap = StrokeCap.Round))
                        else -> { drawCircle(gi.copy(alpha = s), pw * 0.09f, Offset(gx - pw * 0.15f, gy)); drawCircle(gi.copy(alpha = s), pw * 0.06f, Offset(gx + pw * 0.05f, gy)); drawCircle(gi.copy(alpha = s), pw * 0.04f, Offset(gx + pw * 0.2f, gy)) }
                    }
                    // notes rising
                    val rise = stage(p, 0.5f, 1f)
                    for (q in 0 until 2) {
                        val f = ((clock * 1.5f + k * 0.25f + q * 0.5f) % 1f)
                        val a = rise * (1 - f)
                        val nx = gx + sin((f + k) * tau) * pw * 0.2f
                        val ny = y0 - f * h * 0.35f
                        drawCircle(ink.copy(alpha = a), w * 0.014f, Offset(nx, ny))
                        drawLine(ink.copy(alpha = a), Offset(nx + w * 0.013f, ny), Offset(nx + w * 0.013f, ny - h * 0.06f), 3f, StrokeCap.Round)
                    }
                }
            }
            "kids" -> {
                // the parent's monolith and the child's, joined by a line of dots
                val base = h * 0.8f
                drawRect(ink.copy(alpha = 0.3f), Offset(w * 0.12f, base), Size(w * 0.76f, 2.5f))
                monolith(w * 0.38f, base, w * 0.17f, h * 0.52f, ink, stage(p, 0f, 0.35f))
                monolith(w * 0.66f, base, w * 0.1f, h * 0.25f, ink.copy(alpha = 0.8f), stage(p, 0.2f, 0.5f))
                val s = stage(p, 0.45f, 0.9f)
                val p0 = Offset(w * 0.44f, base - h * 0.6f)
                val p2 = Offset(w * 0.69f, base - h * 0.33f)
                val cc = Offset(w * 0.62f, base - h * 0.78f)
                val n = 11
                for (k in 0 until n) {
                    val f = k / (n - 1f)
                    if (f > s) break
                    val q = 1 - f
                    val pt = Offset(q * q * p0.x + 2 * q * f * cc.x + f * f * p2.x, q * q * p0.y + 2 * q * f * cc.y + f * f * p2.y)
                    val tw = 0.55f + 0.45f * sin(clock * tau * 2 - k * 0.6f)
                    drawCircle(ink.copy(alpha = 0.7f * tw), min(w, h) * 0.012f, pt)
                }
            }
            "colors" -> {
                // the only scene in colour: the twelve themes around the monolith
                val darkBg = (bg.red + bg.green + bg.blue) / 3f < 0.5f
                val accents = com.monolith.app.ui.theme.Palettes.ALL.drop(1).map { Color(if (darkBg) it.dark else it.light) }
                val ring = min(w, h) * 0.36f
                val dot = min(w, h) * 0.07f
                accents.forEachIndexed { k, col ->
                    val s = stage(p, k * 0.035f, k * 0.035f + 0.22f)
                    val a = tau * k / accents.size - tau / 4 + clock * tau * 0.15f
                    drawCircle(col, dot * s, Offset(c.x + cos(a) * ring, c.y + sin(a) * ring))
                }
                drawCircle(ink.copy(alpha = 0.12f), ring, c, style = Stroke(2f))
                monolith(c.x, c.y + h * 0.15f, w * 0.13f, h * 0.3f, ink, stage(p, 0.3f, 0.65f))
            }
            "agenda" -> {
                val cols = 7
                val rows = 5
                val gw = w * 0.9f
                val cell = gw / cols
                val ox = (w - gw) / 2
                val oy = h * 0.12f
                for (r in 0 until rows) for (k in 0 until cols) {
                    val i = r * cols + k
                    val s = stage(p, i * 0.012f, i * 0.012f + 0.2f)
                    val x = ox + k * cell + cell * 0.08f
                    val y = oy + r * cell + cell * 0.08f
                    val sz = cell * 0.84f * s
                    val ev = i in setOf(4, 11, 16, 23, 30)
                    drawRoundRect(ink.copy(alpha = if (ev) 0.9f else 0.12f), Offset(x + (cell * 0.84f - sz) / 2, y + (cell * 0.84f - sz) / 2), Size(sz, sz), CornerRadius(cell * 0.18f))
                    if (ev && s > 0.9f) drawCircle(bg, cell * 0.08f, Offset(x + cell * 0.42f, y + cell * 0.42f))
                }
                // countdown pill counting down
                val n = (12 - (stage(p, 0.4f, 0.9f) * 9).toInt()).coerceAtLeast(3)
                val pill = stage(p, 0.35f, 0.5f)
                drawRoundRect(ink, Offset(w * 0.55f, h * 0.82f), Size(w * 0.4f * pill, h * 0.13f), CornerRadius(h * 0.065f))
                if (pill > 0.9f) {
                    for (d in 0 until n.coerceAtMost(12)) drawCircle(bg, 3.5f, Offset(w * 0.6f + d * (w * 0.3f / 12f), h * 0.885f))
                }
            }
            "chat" -> {
                val items = listOf(true to 0.55f, false to 0.7f, true to 0.4f, false to 0.5f)
                items.forEachIndexed { k, (left, len) ->
                    val s = stage(p, k * 0.12f, k * 0.12f + 0.25f)
                    val bw = w * len
                    val x = if (left) w * 0.04f else w * 0.96f - bw
                    val y = h * 0.06f + k * h * 0.15f
                    drawRoundRect(ink.copy(alpha = if (left) 0.18f else 0.9f), Offset(x + (if (left) -1 else 1) * (1 - s) * 80f, y), Size(bw, h * 0.11f), CornerRadius(h * 0.055f), alpha = s)
                }
                // tic-tac-toe drawing itself
                val g = w * 0.36f
                val gx = c.x - g / 2
                val gy = h * 0.66f
                val lines = listOf(
                    Offset(gx + g / 3, gy) to Offset(gx + g / 3, gy + g * 0.9f), Offset(gx + 2 * g / 3, gy) to Offset(gx + 2 * g / 3, gy + g * 0.9f),
                    Offset(gx, gy + g * 0.3f) to Offset(gx + g, gy + g * 0.3f), Offset(gx, gy + g * 0.6f) to Offset(gx + g, gy + g * 0.6f),
                )
                lines.forEachIndexed { k, (a, b) ->
                    val s = stage(p, 0.45f + k * 0.04f, 0.6f + k * 0.04f)
                    drawLine(ink.copy(alpha = 0.6f), a, a + (b - a) * s, 4f, StrokeCap.Round)
                }
                val xo = stage(p, 0.7f, 0.85f)
                if (xo > 0) drawCircle(ink, g * 0.1f * xo, Offset(gx + g / 6, gy + g * 0.15f), style = Stroke(5f))
                val xx = stage(p, 0.8f, 0.95f)
                if (xx > 0) {
                    val cx2 = gx + g / 2
                    val cy2 = gy + g * 0.45f
                    val r = g * 0.1f
                    drawLine(ink, Offset(cx2 - r, cy2 - r), Offset(cx2 - r + 2 * r * xx, cy2 - r + 2 * r * xx), 5f, StrokeCap.Round)
                }
            }
            "stories" -> {
                val rr = min(w, h) * 0.13f
                for (k in 0 until 3) {
                    val s = stage(p, k * 0.1f, k * 0.1f + 0.3f)
                    val center = Offset(w * (0.22f + k * 0.28f), h * 0.22f)
                    drawArc(ink, -90f, 360f * s, false, center - Offset(rr, rr), Size(rr * 2, rr * 2), style = Stroke(5f, cap = StrokeCap.Round))
                    drawCircle(ink.copy(alpha = 0.15f), rr * 0.78f, center)
                }
                // vinyl turning
                val vr = min(w, h) * 0.22f
                val vc = Offset(c.x, h * 0.68f)
                val vin = stage(p, 0.35f, 0.6f)
                rotate(clock * 360f * 2, vc) {
                    drawCircle(ink, vr * vin, vc)
                    for (k in 1..5) drawCircle(bg.copy(alpha = 0.18f), vr * vin * (0.45f + k * 0.1f), vc, style = Stroke(1.5f))
                    drawCircle(bg, vr * vin * 0.28f, vc)
                    drawLine(bg.copy(alpha = 0.6f), vc, vc + Offset(vr * vin * 0.9f, 0f), 2f)
                }
                for (k in 0 until 5) {
                    val bh = (0.3f + 0.7f * kotlin.math.abs(sin(clock * tau * 3 + k))) * h * 0.1f * vin
                    drawRoundRect(ink, Offset(w * 0.78f + k * 10f, h * 0.9f - bh), Size(6f, bh), CornerRadius(3f))
                }
            }
            "calls" -> {
                val people = listOf(Offset(w * 0.3f, h * 0.35f), Offset(w * 0.7f, h * 0.35f), Offset(w * 0.3f, h * 0.7f), Offset(w * 0.7f, h * 0.7f))
                people.forEachIndexed { k, o ->
                    val s = stage(p, k * 0.1f, k * 0.1f + 0.25f)
                    val talk = k == ((clock * 4).toInt() % 4)
                    if (talk) for (q in 0 until 2) {
                        val f = (clock * 8 + q / 2f) % 1f
                        drawCircle(ink.copy(alpha = 0.3f * (1 - f)), w * 0.1f + f * w * 0.08f, o, style = Stroke(3f))
                    }
                    drawCircle(ink, w * 0.09f * s, o)
                }
                val sh = stage(p, 0.55f, 0.8f)
                drawRoundRect(ink.copy(alpha = 0.9f), Offset(c.x - w * 0.14f, h * 0.05f), Size(w * 0.28f * sh, h * 0.14f), CornerRadius(12f), style = Stroke(4f))
            }
            "map" -> {
                for (k in 0 until 6) {
                    drawLine(ink.copy(alpha = 0.1f), Offset(0f, h * k / 5f), Offset(w, h * k / 5f + h * 0.1f), 2f)
                    drawLine(ink.copy(alpha = 0.1f), Offset(w * k / 5f, 0f), Offset(w * k / 5f - w * 0.08f, h), 2f)
                }
                val place = Offset(w * 0.62f, h * 0.45f)
                val pr = stage(p, 0f, 0.3f)
                drawCircle(ink.copy(alpha = 0.12f), w * 0.2f * pr, place)
                drawCircle(ink.copy(alpha = 0.5f), w * 0.2f * pr, place, style = Stroke(3f))
                val route = Path().apply { moveTo(w * 0.08f, h * 0.9f); cubicTo(w * 0.2f, h * 0.5f, w * 0.4f, h * 0.8f, place.x, place.y) }
                val mv = stage(p, 0.2f, 0.85f)
                partialPath(route, mv, ink, 6f)
                val m = PathMeasure().apply { setPath(route, false) }
                val pos = m.getPosition(m.length * mv)
                drawCircle(ink, 14f, pos)
                drawCircle(bg, 6f, pos)
                // compass arrow
                rotate(sin(clock * tau) * 25f, Offset(w * 0.18f, h * 0.18f)) {
                    drawPath(Path().apply { moveTo(w * 0.18f, h * 0.08f); lineTo(w * 0.23f, h * 0.24f); lineTo(w * 0.18f, h * 0.2f); lineTo(w * 0.13f, h * 0.24f); close() }, ink)
                }
            }
            "bond" -> {
                val heights = listOf(0.18f, 0.3f, 0.44f, 0.6f, 0.8f)
                heights.forEachIndexed { k, f ->
                    val s = stage(p, k * 0.12f, k * 0.12f + 0.3f)
                    monolith(w * (0.12f + k * 0.18f), h * 0.92f, w * 0.1f, h * f, ink.copy(alpha = 0.35f + k * 0.16f), s)
                }
                val fl = stage(p, 0.7f, 0.9f)
                drawCircle(ink, 20f * fl, Offset(w * 0.86f, h * 0.06f + 30f))
                drawCircle(bg, 8f * fl, Offset(w * 0.86f, h * 0.06f + 30f))
            }
            "profile" -> {
                // cover sliding in, avatar popping over it, a row of badges
                val cv = stage(p, 0f, 0.3f)
                drawRoundRect(ink.copy(alpha = 0.22f), Offset(0f, h * 0.05f), Size(w * cv, h * 0.34f), CornerRadius(26f))
                for (k in 0 until 5) {
                    val y = h * 0.1f + k * h * 0.06f
                    drawLine(ink.copy(alpha = 0.18f * cv), Offset(w * 0.05f, y + (clock * 40f + k * 13f) % 30f), Offset(w * 0.95f * cv, y), 3f, StrokeCap.Round)
                }
                val av = stage(p, 0.25f, 0.45f)
                drawCircle(bg, w * 0.15f * av, Offset(c.x, h * 0.4f))
                drawCircle(ink, w * 0.13f * av, Offset(c.x, h * 0.4f), style = Stroke(6f))
                drawCircle(ink, w * 0.045f * av, Offset(c.x, h * 0.37f))
                for (k in 0 until 4) {
                    val s = stage(p, 0.45f + k * 0.08f, 0.62f + k * 0.08f)
                    val bc = Offset(w * (0.2f + k * 0.2f), h * 0.78f)
                    val r = w * 0.07f * s
                    val hex = Path().apply {
                        for (q in 0 until 6) {
                            val a = q / 6f * tau - tau / 4
                            val pt = bc + Offset(cos(a) * r, sin(a) * r)
                            if (q == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                        close()
                    }
                    if (k % 2 == 0) drawPath(hex, ink) else drawPath(hex, ink, style = Stroke(5f))
                }
            }
            "shard" -> {
                val railW = w * 0.2f
                drawRoundRect(ink.copy(alpha = 0.1f), Offset(0f, 0f), Size(railW, h), CornerRadius(24f))
                for (k in 0 until 4) {
                    val s = stage(p, k * 0.1f, k * 0.1f + 0.25f)
                    val cy = h * 0.12f + k * h * 0.2f
                    val cx = railW / 2
                    val r = railW * 0.32f * s
                    drawPath(Path().apply { moveTo(cx, cy - r * 1.2f); lineTo(cx + r, cy - r * 0.2f); lineTo(cx - r * 0.1f, cy + r * 1.2f); lineTo(cx - r, cy); close() }, ink.copy(alpha = if (k == 1) 1f else 0.5f))
                }
                for (k in 0 until 5) {
                    val s = stage(p, 0.3f + k * 0.08f, 0.5f + k * 0.08f)
                    val y = h * 0.1f + k * h * 0.17f
                    drawCircle(ink.copy(alpha = 0.6f), 6f * s, Offset(railW + w * 0.1f, y + 10f))
                    drawRoundRect(ink.copy(alpha = if (k < 3) 0.8f else 0.35f), Offset(railW + w * 0.16f, y), Size((w * 0.55f - k * 18f) * s, 20f), CornerRadius(10f))
                }
            }
            "together" -> {
                for (k in 0 until 6) {
                    val s = stage(p, k * 0.07f, k * 0.07f + 0.35f)
                    val a = k / 6f * tau + clock * tau * 0.5f
                    val from = c + Offset(cos(a) * w * 0.45f, sin(a) * h * 0.4f)
                    drawCircle(ink.copy(alpha = 0.8f), 16f, from + (c - from) * s * 0.7f, style = Stroke(4f))
                }
                for (k in 0 until 3) {
                    val s = stage(p, 0.4f + k * 0.12f, 0.6f + k * 0.12f)
                    rotate((k - 1) * 12f * s, c) {
                        drawRoundRect(ink, Offset(c.x - w * 0.14f, c.y - h * 0.14f), Size(w * 0.28f, h * 0.28f), CornerRadius(14f), style = Stroke(5f), alpha = s)
                    }
                }
            }
            "safe" -> {
                val shield = Path().apply {
                    moveTo(c.x, h * 0.08f); lineTo(c.x + w * 0.3f, h * 0.2f); lineTo(c.x + w * 0.26f, h * 0.6f)
                    quadraticBezierTo(c.x + w * 0.15f, h * 0.82f, c.x, h * 0.92f); quadraticBezierTo(c.x - w * 0.15f, h * 0.82f, c.x - w * 0.26f, h * 0.6f)
                    lineTo(c.x - w * 0.3f, h * 0.2f); close()
                }
                partialPath(shield, stage(p, 0f, 0.55f), ink, 7f)
                val ck = stage(p, 0.55f, 0.8f)
                val check = Path().apply { moveTo(c.x - w * 0.1f, h * 0.5f); lineTo(c.x - w * 0.02f, h * 0.6f); lineTo(c.x + w * 0.13f, h * 0.38f) }
                partialPath(check, ck, ink, 10f)
                for (q in 0 until 2) {
                    val f = (clock * 3 + q / 2f) % 1f
                    drawCircle(ink.copy(alpha = 0.15f * (1 - f) * ck), w * 0.35f + f * w * 0.2f, c, style = Stroke(3f))
                }
            }
            "phone" -> {
                val hit = stage(p, 0.3f, 0.5f)
                val left = Offset(w * 0.15f + hit * w * 0.22f, c.y)
                val right = Offset(w * 0.85f - hit * w * 0.22f, c.y)
                rotate(-15f + hit * 15f, left) { drawRoundRect(ink, left - Offset(w * 0.09f, h * 0.2f), Size(w * 0.18f, h * 0.4f), CornerRadius(20f), style = Stroke(6f)) }
                rotate(15f - hit * 15f, right) { drawRoundRect(ink, right - Offset(w * 0.09f, h * 0.2f), Size(w * 0.18f, h * 0.4f), CornerRadius(20f), style = Stroke(6f)) }
                val burst = stage(p, 0.5f, 0.75f)
                if (burst > 0) for (k in 0 until 10) {
                    val a = k / 10f * tau
                    val r1 = w * 0.08f + burst * w * 0.05f
                    val r2 = r1 + w * 0.06f * (1 - burst)
                    drawLine(ink.copy(alpha = 1 - burst * 0.6f), c + Offset(cos(a) * r1, sin(a) * r1), c + Offset(cos(a) * r2, sin(a) * r2), 5f, StrokeCap.Round)
                }
            }
            else -> { // wrapped
                for (k in 0 until 4) {
                    val s = stage(p, k * 0.1f, k * 0.1f + 0.35f)
                    rotate((k - 1.5f) * 9f * s, Offset(c.x, h * 0.95f)) {
                        drawRoundRect(if (k % 2 == 0) ink else ink.copy(alpha = 0.4f), Offset(c.x - w * 0.18f, h * 0.12f), Size(w * 0.36f, h * 0.7f), CornerRadius(22f), alpha = s)
                    }
                }
                for (k in 0 until 7) {
                    val s = stage(p, 0.5f + k * 0.04f, 0.75f + k * 0.04f)
                    val bh = h * (0.1f + ((k * 37) % 11) / 30f) * s
                    drawRoundRect(bg, Offset(c.x - w * 0.14f + k * w * 0.04f, h * 0.7f - bh), Size(w * 0.025f, bh), CornerRadius(4f))
                }
            }
        }
    }
}

private fun Modifier.fillMaxHeight88() = this.then(Modifier.fillMaxSize(0.88f))

/** the film as a screen, from Settings */
@Composable
fun FilmScreen(onClose: () -> Unit) {
    FeatureFilm(onDone = onClose, doneLabel = t("Chiudi"))
}
