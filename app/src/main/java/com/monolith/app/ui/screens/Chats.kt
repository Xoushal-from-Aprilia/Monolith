package com.monolith.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.NavRequest
import com.monolith.app.core.Fmt
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.InviteState
import com.monolith.app.data.Message
import com.monolith.app.data.Reaction
import com.monolith.app.data.Vote
import com.monolith.app.net.Media
import com.monolith.app.net.Messenger
import com.monolith.app.net.ReplyRef
import com.monolith.app.net.VoicePlayer
import com.monolith.app.net.VoiceRecorder
import com.monolith.app.net.replyOf
import com.monolith.app.rtc.CallManager
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.BigTitle
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.EmptyState
import com.monolith.app.ui.components.PulseDot
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
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.MonoDialog
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.AutoAwesome

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
    var statusSheet by remember { mutableStateOf(false) }
    var statusOf by remember { mutableStateOf<Pair<Friend, com.monolith.app.data.Status?>?>(null) }

    val byPk = friends.associateBy { it.pubkey }
    val lastBy = last.associateBy { it.convId }
    val unreadBy = unread.associate { it.convId to it.n }
    val isOnline = { pk: String -> tick >= 0 && now() - (online[pk] ?: 0L) < 100_000L }
    val rows = buildList {
        friends.filter { it.status == FriendStatus.FRIEND && lastBy.containsKey(it.pubkey) }.forEach { f ->
            val m = lastBy[f.pubkey]
            add(ConvRow(f.pubkey, f.displayName, f.emoji, m, unreadBy[f.pubkey] ?: 0, isOnline(f.pubkey), false, m?.ts ?: 0))
        }
        groups.filter { !com.monolith.app.net.Shard.isHiddenGroup(it.id) }.forEach { g ->
            val c = "g:" + g.id
            val m = lastBy[c]
            add(ConvRow(c, g.name, g.emoji, m, unreadBy[c] ?: 0, false, true, m?.ts ?: g.createdAt))
        }
    }.sortedByDescending { it.sort }
    val onlineFriends = friends.filter { it.status == FriendStatus.FRIEND && isOnline(it.pubkey) }

    Box(Modifier.fillMaxSize()) {
        val weeklyReady by com.monolith.app.net.Weekly.ready.collectAsState()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BigTitle(
                        t("Clubhouse"), Modifier.weight(1f),
                        subtitle = if (onlineFriends.isEmpty()) t("Le tue conversazioni") else t("{0} amici online", onlineFriends.size),
                    )
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Rounded.Search, t("Cerca nei messaggi")) }
                    Avatar(profile?.emoji ?: "🗿", 44.dp, Modifier.padding(start = 4.dp, end = 20.dp).bounceClick { nav.navigate("settings") })
                }
            }
            item(key = "statuses") {
                StatusStrip(
                    friends, isOnline,
                    onMine = { Graph.identity.keys?.pub?.let { nav.navigate(Routes.profile(it)) } },
                    onFriend = { f, _ -> nav.navigate(Routes.profile(f.pubkey)) },
                )
            }
            if (weeklyReady) item(key = "weekly") { WeeklyCard { nav.navigate("wrapped") } }
            item(key = "shards") { ShardStrip(nav, unreadBy) }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        t("Nessuna conversazione"),
                        if (friends.none { it.status == FriendStatus.FRIEND }) t("Aggiungi un amico dalla scheda Amici per iniziare a chattare.")
                        else t("Tocca «Nuova chat» per scrivere a un amico o creare un gruppo."),
                    )
                }
            }
            items(rows, key = { it.conv }) { r ->
                ConversationRow(r, typing[r.conv]?.takeIf { it.second > now() }?.first, byPk, Modifier.animateItem()) { nav.navigate(Routes.chat(r.conv)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { newChat = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text(t("Nuova chat")) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).sharedBoundsKey("group-new"),
        )
    }

    if (statusSheet) StatusSheet { statusSheet = false }
    statusOf?.let { (f, st) ->
        StatusDialog(f, st, onClose = { statusOf = null }, onWrite = { nav.navigate(Routes.chat(f.pubkey)) })
    }

    if (newChat) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        MonoSheet(onDismissRequest = { newChat = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(t("Nuova chat"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().bounceClick(0.98f) { newChat = false; nav.navigate("group/new") }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GroupAdd, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(t("Nuovo gruppo"), style = MaterialTheme.typography.titleMedium)
                }
                SectionLabel(t("Amici"))
                val list = friends.filter { it.status == FriendStatus.FRIEND }
                if (list.isEmpty()) Text(t("Non hai ancora amici. Cercali per username nella scheda Amici."), Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Row(modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(r.emoji, 54.dp, Modifier.sharedElementKey("avatar-" + r.conv), online = r.online)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(typingPk != null, label = "preview") { isTyping ->
                if (isTyping) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (r.isGroup) (people[typingPk]?.displayName ?: "") + " " else "") + t("sta scrivendo"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                            if (m == null) t("Gruppo creato") else prefix + Graph.messenger.previewText(m),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (r.unread > 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                ) { Text(r.unread.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ====================================================================== chat

private sealed class ChatItem(val key: String) {
    class Msg(val m: Message, val showName: Boolean) : ChatItem(m.id)
    class Day(val label: String, k: String) : ChatItem(k)
}

private fun hasPerm(ctx: android.content.Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(nav: NavHostController, conv: String, focus: String = "") {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val uiScope = rememberCoroutineScope()
    val isGroup = conv.startsWith("g:")
    val isRoom = com.monolith.app.net.Shard.isRoomConv(conv)
    val messages by remember(conv) { Graph.db.messages().conversation(conv) }.collectAsState(initial = emptyList())
    val friend by remember(conv) { Graph.db.friends().observe(conv) }.collectAsState(initial = null)
    val group by remember(conv) { Graph.db.groups().observe(conv.removePrefix("g:")) }.collectAsState(initial = null)
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val reactions by remember(conv) { Graph.db.social().reactions(conv) }.collectAsState(initial = emptyList())
    val votes by remember(conv) { Graph.db.social().votes(conv) }.collectAsState(initial = emptyList())
    val setting by remember(conv) { Graph.db.social().setting(conv) }.collectAsState(initial = null)
    val online by Graph.messenger.online.collectAsState()
    val typingMap by Graph.messenger.typing.collectAsState()
    val tick = rememberTicker(5_000)
    val myPub = Graph.identity.keys?.pub ?: ""
    val openedAt = remember { now() }

    var text by rememberSaveable(conv) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var shareEvent by remember { mutableStateOf(false) }
    var pollDialog by remember { mutableStateOf(false) }
    var ttlDialog by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var reactPicker by remember { mutableStateOf<Message?>(null) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recStart by remember { mutableLongStateOf(0L) }
    var recCancel by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    var scheduleDialog by remember { mutableStateOf(false) }
    var scratchDialog by remember { mutableStateOf(false) }
    val pins by Graph.pins.pins.collectAsState()
    val scheduledAll by Graph.scheduled.all.collectAsState()
    val scheduledHere = scheduledAll.filter { it.conv == conv }

    DisposableEffect(conv) {
        Graph.messenger.activeConv.value = conv
        onDispose {
            if (Graph.messenger.activeConv.value == conv) Graph.messenger.activeConv.value = null
            recorder.stop(cancel = true)
            VoicePlayer.stop()
        }
    }
    LaunchedEffect(conv) { if (!isGroup) Graph.rtc.ensureConnected(conv) }
    LaunchedEffect(messages.firstOrNull()?.id) { Graph.scope.launch { Graph.messenger.markRead(conv) } }

    val names = people.associateBy { it.pubkey }
    val title = if (isGroup) group?.name ?: t("Gruppo") else friend?.displayName ?: t("Chat")
    val emoji = if (isGroup) group?.emoji ?: "💬" else friend?.emoji ?: "🙂"
    val typing = typingMap[conv]?.takeIf { it.second > now() && tick >= 0 }
    val isOn = !isGroup && now() - (online[conv] ?: 0L) < 100_000L
    val ttl = setting?.ttlSec ?: 0L
    val subtitle = when {
        typing != null -> (if (isGroup) (names[typing.first]?.displayName ?: "") + " " else "") + t("sta scrivendo…")
        isGroup -> group?.memberList()?.let { t("{0} membri", it.size) } ?: ""
        isOn -> t("online")
        (friend?.lastSeen ?: 0L) > 0L -> t("ultimo accesso {0}", Fmt.ago(friend?.lastSeen ?: 0L))
        else -> "@" + (friend?.username ?: "")
    }
    val canCall = !isRoom && if (isGroup) (group?.memberList()?.size ?: 99) <= CallManager.MAX_PEOPLE else friend?.status == FriendStatus.FRIEND
    val reactBy = remember(reactions) { reactions.groupBy { it.msgId } }
    val votesBy = remember(votes) { votes.groupBy { it.msgId } }

    val chatItems = remember(messages, names) {
        val out = ArrayList<ChatItem>()
        messages.forEachIndexed { i, m ->
            val older = messages.getOrNull(i + 1)
            val showName = isGroup && !m.outgoing && m.kind != "system" && (older == null || older.sender != m.sender || older.kind == "system")
            out.add(ChatItem.Msg(m, showName))
            if (older == null || !Fmt.sameDay(older.ts, m.ts)) {
                out.add(ChatItem.Day(if (Fmt.sameDay(m.ts, now())) t("Oggi") else Fmt.dayLong(m.ts), "day" + Fmt.startOfDay(m.ts)))
            }
        }
        out
    }

    fun replyRef(): ReplyRef? = replyTo?.let { ReplyRef(it.id, it.sender, Graph.messenger.previewText(it)) }

    // ---- launchers
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { val r = replyRef(); replyTo = null; Graph.scope.launch { Graph.messenger.sendPhoto(conv, uri, "", r) } }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) { val r = replyRef(); replyTo = null; Graph.scope.launch { Graph.messenger.sendPhoto(conv, u, "", r) } }
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        cameraUri = u
        takePhoto.launch(u)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) launchCamera() }
    var pendingVideo by remember { mutableStateOf(false) }
    val callPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true || hasPerm(ctx, Manifest.permission.RECORD_AUDIO)) Graph.calls.startCall(conv, pendingVideo)
    }
    fun call(video: Boolean) {
        pendingVideo = video
        val need = buildList {
            if (!hasPerm(ctx, Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (video && !hasPerm(ctx, Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
        }
        if (need.isEmpty()) Graph.calls.startCall(conv, video) else callPerms.launch(need.toTypedArray())
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                Avatar(
                    emoji, 42.dp,
                    Modifier.sharedElementKey("avatar-$conv").bounceClick(0.9f) { if (!isGroup) nav.navigate(Routes.profile(conv)) },
                    online = isOn,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).bounceClick(0.99f) { if (!isGroup) nav.navigate(Routes.profile(conv)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (friend?.verified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Verified, t("Verificato"), Modifier.size(16.dp))
                        }
                        if (ttl > 0) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Timer, t("Messaggi effimeri"), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnimatedContent(
                        subtitle,
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                        label = "sub",
                    ) { s -> Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
                if (canCall) {
                    IconButton(onClick = { call(true) }) { Icon(Icons.Rounded.Videocam, t("Videochiamata")) }
                    IconButton(onClick = { call(false) }) { Icon(Icons.Rounded.Call, t("Chiama")) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(t("Trillo")) }, leadingIcon = { Icon(Icons.Rounded.Vibration, null) }, onClick = {
                            menu = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            Graph.scope.launch { Graph.messenger.sendNudge(conv) }
                        })
                        DropdownMenuItem(text = { Text(t("Messaggi effimeri")) }, leadingIcon = { Icon(Icons.Rounded.Timer, null) }, onClick = { menu = false; ttlDialog = true })
                        DropdownMenuItem(text = { Text(t("Svuota chat")) }, onClick = { menu = false; confirm = "clear" })
                        if (isGroup && !isRoom) DropdownMenuItem(text = { Text(t("Lascia il gruppo")) }, onClick = { menu = false; confirm = "leave" })
                        else if (friend?.status == FriendStatus.FRIEND) DropdownMenuItem(text = { Text(t("Rimuovi amico")) }, onClick = { menu = false; confirm = "unfriend" })
                    }
                }
            }
        }

        // ---- messages
        val listState = rememberLazyListState()
        LaunchedEffect(messages.firstOrNull()?.id) { if (listState.firstVisibleItemIndex < 3) listState.animateScrollToItem(0) }
        fun scrollToMsg(id: String) {
            val idx = chatItems.indexOfFirst { it.key == id }
            if (idx >= 0) uiScope.launch { listState.animateScrollToItem(idx + if (typing != null) 1 else 0) }
        }
        // opened from search: go to that message once
        var focused by remember(conv) { mutableStateOf(false) }
        LaunchedEffect(chatItems.size, focus) {
            if (!focused && focus.isNotBlank() && chatItems.any { it.key == focus }) { focused = true; scrollToMsg(focus) }
        }
        val pinned = pins[conv]?.let { id -> messages.firstOrNull { it.id == id && it.kind != "deleted" } }
        PinnedBar(
            pinned?.let { Graph.messenger.previewText(it) },
            onOpen = { pinned?.let { scrollToMsg(it.id) } },
            onUnpin = { Graph.scope.launch { Graph.messenger.pin(conv, null) } },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, reverseLayout = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
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
                        item.m, item.showName, names, myPub,
                        reactBy[item.m.id].orEmpty(), votesBy[item.m.id].orEmpty(),
                        isNew = item.m.ts > openedAt,
                        modifier = Modifier.animateItem(),
                        onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); actionsFor = it },
                        onPhoto = { path -> nav.navigate(Routes.photo(path, "photo-" + item.m.id)) },
                        onAddEvent = { ev ->
                            Graph.scope.launch { Graph.messenger.saveEvent(ev, notifyInvitees = false) }
                            Toast.makeText(ctx, t("Aggiunto all'agenda"), Toast.LENGTH_SHORT).show()
                        },
                        onOpenMap = { lat, lon -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))) } },
                        onQuote = { id -> scrollToMsg(id) },
                        onReact = { m, e -> Graph.scope.launch { Graph.messenger.react(m, e) } },
                    )
                }
            }
        }

        ScheduledBar(scheduledHere) { sc -> com.monolith.app.service.ScheduledSender.cancel(ctx, sc.id) }

        // ---- reply / edit banner
        AnimatedVisibility(replyTo != null || editing != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val m = editing ?: replyTo
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(36.dp).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (editing != null) t("Modifica messaggio") else t("Rispondi a {0}", if (m?.outgoing == true) t("te") else names[m?.sender]?.displayName ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(m?.let { Graph.messenger.previewText(it) } ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (editing != null) text = ""; replyTo = null; editing = null }) { Icon(Icons.Rounded.Close, t("Annulla")) }
                }
            }
        }

        // ---- composer
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                AnimatedContent(recording, label = "composer", modifier = Modifier.weight(1f)) { rec ->
                    if (rec) {
                        val elapsed by produceState(0L, recStart) { while (true) { value = now() - recStart; delay(200) } }
                        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(size = 12.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(Fmt.duration(elapsed), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (recCancel) t("Rilascia per annullare") else t("← Scorri per annullare"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box {
                                IconButton(onClick = { attach = true }) { Icon(Icons.Rounded.Add, t("Allega")) }
                                DropdownMenu(expanded = attach, onDismissRequest = { attach = false }) {
                                    DropdownMenuItem(text = { Text(t("Galleria")) }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = {
                                        attach = false; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    })
                                    DropdownMenuItem(text = { Text(t("Fotocamera")) }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                                        attach = false
                                        if (hasPerm(ctx, Manifest.permission.CAMERA)) launchCamera() else camPerm.launch(Manifest.permission.CAMERA)
                                    })
                                    DropdownMenuItem(text = { Text(t("Sondaggio")) }, leadingIcon = { Icon(Icons.Rounded.BarChart, null) }, onClick = { attach = false; pollDialog = true })
                                    DropdownMenuItem(text = { Text(t("Posizione attuale")) }, leadingIcon = { Icon(Icons.Rounded.LocationOn, null) }, onClick = {
                                        attach = false; locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    })
                                    DropdownMenuItem(text = { Text(t("Evento dall'agenda")) }, leadingIcon = { Icon(Icons.Rounded.Event, null) }, onClick = { attach = false; shareEvent = true })
                                    DropdownMenuItem(text = { Text(t("Messaggio da grattare")) }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) }, onClick = { attach = false; scratchDialog = true })
                                    DropdownMenuItem(text = { Text(t("Programma messaggio")) }, leadingIcon = { Icon(Icons.Rounded.Schedule, null) }, onClick = { attach = false; scheduleDialog = true })
                                }
                            }
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = {
                                        text = it
                                        if (it.isNotBlank() && editing == null) Graph.scope.launch { Graph.messenger.typing(conv) }
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 6,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (text.isEmpty()) Text(t("Messaggio"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        inner()
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    text.isNotBlank() || editing != null,
                    transitionSpec = { (scaleIn(Motion.bouncy(), 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) },
                    label = "send",
                ) { canSend ->
                    if (canSend) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f) {
                                val tx = text
                                text = ""
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val ed = editing
                                if (ed != null) {
                                    editing = null
                                    Graph.scope.launch { Graph.messenger.editMessage(ed, tx) }
                                } else {
                                    val r = replyRef()
                                    replyTo = null
                                    Graph.scope.launch { Graph.messenger.sendText(conv, tx, r) }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, t("Invia"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
                    } else {
                        val recScale by animateFloatAsState(if (recording) 1.35f else 1f, Motion.bouncy(), label = "mic")
                        Box(
                            Modifier.size(48.dp).graphicsLayer { scaleX = recScale; scaleY = recScale }.clip(CircleShape)
                                .background(if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .pointerInput(conv) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        if (!hasPerm(ctx, Manifest.permission.RECORD_AUDIO)) {
                                            micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                            return@awaitEachGesture
                                        }
                                        if (!recorder.start()) return@awaitEachGesture
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        recStart = now()
                                        recCancel = false
                                        recording = true
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            recCancel = down.position.x - ch.position.x > 180f
                                            if (!ch.pressed) break
                                        }
                                        recording = false
                                        val res = recorder.stop(cancel = recCancel)
                                        if (res != null) {
                                            val r = replyRef()
                                            replyTo = null
                                            Graph.scope.launch { Graph.messenger.sendVoice(conv, res.first, res.second, r) }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Mic, t("Tieni premuto per registrare"),
                                tint = if (recording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- message actions
    actionsFor?.let { m ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val mine = reactions.firstOrNull { it.msgId == m.id && it.sender == myPub }?.emoji
        MonoSheet(onDismissRequest = { actionsFor = null }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(bottom = 24.dp)) {
                if (m.kind != "system" && m.kind != "call" && m.kind != "deleted") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Messenger.QUICK_REACTIONS.forEachIndexed { i, e ->
                            com.monolith.app.ui.components.PopIn(i * 35) {
                                Box(
                                    Modifier.size(50.dp).clip(CircleShape)
                                        .background(if (mine == e) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .bounceClick(0.8f) {
                                            actionsFor = null
                                            Graph.scope.launch { Graph.messenger.react(m, if (mine == e) "" else e) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e, fontSize = 24.sp) }
                            }
                        }
                        Box(
                            Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.8f) { reactPicker = m; actionsFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Add, t("Altre emoji")) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionRow(Icons.AutoMirrored.Rounded.Reply, t("Rispondi")) { replyTo = m; editing = null; actionsFor = null }
                }
                if (m.kind != "system" && m.kind != "call" && m.kind != "deleted") {
                    val isPinned = pins[conv] == m.id
                    ActionRow(Icons.Rounded.PushPin, if (isPinned) t("Togli dalla cima") else t("Fissa in alto")) {
                        actionsFor = null
                        Graph.scope.launch { Graph.messenger.pin(conv, if (isPinned) null else m.id) }
                    }
                }
                if (m.text.isNotBlank() && m.kind != "system") {
                    ActionRow(Icons.Rounded.ContentCopy, t("Copia")) {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("msg", m.text))
                        actionsFor = null
                    }
                }
                if (m.outgoing && (m.kind == "text")) {
                    ActionRow(Icons.Rounded.Edit, t("Modifica")) { editing = m; replyTo = null; text = m.text; actionsFor = null }
                }
                if (m.outgoing && m.kind != "deleted" && m.kind != "system" && m.kind != "call") {
                    ActionRow(Icons.Rounded.DeleteOutline, t("Elimina per tutti")) {
                        actionsFor = null
                        Graph.scope.launch { Graph.messenger.deleteForEveryone(m) }
                    }
                }
                ActionRow(Icons.Rounded.DeleteOutline, t("Elimina per me")) {
                    actionsFor = null
                    Graph.scope.launch { Graph.messenger.deleteForMe(m) }
                }
            }
        }
    }
    if (scratchDialog) {
        var sx by remember { mutableStateOf(text) }
        MonoDialog(
            onDismissRequest = { scratchDialog = false },
            icon = { Icon(Icons.Rounded.AutoAwesome, null) },
            title = { Text(t("Messaggio da grattare")) },
            text = {
                Column {
                    Text(t("Arriva coperto: si legge strisciando col dito."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(sx, { sx = it.take(300) }, label = { Text(t("Messaggio")) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = { scratchDialog = false; text = ""; Graph.scope.launch { Graph.messenger.sendScratch(conv, sx) } }, enabled = sx.isNotBlank()) { Text(t("Invia")) }
            },
            dismissButton = { TextButton(onClick = { scratchDialog = false }) { Text(t("Annulla")) } },
        )
    }
    if (scheduleDialog) {
        ScheduleDialog(text, onClose = { scheduleDialog = false }) { tx, at ->
            com.monolith.app.service.ScheduledSender.add(ctx, conv, tx, at)
            text = ""
            Toast.makeText(ctx, t("Messaggio programmato"), Toast.LENGTH_SHORT).show()
        }
    }
    reactPicker?.let { m ->
        EmojiPickerSheet(onDismiss = { reactPicker = null }) { e -> reactPicker = null; Graph.scope.launch { Graph.messenger.react(m, e) } }
    }

    if (shareEvent) {
        val events by remember { Graph.db.agenda().all() }.collectAsState(initial = emptyList())
        val upcoming = events.filter { it.start > now() - 86_400_000L && it.inviteState != InviteState.DECLINED }
        MonoSheet(onDismissRequest = { shareEvent = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(t("Condividi un evento"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            if (upcoming.isEmpty()) Text(t("Nessun evento in programma"), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(bottom = 24.dp)) {
                items(upcoming, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().bounceClick(0.98f) { shareEvent = false; Graph.scope.launch { Graph.messenger.shareEventInChat(conv, e) } }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
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

    if (pollDialog) PollDialog(onDismiss = { pollDialog = false }) { q, opts, multi ->
        pollDialog = false
        Graph.scope.launch { Graph.messenger.sendPoll(conv, q, opts, multi) }
    }

    if (ttlDialog) {
        val options = listOf(0L to t("Disattivati"), 3600L to t("1 ora"), 86400L to t("24 ore"), 604800L to t("7 giorni"))
        MonoDialog(
            onDismissRequest = { ttlDialog = false },
            title = { Text(t("Messaggi effimeri")) },
            text = {
                Column {
                    Text(t("I nuovi messaggi spariranno dopo il tempo scelto, per tutti nella chat."), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    options.forEach { (sec, label) ->
                        Row(Modifier.fillMaxWidth().bounceClick(0.98f) {
                            ttlDialog = false
                            if (sec != ttl) Graph.scope.launch { Graph.messenger.setDisappearing(conv, sec) }
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = ttl == sec, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ttlDialog = false }) { Text(t("Chiudi")) } },
        )
    }

    confirm?.let { what ->
        MonoDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(when (what) {
                    "leave" -> t("Lasciare il gruppo?")
                    "unfriend" -> t("Rimuovere {0} dagli amici?", title)
                    else -> t("Svuotare la chat?")
                })
            },
            text = { Text(if (what == "clear") t("I messaggi verranno cancellati solo da questo telefono.") else t("Non riceverai più i suoi messaggi.")) },
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
                }) { Text(t("Conferma")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PollDialog(onDismiss: () -> Unit, onSend: (String, List<String>, Boolean) -> Unit) {
    var q by remember { mutableStateOf("") }
    val opts = remember { mutableStateListOf("", "") }
    var multi by remember { mutableStateOf(false) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Nuovo sondaggio")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(q, { q = it.take(140) }, label = { Text(t("Domanda")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                opts.forEachIndexed { i, o ->
                    OutlinedTextField(o, { opts[i] = it.take(60) }, label = { Text(t("Opzione {0}", i + 1)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                }
                if (opts.size < 6) TextButton(onClick = { opts.add("") }) { Text(t("+ Aggiungi opzione")) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Risposte multiple"), modifier = Modifier.weight(1f))
                    Switch(checked = multi, onCheckedChange = { multi = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(q, opts.toList(), multi) },
                enabled = q.isNotBlank() && opts.count { it.isNotBlank() } >= 2,
            ) { Text(t("Invia")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: Message,
    showName: Boolean,
    names: Map<String, Friend>,
    myPub: String,
    reactions: List<Reaction>,
    votes: List<Vote>,
    isNew: Boolean,
    modifier: Modifier,
    onLongPress: (Message) -> Unit,
    onPhoto: (String) -> Unit,
    onAddEvent: (AgendaEvent) -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onQuote: (String) -> Unit,
    onReact: (Message, String) -> Unit,
) {
    val appear = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
    val anim = Modifier.graphicsLayer {
        val v = appear.value
        alpha = v.coerceIn(0f, 1f)
        scaleX = 0.85f + 0.15f * v
        scaleY = 0.85f + 0.15f * v
        translationY = (1f - v) * 30f
        transformOrigin = TransformOrigin(if (m.outgoing) 1f else 0f, 1f)
    }

    if (m.kind == "system" || m.kind == "call") {
        Box(modifier.fillMaxWidth().padding(vertical = 6.dp).then(anim), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    (if (m.kind == "call") "📞  " else "") + m.text + if (m.kind == "call") "  ·  " + Fmt.time(m.ts) else "",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val ex = remember(m.extra) { runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject()) }
    val press = Modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress(m) })
    val sender = names[m.sender]

    Column(modifier.fillMaxWidth().padding(vertical = 2.dp).then(anim), horizontalAlignment = if (out) Alignment.End else Alignment.Start) {
        if (showName) {
            Text((sender?.emoji ?: "") + " " + (sender?.displayName ?: t("Membro")), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp))
        }
        val reply = replyOf(m)
        when (m.kind) {
            "deleted" -> Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = press) {
                Text("🚫 " + t("Messaggio eliminato"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            "nudge" -> {
                val shake = remember { Animatable(if (isNew) 1f else 0f) }
                LaunchedEffect(Unit) { if (shake.value > 0f) shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 300f)) }
                Surface(shape = shape, color = bubbleColor, modifier = Modifier.graphicsLayer { rotationZ = shake.value * 12f }.then(press)) {
                    Text(if (out) "📳  " + t("Hai mandato un trillo") else "📳  " + t("Trillo!"), color = textColor, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
            }
            "photo" -> {
                val (w, h) = Media.approxSize(ex.optInt("w"), ex.optInt("h"), 250)
                val path = m.mediaPath
                val ready = path != null && (out || m.progress >= 1f)
                Column(horizontalAlignment = if (out) Alignment.End else Alignment.Start) {
                    if (reply != null) QuoteBox(reply, names, myPub, false, Modifier.widthIn(max = 250.dp)) { onQuote(reply.id) }
                    Box(
                        Modifier.size(w.dp, h.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .combinedClickable(onClick = { if (ready && path != null) onPhoto(path) }, onLongClick = { onLongPress(m) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ready && path != null) {
                            AsyncImage(model = File(path), contentDescription = t("Foto"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().sharedBoundsKey("photo-" + m.id))
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
                    if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            "voice" -> Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(min = 220.dp, max = 280.dp).then(press)) {
                Column(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                    if (reply != null) QuoteBox(reply, names, myPub, out, Modifier.fillMaxWidth()) { onQuote(reply.id) }
                    VoiceBubble(m, ex.optLong("dur"), textColor)
                    MetaLine(m, textColor)
                }
            }
            "poll" -> Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(min = 240.dp, max = 300.dp).then(press)) {
                PollBubble(m, ex, votes, myPub, textColor)
            }
            "event" -> Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 280.dp).then(press)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.optString("emoji", "📅"), fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(ex.optString("title"), color = textColor, style = MaterialTheme.typography.titleMedium)
                            val start = ex.optLong("start")
                            Text(Fmt.dayShort(start) + if (ex.optBoolean("allDay")) "" else " · " + Fmt.time(start), color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!out) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = {
                            onAddEvent(AgendaEvent(
                                id = newId(), title = ex.optString("title"), description = ex.optString("desc"),
                                start = ex.optLong("start"), end = ex.optLong("end").takeIf { it > 0 },
                                allDay = ex.optBoolean("allDay"), emoji = ex.optString("emoji", "📌"),
                                repeat = ex.optInt("rep", 0), createdAt = now(), updatedAt = now(),
                            ))
                        }, shape = CircleShape) { Text(t("Aggiungi all'agenda")) }
                    }
                    MetaLine(m, textColor)
                }
            }
            "scratch" -> {
                Surface(
                    shape = shape, color = bubbleColor,
                    modifier = Modifier.widthIn(min = 180.dp, max = 280.dp).combinedClickable(onClick = {}, onLongClick = { onLongPress(m) }),
                ) {
                    Column {
                        ScratchCard(m, textColor, bubbleColor)
                        Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) { MetaLine(m, textColor) }
                    }
                }
            }
            "place" -> {
                val pid = ex.optString("id")
                Surface(
                    shape = shape, color = bubbleColor,
                    modifier = Modifier.widthIn(max = 280.dp).combinedClickable(
                        onClick = {
                            if (Graph.places.get(pid) != null) {
                                NavBus.focusPlace.value = pid
                                NavBus.requests.value = NavRequest(tab = 2)
                            }
                        },
                        onLongClick = { onLongPress(m) },
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(textColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(ex.optString("e", "📍"), fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(ex.optString("n").ifBlank { m.text }, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (out) t("Hai raggiunto questo luogo") else t("Ha raggiunto questo luogo"),
                                    color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        MetaLine(m, textColor)
                    }
                }
            }
            "sos" -> {
                val lat = ex.optDouble("lat")
                val lon = ex.optDouble("lon")
                val hasPos = !lat.isNaN() && !lon.isNaN()
                Surface(
                    shape = shape, color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.widthIn(max = 280.dp).combinedClickable(
                        onClick = { if (hasPos) onOpenMap(lat, lon) },
                        onLongClick = { onLongPress(m) },
                    ),
                ) {
                    val ink = MaterialTheme.colorScheme.inverseOnSurface
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🆘", fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("SOS", color = ink, style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(
                            if (out) t("Hai chiesto aiuto a tutti i tuoi amici") else t("Ha bisogno di aiuto"),
                            color = ink, style = MaterialTheme.typography.bodyLarge,
                        )
                        if (hasPos) {
                            Spacer(Modifier.height(6.dp))
                            Text(t("Tocca per vedere dove si trova"), color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                        }
                        MetaLine(m, ink)
                    }
                }
            }
            "loc" -> {
                val lat = ex.optDouble("lat")
                val lon = ex.optDouble("lon")
                Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 260.dp).combinedClickable(onClick = { onOpenMap(lat, lon) }, onLongClick = { onLongPress(m) })) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, null, tint = textColor)
                            Spacer(Modifier.width(8.dp))
                            Text(t("Posizione condivisa"), color = textColor, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon), color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        Text(t("Tocca per aprire nelle mappe"), color = textColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        MetaLine(m, textColor)
                    }
                }
            }
            else -> Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 300.dp).then(press)) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                    if (reply != null) QuoteBox(reply, names, myPub, out, Modifier) { onQuote(reply.id) }
                    Text(m.text, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 2.dp))
                    MetaLine(m, textColor)
                }
            }
        }
        if (reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.groupBy { it.emoji }.forEach { (e, list) ->
                    val mine = list.any { it.sender == myPub }
                    com.monolith.app.ui.components.PopIn {
                        Surface(
                            shape = CircleShape,
                            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.bounceClick(0.85f) { onReact(m, if (mine) "" else e) },
                        ) {
                            Text(e + if (list.size > 1) " ${list.size}" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteBox(r: ReplyRef, names: Map<String, Friend>, myPub: String, onDark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (onDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.1f)).bounceClick(0.98f, onClick = onClick).padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(c.copy(alpha = 0.7f)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(if (r.sender == myPub) t("Tu") else names[r.sender]?.displayName ?: "", style = MaterialTheme.typography.labelMedium, color = c)
            Text(r.preview, style = MaterialTheme.typography.bodySmall, color = c.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VoiceBubble(m: Message, durationMs: Long, color: Color) {
    val playing by VoicePlayer.playing.collectAsState()
    val progress by VoicePlayer.progress.collectAsState()
    val isMe = playing == m.id
    val path = m.mediaPath
    val ready = path != null && (m.outgoing || m.progress >= 1f)
    val bars = remember(m.id) { val r = java.util.Random(m.id.hashCode().toLong()); List(28) { 0.25f + r.nextFloat() * 0.75f } }
    val inf = rememberInfiniteTransition(label = "voice")
    val pulse by inf.animateFloat(0.85f, 1f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)).bounceClick(0.85f, enabled = ready) { if (path != null) VoicePlayer.toggle(m.id, path) },
            contentAlignment = Alignment.Center,
        ) {
            if (!ready) CircularProgressIndicator(progress = { m.progress.coerceIn(0.02f, 1f) }, color = color, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            else Icon(if (isMe) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = color)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bars.forEachIndexed { i, b ->
                val played = isMe && i.toFloat() / bars.size <= progress
                Box(
                    Modifier.weight(1f).fillMaxHeight(b * if (isMe) pulse else 1f).clip(CircleShape)
                        .background(color.copy(alpha = if (played) 1f else 0.4f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(Fmt.duration(if (isMe) (durationMs * progress).toLong() else durationMs), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun PollBubble(m: Message, ex: JSONObject, votes: List<Vote>, myPub: String, color: Color) {
    val q = ex.optString("q")
    val opts = remember(ex) { (0 until (ex.optJSONArray("o")?.length() ?: 0)).map { ex.optJSONArray("o")!!.optString(it) } }
    val multi = ex.optBoolean("m")
    val myVote = votes.firstOrNull { it.voter == myPub }?.list().orEmpty()
    val counts = opts.indices.map { i -> votes.count { i in it.list() } }
    val voters = votes.count { it.list().isNotEmpty() }.coerceAtLeast(1)
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BarChart, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("Sondaggio") + if (multi) " · " + t("più risposte") else "", style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        Text(q, style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.height(8.dp))
        opts.forEachIndexed { i, o ->
            val sel = i in myVote
            val frac by animateFloatAsState(counts[i].toFloat() / voters, Motion.smooth(), label = "v$i")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).bounceClick(0.98f) {
                    val next = if (multi) { if (sel) myVote - i else myVote + i } else { if (sel) emptyList() else listOf(i) }
                    Graph.scope.launch { Graph.messenger.vote(m, next) }
                }.padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sel, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color.copy(alpha = 0.6f), checkmarkColor = if (color == MaterialTheme.colorScheme.onPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary))
                    Spacer(Modifier.width(8.dp))
                    Text(o, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(counts[i].toString(), color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { frac }, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 2.dp).height(4.dp).clip(CircleShape),
                    color = color, trackColor = color.copy(alpha = 0.15f), drawStopIndicator = {},
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = color.copy(alpha = 0.15f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("{0} voti", votes.count { it.list().isNotEmpty() }), color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            MetaLine(m, color)
        }
    }
}

@Composable
private fun MetaLine(m: Message, color: Color) {
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (m.expiresAt > 0) {
            Icon(Icons.Rounded.Timer, null, Modifier.size(11.dp), tint = color.copy(alpha = 0.6f))
            Spacer(Modifier.width(3.dp))
        }
        if (m.edited) {
            Text(t("modificato") + " · ", color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        Text(Fmt.time(m.ts), color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        if (m.outgoing) {
            Spacer(Modifier.width(3.dp))
            StatusTicks(m.status, color)
        }
    }
}

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
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, t("Chiudi")) }
                Text(t("Nuovo gruppo"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                    shape = CircleShape, modifier = Modifier.padding(end = 8.dp),
                ) { Text(t("Crea")) }
            }
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.88f) { picker = true }, contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 30.sp)
                }
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(t("Nome del gruppo")) },
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
            }
            SectionLabel(t("Membri") + " · ${selected.size}")
            if (friends.isEmpty()) EmptyState(t("Nessun amico"), t("Aggiungi amici per creare un gruppo."))
            LazyColumn(Modifier.weight(1f)) {
                items(friends, key = { it.pubkey }) { f ->
                    val sel = f.pubkey in selected
                    Row(
                        Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { selected = if (sel) selected - f.pubkey else selected + f.pubkey }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(f.emoji, 46.dp, ring = sel)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("@" + f.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = sel, onCheckedChange = { selected = if (sel) selected - f.pubkey else selected + f.pubkey },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }
            if (selected.size + 1 > CallManager.MAX_PEOPLE) {
                Text(t("Le chiamate di gruppo sono disponibili fino a 4 persone."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
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
                detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path), contentDescription = t("Foto"), contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().sharedBoundsKey(key).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        )
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Rounded.Close, t("Chiudi"), tint = Color.White)
        }
    }
}
