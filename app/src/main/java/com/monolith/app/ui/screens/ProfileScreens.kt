package com.monolith.app.ui.screens

import android.content.Context
import com.monolith.app.data.SavedItem
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Message
import com.monolith.app.net.Bond
import com.monolith.app.net.Bonds
import com.monolith.app.net.Stories
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.launch
import java.io.File

// ====================================================================== the bond

/** a monolith that grows with the bond: from a pebble to a tall slab */
@Composable
fun BondStone(bond: Bond, modifier: Modifier = Modifier) {
    val target = 0.22f + 0.78f * (bond.level - 1) / 4f
    val h by animateFloatAsState(target, tween(1200), label = "bond")
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val w = size.width * 0.34f
        val left = (size.width - w) / 2
        val ground = size.height * 0.94f
        val top = ground - (ground - size.height * 0.04f) * h
        drawLine(faint, Offset(size.width * 0.12f, ground), Offset(size.width * 0.88f, ground), 2.dp.toPx(), StrokeCap.Round)
        // the slab, with a slanted top like a stone cut by hand
        val p = Path().apply {
            moveTo(left, ground)
            lineTo(left, top + w * 0.12f)
            lineTo(left + w * 0.55f, top)
            lineTo(left + w, top + w * 0.06f)
            lineTo(left + w, ground)
            close()
        }
        drawPath(p, ink)
        // one notch for each day in a row, up to seven
        val notches = bond.streak.coerceAtMost(7)
        for (i in 0 until notches) {
            val y = ground - (i + 1) * 9.dp.toPx()
            if (y < top + 14.dp.toPx()) break
            drawLine(faint, Offset(left + w * 0.3f, y), Offset(left + w * 0.7f, y), 1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

// ====================================================================== profile

@Composable
fun ProfileScreen(nav: NavHostController, pk: String) {
    val ctx = LocalContext.current
    val myPub = Graph.identity.keys?.pub
    val mine = pk == myPub
    val friend by remember(pk) { Graph.db.friends().observe(pk) }.collectAsState(initial = null)
    val myProfile by Graph.identity.profile.collectAsState()
    val statuses by Graph.statuses.friends.collectAsState()
    val myStatus by Graph.statuses.mine.collectAsState()
    val bdays by Graph.birthdays.friends.collectAsState()
    val myBday by Graph.birthdays.mine.collectAsState()
    val storyMsgs by remember(pk) { Graph.db.messages().conversation(Stories.convOf(pk)) }.collectAsState(initial = emptyList())
    val stories = Stories.visible(storyMsgs).filter { it.sender == pk }
    val bond by produceState<Bond?>(null, pk) { if (!mine) value = Bonds.with(pk) }
    val favs by Graph.favorites.all.collectAsState()
    val savedAll by Graph.saved.all.collectAsState()
    val saved = savedAll.filter { it.owner == pk }.sortedByDescending { it.savedAt }
    var statusSheet by remember { mutableStateOf(false) }
    var textStory by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Graph.scope.launch { Stories.postPhoto(uri) }
    }

    val emoji = if (mine) myProfile?.emoji ?: "🗿" else friend?.emoji ?: "🙂"
    val name = if (mine) myProfile?.displayName ?: "" else friend?.displayName ?: ""
    val user = if (mine) myProfile?.username ?: "" else friend?.username ?: ""
    val bio = if (mine) myProfile?.bio ?: "" else friend?.bio ?: ""
    val status = (if (mine) myStatus else statuses[pk])?.takeIf { it.active }
    val bday = if (mine) myBday else bdays[pk]

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Spacer(Modifier.weight(1f))
            if (!mine && friend?.status == FriendStatus.FRIEND) {
                val fav = pk in favs
                IconButton(onClick = { Graph.favorites.toggle(pk) }) {
                    Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) t("Togli dai preferiti") else t("Aggiungi ai preferiti"))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(132.dp).clip(CircleShape)
                    .then(if (stories.isNotEmpty()) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .bounceClick(0.95f) { if (stories.isNotEmpty()) nav.navigate(Routes.stories(pk)) },
                contentAlignment = Alignment.Center,
            ) { Avatar(emoji, 116.dp) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (!mine) com.monolith.app.ui.components.BondBadge(pk)
            }
            if (user.isNotBlank()) Text("@$user", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(bio, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            if (status != null || bday != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    status?.let {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.bounceClick(0.95f) { if (mine) statusSheet = true }) {
                            Text(it.emoji + "  " + it.text.ifBlank { t("Il tuo stato") }, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    bday?.let {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text("🎂  " + birthdayText(it), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (!mine && friend?.status == FriendStatus.FRIEND) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nav.navigate(Routes.chat(pk)) }, shape = CircleShape, modifier = Modifier.height(48.dp)) { Text(t("Scrivi")) }
                }
            }
            if (mine) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = CircleShape, modifier = Modifier.height(48.dp)) { Text(t("Storia con foto")) }
                    OutlinedButton(onClick = { textStory = true }, shape = CircleShape, modifier = Modifier.height(48.dp)) { Text(t("Storia di testo")) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { statusSheet = true }) { Text(t("Il tuo stato")) }
                    TextButton(onClick = { nav.navigate("settings") }) { Text(t("Modifica profilo")) }
                }
            }
        }
        if (stories.isNotEmpty()) {
            Text(t("Storie"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stories, key = { it.id }) { m ->
                    Box(
                        Modifier.size(78.dp, 120.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                            .bounceClick(0.95f) { nav.navigate(Routes.stories(pk)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (m.kind == "photo" && m.mediaPath != null) AsyncImage(model = File(m.mediaPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(m.text, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 11.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
                        Text(
                            Fmt.time(m.ts), color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        )
                    }
                }
            }
        }
        if (!mine && saved.isNotEmpty()) SavedSection(nav, saved, friend?.displayName ?: "")
        bond?.let { b ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BondStone(b, Modifier.size(90.dp, 120.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Legame"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(b.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (b.streak > 0) t("🔥 {0} giorni di fila", b.streak) else t("Scrivetevi oggi per iniziare una serie"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(t("{0} giorni insieme nell'ultimo mese", b.days30), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
    if (statusSheet) StatusSheet { statusSheet = false }
    if (textStory) {
        var text by remember { mutableStateOf("") }
        MonoDialog(
            onDismissRequest = { textStory = false },
            title = { Text(t("Storia di testo")) },
            text = { OutlinedTextField(text, { text = it.take(200) }, label = { Text(t("Cosa vuoi raccontare?")) }, modifier = Modifier.fillMaxWidth(), maxLines = 5) },
            confirmButton = { TextButton(onClick = { textStory = false; Graph.scope.launch { Stories.postText(text) } }, enabled = text.isNotBlank()) { Text(t("Pubblica")) } },
            dismissButton = { TextButton(onClick = { textStory = false }) { Text(t("Annulla")) } },
        )
    }
}

// ====================================================================== story viewer

@Composable
fun StoryViewer(nav: NavHostController, pk: String) {
    val myPub = Graph.identity.keys?.pub
    val friend by remember(pk) { Graph.db.friends().observe(pk) }.collectAsState(initial = null)
    val myProfile by Graph.identity.profile.collectAsState()
    val msgs by remember(pk) { Graph.db.messages().conversation(Stories.convOf(pk)) }.collectAsState(initial = null)
    val list = msgs?.let { all -> Stories.visible(all).filter { it.sender == pk } }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var paused by remember { mutableStateOf(false) }

    if (list != null && list.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val items = list ?: return
    val cur = items[index.coerceIn(0, items.lastIndex)]
    fun next() { if (index < items.lastIndex) index++ else nav.popBackStack() }
    fun prev() { if (index > 0) index-- }
    var lastId by remember { mutableStateOf("") }
    LaunchedEffect(cur.id, paused) {
        if (lastId != cur.id) { lastId = cur.id; progress.snapTo(0f) }
        Stories.markSeen(pk, cur.ts)
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * 5000).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        next()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(items.size) {
            detectTapGestures(
                onPress = { paused = true; tryAwaitRelease(); paused = false },
                onTap = { o -> if (o.x < size.width / 3f) prev() else next() },
            )
        },
    ) {
        if (cur.kind == "photo") {
            val path = cur.mediaPath
            if (path != null) AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (cur.text.isNotBlank()) {
                Text(
                    cur.text, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } else {
            Text(
                cur.text, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { i, _ ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                        val f = when {
                            i < index -> 1f
                            i == index -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(if (pk == myPub) myProfile?.emoji ?: "🗿" else friend?.emoji ?: "🙂", 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (pk == myPub) t("Tu") else friend?.displayName ?: "", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(Fmt.ago(cur.ts), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
                if (pk == myPub) {
                    IconButton(onClick = { scope.launch { Graph.messenger.deleteForEveryone(cur) } }) { Icon(Icons.Rounded.DeleteOutline, t("Elimina"), tint = Color.White) }
                }
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, t("Chiudi"), tint = Color.White) }
            }
        }
    }
}

// ====================================================================== scratch message

private fun scratched(ctx: Context) = ctx.getSharedPreferences("scratched", Context.MODE_PRIVATE)

/** a message hidden under a silver layer: rub it with a finger to read it */
@Composable
fun ScratchCard(m: Message, ink: Color, bubble: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var revealed by remember(m.id) { mutableStateOf(m.outgoing || scratched(ctx).getBoolean(m.id, false)) }
    val path = remember(m.id) { Path() }
    var tick by remember { mutableIntStateOf(0) }
    val touched = remember(m.id) { HashSet<Int>() }
    val cover by animateFloatAsState(if (revealed) 0f else 1f, tween(500), label = "scratch")
    Box(modifier.padding(14.dp)) {
        Column {
            Text("✨ " + t("Messaggio da grattare"), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(m.text, color = ink, style = MaterialTheme.typography.bodyLarge)
        }
        if (cover > 0f) {
            Canvas(
                Modifier.matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = cover }
                    .pointerInput(m.id) {
                        detectDragGestures(
                            onDragStart = { o -> path.moveTo(o.x, o.y) },
                            onDrag = { change, _ ->
                                val p = change.position
                                path.lineTo(p.x, p.y)
                                // count the 12x6 cells already scratched
                                val cx = (p.x / size.width * 12).toInt().coerceIn(0, 11)
                                val cy = (p.y / size.height * 6).toInt().coerceIn(0, 5)
                                touched += cy * 12 + cx
                                tick++
                                if (touched.size > 40 && !revealed) {
                                    revealed = true
                                    scratched(ctx).edit().putBoolean(m.id, true).apply()
                                }
                            },
                        )
                    },
            ) {
                if (tick < 0) return@Canvas
                drawRect(bubble)
                drawRect(ink.copy(alpha = 0.22f))
                // a few sparkles on the cover
                for (i in 0 until 14) {
                    val x = (i * 73 % 100) / 100f * size.width
                    val y = (i * 37 % 100) / 100f * size.height
                    drawCircle(ink.copy(alpha = 0.35f), 1.6.dp.toPx(), Offset(x, y))
                }
                drawPath(path, Color.Black, style = Stroke(26.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
            }
            Text(
                t("Gratta con il dito"), color = ink.copy(alpha = 0.8f * cover), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun savedText(s: SavedItem): String = when (s.kind) {
    "voice" -> "🎤  " + t("Messaggio vocale")
    "loc" -> "📍  " + t("Posizione")
    "place" -> "📍  " + s.text
    "scratch" -> "✨  " + s.text
    else -> s.text
}

/** messages and photos you kept from the chat with this friend: they stay even if the chat is deleted */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedSection(nav: NavHostController, items: List<SavedItem>, friendName: String) {
    var remove by remember { mutableStateOf<SavedItem?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) { onDispose { runCatching { player[0]?.release() } } }
    val myPub = Graph.identity.keys?.pub
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("Salvati"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(items.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val photos = items.filter { it.kind == "photo" && it.media != null }
        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            ) {
                items(photos, key = { it.id }) { p ->
                    AsyncImage(
                        File(p.media!!), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(RoundedCornerShape(18.dp))
                            .combinedClickable(onClick = { nav.navigate(Routes.photo(p.media, "saved-" + p.id)) }, onLongClick = { remove = p }),
                    )
                }
            }
        }
        items.filter { it.kind != "photo" }.forEach { s ->
            Surface(
                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .combinedClickable(
                        onClick = {
                            if (s.kind == "voice" && s.media != null) {
                                runCatching { player[0]?.release() }
                                player[0] = null
                                if (playing == s.id) {
                                    playing = null
                                } else {
                                    player[0] = runCatching {
                                        MediaPlayer().apply {
                                            setDataSource(s.media); prepare(); start()
                                            setOnCompletionListener { playing = null }
                                        }
                                    }.getOrNull()
                                    playing = if (player[0] != null) s.id else null
                                }
                            }
                        },
                        onLongClick = { remove = s },
                    ),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        if (s.kind == "voice" && playing == s.id) "⏸  " + t("In riproduzione…") else savedText(s),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (s.from == myPub) t("Tu") else friendName) + " · " + Fmt.dayShort(s.ts),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            t("Tieni premuto per togliere"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
    remove?.let { r ->
        MonoDialog(
            onDismissRequest = { remove = null },
            title = { Text(t("Togliere dai salvati?")) },
            confirmButton = { TextButton(onClick = { Graph.saved.remove(r.id); remove = null }) { Text(t("Togli")) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(t("Annulla")) } },
        )
    }
}
