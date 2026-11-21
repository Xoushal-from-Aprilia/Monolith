package com.monolith.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.Message
import com.monolith.app.net.Games
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ====================================================================== preferences of the chat screen

object ChatLooks {
    val PATTERNS = listOf("none", "dots", "grid", "lines", "waves", "stones", "rings", "stars")
    private val sp by lazy { Graph.app.getSharedPreferences("chat_looks", Context.MODE_PRIVATE) }
    private val _walls = MutableStateFlow<Map<String, String>>(emptyMap())
    val walls: StateFlow<Map<String, String>> = _walls.asStateFlow()
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        _walls.value = sp.all.filterKeys { it.startsWith("w:") }.mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("w:") to it } }.toMap()
    }

    fun wallsFlow(): StateFlow<Map<String, String>> { load(); return walls }

    fun setWall(conv: String, pattern: String) {
        load()
        sp.edit().putString("w:$conv", pattern).apply()
        _walls.value = _walls.value + (conv to pattern)
    }

    var shake: Boolean
        get() = sp.getBoolean("shake", true)
        set(v) { sp.edit().putBoolean("shake", v).apply() }
}

// ====================================================================== wallpaper

fun DrawScope.drawPattern(pattern: String, ink: Color) {
    val w = size.width
    val h = size.height
    val c = ink
    when (pattern) {
        "dots" -> {
            val step = 34.dp.toPx()
            var y = step / 2
            var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) step / 2 else step
                while (x < w) { drawCircle(c, 1.6.dp.toPx(), Offset(x, y)); x += step }
                y += step * 0.8f; row++
            }
        }
        "grid" -> {
            val step = 28.dp.toPx()
            var x = 0f
            while (x < w) { drawLine(c, Offset(x, 0f), Offset(x, h), 1f); x += step }
            var y = 0f
            while (y < h) { drawLine(c, Offset(0f, y), Offset(w, y), 1f); y += step }
        }
        "lines" -> {
            val step = 22.dp.toPx()
            var x = -h
            while (x < w) { drawLine(c, Offset(x, h), Offset(x + h, 0f), 1.2f); x += step }
        }
        "waves" -> {
            val step = 26.dp.toPx()
            var y = 0f
            while (y < h + step) {
                val p = Path()
                p.moveTo(0f, y)
                var x = 0f
                while (x <= w) { p.lineTo(x, y + sin(x / w * 6 * PI).toFloat() * 6.dp.toPx()); x += 8f }
                drawPath(p, c, style = Stroke(1.4f))
                y += step
            }
        }
        "stones" -> {
            val cw = 46.dp.toPx()
            val ch = 74.dp.toPx()
            var y = 10f
            var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) 8f else cw * 0.7f
                var i = 0
                while (x < w) {
                    val sw = 9.dp.toPx() + (i * 7 + row * 3) % 5 * 2f
                    val sh = 18.dp.toPx() + (i * 11 + row * 5) % 7 * 3f
                    drawRoundRect(c, Offset(x, y + (ch - sh)), Size(sw, sh), CornerRadius(sw / 2.5f))
                    x += cw; i++
                }
                y += ch; row++
            }
        }
        "rings" -> {
            val step = 90.dp.toPx()
            var y = step / 2
            var row = 0
            while (y < h + step) {
                var x = if (row % 2 == 0) step / 2 else step
                while (x < w + step) {
                    for (k in 1..3) drawCircle(c, k * 11.dp.toPx(), Offset(x, y), style = Stroke(1.2f))
                    x += step
                }
                y += step * 0.86f; row++
            }
        }
        "stars" -> {
            val r = java.util.Random(7)
            repeat(((w * h) / 9000f).toInt().coerceAtMost(400)) {
                val x = r.nextFloat() * w
                val y = r.nextFloat() * h
                val s = 0.8f + r.nextFloat() * 2.2f
                drawCircle(c.copy(alpha = c.alpha * (0.4f + r.nextFloat() * 0.6f)), s, Offset(x, y))
            }
        }
    }
}

@Composable
fun ChatWallpaper(conv: String, modifier: Modifier) {
    val walls by remember { ChatLooks.wallsFlow() }.collectAsState()
    val pattern = walls[conv] ?: "none"
    if (pattern == "none") return
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val tilt = com.monolith.app.ui.components.rememberTiltState()
    Canvas(modifier.graphicsLayer {
        translationX = tilt.value.x * 14.dp.toPx()
        translationY = tilt.value.y * 14.dp.toPx()
        scaleX = 1.08f
        scaleY = 1.08f
    }) { drawPattern(pattern, ink) }
}

private fun patternName(p: String) = when (p) {
    "none" -> t("Nessuno")
    "dots" -> t("Puntini")
    "grid" -> t("Griglia")
    "lines" -> t("Righe")
    "waves" -> t("Onde")
    "stones" -> t("Monoliti")
    "rings" -> t("Cerchi")
    else -> t("Stelle")
}

@Composable
fun WallpaperSheet(conv: String, onDismiss: () -> Unit) {
    val walls by remember { ChatLooks.wallsFlow() }.collectAsState()
    val cur = walls[conv] ?: "none"
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(t("Sfondo della chat"), style = MaterialTheme.typography.titleLarge)
            Text(t("Solo su questo telefono"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                ChatLooks.PATTERNS.forEach { p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.62f).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(if (p == cur) 2.5.dp else 1.dp, if (p == cur) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .bounceClick(0.94f) { ChatLooks.setWall(conv, p) },
                        ) { Canvas(Modifier.fillMaxSize()) { drawPattern(p, ink) } }
                        Spacer(Modifier.height(4.dp))
                        Text(patternName(p), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ====================================================================== shake for a nudge

@Composable
fun ShakeToNudge(conv: String) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    if (!ChatLooks.shake) return
    DisposableEffect(conv) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var peaks = 0
        var firstPeak = 0L
        var lastPeak = 0L
        var lastSent = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g < 2.6f || now - lastPeak < 120) return
                if (now - firstPeak > 900) { peaks = 0; firstPeak = now }
                lastPeak = now
                peaks++
                if (peaks >= 3 && now - lastSent > 8000) {
                    lastSent = now
                    peaks = 0
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    Graph.scope.launch { Graph.messenger.sendNudge(conv) }
                    Toast.makeText(ctx, t("Trillo inviato"), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(l) }
    }
}

// ====================================================================== mentions

private val MENTION = Regex("(^|\\s)@([\\p{L}]*)$")

@Composable
fun MentionBar(text: String, members: List<Friend>, onPick: (String) -> Unit) {
    val m = MENTION.find(text) ?: return
    val q = m.groupValues[2].lowercase()
    val list = members.filter { it.displayName.lowercase().startsWith(q) || it.displayName.substringAfter(' ', "").lowercase().startsWith(q) }.take(8)
    val everyone = t("tutti")
    val showAll = everyone.startsWith(q)
    if (list.isEmpty() && !showAll) return
    val start = m.range.first + m.groupValues[1].length
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showAll) item {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.bounceClick(0.95f) { onPick(text.substring(0, start) + "@" + everyone + " ") }) {
                Text("@" + everyone, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        items(list, key = { it.pubkey }) { f ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.bounceClick(0.95f) { onPick(text.substring(0, start) + "@" + f.displayName + " ") }) {
                Text(f.emoji + "  " + f.displayName, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ====================================================================== games, dice and more

@Composable
fun FunSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(t("Giochi e dadi"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            val items = listOf(
                Triple("ttt", "⭕", t("Tris")),
                Triple("c4", "🔴", t("Forza 4")),
                Triple("hang", "🪢", t("Impiccato")),
                Triple("dice", "🎲", t("Un dado")),
                Triple("dice2", "🎲", t("Due dadi")),
                Triple("coin", "🪙", t("Testa o croce")),
                Triple("board", "🎨", t("Lavagna condivisa")),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                items.forEach { (id, e, label) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .bounceClick(0.94f) { onPick(id) }.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(e, fontSize = 30.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun HangmanDialog(onDismiss: () -> Unit, onStart: (String) -> Unit) {
    var word by remember { mutableStateOf("") }
    val clean = Games.cleanWord(word)
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Impiccato")) },
        text = {
            Column {
                Text(t("Scrivi una parola o una frase corta: gli altri dovranno indovinarla lettera per lettera."), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(word, { word = it.take(24) }, label = { Text(t("Parola segreta")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onStart(clean) }, enabled = clean.count { it.isLetter() } >= 2) { Text(t("Inizia")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

@Composable
fun GameBubble(m: Message, myPub: String, names: Map<String, Friend>, shape: Shape, onLongPress: (Message) -> Unit) {
    val out = m.outgoing
    val bg = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (out) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val st = remember(m.extra) { Games.parse(m) }
    val res = Games.result(st)
    val myTurn = Games.canMoveAny(st, myPub)
    fun nameOf(pk: String?): String = when {
        pk == null -> t("chi risponde")
        pk == myPub -> t("Tu")
        else -> names[pk]?.displayName?.substringBefore(' ') ?: t("Qualcuno")
    }
    val status = when {
        st.type == Games.HANG && res == 2 -> t("Parola indovinata!")
        st.type == Games.HANG && res == 1 -> t("Impiccato! La parola era {0}", st.word)
        st.type == Games.HANG && res == 3 -> t("Partita finita")
        res == 3 -> t("Pareggio")
        res == 1 -> if (st.creator == myPub) t("Hai vinto!") else t("Ha vinto {0}", nameOf(st.creator))
        res == 2 -> if (st.second == myPub) t("Hai vinto!") else t("Ha vinto {0}", nameOf(st.second))
        myTurn -> t("Tocca a te")
        st.type == Games.HANG -> if (st.creator == myPub) t("Gli altri stanno indovinando") else t("Indovinate la parola")
        Games.turn(st) == 1 -> t("Tocca a {0}", nameOf(st.creator))
        else -> t("Tocca a {0}", nameOf(st.second))
    }
    fun move(v: String) { Graph.scope.launch { Graph.messenger.gameMove(m.id, v) } }
    Surface(shape = shape, color = bg, modifier = Modifier.width(272.dp).combinedClickable(onClick = {}, onLongClick = { onLongPress(m) })) {
        Column(Modifier.padding(14.dp)) {
            Text(Games.emoji(st.type) + "  " + Games.name(st.type), color = ink, style = MaterialTheme.typography.titleMedium)
            if (st.type != Games.HANG) {
                Text(t("{0} contro {1}", nameOf(st.creator), nameOf(st.second)), color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            when (st.type) {
                Games.TTT -> TttBoard(st, ink, myTurn) { move(it.toString()) }
                Games.C4 -> C4Board(st, ink, myTurn) { move(it.toString()) }
                else -> HangBoard(st, ink, myPub, myTurn) { move(it) }
            }
            Spacer(Modifier.height(10.dp))
            Text(status, color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TttBoard(st: Games.State, ink: Color, myTurn: Boolean, onCell: (Int) -> Unit) {
    val b = Games.tttBoard(st)
    val win = Games.tttWinLine(b)?.toSet() ?: emptySet()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until 3) {
                val i = r * 3 + c
                val v = b[i]
                val appear = remember(v) { Animatable(if (v == 0) 1f else 0f) }
                LaunchedEffect(v) { if (v != 0) appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                        .background(ink.copy(alpha = if (i in win) 0.28f else 0.1f))
                        .bounceClick(0.9f, enabled = myTurn && v == 0) { onCell(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (v != 0) Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                        val sw = 5.dp.toPx()
                        val p = appear.value
                        if (v == 1) {
                            drawLine(ink, Offset(0f, 0f), Offset(size.width * p, size.height * p), sw, StrokeCap.Round)
                            if (p > 0.5f) drawLine(ink, Offset(size.width, 0f), Offset(size.width - size.width * (p - 0.5f) * 2, size.height * (p - 0.5f) * 2), sw, StrokeCap.Round)
                        } else {
                            drawArc(ink, -90f, 360f * p, false, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun C4Board(st: Games.State, ink: Color, myTurn: Boolean, onCol: (Int) -> Unit) {
    val b = Games.c4Board(st)
    val win = Games.c4WinCells(b)?.toSet() ?: emptySet()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = 0.1f)).padding(4.dp)) {
        for (c in 0 until Games.C4_COLS) {
            Column(
                Modifier.weight(1f).bounceClick(0.95f, enabled = myTurn && b[c] == 0) { onCol(c) },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (r in 0 until Games.C4_ROWS) {
                    val v = b[r * Games.C4_COLS + c]
                    val drop = remember(v) { Animatable(if (v == 0) 1f else 0f) }
                    LaunchedEffect(v) { if (v != 0) drop.animateTo(1f, tween(260 + r * 40, easing = FastOutSlowInEasing)) }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(1.5.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().graphicsLayer { translationY = -(1f - drop.value) * size.height * (r + 1) }) {
                            val rad = size.minDimension / 2 * (if (r * Games.C4_COLS + c in win) 1f else 0.86f)
                            when (v) {
                                0 -> drawCircle(ink.copy(alpha = 0.12f), size.minDimension / 2 * 0.8f)
                                1 -> drawCircle(ink, rad)
                                else -> {
                                    drawCircle(ink.copy(alpha = 0.18f), rad)
                                    drawCircle(ink, rad - 1.5.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(ink) }
        Spacer(Modifier.width(4.dp))
        Text(t("chi ha iniziato"), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.size(10.dp)) { drawCircle(ink, style = Stroke(1.5.dp.toPx())) }
        Spacer(Modifier.width(4.dp))
        Text(t("l'altro"), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HangBoard(st: Games.State, ink: Color, myPub: String, myTurn: Boolean, onLetter: (String) -> Unit) {
    val wrong = Games.wrong(st)
    val finished = Games.result(st) != 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(84.dp)) {
            val sw = 3.dp.toPx()
            val w = size.width
            val h = size.height
            drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.1f, h * 0.95f), Offset(w * 0.6f, h * 0.95f), sw, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.25f, h * 0.95f), Offset(w * 0.25f, h * 0.06f), sw, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.25f, h * 0.06f), Offset(w * 0.7f, h * 0.06f), sw, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.7f, h * 0.06f), Offset(w * 0.7f, h * 0.18f), sw, StrokeCap.Round)
            if (wrong >= 1) drawCircle(ink, w * 0.09f, Offset(w * 0.7f, h * 0.27f), style = Stroke(sw))
            if (wrong >= 2) drawLine(ink, Offset(w * 0.7f, h * 0.36f), Offset(w * 0.7f, h * 0.62f), sw, StrokeCap.Round)
            if (wrong >= 3) drawLine(ink, Offset(w * 0.7f, h * 0.42f), Offset(w * 0.56f, h * 0.52f), sw, StrokeCap.Round)
            if (wrong >= 4) drawLine(ink, Offset(w * 0.7f, h * 0.42f), Offset(w * 0.84f, h * 0.52f), sw, StrokeCap.Round)
            if (wrong >= 5) drawLine(ink, Offset(w * 0.7f, h * 0.62f), Offset(w * 0.58f, h * 0.8f), sw, StrokeCap.Round)
            if (wrong >= 6) drawLine(ink, Offset(w * 0.7f, h * 0.62f), Offset(w * 0.82f, h * 0.8f), sw, StrokeCap.Round)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                Games.masked(st, reveal = finished || st.creator == myPub), color = ink, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp,
            )
            Text(t("Errori: {0} su {1}", wrong, Games.HANG_LIVES), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (st.creator != myPub && !finished) {
        Spacer(Modifier.height(8.dp))
        val used = Games.guessed(st).toSet()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ('A'..'Z').forEach { ch ->
                val l = ch.toString()
                val done = l in used
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = if (done) 0.05f else 0.14f))
                        .bounceClick(0.85f, enabled = myTurn && !done) { onLetter(l) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, color = ink.copy(alpha = if (done) 0.3f else 1f), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun DiceBubble(m: Message, isNew: Boolean, shape: Shape, onLongPress: (Message) -> Unit) {
    val out = m.outgoing
    val bg = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (out) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val ex = remember(m.extra) { runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()) }
    val type = ex.optString("d", "dice")
    val res = remember(m.extra) { ex.optJSONArray("r")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: listOf(1) }
    val spin = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(Unit) { if (spin.value < 1f) spin.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    val shown by produceState(if (isNew) res.map { 1 } else res, isNew) {
        if (isNew) {
            val r = java.util.Random()
            repeat(11) { value = res.map { if (type == "coin") r.nextInt(2) else r.nextInt(6) + 1 }; delay(110) }
        }
        value = res
    }
    Surface(shape = shape, color = bg, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress(m) })) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEach { v ->
                    Box(Modifier.size(64.dp).graphicsLayer {
                        if (type == "coin") rotationY = spin.value * 1440f else rotationZ = (1f - spin.value) * 540f
                        cameraDistance = 12f * density
                    }) {
                        if (type == "coin") Coin(v == 0, ink, bg) else DieFace(v, ink, bg)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (spin.value < 1f) "…" else if (type == "coin") (if (res[0] == 0) t("Testa") else t("Croce")) else m.text,
                color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DieFace(n: Int, ink: Color, bg: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(ink, cornerRadius = CornerRadius(14.dp.toPx()))
        val r = size.width * 0.085f
        val a = size.width * 0.27f
        val c = size.width / 2
        val b = size.width - a
        val pips = when (n) {
            1 -> listOf(c to c)
            2 -> listOf(a to a, b to b)
            3 -> listOf(a to a, c to c, b to b)
            4 -> listOf(a to a, b to a, a to b, b to b)
            5 -> listOf(a to a, b to a, c to c, a to b, b to b)
            else -> listOf(a to a, b to a, a to c, b to c, a to b, b to b)
        }
        pips.forEach { (x, y) -> drawCircle(bg, r, Offset(x, y)) }
    }
}

@Composable
private fun Coin(heads: Boolean, ink: Color, bg: Color) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(ink).border(3.dp, bg.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
        Text(if (heads) "👑" else "✦", fontSize = 26.sp, color = bg)
    }
}

/** view-once photo: the receiver opens it once, the sender sees whether it was opened */
@Composable
fun OnceBubble(m: Message, myPub: String, shape: Shape, onOpen: () -> Unit, onLongPress: (Message) -> Unit) {
    val out = m.outgoing
    val bg = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (out) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val ex = remember(m.extra) { runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()) }
    val openedBy = remember(m.extra) { ex.optJSONArray("ob")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }
    val group = m.convId.startsWith("g:")
    val ready = !out && m.mediaPath != null && m.progress >= 1f && myPub !in openedBy
    val (title, sub) = when {
        out && group -> t("Foto a vista unica") to if (openedBy.isEmpty()) t("Non ancora aperta") else t("Aperta da {0}", openedBy.size)
        out -> t("Foto a vista unica") to if (openedBy.isEmpty()) t("Non ancora aperta") else t("Aperta")
        myPub in openedBy -> t("Foto a vista unica") to t("Aperta")
        m.progress < 1f -> t("Foto a vista unica") to t("In arrivo…")
        else -> t("Foto a vista unica") to t("Tocca per aprirla: si vede una volta sola")
    }
    Surface(
        shape = shape, color = bg,
        modifier = Modifier.width(250.dp).combinedClickable(onClick = { if (ready) onOpen() }, onLongClick = { onLongPress(m) }),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).then(if (ready) Modifier.background(ink) else Modifier.border(2.dp, ink.copy(alpha = 0.5f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { Text("1", color = if (ready) bg else ink.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = ink, style = MaterialTheme.typography.titleSmall)
                Text(sub, color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun BoardBubble(m: Message, shape: Shape, onOpen: () -> Unit, onLongPress: (Message) -> Unit) {
    val out = m.outgoing
    val bg = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (out) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(shape = shape, color = bg, modifier = Modifier.width(240.dp).combinedClickable(onClick = onOpen, onLongClick = { onLongPress(m) })) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎨", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Lavagna condivisa"), color = ink, style = MaterialTheme.typography.titleSmall)
                Text(t("Tocca per disegnare insieme"), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** keep screenshots and screen recordings out while this is on screen */
@Composable
fun SecureScreen(on: Boolean = true) {
    val ctx = LocalContext.current
    DisposableEffect(on) {
        val w = ctx.findActivity()?.window
        if (on) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (on) w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Android 14 and later tell the app when a screenshot is taken: the chat lets the others know */
@Composable
fun ScreenshotWatcher(conv: String) {
    if (android.os.Build.VERSION.SDK_INT < 34) return
    val act = LocalContext.current.findActivity() ?: return
    DisposableEffect(conv) {
        val cb = ScreenCapture.register(act, conv)
        onDispose { ScreenCapture.unregister(act, cb) }
    }
}

private object ScreenCapture {
    fun register(act: Activity, conv: String): Any? {
        if (android.os.Build.VERSION.SDK_INT < 34) return null
        return runCatching {
            val cb = Activity.ScreenCaptureCallback { Graph.scope.launch { Graph.messenger.screenshotTaken(conv) } }
            act.registerScreenCaptureCallback(act.mainExecutor, cb)
            cb
        }.getOrNull()
    }

    fun unregister(act: Activity, cb: Any?) {
        if (android.os.Build.VERSION.SDK_INT < 34 || cb == null) return
        runCatching { act.unregisterScreenCaptureCallback(cb as Activity.ScreenCaptureCallback) }
    }
}
