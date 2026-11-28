package com.monolith.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.net.Duo
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val INK = Color(0xFFF1F1EF)

/** millimetres to pixels on this screen (the density the screen declares, checked against the usual one) */
@Composable
private fun pxPerMm(): Float {
    val dm = LocalContext.current.resources.displayMetrics
    val x = (dm.xdpi + dm.ydpi) / 2f
    val dpi = if (x > dm.densityDpi * 0.6f && x < dm.densityDpi * 1.6f) x else dm.densityDpi.toFloat()
    return dpi / 25.4f
}

/** a small JPEG of a picture, to send with a throw */
private fun thumbOf(ctx: android.content.Context, uri: Uri): Pair<String, ImageBitmap>? = runCatching {
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) } ?: return null
    val k = 320f / maxOf(bmp.width, bmp.height)
    val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt(), (bmp.height * k).roundToInt(), true) else bmp
    val out = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 62, out)
    java.util.Base64.getEncoder().encodeToString(out.toByteArray()) to small.asImageBitmap()
}.getOrNull()

private fun decodeThumb(b64: String): ImageBitmap? = runCatching {
    val b = java.util.Base64.getDecoder().decode(b64)
    BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
}.getOrNull()

/** two phones side by side, one screen */
@Composable
fun DuoScreen(nav: NavHostController, pk: String) {
    val ctx = LocalContext.current
    val st by Duo.state.collectAsState()
    val friend by remember(pk) { Graph.db.friends().observe(pk) }.collectAsState(initial = null)
    val chat by remember(pk) { Graph.db.messages().conversation(pk, 400) }.collectAsState(initial = emptyList())
    val photos = chat.filter { it.kind == "photo" && it.mediaPath != null && it.extra?.contains("\"once\":true") != true }
    val mm = pxPerMm()
    val name = friend?.displayName ?: ""
    var pickSheet by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<Triple<Uri, String, ImageBitmap>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Graph.scope.launch {
            val th = withContext(Dispatchers.IO) { thumbOf(ctx, uri) } ?: return@launch
            withContext(Dispatchers.Main) { held = Triple(uri, th.first, th.second) }
        }
    }
    val inf = rememberInfiniteTransition(label = "duo")
    val clock by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "clock")

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF050505))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val wMm = wPx / mm
        val hMm = hPx / mm
        LaunchedEffect(pk, wMm, hMm) { Duo.open(pk, wMm, hMm) }
        DisposableEffect(pk) { onDispose { Duo.close() } }
        val l = st.layout
        val photoPath = st.photo?.let { id -> chat.firstOrNull { it.id == id }?.mediaPath }
        val photoBmp = remember(photoPath) { photoPath?.let { p -> runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() } }
        val edgePx = with(LocalDensity.current) { 40.dp.toPx() }

        // the shared picture: drawn in millimetres over both screens, this phone shows its part
        Canvas(
            Modifier.fillMaxSize().pointerInput(wPx, mm) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        last = ch.position
                        if (!ch.pressed) break
                    }
                    val dx = last.x - down.position.x
                    when {
                        last.x > size.width - edgePx && dx > 60f -> Duo.edge(true, 'R', last.y / mm)
                        last.x < edgePx && dx < -60f -> Duo.edge(true, 'L', last.y / mm)
                        down.position.x < edgePx && dx > 60f -> Duo.edge(false, 'L', down.position.y / mm)
                        down.position.x > size.width - edgePx && dx < -60f -> Duo.edge(false, 'R', down.position.y / mm)
                    }
                }
            },
        ) {
            if (l == null) {
                // waiting: an arrow that runs towards the edges
                val f = (clock * 3f) % 1f
                val y = size.height * 0.5f
                listOf(-1f, 1f).forEach { dir ->
                    val x = size.width / 2 + dir * size.width * 0.42f * f
                    drawCircle(INK.copy(alpha = 0.6f * (1f - f)), 14f + 10f * f, Offset(x, y))
                }
                drawLine(INK.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                return@Canvas
            }
            // the rectangle made by both screens, in millimetres
            val minX = minOf(0f, l.x)
            val minY = minOf(0f, l.y)
            val maxX = maxOf(wMm, l.x + l.w)
            val maxY = maxOf(hMm, l.y + l.h)
            if (photoBmp != null) {
                val bw = (maxX - minX) * mm
                val bh = (maxY - minY) * mm
                val k = maxOf(bw / photoBmp.width, bh / photoBmp.height)
                val dw = photoBmp.width * k
                val dh = photoBmp.height * k
                val ox = minX * mm + (bw - dw) / 2
                val oy = minY * mm + (bh - dh) / 2
                drawImage(photoBmp, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
            } else {
                // one big ring and a monolith across the two screens: the proof that they are one
                val cx = (minX + maxX) / 2 * mm
                val cy = (minY + maxY) / 2 * mm
                val r = minOf(maxX - minX, maxY - minY) * 0.42f * mm
                for (k in 0 until 3) {
                    val rr = r * (0.55f + 0.22f * k)
                    drawCircle(INK.copy(alpha = 0.16f + 0.1f * k), rr, Offset(cx, cy), style = Stroke(6f))
                }
                val a = clock * 2 * PI.toFloat()
                for (k in 0 until 24) {
                    val ang = a + k * (2 * PI.toFloat() / 24)
                    drawCircle(INK.copy(alpha = 0.7f), 7f, Offset(cx + cos(ang) * r, cy + sin(ang) * r))
                }
                val mw = r * 0.34f
                val mh = r * 1.15f
                drawRect(INK, Offset(cx - mw / 2, cy - mh / 2), Size(mw, mh))
                drawRect(INK.copy(alpha = 0.45f), Offset(cx + mw / 2, cy - mh / 2 + mw * 0.18f), Size(mw * 0.22f, mh - mw * 0.18f))
            }
        }

        // photos thrown across
        st.throws.lastOrNull()?.let { th -> key(th.id) { FlyingCard(th, wPx, hPx, mm, l?.right ?: true) } }

        // a photo picked from the gallery, held in the hand: drag it towards the other phone to throw it
        held?.let { (uri, b64, bmp) ->
            var off by remember(uri) { mutableStateOf(Offset.Zero) }
            val cardW = wPx * 0.46f
            val cardH = cardW * bmp.height / bmp.width.coerceAtLeast(1)
            Box(
                Modifier.offset { IntOffset((wPx / 2 - cardW / 2 + off.x).roundToInt(), (hPx / 2 - cardH / 2 + off.y).roundToInt()) }
                    .size(with(LocalDensity.current) { cardW.toDp() }, with(LocalDensity.current) { cardH.toDp() })
                    .graphicsLayer { rotationZ = off.x / 40f }
                    .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(5.dp)
                    .pointerInput(uri) {
                        detectDragGestures(
                            onDragEnd = {
                                val towardRight = l?.right ?: true
                                val cx = wPx / 2 + off.x
                                val thrown = if (towardRight) cx > wPx * 0.72f else cx < wPx * 0.28f
                                if (thrown) {
                                    Duo.throwPhoto(uri, (hPx / 2 + off.y) / mm, 0f, b64)
                                    held = null
                                } else off = Offset.Zero
                            },
                        ) { change, amount -> change.consume(); off += amount }
                    },
            ) { Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) }
        }

        // top: who and what to do
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Schermo unito"), color = INK, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, t("Chiudi"), tint = INK) }
            }
            Text(
                when {
                    !st.here -> t("Aspetto che {0} apra lo schermo unito: gli è arrivato l'invito.", name)
                    l == null -> t("Avvicinate i telefoni, uno accanto all'altro, e passate il dito da uno schermo all'altro.")
                    held != null -> t("Trascina la foto verso il telefono di {0} e lasciala andare.", name)
                    else -> if (l.right) t("Uniti: {0} è a destra.", name) else t("Uniti: {0} è a sinistra.", name)
                },
                color = INK.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium,
            )
        }

        // bottom: what to show
        if (st.here) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DuoButton(t("Foto della chat"), Modifier.weight(1f)) { pickSheet = true }
            val kidRules by com.monolith.app.kids.Kids.rules.collectAsState()
            if (!com.monolith.app.kids.Kids.isKid || kidRules.photos) DuoButton(t("Lancia una foto"), Modifier.weight(1f)) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            DuoButton(if (st.photo != null) t("Togli") else t("Allinea"), Modifier.weight(0.7f)) { if (st.photo != null) Duo.showPhoto(null) else Duo.realign() }
        }
    }

    if (pickSheet) MonoSheet(onDismissRequest = { pickSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(bottom = 20.dp)) {
            Text(t("Una foto per tutti e due gli schermi"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (photos.isEmpty()) Text(t("Non avete ancora foto in chat."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(380.dp), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(photos, key = { it.id }) { m ->
                    AsyncImage(
                        model = File(m.mediaPath!!), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).bounceClick(0.95f) { pickSheet = false; Duo.showPhoto(m.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DuoButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = INK.copy(alpha = 0.12f), modifier = modifier.bounceClick(0.95f, onClick = onClick)) {
        Text(label, color = INK, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp))
    }
}

/** a photo crossing the edge: leaving this screen, or landing on it */
@Composable
private fun FlyingCard(th: Duo.Throw, wPx: Float, hPx: Float, mm: Float, peerRight: Boolean) {
    val bmp = remember(th.id) { decodeThumb(th.thumb) } ?: return
    val p = remember(th.id) { Animatable(0f) }
    LaunchedEffect(th.id) { p.animateTo(1f, tween(if (th.incoming) 700 else 450)) }
    val cardW = wPx * 0.46f
    val cardH = cardW * bmp.height / bmp.width.coerceAtLeast(1)
    // an incoming photo comes from the other phone's side; an outgoing one leaves towards it
    val fromSide = if (peerRight) wPx + cardW / 2 else -cardW / 2
    val center = wPx / 2
    val v = p.value
    val x = if (th.incoming) fromSide + (center - fromSide) * (1 - (1 - v) * (1 - v)) else center + (fromSide - center) * v
    val y = if (th.incoming) (th.y * mm).coerceIn(cardH / 2, hPx - cardH / 2) + (hPx / 2 - (th.y * mm).coerceIn(cardH / 2, hPx - cardH / 2)) * v else (th.y * mm)
    val d = LocalDensity.current
    Box(
        Modifier.offset { IntOffset((x - cardW / 2).roundToInt(), (y - cardH / 2).roundToInt()) }
            .size(with(d) { cardW.toDp() }, with(d) { cardH.toDp() })
            .graphicsLayer { rotationZ = (1 - v) * (if (peerRight) 14f else -14f); alpha = if (th.incoming) 1f else 1f - v * 0.6f }
            .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(5.dp),
    ) { Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) }
}

@Composable
private fun key(id: String, content: @Composable () -> Unit) = androidx.compose.runtime.key(id) { content() }
