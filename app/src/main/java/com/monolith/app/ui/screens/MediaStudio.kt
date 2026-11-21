package com.monolith.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.net.ReplyRef
import com.monolith.app.net.Stories
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.max

// ====================================================================== the photo waiting for its filter

/** what the editor is about to send: a chat photo (conv) or a story (conv = null) */
object PendingPhoto {
    var uri: Uri? = null
    var conv: String? = null
    var reply: ReplyRef? = null
    var once: Boolean = false

    fun set(uri: Uri, conv: String?, reply: ReplyRef? = null, once: Boolean = false) {
        this.uri = uri; this.conv = conv; this.reply = reply; this.once = once
    }
}

// ====================================================================== black and white filters

data class PhotoFilter(val id: String, val name: String, val contrast: Float, val light: Float, val grain: Float, val vignette: Float, val mono: Boolean = true)

val FILTERS = listOf(
    PhotoFilter("none", "Originale", 1f, 0f, 0f, 0f, mono = false),
    PhotoFilter("bn", "Bianco e nero", 1f, 0f, 0f, 0f),
    PhotoFilter("hard", "Contrasto", 1.6f, -0.03f, 0f, 0f),
    PhotoFilter("soft", "Morbido", 0.75f, 0.06f, 0f, 0f),
    PhotoFilter("grain", "Grana", 1.15f, 0f, 0.09f, 0.15f),
    PhotoFilter("night", "Notte", 1.3f, -0.12f, 0.03f, 0.55f),
    PhotoFilter("paper", "Carta", 0.6f, 0.18f, 0.04f, 0f),
    PhotoFilter("neg", "Negativo", -1f, 0f, 0f, 0f),
)

private fun filterName(id: String) = when (id) {
    "none" -> t("Originale")
    "bn" -> t("Bianco e nero")
    "hard" -> t("Contrasto")
    "soft" -> t("Morbido")
    "grain" -> t("Grana")
    "night" -> t("Notte")
    "paper" -> t("Carta")
    else -> t("Negativo")
}

/** 4x5 colour matrix: grey from luma, then contrast around the middle and a lift */
fun filterMatrix(f: PhotoFilter): FloatArray {
    if (!f.mono) return floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    val r = 0.2126f * f.contrast
    val g = 0.7152f * f.contrast
    val b = 0.0722f * f.contrast
    val off = (0.5f * (1f - f.contrast) + f.light) * 255f
    return floatArrayOf(r, g, b, 0f, off, r, g, b, 0f, off, r, g, b, 0f, off, 0f, 0f, 0f, 1f, 0f)
}

private fun decode(ctx: Context, uri: Uri, maxDim: Int): Bitmap? = runCatching {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val rot = cr.openInputStream(uri)?.use { androidx.exifinterface.media.ExifInterface(it).rotationDegrees } ?: 0
    val scale = minOf(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
    if (rot == 0 && scale >= 1f) bmp
    else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { if (scale < 1f) postScale(scale, scale); postRotate(rot.toFloat()) }, true)
}.getOrNull()

/** the filter applied for real, with grain and vignette drawn on top */
fun applyFilter(src: Bitmap, f: PhotoFilter): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(filterMatrix(f)) })
    if (f.vignette > 0f) {
        val r = max(src.width, src.height) * 0.75f
        val p = Paint().apply {
            shader = RadialGradient(src.width / 2f, src.height / 2f, r, intArrayOf(0x00000000, ((f.vignette * 255).toInt() shl 24)), floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), p)
    }
    if (f.grain > 0f) {
        val rnd = java.util.Random(42)
        val p = Paint()
        val n = (src.width * src.height / 10)
        repeat(n) {
            val v = if (rnd.nextBoolean()) 255 else 0
            p.color = android.graphics.Color.argb((rnd.nextFloat() * f.grain * 255).toInt(), v, v, v)
            c.drawPoint(rnd.nextFloat() * src.width, rnd.nextFloat() * src.height, p)
        }
    }
    return out
}

@Composable
fun PhotoEditScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val uri = PendingPhoto.uri
    val conv = PendingPhoto.conv
    val isStory = conv == null
    val bmp by produceState<Bitmap?>(null, uri) { value = uri?.let { u -> withContext(Dispatchers.IO) { decode(ctx, u, 1600) } } }
    val thumb by produceState<Bitmap?>(null, bmp) { value = bmp?.let { b -> withContext(Dispatchers.Default) { Bitmap.createScaledBitmap(b, 160, (160f * b.height / b.width).toInt().coerceAtLeast(1), true) } } }
    var filter by remember { mutableStateOf(if (isStory) FILTERS[1] else FILTERS[0]) }
    var caption by remember { mutableStateOf("") }
    var music by remember { mutableStateOf<Song?>(null) }
    var musicSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    if (uri == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro"), tint = Color.White) }
            Text(
                if (isStory) t("Nuova storia") else if (PendingPhoto.once) t("Foto a vista unica") else t("Foto"),
                color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            if (isStory) {
                IconButton(onClick = { musicSheet = true }) { Icon(Icons.Rounded.MusicNote, t("Musica"), tint = Color.White) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b == null) CircularProgressIndicator(color = Color.White)
            else {
                Box {
                    Image(
                        b.asImageBitmap(), null, contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(filterMatrix(filter))),
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (filter.vignette > 0f) {
                        Box(Modifier.matchParentSize().background(Brush.radialGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = filter.vignette))))
                    }
                    if (filter.grain > 0f) {
                        Canvas(Modifier.matchParentSize()) {
                            val rnd = java.util.Random(42)
                            repeat(2500) {
                                drawCircle(
                                    (if (rnd.nextBoolean()) Color.White else Color.Black).copy(alpha = rnd.nextFloat() * filter.grain * 2f),
                                    0.9f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                                )
                            }
                        }
                    }
                }
            }
            music?.let { s ->
                MusicChip(s, Modifier.align(Alignment.TopStart).padding(12.dp)) { music = null }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FILTERS, key = { it.id }) { f ->
                val sel = f.id == filter.id
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bounceClick(0.92f) { filter = f }) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).border(if (sel) 2.5.dp else 0.dp, Color.White, RoundedCornerShape(14.dp))) {
                        thumb?.let { th ->
                            Image(
                                th.asImageBitmap(), null, contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(filterMatrix(f))),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(filterName(f.id), color = if (sel) Color.White else Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!PendingPhoto.once) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    androidx.compose.foundation.text.BasicTextField(
                        caption, { caption = it.take(300) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        decorationBox = { inner ->
                            if (caption.isEmpty()) Text(t("Aggiungi una didascalia…"), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyLarge)
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White).bounceClick(0.88f, enabled = bmp != null && !busy) {
                    val b = bmp ?: return@bounceClick
                    busy = true
                    val f = filter
                    val cap = caption
                    val mu = music?.json()
                    val reply = PendingPhoto.reply
                    val once = PendingPhoto.once
                    Graph.scope.launch {
                        val out = File(ctx.cacheDir, "edit-${now()}.jpg")
                        val ok = withContext(Dispatchers.Default) {
                            runCatching {
                                val done = if (f.id == "none") b else applyFilter(b, f)
                                FileOutputStream(out).use { done.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            }.isSuccess
                        }
                        if (ok) {
                            if (conv == null) Stories.postPhoto(Uri.fromFile(out), cap, mu)
                            else Graph.messenger.sendPhoto(conv, Uri.fromFile(out), cap, reply, once)
                        }
                        runCatching { out.delete() }
                    }
                    PendingPhoto.uri = null
                    nav.popBackStack()
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Send, t("Invia"), tint = Color.Black) }
        }
    }
    if (musicSheet) MusicPickerSheet(onDismiss = { musicSheet = false }, onPick = { music = it; musicSheet = false })
}

// ====================================================================== music for stories

/** a song from the public iTunes catalogue: 30-second preview, no key and no account */
data class Song(val title: String, val artist: String, val art: String, val preview: String, val local: Boolean = false) {
    fun json(): JSONObject = JSONObject().put("t", title).put("a", artist).put("art", art).put("p", preview).put("l", local)

    companion object {
        fun from(o: JSONObject?): Song? = o?.let { Song(it.optString("t"), it.optString("a"), it.optString("art"), it.optString("p"), it.optBoolean("l")) }
    }
}

object MusicSearch {
    suspend fun search(q: String): List<Song> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        runCatching {
            val country = Locale.getDefault().country.ifBlank { "IT" }
            val url = "https://itunes.apple.com/search?media=music&entity=song&limit=25&country=$country&term=" + URLEncoder.encode(q.trim(), "UTF-8")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val o = c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            c.disconnect()
            val a = o.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until a.length()).mapNotNull { i ->
                val r = a.optJSONObject(i) ?: return@mapNotNull null
                val p = r.optString("previewUrl")
                if (p.isBlank()) null else Song(r.optString("trackName"), r.optString("artistName"), r.optString("artworkUrl100"), p)
            }
        }.getOrDefault(emptyList())
    }
}

@Composable
fun MusicChip(s: Song, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val tr = rememberInfiniteTransition(label = "eq")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(1f, 0.25f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.art.isNotBlank()) AsyncImage(s.art, null, Modifier.size(28.dp).clip(CircleShape))
        else Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text("♪", color = Color.White) }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(a, b, (a + b) / 2).forEach { h -> Box(Modifier.width(3.dp).fillMaxHeight(h).background(Color.White)) }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(s.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.artist.isNotBlank()) Text(s.artist, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Close, t("Togli"), Modifier.size(18.dp).bounceClick(0.8f) { onRemove() }, tint = Color.White)
        }
    }
}

@Composable
fun MusicPickerSheet(onDismiss: () -> Unit, onPick: (Song) -> Unit) {
    var q by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() { runCatching { player[0]?.release() }; player[0] = null; playing = null }
    DisposableEffect(Unit) { onDispose { stop() } }
    val results by produceState(emptyList<Song>(), q) {
        delay(400)
        value = MusicSearch.search(q)
    }
    MonoSheet(onDismissRequest = { stop(); onDismiss() }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(t("Musica per la storia"), style = MaterialTheme.typography.titleLarge)
            Text(t("Anteprime di 30 secondi dal catalogo pubblico di iTunes"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(q, { q = it.take(80) }, label = { Text(t("Cerca una canzone o un artista")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                items(results, key = { it.preview }) { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).bounceClick(0.9f) {
                                if (playing == s.preview) stop()
                                else {
                                    stop()
                                    player[0] = runCatching {
                                        MediaPlayer().apply {
                                            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                                            setDataSource(s.preview)
                                            setOnPreparedListener { it.start() }
                                            setOnCompletionListener { playing = null }
                                            prepareAsync()
                                        }
                                    }.getOrNull()
                                    playing = s.preview
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(s.art, null, Modifier.fillMaxSize())
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                Icon(if (playing == s.preview) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { stop(); onPick(s) }) { Text(t("Usa")) }
                    }
                }
            }
        }
    }
}

// ====================================================================== a song from the phone

object AudioCut {
    /** a [lenMs] piece of the song starting at [startMs]; AAC and MP3 are copied as they are, no re-encoding */
    fun cut(ctx: Context, uri: Uri, startMs: Long, lenMs: Long): Pair<File, Long>? = runCatching {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
        ex.selectTrack(track)
        ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val endUs = (startMs + lenMs) * 1000
        val buf = ByteBuffer.allocate(1 shl 20)
        val info = android.media.MediaCodec.BufferInfo()
        var first = -1L
        var last = 0L
        val out: File
        when (mime) {
            "audio/mp4a-latm" -> {
                out = File(ctx.cacheDir, "song-${now()}.m4a")
                val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val t = mux.addTrack(fmt)
                mux.start()
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    val ts = ex.sampleTime
                    if (ts > endUs) break
                    if (first < 0) first = ts
                    info.offset = 0; info.size = n; info.presentationTimeUs = ts - first; info.flags = android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                    mux.writeSampleData(t, buf, info)
                    last = ts
                    ex.advance()
                }
                mux.stop(); mux.release()
            }
            "audio/mpeg" -> {
                out = File(ctx.cacheDir, "song-${now()}.mp3")
                FileOutputStream(out).use { os ->
                    val arr = ByteArray(1 shl 20)
                    while (true) {
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) break
                        val ts = ex.sampleTime
                        if (ts > endUs) break
                        if (first < 0) first = ts
                        buf.position(0); buf.get(arr, 0, n)
                        os.write(arr, 0, n)
                        last = ts
                        ex.advance()
                    }
                }
            }
            else -> { ex.release(); return null }
        }
        ex.release()
        if (first < 0 || out.length() < 1000) { out.delete(); null } else out to ((last - first) / 1000).coerceAtLeast(1000)
    }.getOrNull()

    fun info(ctx: Context, uri: Uri): Triple<String, String, Long> {
        val mmr = MediaMetadataRetriever()
        return runCatching {
            mmr.setDataSource(ctx, uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }?.substringBeforeLast('.')
                ?: t("Canzone")
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(title, artist, dur)
        }.getOrDefault(Triple(t("Canzone"), "", 0L)).also { runCatching { mmr.release() } }
    }
}

/** pick 30 seconds of a song from the phone and post them as a story */
@Composable
fun AudioStoryDialog(uri: Uri, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val info by produceState<Triple<String, String, Long>?>(null, uri) { value = withContext(Dispatchers.IO) { AudioCut.info(ctx, uri) } }
    var start by remember { mutableFloatStateOf(0f) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    var playing by remember { mutableStateOf(false) }
    fun stop() { runCatching { player[0]?.release() }; player[0] = null; playing = false }
    DisposableEffect(Unit) { onDispose { stop() } }
    val dur = info?.third ?: 0L
    val maxStart = (dur - 30_000L).coerceAtLeast(0L)
    MonoDialog(
        onDismissRequest = { stop(); onDismiss() },
        title = { Text(t("Storia con una canzone")) },
        text = {
            Column {
                val i = info
                if (i == null) CircularProgressIndicator()
                else {
                    Text(i.first, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    if (i.second.isNotBlank()) Text(i.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(t("Da {0} a {1}", Fmt.duration((start * maxStart).toLong()), Fmt.duration((start * maxStart).toLong() + minOf(30_000L, dur))), style = MaterialTheme.typography.labelLarge)
                    if (maxStart > 0) Slider(value = start, onValueChange = { start = it; stop() })
                    OutlinedButton(onClick = {
                        if (playing) stop() else {
                            player[0] = runCatching {
                                MediaPlayer().apply { setDataSource(ctx, uri); prepare(); seekTo((start * maxStart).toInt()); start() }
                            }.getOrNull()
                            playing = player[0] != null
                        }
                    }, shape = CircleShape) { Text(if (playing) t("Ferma") else t("Ascolta il pezzo")) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(caption, { caption = it.take(140) }, label = { Text(t("Testo (facoltativo)")) }, modifier = Modifier.fillMaxWidth())
                    if (failed) Text(t("Questo formato non si può usare: prova un file MP3 o M4A."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = info != null && !busy, onClick = {
                val i = info ?: return@TextButton
                busy = true
                stop()
                val from = (start * maxStart).toLong()
                Graph.scope.launch {
                    val res = withContext(Dispatchers.IO) { AudioCut.cut(ctx, uri, from, 30_000L) }
                    if (res == null) { failed = true; busy = false; return@launch }
                    Stories.postAudio(res.first, res.second, Song(i.first, i.second, "", "", local = true).json(), caption.trim())
                    onDismiss()
                }
            }) { Text(if (busy) t("Un momento…") else t("Pubblica")) }
        },
        dismissButton = { TextButton(onClick = { stop(); onDismiss() }) { Text(t("Annulla")) } },
    )
}

/** plays the song of a story while it is on screen */
@Composable
fun StoryMusic(song: Song?, localPath: String?, paused: Boolean) {
    val ctx = LocalContext.current
    val player = remember(song?.preview, localPath) { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(song?.preview, localPath) {
        val src = localPath ?: song?.preview?.takeIf { it.isNotBlank() }
        if (src != null) {
            player[0] = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    if (localPath != null) setDataSource(ctx, Uri.fromFile(File(localPath))) else setDataSource(src)
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            }.getOrNull()
        }
        onDispose { runCatching { player[0]?.release() }; player[0] = null }
    }
    LaunchedEffect(paused) {
        val p = player[0] ?: return@LaunchedEffect
        runCatching { if (paused) { if (p.isPlaying) p.pause() } else if (!p.isPlaying) p.start() }
    }
}
