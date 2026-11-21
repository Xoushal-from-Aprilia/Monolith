package com.monolith.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as ACanvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.newId
import com.monolith.app.core.t
import com.monolith.app.data.Message
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** my stickers: square black and white images with a transparent edge, kept in files/stickers */
object Stickers {
    private val dir by lazy { File(Graph.app.filesDir, "stickers").apply { mkdirs() } }
    private val version = kotlinx.coroutines.flow.MutableStateFlow(0)
    val changes: kotlinx.coroutines.flow.StateFlow<Int> = version

    fun list(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun count() = list().size

    fun save(bmp: Bitmap): File {
        val f = File(dir, newId() + ".webp")
        FileOutputStream(f).use { os ->
            @Suppress("DEPRECATION")
            val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bmp.compress(fmt, 86, os)
        }
        version.value++
        return f
    }

    fun remove(f: File) {
        runCatching { f.delete() }
        version.value++
    }

    /** someone else's sticker from a chat, added to mine */
    fun adopt(path: String): Boolean = runCatching {
        val src = File(path)
        src.copyTo(File(dir, newId() + ".webp"))
        version.value++
        true
    }.getOrDefault(false)
}

private val SHAPES = listOf("circle", "round", "slab")
private val STICKER_FILTERS = listOf(FILTERS[1], FILTERS[2], FILTERS[5], FILTERS[4])

/** builds the sticker: crop, black and white, shape with a white border, optional words */
private fun makeSticker(src: Bitmap, shape: String, filter: PhotoFilter, zoom: Float, words: String): Bitmap {
    val size = 512
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = ACanvas(out)
    val side = (min(src.width, src.height) / zoom).toInt().coerceAtLeast(16)
    val sx = (src.width - side) / 2
    val sy = (src.height - side) / 2
    val inset = 18f
    val rect = RectF(inset, inset, size - inset, size - inset)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    fun shapePath(r: RectF) = android.graphics.Path().apply {
        when (shape) {
            "circle" -> addOval(r, android.graphics.Path.Direction.CW)
            "round" -> addRoundRect(r, r.width() * 0.22f, r.height() * 0.22f, android.graphics.Path.Direction.CW)
            else -> { // a slab of the monolith, slightly leaning
                moveTo(r.left + r.width() * 0.16f, r.top + r.height() * 0.04f)
                lineTo(r.right - r.width() * 0.06f, r.top)
                lineTo(r.right - r.width() * 0.16f, r.bottom)
                lineTo(r.left + r.width() * 0.04f, r.bottom - r.height() * 0.03f)
                close()
            }
        }
    }
    // white border
    c.drawPath(shapePath(RectF(4f, 4f, size - 4f, size - 4f)), maskPaint)
    // photo inside the shape
    val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val lc = ACanvas(layer)
    lc.drawPath(shapePath(rect), maskPaint)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        colorFilter = ColorMatrixColorFilter(filterMatrix(filter))
    }
    lc.drawBitmap(src, android.graphics.Rect(sx, sy, sx + side, sy + side), rect, p)
    c.drawBitmap(layer, 0f, 0f, null)
    layer.recycle()
    if (words.isNotBlank()) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 64f
        }
        val text = words.uppercase().take(18)
        while (tp.measureText(text) > size * 0.86f && tp.textSize > 28f) tp.textSize -= 2f
        val y = size - 60f
        tp.style = Paint.Style.STROKE; tp.strokeWidth = 14f; tp.color = android.graphics.Color.BLACK
        c.drawText(text, size / 2f, y, tp)
        tp.style = Paint.Style.FILL; tp.color = android.graphics.Color.WHITE
        c.drawText(text, size / 2f, y, tp)
    }
    return out
}

/** the stickers drawer from the chat composer */
@Composable
fun StickerSheet(onDismiss: () -> Unit, onSend: (File) -> Unit) {
    val ver by Stickers.changes.collectAsState()
    val files = remember(ver) { Stickers.list() }
    var making by remember { mutableStateOf<Uri?>(null) }
    var remove by remember { mutableStateOf<File?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) making = u }
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(t("I tuoi sticker"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            Text(t("Tieni premuto uno sticker per toglierlo"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .bounceClick(0.92f) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Add, null)
                        Text(t("Nuovo"), style = MaterialTheme.typography.labelSmall)
                    }
                }
                files.forEach { f -> StickerTile(f, Modifier.weight(1f), onClick = { onSend(f) }, onLong = { remove = f }) }
                // keep the last row aligned
                repeat((4 - (files.size + 1) % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    making?.let { u -> StickerMaker(u, onDismiss = { making = null }) }
    remove?.let { f ->
        MonoDialog(
            onDismissRequest = { remove = null },
            title = { Text(t("Togliere lo sticker?")) },
            confirmButton = { TextButton(onClick = { Stickers.remove(f); remove = null }) { Text(t("Togli")) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(t("Annulla")) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(f: File, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    AsyncImage(
        f, null, contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(1f).combinedClickable(onClick = onClick, onLongClick = onLong),
    )
}

@Composable
private fun StickerMaker(uri: Uri, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val src by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            }.getOrNull()
        }
    }
    var shape by remember { mutableStateOf("circle") }
    var fi by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableStateOf(1f) }
    var words by remember { mutableStateOf("") }
    val preview by produceState<Bitmap?>(null, src, shape, fi, zoom, words) {
        val s = src ?: return@produceState
        value = withContext(Dispatchers.Default) { makeSticker(s, shape, STICKER_FILTERS[fi], zoom, words) }
    }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Nuovo sticker")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(180.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val p = preview
                    if (p == null) CircularProgressIndicator() else Image(p.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SHAPES.forEach { sh ->
                        Chip(
                            when (sh) { "circle" -> t("Cerchio"); "round" -> t("Arrotondato"); else -> t("Lastra") },
                            sh == shape,
                        ) { shape = sh }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    STICKER_FILTERS.forEachIndexed { i, f ->
                        Chip(
                            when (f.id) { "bn" -> t("Bianco e nero"); "hard" -> t("Contrasto"); "night" -> t("Notte"); else -> t("Grana") },
                            i == fi,
                        ) { fi = i }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1f, 1.4f, 2f).forEach { z -> Chip(t("Zoom {0}×", if (z == 1f) "1" else if (z == 1.4f) "1,4" else "2"), zoom == z) { zoom = z } }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(words, { words = it.take(18) }, label = { Text(t("Scritta (facoltativa)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = preview != null, onClick = {
                val p = preview ?: return@TextButton
                Graph.scope.launch(Dispatchers.IO) { Stickers.save(p) }
                onDismiss()
            }) { Text(t("Salva")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.bounceClick(0.9f, onClick = onClick),
    ) {
        Text(
            label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** a sticker in the chat: no bubble, just the image */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerBubble(m: Message, shape: Shape, onLongPress: (Message) -> Unit) {
    Box(Modifier.size(150.dp).combinedClickable(onClick = {}, onLongClick = { onLongPress(m) }), contentAlignment = Alignment.Center) {
        if (m.mediaPath != null && m.progress >= 1f || (m.outgoing && m.mediaPath != null)) {
            AsyncImage(File(m.mediaPath!!), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
    }
}
