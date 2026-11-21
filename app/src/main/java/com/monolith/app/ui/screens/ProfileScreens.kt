package com.monolith.app.ui.screens

import android.content.Context
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Edit
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
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.tiltDepth
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
    val stories = remember(storyMsgs, pk) { Stories.visible(storyMsgs).filter { it.sender == pk } }
    val bond by produceState<Bond?>(null, pk) { if (!mine) value = Bonds.with(pk) }
    val favs by Graph.favorites.all.collectAsState()
    val savedAll by Graph.saved.all.collectAsState()
    val saved = remember(savedAll, pk) { savedAll.filter { it.owner == pk }.sortedByDescending { it.savedAt } }
    val friendsList by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    val bondDays by Graph.bondDays.days.collectAsState()
    val earnedMine by com.monolith.app.net.Badges.earned.collectAsState()
    val theirBadges by Graph.profileExtras.badges.collectAsState()
    val myFeatured by Graph.profileExtras.featured.collectAsState()
    val featuredOf by Graph.profileExtras.featuredOf.collectAsState()
    val myCover by Graph.profileExtras.cover.collectAsState()
    val covers by Graph.profileExtras.covers.collectAsState()
    var statusSheet by remember { mutableStateOf(false) }
    var storySheet by remember { mutableStateOf(false) }
    var textStory by remember { mutableStateOf(false) }
    var badgeDetail by remember { mutableStateOf<com.monolith.app.net.BadgeDef?>(null) }
    var songUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) songUri = u }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { PendingPhoto.set(uri, null); nav.navigate("photoedit") }
    }
    var pendingVideo by remember { mutableStateOf(false) }
    fun granted(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val callPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(android.Manifest.permission.RECORD_AUDIO)) Graph.calls.startCall(pk, pendingVideo)
    }
    fun call(video: Boolean) {
        pendingVideo = video
        val need = buildList {
            if (!granted(android.Manifest.permission.RECORD_AUDIO)) add(android.Manifest.permission.RECORD_AUDIO)
            if (video && !granted(android.Manifest.permission.CAMERA)) add(android.Manifest.permission.CAMERA)
        }
        if (need.isEmpty()) Graph.calls.startCall(pk, video) else callPerms.launch(need.toTypedArray())
    }

    val isFriend = !mine && friend?.status == FriendStatus.FRIEND
    val emoji = if (mine) myProfile?.emoji ?: "🗿" else friend?.emoji ?: "🙂"
    val name = if (mine) myProfile?.displayName ?: "" else friend?.displayName ?: ""
    val user = if (mine) myProfile?.username ?: "" else friend?.username ?: ""
    val bio = if (mine) myProfile?.bio ?: "" else friend?.bio ?: ""
    val status = (if (mine) myStatus else statuses[pk])?.takeIf { it.active }
    val bday = if (mine) myBday else bdays[pk]
    val cover = if (mine) myCover else covers[pk] ?: "monoliths"
    val featured = (if (mine) myFeatured else featuredOf[pk] ?: emptyList()).mapNotNull { com.monolith.app.net.Badges.def(it) }
    val badgeIds: List<String> = if (mine) earnedMine.entries.sortedByDescending { it.value }.map { it.key } else theirBadges[pk] ?: emptyList()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        // ------------------------------------------------ cover, top bar, picture
        val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxWidth().height(topInset + 214.dp)) {
            com.monolith.app.ui.components.CoverArt(cover, Modifier.fillMaxWidth().height(topInset + 156.dp))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverButton { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) } }
                Spacer(Modifier.weight(1f))
                if (mine) {
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        modifier = Modifier.bounceClick(0.94f) { nav.navigate("editprofile") },
                    ) {
                        Row(Modifier.height(48.dp).padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t("Modifica"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (isFriend) {
                    val fav = pk in favs
                    CoverButton {
                        IconButton(onClick = { Graph.favorites.toggle(pk) }) {
                            Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) t("Togli dai preferiti") else t("Aggiungi ai preferiti"))
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).size(124.dp).tiltDepth(8f, 10f).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background).padding(5.dp).clip(CircleShape)
                    .then(if (stories.isNotEmpty()) Modifier.border(3.dp, ink, CircleShape).padding(5.dp) else Modifier)
                    .bounceClick(0.95f) { if (stories.isNotEmpty()) nav.navigate(Routes.stories(pk)) },
                contentAlignment = Alignment.Center,
            ) { Avatar(emoji, if (stories.isNotEmpty()) 104.dp else 114.dp) }
        }

        // ------------------------------------------------ who
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (!mine) com.monolith.app.ui.components.BondBadge(pk)
            }
            if (user.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("@$user  ·  #${com.monolith.app.core.tagOf(pk)}", style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            if (featured.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    featured.forEach { d -> com.monolith.app.ui.components.BadgeEmblem(d, true, 38.dp, Modifier.bounceClick(0.9f) { badgeDetail = d }) }
                }
            }
            if (bio.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(bio, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            if (status != null || bday != null) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    status?.let { InfoChip(it.emoji, null, it.text.ifBlank { t("Il tuo stato") }) { if (mine) statusSheet = true } }
                    bday?.let { InfoChip(null, Icons.Rounded.Cake, birthdayText(it)) {} }
                }
            }
        }

        // ------------------------------------------------ numbers
        val stats: List<Pair<String, String>> = when {
            mine -> listOf(
                friendsList.count { it.status == FriendStatus.FRIEND }.toString() to t("Amici"),
                earnedMine.size.toString() to t("Distintivi"),
                (bondDays.values.maxOfOrNull { com.monolith.app.data.streakOf(it) } ?: 0).toString() to t("Serie migliore"),
            )
            isFriend -> listOf(
                friendSinceShort(friend?.createdAt ?: 0L) to t("Amici da"),
                com.monolith.app.data.streakOf(bondDays[pk] ?: emptySet()).toString() to t("Giorni di fila"),
                badgeIds.size.toString() to t("Distintivi"),
            )
            else -> emptyList()
        }
        if (stats.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp),
            ) {
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    stats.forEachIndexed { i, (v, label) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ what you can do
        if (mine || isFriend) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isFriend) {
                    Button(onClick = { nav.navigate(Routes.chat(pk)) }, shape = CircleShape, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.ChatBubble, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Scrivi"))
                    }
                    androidx.compose.material3.FilledTonalIconButton(onClick = { call(false) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Call, t("Chiama")) }
                    androidx.compose.material3.FilledTonalIconButton(onClick = { call(true) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Videocam, t("Videochiamata")) }
                } else {
                    Button(onClick = { storySheet = true }, shape = CircleShape, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Nuova storia"), maxLines = 1)
                    }
                    androidx.compose.material3.FilledTonalButton(onClick = { statusSheet = true }, shape = CircleShape, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Mood, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (status != null) t("Cambia stato") else t("Imposta stato"), maxLines = 1)
                    }
                }
            }
        }

        // ------------------------------------------------ stories
        if (stories.isNotEmpty()) {
            SectionHead(t("Storie"), stories.size.toString())
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stories, key = { it.id }) { m ->
                    Box(
                        Modifier.size(84.dp, 128.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                            .bounceClick(0.95f) { nav.navigate(Routes.stories(pk)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (m.kind == "photo" && m.mediaPath != null) AsyncImage(model = File(m.mediaPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(m.text, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 11.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                        Text(
                            Fmt.time(m.ts), color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(topEnd = 10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // ------------------------------------------------ badges
        BadgesSection(pk, mine, badgeIds) { badgeDetail = it }

        // ------------------------------------------------ the two of you
        if (isFriend) {
            SectionHead(t("Voi due"))
            bond?.let { b ->
                Surface(
                    shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        BondStone(b, Modifier.size(64.dp, 88.dp).tiltDepth(6f, 12f))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("Legame"), style = MaterialTheme.typography.labelMedium, color = muted)
                            Text(b.name, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            BondMeter(b.level)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (b.streak > 0) t("{0} giorni insieme nell'ultimo mese", b.days30) else t("Scrivetevi oggi per iniziare una serie"),
                                style = MaterialTheme.typography.bodySmall, color = muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            NearbyCard(pk, friend?.displayName ?: "")
        }
        if (!mine && saved.isNotEmpty()) SavedSection(nav, saved, friend?.displayName ?: "")
        Spacer(Modifier.navigationBarsPadding().height(36.dp))
    }

    if (statusSheet) StatusSheet { statusSheet = false }
    songUri?.let { u -> AudioStoryDialog(u) { songUri = null } }
    if (storySheet) {
        MonoSheet(onDismissRequest = { storySheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(t("Nuova storia"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 4.dp))
                Text(t("Resta visibile ai tuoi amici per 24 ore"), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
                SheetRow(Icons.Rounded.PhotoCamera, t("Foto"), t("Con filtri in bianco e nero e musica")) {
                    storySheet = false
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetRow(Icons.Rounded.TextFields, t("Testo"), t("Qualche parola, anche con una canzone")) { storySheet = false; textStory = true }
                SheetRow(Icons.Rounded.MusicNote, t("Canzone dal telefono"), t("30 secondi di un brano che hai sul telefono")) { storySheet = false; pickSong.launch("audio/*") }
            }
        }
    }
    badgeDetail?.let { d -> BadgeDetailDialog(d, mine, pk) { badgeDetail = null } }
    if (textStory) {
        var text by remember { mutableStateOf("") }
        var song by remember { mutableStateOf<Song?>(null) }
        var musicSheet by remember { mutableStateOf(false) }
        MonoDialog(
            onDismissRequest = { textStory = false },
            title = { Text(t("Storia di testo")) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it.take(200) }, label = { Text(t("Cosa vuoi raccontare?")) }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
                    Spacer(Modifier.height(10.dp))
                    val s = song
                    if (s == null) TextButton(onClick = { musicSheet = true }) { Text("♪  " + t("Aggiungi musica")) }
                    else MusicChip(s) { song = null }
                }
            },
            confirmButton = { TextButton(onClick = { textStory = false; val mu = song?.json(); Graph.scope.launch { Stories.postText(text, mu) } }, enabled = text.isNotBlank()) { Text(t("Pubblica")) } },
            dismissButton = { TextButton(onClick = { textStory = false }) { Text(t("Annulla")) } },
        )
        if (musicSheet) MusicPickerSheet(onDismiss = { musicSheet = false }, onPick = { song = it; musicSheet = false })
    }
}

/** five little bars: the level of the bond */
@Composable
private fun BondMeter(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            Box(
                Modifier.size(22.dp, 6.dp).clip(CircleShape)
                    .background(if (i <= level) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** the title of a part of the profile, with an optional count and action on the right */
@Composable
internal fun SectionHead(title: String, count: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 28.dp, bottom = 10.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun InfoChip(emoji: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.bounceClick(0.96f, onClick = onClick)) {
        Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) Text(emoji, fontSize = 15.sp)
            if (icon != null) Icon(icon, null, Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "3 mesi", "2 anni": how long you have been friends, short */
private fun friendSinceShort(ts: Long): String {
    if (ts <= 0L) return "—"
    val a = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val b = java.util.Calendar.getInstance()
    var months = (b.get(java.util.Calendar.YEAR) - a.get(java.util.Calendar.YEAR)) * 12 + (b.get(java.util.Calendar.MONTH) - a.get(java.util.Calendar.MONTH))
    if (b.get(java.util.Calendar.DAY_OF_MONTH) < a.get(java.util.Calendar.DAY_OF_MONTH)) months--
    months = months.coerceAtLeast(0)
    return when {
        months == 0 -> t("poco")
        months < 12 -> if (months == 1) t("1 mese") else t("{0} mesi", months)
        months < 24 -> t("1 anno")
        else -> t("{0} anni", months / 12)
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
    val curEx = remember(cur.id, cur.extra) { runCatching { org.json.JSONObject(cur.extra ?: "{}") }.getOrDefault(org.json.JSONObject()) }
    val curSong = Song.from(curEx.optJSONObject("mu"))
    val hasBar = pk != myPub && friend?.status == FriendStatus.FRIEND
    val storyCtx = androidx.compose.ui.platform.LocalContext.current
    StoryMusic(curSong?.takeIf { !it.local }, if (cur.kind == "voice") cur.mediaPath?.takeIf { cur.progress >= 1f || cur.outgoing } else null, paused)
    LaunchedEffect(cur.id, paused) {
        if (lastId != cur.id) { lastId = cur.id; progress.snapTo(0f) }
        Stories.markSeen(pk, cur.ts)
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * (if (curSong != null || cur.kind == "voice") 15000 else 5000)).toInt().coerceAtLeast(1)
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
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (hasBar) 128.dp else 24.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } else if (cur.kind == "voice") {
            VinylStory(curSong, cur.text, Modifier.align(Alignment.Center))
        } else {
            Text(
                cur.text, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
        if (curSong != null && cur.kind != "voice") {
            MusicChip(curSong, Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = (if (cur.text.isNotBlank() && cur.kind == "photo") 84.dp else 24.dp) + (if (hasBar) 108.dp else 0.dp)))
        }
        if (hasBar) {
            StoryReplyBar(friend?.displayName?.substringBefore(' ') ?: "", Modifier.align(Alignment.BottomCenter), onFocus = { paused = it }) { text, react ->
                val story = cur
                scope.launch { Graph.messenger.replyToStory(story, text, react) }
                android.widget.Toast.makeText(storyCtx, if (react) text else t("Inviato in chat"), android.widget.Toast.LENGTH_SHORT).show()
            }
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
    Column(Modifier.fillMaxWidth()) {
        SectionHead(t("Salvati"), items.size.toString())
        val photos = items.filter { it.kind == "photo" && it.media != null }
        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
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
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
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


/** a story made of a song: a record spinning slowly */
@Composable
private fun VinylStory(song: Song?, caption: String, modifier: Modifier) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "vinyl")
    val rot by tr.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "rot")
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(220.dp).graphicsLayer { rotationZ = rot }) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFF111111), r)
            for (k in 1..9) drawCircle(Color.White.copy(alpha = 0.06f), r * (0.4f + k * 0.065f), style = Stroke(1.5f))
            drawCircle(Color(0xFFE6E6E6), r * 0.3f)
            drawCircle(Color(0xFF111111), r * 0.04f)
            drawLine(Color.White.copy(alpha = 0.25f), Offset(r * 1.25f, r * 0.55f), Offset(r * 1.6f, r * 0.4f), 3f)
        }
        Spacer(Modifier.height(24.dp))
        Text(song?.title ?: "", color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (!song?.artist.isNullOrBlank()) Text(song?.artist ?: "", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(caption, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
    }
}



@Composable
private fun CoverButton(content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) { content() }
}

/** the badges on a profile: mine (earned, with all the others in "see all") or a friend's */
@Composable
private fun BadgesSection(pk: String, mine: Boolean, earnedIds: List<String>, onDetail: (com.monolith.app.net.BadgeDef) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(mine) { if (mine) runCatching { com.monolith.app.net.Badges.refresh(ctx) } }
    val earned = remember(earnedIds) { earnedIds.mapNotNull { com.monolith.app.net.Badges.def(it) } }
    if (!mine && earned.isEmpty()) return
    var all by remember { mutableStateOf(false) }
    SectionHead(t("Distintivi"), t("{0} su {1}", earned.size, com.monolith.app.net.Badges.ALL.size), t("Vedi tutti")) { all = true }
    if (earned.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text(
                t("Usa Monolith e sbloccherai i primi distintivi."), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
            )
        }
    } else {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // no keys: a badge earned while the screen is open must not scroll the row
            items(earned) { d ->
                Column(Modifier.width(84.dp).bounceClick(0.92f) { onDetail(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    com.monolith.app.ui.components.BadgeEmblem(d, true, 64.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(t(d.name), style = MaterialTheme.typography.labelMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (all) {
        MonoSheet(onDismissRequest = { all = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text(t("Distintivi"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                Text(
                    t("{0} su {1}", earned.size, com.monolith.app.net.Badges.ALL.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
                val list = if (mine) earned + com.monolith.app.net.Badges.ALL.filter { it.id !in earnedIds } else earned
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 4,
                ) {
                    list.forEach { d ->
                        val got = d.id in earnedIds
                        Column(Modifier.weight(1f).bounceClick(0.94f) { onDetail(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            com.monolith.app.ui.components.BadgeEmblem(d, got, 68.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                t(d.name), style = MaterialTheme.typography.labelMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                color = if (got) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // fill the last row so the badges keep their size
                    repeat((4 - list.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun BadgeDetailDialog(d: com.monolith.app.net.BadgeDef, mine: Boolean, pk: String, onDismiss: () -> Unit) {
    val earnedMine by com.monolith.app.net.Badges.earned.collectAsState()
    val theirs by Graph.profileExtras.badges.collectAsState()
    val got = if (mine) d.id in earnedMine else d.id in (theirs[pk] ?: emptyList())
    MonoDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                com.monolith.app.ui.components.BadgeEmblem(d, got, 132.dp, Modifier.tiltDepth(10f, 18f))
                Spacer(Modifier.height(16.dp))
                Text(t(d.name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(t(d.desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (mine) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        earnedMine[d.id]?.let { t("Ottenuto il {0}", Fmt.dayShort(it)) } ?: t("Ancora da sbloccare"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Chiudi")) } },
    )
}
