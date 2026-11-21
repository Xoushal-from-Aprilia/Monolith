package com.monolith.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Message
import com.monolith.app.net.BoardStroke
import com.monolith.app.net.Boards
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val PAPER = Color(0xFFF4F4F2)
private val INKS = listOf(Color(0xFF111111), Color(0xFF6E6E6E), Color(0xFFB9B9B9))
private val WIDTHS = listOf(0.008f, 0.02f, 0.05f)

private fun DrawScope.drawStroke(s: BoardStroke) {
    if (s.pts.size < 2) return
    val w = size.width
    val h = size.height
    val color = if (s.erase) PAPER else INKS[s.shade.coerceIn(0, 2)]
    if (s.pts.size == 2) {
        drawCircle(color, s.w * w / 2, Offset(s.pts[0] * w, s.pts[1] * h))
        return
    }
    val p = Path()
    p.moveTo(s.pts[0] * w, s.pts[1] * h)
    var i = 2
    while (i + 1 < s.pts.size) { p.lineTo(s.pts[i] * w, s.pts[i + 1] * h); i += 2 }
    drawPath(p, color, style = Stroke(s.w * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun render(strokes: List<BoardStroke>, file: File) {
    val w = 1080
    val h = 1440
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(PAPER.toArgb())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    strokes.forEach { s ->
        if (s.pts.size < 2) return@forEach
        paint.color = (if (s.erase) PAPER else INKS[s.shade.coerceIn(0, 2)]).toArgb()
        paint.strokeWidth = s.w * w
        val p = android.graphics.Path()
        p.moveTo(s.pts[0] * w, s.pts[1] * h)
        var i = 2
        if (s.pts.size == 2) p.lineTo(s.pts[0] * w + 0.1f, s.pts[1] * h)
        while (i + 1 < s.pts.size) { p.lineTo(s.pts[i] * w, s.pts[i + 1] * h); i += 2 }
        c.drawPath(p, paint)
    }
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    bmp.recycle()
}

/** the stroke being drawn by me, sent in small pieces while the finger moves */
private class Pen {
    var id: String? = null
    var pts = ArrayList<Float>()
    var sent = 0
}

@Composable
fun BoardScreen(nav: NavHostController, conv: String) {
    val ctx = LocalContext.current
    val myPub = Graph.identity.keys?.pub ?: ""
    val all by Boards.strokes.collectAsState()
    val here by Boards.here.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val strokes = all[conv].orEmpty()
    var shade by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(1) }
    var erase by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val pen = remember { Pen() }

    LaunchedEffect(conv) {
        Graph.messenger.fastToConv(conv, JSONObject().put("t", "drawjoin"))
        // flush my current stroke to the others
        while (true) {
            delay(70)
            val id = pen.id ?: continue
            val pts = synchronized(pen) { ArrayList(pen.pts) }
            if (pts.size > pen.sent) {
                val from = pen.sent
                pen.sent = pts.size
                val payload = JSONObject().put("t", "draw").put("s", id).put("i", from).put("w", WIDTHS[width].toDouble())
                    .put("h", shade).put("e", erase).put("p", JSONArray().also { a -> pts.subList(from, pts.size).forEach { a.put(it.toDouble()) } })
                Graph.messenger.fastToConv(conv, payload)
            }
        }
    }
    LaunchedEffect(conv) {
        while (true) {
            delay(25_000)
            Graph.messenger.fastToConv(conv, JSONObject().put("t", "drawjoin"))
        }
    }
    DisposableEffect(conv) {
        onDispose { Graph.scope.launch { Graph.messenger.fastToConv(conv, JSONObject().put("t", "drawbye")) } }
    }

    val names = people.associateBy { it.pubkey }
    val present = (here[conv] ?: emptyMap()).filter { now() - it.value < 60_000 && it.key != myPub }.keys.mapNotNull { names[it]?.displayName?.substringBefore(' ') }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Column(Modifier.weight(1f)) {
                Text(t("Lavagna condivisa"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (present.isEmpty()) t("Aspettando gli altri…") else t("Con te: {0}", present.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                strokes.lastOrNull { it.by == myPub }?.let { s ->
                    Boards.undo(conv, s.id)
                    Graph.scope.launch { Graph.messenger.fastToConv(conv, JSONObject().put("t", "drawundo").put("s", s.id)) }
                }
            }) { Icon(Icons.AutoMirrored.Rounded.Undo, t("Annulla l'ultimo tratto")) }
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteOutline, t("Pulisci la lavagna")) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp)).background(PAPER)
                    .pointerInput(conv) {
                        fun addPoint(o: Offset) {
                            val id = pen.id ?: return
                            val x = (o.x / size.width).coerceIn(0f, 1f)
                            val y = (o.y / size.height).coerceIn(0f, 1f)
                            val from = synchronized(pen) { pen.pts.size.also { pen.pts.add(x); pen.pts.add(y) } }
                            Boards.addPoints(conv, id, myPub, WIDTHS[width], shade, erase, from, listOf(x, y))
                        }
                        detectDragGestures(
                            onDragStart = { o -> synchronized(pen) { pen.id = newId(); pen.pts = ArrayList(); pen.sent = 0 }; addPoint(o) },
                            onDrag = { change, _ -> addPoint(change.position) },
                        )
                    }
                    .pointerInput(conv) {
                        detectTapGestures { o ->
                            synchronized(pen) { pen.id = newId(); pen.pts = ArrayList(); pen.sent = 0 }
                            val x = (o.x / size.width).coerceIn(0f, 1f)
                            val y = (o.y / size.height).coerceIn(0f, 1f)
                            synchronized(pen) { pen.pts.add(x); pen.pts.add(y) }
                            Boards.addPoints(conv, pen.id!!, myPub, WIDTHS[width], shade, erase, 0, listOf(x, y))
                        }
                    },
            ) { strokes.forEach { drawStroke(it) } }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            INKS.forEachIndexed { i, c ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c)
                        .border(if (!erase && shade == i) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (!erase && shade == i) 1f else 0.2f), CircleShape)
                        .bounceClick(0.85f) { shade = i; erase = false },
                )
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(PAPER)
                    .border(if (erase) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (erase) 1f else 0.2f), CircleShape)
                    .bounceClick(0.85f) { erase = !erase },
                contentAlignment = Alignment.Center,
            ) { Text("⌫", color = Color(0xFF111111)) }
            Spacer(Modifier.weight(1f))
            WIDTHS.forEachIndexed { i, _ ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (width == i) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .bounceClick(0.85f) { width = i },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size((5 + i * 6).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)) }
            }
        }
        Button(
            onClick = {
                if (sending || strokes.isEmpty()) return@Button
                sending = true
                Graph.scope.launch {
                    val f = File(ctx.cacheDir, "board-${now()}.jpg")
                    withContext(Dispatchers.Default) { render(strokes, f) }
                    Graph.messenger.sendPhoto(conv, Uri.fromFile(f), t("Dalla lavagna"))
                    runCatching { f.delete() }
                }
                nav.popBackStack()
            },
            enabled = strokes.isNotEmpty() && !sending,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("Invia in chat come foto"))
        }
    }
    if (confirmClear) {
        MonoDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("Pulire la lavagna?")) },
            text = { Text(t("Il disegno sparisce per tutti.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    Boards.clear(conv)
                    Graph.scope.launch { Graph.messenger.fastToConv(conv, JSONObject().put("t", "drawclr")) }
                }) { Text(t("Pulisci")) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("Annulla")) } },
        )
    }
}

/** a photo that can be seen once: no screenshots, gone when you close it */
@Composable
fun OnceViewer(nav: NavHostController, id: String) {
    SecureScreen()
    val msg by produceState<Message?>(null, id) { value = Graph.db.messages().get(id) }
    val m = msg
    DisposableEffect(m?.id) {
        onDispose {
            if (m != null && !m.outgoing && m.mediaPath != null) Graph.scope.launch { Graph.messenger.openedOnce(m) }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val path = m?.mediaPath
        if (path != null && File(path).exists()) {
            AsyncImage(File(path), t("Foto"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else if (m != null) {
            Text(t("Questa foto è già stata aperta"), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, t("Chiudi"), tint = Color.White) }
            Text(t("Si vede una volta sola"), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
