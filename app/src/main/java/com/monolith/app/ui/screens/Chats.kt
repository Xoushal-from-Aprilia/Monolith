package com.monolith.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Group
import com.monolith.app.data.InviteState
import com.monolith.app.data.Message
import com.monolith.app.net.Media
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.BigTitle
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.EmptyState
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.StatusTicks
import com.monolith.app.ui.components.TypingDots
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.sharedBoundsKey
import com.monolith.app.ui.components.sharedElementKey
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private data class ConvRow(
    val conv: String,
    val title: String,
    val emoji: String,
    val last: Message?,
    val unread: Int,
    val online: Boolean,
    val isGroup: Boolean,
    val sort: Long,
)

@Composable
private fun rememberTicker(periodMs: Long = 15_000): Int {
    val v by produceState(0) {
        while (true) {
            delay(periodMs)
            value++
        }
    }
    return v
}

@Composable
fun ChatListScreen(nav: NavHostController) {
    val last by remember { Graph.db.messages().lastMessages() }.collectAsState(initial = emptyList())
    val unread by remember { Graph.db.messages().unread() }.collectAsState(initial = emptyList())
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val groups by remember { Graph.db.groups().all() }.collectAsState(initial = emptyList())
    val online by Graph.messenger.online.collectAsState()
    val typing by Graph.messenger.typing.collectAsState()
    val profile by Graph.identity.profile.collectAsState()
    val tick = rememberTicker()
    var newChat by remember { mutableStateOf(false) }

    val byPk = friends.associateBy { it.pubkey }
    val lastBy = last.associateBy { it.convId }
    val unreadBy = unread.associate { it.convId to it.n }
    val isOnline = { pk: String -> tick >= 0 && now() - (online[pk] ?: 0L) < 100_000L }
    val rows = buildList {
        friends.filter { it.status == FriendStatus.FRIEND && lastBy.containsKey(it.pubkey) }.forEach { f ->
            val m = lastBy[f.pubkey]
            add(ConvRow(f.pubkey, f.displayName, f.emoji, m, unreadBy[f.pubkey] ?: 0, isOnline(f.pubkey), false, m?.ts ?: 0))
        }
        groups.forEach { g ->
            val c = "g:" + g.id
            val m = lastBy[c]
            add(ConvRow(c, g.name, g.emoji, m, unreadBy[c] ?: 0, false, true, m?.ts ?: g.createdAt))
        }
    }.sortedByDescending { it.sort }
    val onlineFriends = friends.filter { it.status == FriendStatus.FRIEND && isOnline(it.pubkey) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BigTitle("Clubhouse", Modifier.weight(1f), subtitle = if (onlineFriends.isEmpty()) "Le tue conversazioni" else "${onlineFriends.size} amici online")
                    Avatar(profile?.emoji ?: "🗿", 44.dp, Modifier.padding(end = 20.dp).bounceClick { nav.navigate("settings") })
                }
            }
            if (onlineFriends.isNotEmpty()) {
                item(key = "online") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        items(onlineFriends, key = { it.pubkey }) { f ->
                            Column(
                                Modifier.width(64.dp).animateItem().bounceClick { nav.navigate(Routes.chat(f.pubkey)) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(f.emoji, 58.dp, online = true, ring = true)
                                Spacer(Modifier.height(4.dp))
                                Text(f.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "Nessuna conversazione",
                        if (friends.none { it.status == FriendStatus.FRIEND }) "Aggiungi un amico dalla scheda Amici per iniziare a chattare." else "Tocca \"Nuova chat\" per scrivere a un amico o creare un gruppo.",
                    )
                }
            }
            items(rows, key = { it.conv }) { r ->
                ConversationRow(r, typing[r.conv]?.takeIf { it.second > now() }?.first, byPk, Modifier.animateItem()) {
                    nav.navigate(Routes.chat(r.conv))
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { newChat = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Nuova chat") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).sharedBoundsKey("group-new"),
        )
    }

    if (newChat) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { newChat = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Nuova chat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().bounceClick(0.98f) { newChat = false; nav.navigate("group/new") }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GroupAdd, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Nuovo gruppo", style = MaterialTheme.typography.titleMedium)
                }
                SectionLabel("Amici")
                val list = friends.filter { it.status == FriendStatus.FRIEND }
                if (list.isEmpty()) {
                    Text("Non hai ancora amici. Cercali per username nella scheda Amici.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.pubkey }) { f ->
                        Row(
                            Modifier.fillMaxWidth().bounceClick(0.98f) { newChat = false; nav.navigate(Routes.chat(f.pubkey)) }.padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(f.emoji, 44.dp, online = isOnline(f.pubkey))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("@" + f.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(r: ConvRow, typingPk: String?, people: Map<String, Friend>, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(r.emoji, 54.dp, Modifier.sharedElementKey("avatar-" + r.conv), online = r.online)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(typingPk != null, label = "preview") { isTyping ->
                if (isTyping) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (r.isGroup) (people[typingPk]?.displayName ?: "") + " " else "") + "sta scrivendo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                        TypingDots()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val m = r.last
                        if (m != null && m.outgoing && m.kind != "system" && m.kind != "call") {
                            StatusTicks(m.status, MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        val prefix = if (r.isGroup && m != null && !m.outgoing && m.kind != "system") (people[m.sender]?.displayName?.substringBefore(' ') ?: "") + ": " else ""
                        Text(
                            if (m == null) "Gruppo creato" else prefix + Graph.messenger.previewText(m),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (r.unread > 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(r.last?.let { Fmt.chatStamp(it.ts) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(r.unread > 0, enter = scaleIn(Motion.bouncy()), exit = scaleOut()) {
                Box(
                    Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(r.unread.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ====================================================================== chat

private sealed class ChatItem(val key: String) {
    class Msg(val m: Message, val showName: Boolean) : ChatItem(m.id)
    class Day(val label: String, k: String) : ChatItem(k)
}

@Composable
fun ChatScreen(nav: NavHostController, conv: String) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isGroup = conv.startsWith("g:")
    val messages by remember(conv) { Graph.db.messages().conversation(conv) }.collectAsState(initial = emptyList())
    val friend by remember(conv) { Graph.db.friends().observe(conv) }.collectAsState(initial = null)
    val group by remember(conv) { Graph.db.groups().observe(conv.removePrefix("g:")) }.collectAsState(initial = null)
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val online by Graph.messenger.online.collectAsState()
    val typingMap by Graph.messenger.typing.collectAsState()
    val p2p by Graph.rtc.connected.collectAsState()
    val tick = rememberTicker(5_000)
    val openedAt = remember { now() }

    var text by rememberSaveable(conv) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var shareEvent by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    DisposableEffect(conv) {
        Graph.messenger.activeConv.value = conv
        onDispose { if (Graph.messenger.activeConv.value == conv) Graph.messenger.activeConv.value = null }
    }
    LaunchedEffect(conv) { if (!isGroup) Graph.rtc.ensureConnected(conv) }
    LaunchedEffect(messages.firstOrNull()?.id) { Graph.scope.launch { Graph.messenger.markRead(conv) } }

    val names = people.associateBy { it.pubkey }
    val title = if (isGroup) group?.name ?: "Gruppo" else friend?.displayName ?: "Chat"
    val emoji = if (isGroup) group?.emoji ?: "💬" else friend?.emoji ?: "🙂"
    val typing = typingMap[conv]?.takeIf { it.second > now() && tick >= 0 }
    val isOn = !isGroup && now() - (online[conv] ?: 0L) < 100_000L
    val subtitle = when {
        typing != null -> (if (isGroup) (names[typing.first]?.displayName ?: "") + " " else "") + "sta scrivendo…"
        isGroup -> group?.memberList()?.let { "${it.size} membri" } ?: ""
        conv in p2p -> "online · connessione diretta"
        isOn -> "online"
        (friend?.lastSeen ?: 0L) > 0L -> "ultimo accesso " + Fmt.ago(friend?.lastSeen ?: 0L)
        else -> "@" + (friend?.username ?: "")
    }

    val chatItems = remember(messages, names) {
        val out = ArrayList<ChatItem>()
        messages.forEachIndexed { i, m ->
            val older = messages.getOrNull(i + 1)
            val showName = isGroup && !m.outgoing && m.kind != "system" && (older == null || older.sender != m.sender || older.kind == "system")
            out.add(ChatItem.Msg(m, showName))
            if (older == null || !Fmt.sameDay(older.ts, m.ts)) {
                out.add(ChatItem.Day(if (Fmt.sameDay(m.ts, now())) "Oggi" else Fmt.dayLong(m.ts), "day" + Fmt.startOfDay(m.ts)))
            }
        }
        out
    }

    // ---- launchers
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Graph.scope.launch { Graph.messenger.sendPhoto(conv, uri, "") }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) Graph.scope.launch { Graph.messenger.sendPhoto(conv, u, "") }
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        cameraUri = u
        takePhoto.launch(u)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) launchCamera() }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) Graph.calls.startCall(conv) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            Graph.location.watch(true)
            Graph.scope.launch {
                var waited = 0
                while (Graph.location.me.value == null && waited < 15_000) { delay(500); waited += 500 }
                Graph.location.watch(false)
                Graph.location.me.value?.let { Graph.messenger.sendLocationPin(conv, it.latitude, it.longitude) }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        // ---- top bar
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
                Avatar(emoji, 42.dp, Modifier.sharedElementKey("avatar-$conv"), online = isOn)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedContent(
                        subtitle,
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                        label = "sub",
                    ) { s -> Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
                if (!isGroup && friend?.status == FriendStatus.FRIEND) {
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) Graph.calls.startCall(conv)
                        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Icon(Icons.Rounded.Call, "Chiama") }
                }
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    Graph.scope.launch { Graph.messenger.sendNudge(conv) }
                }) { Icon(Icons.Rounded.Vibration, "Trillo") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Altro") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Svuota chat") }, onClick = { menu = false; confirm = "clear" })
                        if (isGroup) {
                            DropdownMenuItem(text = { Text("Lascia il gruppo") }, onClick = { menu = false; confirm = "leave" })
                        } else if (friend?.status == FriendStatus.FRIEND) {
                            DropdownMenuItem(text = { Text("Rimuovi amico") }, onClick = { menu = false; confirm = "unfriend" })
                        }
                    }
                }
            }
        }

        // ---- messages
        val listState = rememberLazyListState()
        LaunchedEffect(messages.firstOrNull()?.id) { if (listState.firstVisibleItemIndex < 3) listState.animateScrollToItem(0) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (typing != null) {
                item(key = "typing") {
                    Row(Modifier.animateItem().padding(vertical = 4.dp)) {
                        Surface(shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            TypingDots(Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                        }
                    }
                }
            }
            items(chatItems, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.Day -> Box(Modifier.fillMaxWidth().animateItem().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(item.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    is ChatItem.Msg -> MessageRow(
                        item.m, item.showName, names[item.m.sender], isNew = item.m.ts > openedAt,
                        modifier = Modifier.animateItem(),
                        onPhoto = { path -> nav.navigate(Routes.photo(path, "photo-" + item.m.id)) },
                        onAddEvent = { ev ->
                            Graph.scope.launch { Graph.messenger.saveEvent(ev, notifyInvitees = false) }
                            Toast.makeText(ctx, "Aggiunto all'agenda", Toast.LENGTH_SHORT).show()
                        },
                        onOpenMap = { lat, lon ->
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))) }
                        },
                    )
                }
            }
        }

        // ---- composer
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box {
                    IconButton(onClick = { attach = true }) {
                        Icon(Icons.Rounded.Add, "Allega")
                    }
                    DropdownMenu(expanded = attach, onDismissRequest = { attach = false }) {
                        DropdownMenuItem(text = { Text("Galleria") }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = {
                            attach = false
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text("Fotocamera") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                            attach = false
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                            else camPerm.launch(Manifest.permission.CAMERA)
                        })
                        DropdownMenuItem(text = { Text("Posizione attuale") }, leadingIcon = { Icon(Icons.Rounded.LocationOn, null) }, onClick = {
                            attach = false
                            locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        })
                        DropdownMenuItem(text = { Text("Evento dall'agenda") }, leadingIcon = { Icon(Icons.Rounded.Event, null) }, onClick = {
                            attach = false; shareEvent = true
                        })
                    }
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            if (it.isNotBlank()) Graph.scope.launch { Graph.messenger.typing(conv) }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text("Messaggio", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    text.isNotBlank(),
                    transitionSpec = { (scaleIn(Motion.bouncy(), 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) },
                    label = "send",
                ) { canSend ->
                    if (canSend) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f) {
                                val t = text
                                text = ""
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Graph.scope.launch { Graph.messenger.sendText(conv, t) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, "Invia", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
                    } else {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.85f) {
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                                else camPerm.launch(Manifest.permission.CAMERA)
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PhotoCamera, "Foto", modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }
    }

    if (shareEvent) {
        val events by remember { Graph.db.agenda().all() }.collectAsState(initial = emptyList())
        val upcoming = events.filter { it.start > now() - 86_400_000L && it.inviteState != InviteState.DECLINED }
        ModalBottomSheet(onDismissRequest = { shareEvent = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text("Condividi un evento", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            if (upcoming.isEmpty()) Text("Nessun evento in programma.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(bottom = 24.dp)) {
                items(upcoming, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().bounceClick(0.98f) {
                            shareEvent = false
                            Graph.scope.launch { Graph.messenger.shareEventInChat(conv, e) }
                        }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                            Text(Fmt.dayShort(e.start) + if (e.allDay) "" else " · " + Fmt.time(e.start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (what) {
                        "leave" -> "Lasciare il gruppo?"
                        "unfriend" -> "Rimuovere $title dagli amici?"
                        else -> "Svuotare la chat?"
                    }
                )
            },
            text = { Text(if (what == "clear") "I messaggi verranno cancellati solo da questo telefono." else "Non riceverai più i suoi messaggi.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    Graph.scope.launch {
                        when (what) {
                            "leave" -> Graph.messenger.leaveGroup(conv.removePrefix("g:"))
                            "unfriend" -> Graph.messenger.removeFriend(conv)
                            else -> Graph.db.messages().clear(conv)
                        }
                    }
                    if (what != "clear") nav.popBackStack()
                }) { Text("Conferma") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun MessageRow(
    m: Message,
    showName: Boolean,
    sender: Friend?,
    isNew: Boolean,
    modifier: Modifier,
    onPhoto: (String) -> Unit,
    onAddEvent: (AgendaEvent) -> Unit,
    onOpenMap: (Double, Double) -> Unit,
) {
    val appear = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
    val anim = Modifier.graphicsLayer {
        val v = appear.value
        alpha = v.coerceIn(0f, 1f)
        scaleX = 0.85f + 0.15f * v
        scaleY = 0.85f + 0.15f * v
        translationY = (1f - v) * 30f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (m.outgoing) 1f else 0f, 1f)
    }

    if (m.kind == "system" || m.kind == "call") {
        Box(modifier.fillMaxWidth().padding(vertical = 6.dp).then(anim), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    (if (m.kind == "call") "📞  " else "") + m.text + if (m.kind == "call") "  ·  " + Fmt.time(m.ts) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        return
    }

    val out = m.outgoing
    val bubbleColor = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (out) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (out) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)

    Column(
        modifier.fillMaxWidth().padding(vertical = 2.dp).then(anim),
        horizontalAlignment = if (out) Alignment.End else Alignment.Start,
    ) {
        if (showName) {
            Text(
                (sender?.emoji ?: "") + " " + (sender?.displayName ?: "Membro"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
            )
        }
        when (m.kind) {
            "nudge" -> {
                val shake = remember { Animatable(if (isNew) 1f else 0f) }
                LaunchedEffect(Unit) { if (shake.value > 0f) shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 300f)) }
                Surface(
                    shape = shape, color = bubbleColor,
                    modifier = Modifier.graphicsLayer { rotationZ = shake.value * 12f },
                ) {
                    Text(
                        if (out) "📳  Hai mandato un trillo" else "📳  Trillo!",
                        color = textColor, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            "photo" -> {
                val ex = remember(m.extra) { runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()) }
                val (w, h) = Media.approxSize(ex.optInt("w"), ex.optInt("h"), 250)
                val path = m.mediaPath
                Box(
                    Modifier.size(w.dp, h.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .then(if (path != null && m.progress >= 1f || (out && path != null)) Modifier.bounceClick(0.97f) { onPhoto(path!!) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (path != null && (out || m.progress >= 1f)) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().sharedBoundsKey("photo-" + m.id),
                        )
                    }
                    if (m.progress < 1f) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { m.progress.coerceIn(0.02f, 1f) }, color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Fmt.time(m.ts), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        if (out) { Spacer(Modifier.width(3.dp)); StatusTicks(m.status, Color.White) }
                    }
                }
                if (m.text.isNotBlank()) {
                    Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            "event" -> {
                val ex = remember(m.extra) { runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()) }
                Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 280.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.optString("emoji", "📅"), fontSize = 28.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(ex.optString("title"), color = textColor, style = MaterialTheme.typography.titleMedium)
                                val start = ex.optLong("start")
                                Text(
                                    Fmt.dayShort(start) + if (ex.optBoolean("allDay")) "" else " · " + Fmt.time(start),
                                    color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!out) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    onAddEvent(
                                        AgendaEvent(
                                            id = newId(), title = ex.optString("title"), description = ex.optString("desc"),
                                            start = ex.optLong("start"), end = ex.optLong("end").takeIf { it > 0 },
                                            allDay = ex.optBoolean("allDay"), emoji = ex.optString("emoji", "📌"),
                                            createdAt = now(), updatedAt = now(),
                                        )
                                    )
                                },
                                shape = CircleShape,
                            ) { Text("Aggiungi all'agenda") }
                        }
                        MetaLine(m, textColor)
                    }
                }
            }
            "loc" -> {
                val ex = remember(m.extra) { runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()) }
                val lat = ex.optDouble("lat")
                val lon = ex.optDouble("lon")
                Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 260.dp).bounceClick(0.97f) { onOpenMap(lat, lon) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, null, tint = textColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Posizione condivisa", color = textColor, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon), color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        Text("Tocca per aprire nelle mappe", color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        MetaLine(m, textColor)
                    }
                }
            }
            else -> {
                Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 300.dp)) {
                    Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 6.dp)) {
                        Text(m.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
                        MetaLine(m, textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(m: Message, color: Color) {
    Row(Modifier.padding(top = 2.dp).fillMaxWidthIfShort(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.time(m.ts), color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        if (m.outgoing) {
            Spacer(Modifier.width(3.dp))
            StatusTicks(m.status, color)
        }
    }
}

private fun Modifier.fillMaxWidthIfShort(): Modifier = this

// ====================================================================== new group

@Composable
fun NewGroupScreen(nav: NavHostController) {
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("💬") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var picker by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize().sharedBoundsKey("group-new"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Chiudi") }
                Text("Nuovo gruppo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        creating = true
                        Graph.scope.launch {
                            val gid = Graph.messenger.createGroup(name, emoji, selected.toList())
                            if (gid != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    nav.popBackStack()
                                    nav.navigate(Routes.chat("g:$gid"))
                                }
                            }
                        }
                    },
                    enabled = name.isNotBlank() && selected.isNotEmpty() && !creating,
                    shape = CircleShape,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Crea") }
            }
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.88f) { picker = true },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 30.sp) }
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Nome del gruppo") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                )
            }
            SectionLabel("Membri · ${selected.size}")
            if (friends.isEmpty()) {
                EmptyState("Nessun amico", "Aggiungi amici per creare un gruppo.")
            }
            LazyColumn(Modifier.weight(1f)) {
                items(friends, key = { it.pubkey }) { f ->
                    val sel = f.pubkey in selected
                    Row(
                        Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { selected = if (sel) selected - f.pubkey else selected + f.pubkey }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(f.emoji, 46.dp, ring = sel)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("@" + f.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(
                            checked = sel, onCheckedChange = { selected = if (sel) selected - f.pubkey else selected + f.pubkey },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
}

// ====================================================================== photo viewer

@Composable
fun PhotoViewer(nav: NavHostController, path: String, key: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = "Foto",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().sharedBoundsKey(key).graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        )
        IconButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) { Icon(Icons.Rounded.Close, "Chiudi", tint = Color.White) }
    }
}
