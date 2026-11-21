package com.monolith.app.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.monolith.app.core.I18n
import com.monolith.app.core.t
import com.monolith.app.net.WeekRecap
import com.monolith.app.net.Weekly
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.tiltDepth
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SLIDE_MS = 6500

private fun slidesFor(r: WeekRecap): List<String> = buildList {
    add("intro")
    if (r.quiet) {
        add("quiet")
    } else {
        if (r.messages > 0) add("messages")
        if (r.people.isNotEmpty()) add("people")
        if (r.bond != null) add("bond")
        if (r.emojis.isNotEmpty()) add("emoji")
        if (r.calls > 0) add("calls")
        if (r.messages > 0) add("rhythm")
        if (r.arrivals > 0) add("places")
        if (r.photos + r.voices > 0) add("photos")
        if (r.events > 0 || r.habitDays > 0) add("plans")
        if (r.newFriends.isNotEmpty()) add("friends")
    }
    add("final")
}

private fun num(n: Int): String = NumberFormat.getIntegerInstance(I18n.locale).format(n)
private fun dateFmt(ts: Long, pattern: String) =
    SimpleDateFormat(pattern, I18n.locale).format(Date(ts)).replaceFirstChar { it.titlecase(I18n.locale) }

/** The Sunday recap: full-screen slides that move on by themselves, like stories. */
@Composable
fun WrappedScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val recap by produceState<WeekRecap?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { Weekly.compute() }.getOrNull() }
    }
    LaunchedEffect(Unit) { Weekly.markSeen(ctx) }
    val r = recap
    if (r == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF050505)), contentAlignment = Alignment.Center) {
            Text("MONOLITH", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp)
        }
        return
    }
    val slides = remember(r) { slidesFor(r) }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(-1) }
    fun next() { if (index < slides.lastIndex) index++ else nav.popBackStack() }
    fun prev() { if (index > 0) index-- }
    LaunchedEffect(index, paused) {
        if (shown != index) { shown = index; progress.snapTo(0f) }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_MS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        // the last slide stays until you close it
        if (index < slides.lastIndex) index++
    }
    val dark = index % 2 == 0
    val bg by animateColorAsState(if (dark) Color(0xFF050505) else Color(0xFFF2F2F2), tween(550), label = "bg")
    val ink by animateColorAsState(if (dark) Color(0xFFF2F2F2) else Color(0xFF0A0A0A), tween(550), label = "ink")
    val id = slides[index]

    Box(Modifier.fillMaxSize().background(bg)) {
        WeekArt(id, ink, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().pointerInput(slides.size) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o -> if (o.x < size.width / 3f) prev() else next() },
                )
            },
        )
        key(index) {
            Box(
                Modifier.fillMaxSize().tiltDepth(6f, 3f).statusBarsPadding().navigationBarsPadding()
                    .padding(start = 28.dp, end = 28.dp, top = 72.dp, bottom = 28.dp),
            ) {
                Slide(id, r, ink, bg, onClose = { nav.popBackStack() }, onShare = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r.shareText())
                    runCatching { ctx.startActivity(Intent.createChooser(send, t("Condividi"))) }
                })
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.forEachIndexed { i, _ ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.25f))) {
                        val f = when {
                            i < index -> 1f
                            i == index -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(ink))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MONOLITH  ·  " + dateFmt(r.from, "d MMM") + " – " + dateFmt(r.to, "d MMM"),
                    color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, t("Chiudi"), tint = ink) }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Rise(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(750, easing = Motion.EmphasizedDecel))
    }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 44.dp.toPx() }) { content() }
}

@Composable
private fun CountUp(n: Int, ink: Color, size: TextUnit = 112.sp, delayMs: Int = 250) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(n) {
        delay(delayMs.toLong())
        a.animateTo(n.toFloat(), tween(1500, easing = FastOutSlowInEasing))
    }
    Text(
        num(a.value.roundToInt()), color = ink, fontSize = size, fontWeight = FontWeight.Black,
        lineHeight = size, letterSpacing = (-2).sp, maxLines = 1,
    )
}

@Composable
private fun Kicker(s: String, ink: Color) {
    Text(s.uppercase(I18n.locale), color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp)
}

@Composable
private fun Big(s: String, ink: Color, size: TextUnit = 44.sp) {
    Text(s, color = ink, fontSize = size, fontWeight = FontWeight.Black, lineHeight = size * 1.08f, letterSpacing = (-1).sp)
}

@Composable
private fun Line(s: String, ink: Color, alpha: Float = 0.75f) {
    Text(s, color = ink.copy(alpha = alpha), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Face(emoji: String, size: Dp, ink: Color) {
    Box(
        Modifier.size(size).clip(CircleShape).background(ink.copy(alpha = 0.08f)).border(2.dp, ink.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.48f).sp) }
}

@Composable
private fun Pulse(content: @Composable () -> Unit) {
    val tr = rememberInfiniteTransition(label = "pulse")
    val s by tr.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }) { content() }
}

// ---------------------------------------------------------------- slides

@Composable
private fun Slide(id: String, r: WeekRecap, ink: Color, bg: Color, onClose: () -> Unit, onShare: () -> Unit) {
    val top = Modifier.fillMaxSize()
    when (id) {
        "intro" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Il tuo riepilogo"), ink) }
            Spacer(Modifier.height(12.dp))
            Rise(150) { Big(t("La tua\nsettimana"), ink, 60.sp) }
            Spacer(Modifier.height(16.dp))
            Rise(450) { Line(dateFmt(r.from, "d MMMM") + " – " + dateFmt(r.to, "d MMMM"), ink) }
            Spacer(Modifier.height(40.dp))
            Rise(1100) { Line(t("Tocca per andare avanti"), ink, 0.45f) }
        }

        "quiet" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Settimana tranquilla"), ink) }
            Spacer(Modifier.height(18.dp))
            Rise(150) { Pulse { Text("🗿", fontSize = 110.sp) } }
            Spacer(Modifier.height(18.dp))
            Rise(400) { Big(t("Pochi messaggi, nessuna fretta."), ink, 36.sp) }
            Spacer(Modifier.height(12.dp))
            Rise(800) { Line(t("Scrivi a un amico: domenica prossima avrai qualcosa da raccontare"), ink) }
        }

        "messages" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Messaggi"), ink) }
            Spacer(Modifier.height(10.dp))
            if (r.sent > 0) {
                Rise(100) { Line(t("Hai scritto"), ink) }
                CountUp(r.sent, ink, 120.sp)
                Rise(300) { Big(t("messaggi"), ink, 40.sp) }
                if (r.received > 0) {
                    Spacer(Modifier.height(22.dp))
                    Rise(1700) { Line(t("e ne hai ricevuti {0}", num(r.received)), ink) }
                }
            } else {
                Rise(100) { Line(t("Hai ricevuto"), ink) }
                CountUp(r.received, ink, 120.sp)
                Rise(300) { Big(t("messaggi"), ink, 40.sp) }
            }
        }

        "people" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Le tue persone"), ink) }
            Spacer(Modifier.height(10.dp))
            Rise(100) { Big(t("Con chi hai parlato di più"), ink, 34.sp) }
            Spacer(Modifier.height(28.dp))
            val first = r.people.first()
            Rise(400) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pulse { Face(first.emoji, 96.dp, ink) }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Big(first.name, ink, 32.sp)
                        Line(t("{0} messaggi", num(first.n)), ink, 0.6f)
                    }
                }
            }
            r.people.drop(1).forEachIndexed { i, p ->
                Spacer(Modifier.height(16.dp))
                Rise(900 + i * 250) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 2}", color = ink.copy(alpha = 0.45f), fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
                        Face(p.emoji, 48.dp, ink)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, color = ink, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(num(p.n), color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        "bond" -> Column(top, verticalArrangement = Arrangement.Center) {
            val (p, b) = r.bond!!
            Rise(0) { Kicker(t("Il legame più forte"), ink) }
            Spacer(Modifier.height(10.dp))
            Rise(100) { Big(p.name, ink, 40.sp) }
            Spacer(Modifier.height(24.dp))
            Rise(350) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BondStone(b, Modifier.size(110.dp, 150.dp).tiltDepth(8f, 14f))
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Big(b.name, ink, 36.sp)
                        Line(t("{0} giorni insieme nell'ultimo mese", b.days30), ink, 0.65f)
                    }
                }
            }
            if (b.streak > 1) {
                Spacer(Modifier.height(26.dp))
                Rise(1300) { Line(t("🔥 {0} giorni di fila", b.streak), ink) }
            }
        }

        "emoji" -> Column(top, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Rise(0) { Kicker(t("La tua emoji"), ink) }
            Spacer(Modifier.height(20.dp))
            val (e, n) = r.emojis.first()
            Rise(200) { Pulse { Text(e, fontSize = 150.sp, lineHeight = 160.sp) } }
            Spacer(Modifier.height(16.dp))
            Rise(600) { Line(t("usata {0} volte", num(n)), ink) }
            if (r.emojis.size > 1) {
                Spacer(Modifier.height(36.dp))
                Rise(1100) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        r.emojis.drop(1).forEach { (o, c) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(o, fontSize = 34.sp)
                                Text(num(c), color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        "calls" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Chiamate"), ink) }
            Spacer(Modifier.height(10.dp))
            CountUp(r.callMinutes, ink, 120.sp)
            Rise(300) { Big(t("minuti di voce"), ink, 40.sp) }
            Spacer(Modifier.height(18.dp))
            Rise(1600) { Line(if (r.calls == 1) t("in una chiamata") else t("in {0} chiamate", num(r.calls)), ink) }
        }

        "rhythm" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Il tuo ritmo"), ink) }
            Spacer(Modifier.height(10.dp))
            val best = r.days.maxBy { it.second }
            Rise(100) { Line(t("Il giorno più vivo è stato"), ink) }
            Rise(250) { Big(dateFmt(best.first, "EEEE"), ink, 52.sp) }
            Spacer(Modifier.height(28.dp))
            DayBars(r.days, ink)
            if (r.hour >= 0) {
                Spacer(Modifier.height(24.dp))
                Rise(1500) { Line(t("L'ora con più messaggi: le {0}", "%02d:00".format(r.hour)), ink) }
            }
        }

        "places" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Luoghi comuni"), ink) }
            Spacer(Modifier.height(10.dp))
            CountUp(r.arrivals, ink, 120.sp)
            Rise(300) { Big(if (r.arrivals == 1) t("arrivo") else t("arrivi"), ink, 40.sp) }
            r.places.firstOrNull()?.let { (label, n) ->
                Spacer(Modifier.height(26.dp))
                Rise(1300) {
                    Column {
                        Line(t("Il posto della settimana"), ink, 0.55f)
                        Big(label, ink, 34.sp)
                        Line(t("{0} volte", num(n)), ink, 0.55f)
                    }
                }
            }
        }

        "photos" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Foto e vocali"), ink) }
            Spacer(Modifier.height(10.dp))
            if (r.photos > 0) {
                CountUp(r.photos, ink, 120.sp)
                Rise(300) { Big(t("foto condivise"), ink, 40.sp) }
                if (r.voices > 0) {
                    Spacer(Modifier.height(18.dp))
                    Rise(1500) { Line(t("e {0} messaggi vocali", num(r.voices)), ink) }
                }
            } else {
                CountUp(r.voices, ink, 120.sp)
                Rise(300) { Big(t("messaggi vocali"), ink, 40.sp) }
            }
        }

        "plans" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Agenda e abitudini"), ink) }
            Spacer(Modifier.height(18.dp))
            if (r.events > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(r.events, ink, 84.sp)
                    Spacer(Modifier.width(12.dp))
                    Rise(300) { Line(if (r.events == 1) t("evento") else t("eventi"), ink) }
                }
                if (r.eventsDone > 0) Rise(1200) { Line(t("{0} segnati come fatti", num(r.eventsDone)), ink, 0.55f) }
                Spacer(Modifier.height(26.dp))
            }
            if (r.habitDays > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(r.habitDays, ink, 84.sp, delayMs = 700)
                    Spacer(Modifier.width(12.dp))
                    Rise(900) { Line(t("abitudini spuntate"), ink) }
                }
                r.bestHabit?.let { (e, name, streak) ->
                    Rise(1600) { Line(t("Serie migliore: {0} {1}, {2} giorni di fila", e, name, streak), ink, 0.55f) }
                }
            }
        }

        "friends" -> Column(top, verticalArrangement = Arrangement.Center) {
            Rise(0) { Kicker(t("Nuovi amici"), ink) }
            Spacer(Modifier.height(10.dp))
            Rise(100) { Big(t("Sono entrati nella tua cerchia"), ink, 34.sp) }
            Spacer(Modifier.height(24.dp))
            r.newFriends.take(5).forEachIndexed { i, f ->
                Rise(450 + i * 220) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Face(f.emoji, 52.dp, ink)
                        Spacer(Modifier.width(14.dp))
                        Text(f.name, color = ink, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        else -> FinalSlide(r, ink, bg, onClose, onShare)
    }
}

@Composable
private fun FinalSlide(r: WeekRecap, ink: Color, bg: Color, onClose: () -> Unit, onShare: () -> Unit) {
    val (emoji, title, subtitle) = remember(r) { r.persona() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Rise(0) { Kicker(t("La tua settimana in una parola"), ink) }
        Spacer(Modifier.height(14.dp))
        Rise(150) { Pulse { Text(emoji, fontSize = 84.sp, lineHeight = 92.sp) } }
        Spacer(Modifier.height(8.dp))
        Rise(350) { Big(title, ink, 42.sp) }
        Spacer(Modifier.height(6.dp))
        Rise(550) { Line(subtitle, ink, 0.65f) }
        Spacer(Modifier.height(28.dp))
        Rise(800) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(r.messages, t("messaggi"), ink, Modifier.weight(1f))
                    Tile(r.callMinutes, t("minuti di chiamata"), ink, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(r.photos, t("foto"), ink, Modifier.weight(1f))
                    Tile(r.arrivals, t("arrivi"), ink, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Rise(1200) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = bg),
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Condividi"))
                }
                OutlinedButton(onClick = onClose, colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)) { Text(t("Chiudi")) }
            }
        }
    }
}

@Composable
private fun Tile(n: Int, label: String, ink: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).border(1.5.dp, ink.copy(alpha = 0.22f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CountUp(n, ink, 30.sp, delayMs = 900)
        Text(label, color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DayBars(days: List<Pair<Long, Int>>, ink: Color) {
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val best = days.indices.maxBy { days[it].second }
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, (ts, n) ->
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(350L + i * 90L)
                a.animateTo(1f, tween(900, easing = Motion.EmphasizedDecel))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight((n.toFloat() / max * a.value * 0.8f).coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(8.dp)).background(if (i == best) ink else ink.copy(alpha = 0.28f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(dateFmt(ts, "EEEEE"), color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** quiet moving shapes behind each slide, always in the same ink as the text */
@Composable
private fun WeekArt(id: String, ink: Color, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "art")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "ph")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val tau = (2 * PI).toFloat()
        when (id) {
            "messages" -> {
                val cols = 6
                for (i in 0 until cols) {
                    val x = w * (i + 0.5f) / cols
                    val speed = 1f + (i % 3) * 0.5f
                    for (j in 0 until 6) {
                        val y = ((ph * speed + j / 6f + i * 0.13f) % 1f) * (h + 160f) - 80f
                        val bw = 54f + (i * 7 + j * 13) % 46
                        drawRoundRect(ink.copy(alpha = 0.07f), Offset(x - bw / 2, y), Size(bw, 28f), CornerRadius(14f))
                    }
                }
            }
            "people", "friends" -> {
                val center = Offset(w / 2, h * 0.45f)
                for (k in 1..3) {
                    val rad = w * 0.17f * k
                    drawCircle(ink.copy(alpha = 0.07f), rad, center, style = Stroke(2f))
                    val dir = if (k % 2 == 0) -1f else 1f
                    val a = ph * tau * dir * (4 - k) + k
                    drawCircle(ink.copy(alpha = 0.2f), 10f, center + Offset(cos(a), sin(a)) * rad)
                }
            }
            "emoji", "intro", "quiet" -> {
                val center = Offset(w / 2, if (id == "emoji") h * 0.42f else h * 0.85f)
                for (k in 0 until 5) {
                    val f = (ph * 4 + k / 5f) % 1f
                    drawCircle(ink.copy(alpha = 0.12f * (1 - f)), f * w * 0.95f, center, style = Stroke(3f))
                }
            }
            "places" -> {
                val center = Offset(w / 2, h * 0.7f)
                for (k in 1..4) drawCircle(ink.copy(alpha = 0.07f), w * 0.12f * k, center, style = Stroke(2f))
                val a = ph * tau * 5
                drawLine(ink.copy(alpha = 0.16f), center, center + Offset(cos(a), sin(a)) * (w * 0.48f), strokeWidth = 3f)
                drawCircle(ink.copy(alpha = 0.2f), 8f, center)
            }
            "calls" -> {
                for (k in 0 until 4) {
                    val path = Path()
                    val baseY = h * (0.7f + k * 0.06f)
                    val amp = 24f + k * 10f
                    var x = 0f
                    path.moveTo(0f, baseY)
                    while (x <= w + 12f) {
                        path.lineTo(x, baseY + sin(x / w * 2 * tau + ph * tau * (k + 2) + k) * amp)
                        x += 12f
                    }
                    drawPath(path, ink.copy(alpha = 0.1f), style = Stroke(3f))
                }
            }
            "rhythm", "photos" -> {
                val step = 48f
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        val d = (sin((x + y) / (w + h) * 2 * tau - ph * tau * 4) + 1f) / 2f
                        drawCircle(ink.copy(alpha = 0.04f + 0.08f * d), 2.5f + 3f * d, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            else -> {
                val n = 6
                val bw = w / (n * 2.2f)
                for (i in 0 until n) {
                    val x = w * (i + 0.5f) / n - bw / 2
                    val bh = h * (0.16f + ((i * 37) % 23) / 70f)
                    val lift = sin(ph * tau * 2 + i) * 20f
                    drawRoundRect(ink.copy(alpha = 0.05f + (i % 2) * 0.03f), Offset(x, h - bh + lift), Size(bw, bh + 40f), CornerRadius(bw / 2.5f))
                }
            }
        }
    }
}

/** Clubhouse card shown after the Sunday notification, until you open the recap */
@Composable
fun WeeklyCard(onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primary).bounceClick(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t("La tua settimana è pronta"), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
            Text(t("Tocca per scoprire com'è andata"), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
