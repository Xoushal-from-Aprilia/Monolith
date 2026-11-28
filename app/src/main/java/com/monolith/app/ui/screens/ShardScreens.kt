package com.monolith.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import com.monolith.app.core.now
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.net.Shard
import com.monolith.app.net.ShardRoom
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.PulseDot
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.launch

/** the mark of a shard for everyone, drawn by the app: a globe of lines */
@Composable
fun EveryoneMark(size: androidx.compose.ui.unit.Dp) {
    val ink = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.085f)
        val r = w * 0.42f
        val c = center
        drawCircle(ink, r, c, style = st)
        drawOval(ink, androidx.compose.ui.geometry.Offset(c.x - r * 0.45f, c.y - r), androidx.compose.ui.geometry.Size(r * 0.9f, r * 2f), style = st)
        drawLine(ink, androidx.compose.ui.geometry.Offset(c.x - r, c.y), androidx.compose.ui.geometry.Offset(c.x + r, c.y), strokeWidth = w * 0.085f)
        drawLine(ink, androidx.compose.ui.geometry.Offset(c.x - r * 0.86f, c.y - r * 0.5f), androidx.compose.ui.geometry.Offset(c.x + r * 0.86f, c.y - r * 0.5f), strokeWidth = w * 0.06f)
        drawLine(ink, androidx.compose.ui.geometry.Offset(c.x - r * 0.86f, c.y + r * 0.5f), androidx.compose.ui.geometry.Offset(c.x + r * 0.86f, c.y + r * 0.5f), strokeWidth = w * 0.06f)
    }
}

private val SHARD_EMOJI = listOf("🪨", "🎮", "🎧", "📚", "⚽", "🍕", "🎬", "🏔️", "💼", "🎨", "🌙", "🔥")

/** create (shard = null) or edit a shard */
@Composable
fun ShardEditSheet(shard: Shard?, onClose: () -> Unit, left: Map<String, String> = emptyMap(), onCreated: (String) -> Unit = {}) {
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(shard?.name ?: "") }
    var emoji by remember { mutableStateOf(shard?.emoji ?: "🪨") }
    var desc by remember { mutableStateOf(shard?.desc ?: "") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var picker by remember { mutableStateOf(false) }
    // verified developers: a shard with every Monolith user inside
    val canOpen = shard == null && Graph.shards.canMakeOpen()
    var forAll by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).imePadding()) {
            Text(if (shard == null) t("Nuovo shard") else t("Modifica shard"), style = MaterialTheme.typography.titleLarge)
            if (shard == null) {
                Text(
                    t("Uno spazio per voi, con stanze scritte e vocali. Nasce con #generale e una stanza vocale."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.9f) { picker = true },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 28.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Nome dello shard")) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(desc, { desc = it.take(500) }, label = { Text(t("Descrizione (facoltativa)")) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SHARD_EMOJI.forEach { e ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (e == emoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bounceClick(0.85f) { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
            if (canOpen) {
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(18.dp), color = if (forAll) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) { forAll = !forAll }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EveryoneMark(28.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("Per tutti gli utenti di Monolith"), style = MaterialTheme.typography.titleSmall)
                            Text(t("Entrano tutti in automatico, anche chi si iscrive dopo. Solo per gli account verificati come Sviluppatore."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = forAll, onCheckedChange = { forAll = it })
                    }
                }
            }
            if (shard == null && !forAll) {
                Spacer(Modifier.height(14.dp))
                Text(t("Invita"), style = MaterialTheme.typography.titleSmall)
                val list = friends.filter { it.status == FriendStatus.FRIEND }
                if (list.isEmpty()) Text(t("Aggiungi prima un amico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 220.dp)) {
                    list.forEach { f -> FriendChip(f, f.pubkey in chosen) { chosen = if (f.pubkey in chosen) chosen - f.pubkey else chosen + f.pubkey } }
                }
            }
            // shards for everyone I left: back in with a tap
            left.forEach { (id, n) ->
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { onClose(); Graph.scope.launch { Graph.shards.rejoin(id) } }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text(t("Rientra in {0}", n)) }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    onClose()
                    Graph.scope.launch {
                        if (shard == null) {
                            val id = if (forAll) Graph.shards.createOpen(name, emoji, desc) else Graph.shards.create(name, emoji, chosen.toList(), desc)
                            id?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onCreated(it) } }
                        } else Graph.shards.edit(shard.id, name, emoji, desc)
                    }
                },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
            ) { Text(if (shard == null) t("Crea") else t("Salva")) }
        }
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
}

@Composable
private fun FriendChip(f: Friend, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(f.displayName) }, leadingIcon = { Text(f.emoji) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
    )
}

@Composable
fun ShardScreen(nav: NavHostController, id: String) = ShardBody(nav, id, embedded = false)

/** the content of a shard: on its own screen, or next to the rail in the Shard tab ([embedded]) */
@Composable
fun ShardBody(nav: NavHostController, id: String, embedded: Boolean) {
    val ctx = LocalContext.current
    val shards by Graph.shards.all.collectAsState()
    val voiceMap by Graph.shards.voice.collectAsState()
    val call by Graph.calls.state.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val unread by remember { Graph.db.messages().unread() }.collectAsState(initial = emptyList())
    val last by remember { Graph.db.messages().lastMessages() }.collectAsState(initial = emptyList())
    val s = shards.firstOrNull { it.id == id }
    val me = Graph.identity.keys?.pub
    var menu by remember { mutableStateOf(false) }
    var editSheet by remember { mutableStateOf(false) }
    var inviteSheet by remember { mutableStateOf(false) }
    var roomDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingRoom by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { Graph.shards.ask(id) }
    LaunchedEffect(s == null) { if (!embedded && s == null && shards.isNotEmpty()) nav.popBackStack() }
    if (s == null) return
    val byPk = people.associateBy { it.pubkey }
    val unreadBy = unread.associate { it.convId to it.n }
    val lastBy = last.associateBy { it.convId }
    val isOwner = s.owner == me
    val canManage = s.canManage(me)
    var noteDialog by remember { mutableStateOf(false) }
    var eventDialog by remember { mutableStateOf(false) }
    var memberMenu by remember { mutableStateOf<String?>(null) }
    var welcomeDialog by remember { mutableStateOf(false) }
    var titleFor by remember { mutableStateOf<String?>(null) }
    var roomMenu by remember { mutableStateOf<ShardRoom?>(null) }
    val current = if (voiceMap.isNotEmpty() || call.cid != null) Graph.shards.currentRoom() else null
    val mentions by Graph.mentions.convs.collectAsState()
    val asks by Graph.joinAsks.all.collectAsState()
    val waiting = asks.filter { it.target == "s:" + s.id && it.pk !in s.members }
    var qr by remember { mutableStateOf(false) }
    var modPicker by remember { mutableStateOf(false) }

    fun join(r: ShardRoom) {
        if (!Graph.shards.joinVoice(s.id, r.id)) Toast.makeText(ctx, t("La stanza è piena (massimo 4 persone)"), Toast.LENGTH_SHORT).show()
        else nav.navigate("call") { launchSingleTop = true }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingRoom?.let { rid -> s.rooms.firstOrNull { it.id == rid }?.let { join(it) } }
        pendingRoom = null
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (embedded) Modifier else Modifier.statusBarsPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Text(s.emoji, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.open) { Spacer(Modifier.width(6.dp)); EveryoneMark(18.dp) }
                }
                Text(
                    if (s.open) t("Per tutti gli utenti di Monolith") else t("{0} membri", s.members.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!s.open) IconButton(onClick = { inviteSheet = true }) { Icon(Icons.Rounded.PersonAdd, t("Invita")) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (isOwner) DropdownMenuItem(text = { Text(t("Modifica shard")) }, onClick = { menu = false; editSheet = true })
                    if (canManage) {
                        DropdownMenuItem(text = { Text(t("Nuova stanza")) }, onClick = { menu = false; roomDialog = true })
                        DropdownMenuItem(text = { Text(t("Nuova nota in bacheca")) }, onClick = { menu = false; noteDialog = true })
                        DropdownMenuItem(text = { Text(t("Messaggio di benvenuto")) }, onClick = { menu = false; welcomeDialog = true })
                    }
                    DropdownMenuItem(text = { Text(t("Nuovo evento")) }, onClick = { menu = false; eventDialog = true })
                    DropdownMenuItem(text = { Text(t("Esci dallo shard")) }, onClick = { menu = false; confirmLeave = true })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            if (s.desc.isNotBlank()) item(key = "desc") {
                Text(s.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            if (s.open) item(key = "openInfo") {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EveryoneMark(26.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t("Qui ci sono tutti gli utenti di Monolith, anche chi si iscrive dopo. È aperto a tutti: non scrivere cose private."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // people who used the link and wait for a yes
            if (canManage && waiting.isNotEmpty()) {
                item(key = "asksLabel") { SectionLabel(t("Chiedono di entrare")) }
                items(waiting, key = { "ask" + it.pk }) { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(a.emoji, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { Graph.joinAsks.remove(a.target, a.pk) }) { Text(t("Rifiuta")) }
                        Button(onClick = { Graph.scope.launch { Graph.shards.admit(s.id, a.pk, viaLink = true) } }, shape = CircleShape) { Text(t("Accetta")) }
                    }
                }
            }
            item(key = "textLabel") { SectionLabel(t("Stanze scritte")) }
            items(s.rooms.filter { !it.voice && s.canSee(it, me) }, key = { "t" + it.id }) { r ->
                val conv = s.conv(r)
                val n = unreadBy[conv] ?: 0
                Row(
                    Modifier.fillMaxWidth().animateItem()
                        .combinedClickable(onClick = { nav.navigate(Routes.chat(conv)) }, onLongClick = { if (canManage) roomMenu = r })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (r.only != null) Icons.Rounded.Lock else if (r.ro) Icons.Rounded.Campaign else Icons.Rounded.Tag, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        if (r.ro && lastBy[conv] == null) Text(t("Scrivono solo il fondatore e i moderatori"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lastBy[conv]?.let { m ->
                            Text(
                                (if (m.outgoing) t("Tu") else byPk[m.sender]?.displayName ?: "") + ": " + Graph.messenger.previewText(m),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (n > 0 && conv in mentions) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text("@", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (n > 0) {
                        Box(Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                            Text(if (n > 99) "99+" else "$n", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (!s.open || s.rooms.any { it.voice }) item(key = "voiceLabel") { SectionLabel(t("Stanze vocali"), Modifier.padding(top = 10.dp)) }
            items(s.rooms.filter { it.voice && s.canSee(it, me) }, key = { "v" + it.id }) { r ->
                val inside = (Graph.shards.inRoom(s.id, r.id) + listOfNotNull(me.takeIf { current == s.id to r.id })).distinct()
                val mine = current == s.id to r.id
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (mine) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().animateItem().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { if (canManage) roomMenu = r })) {
                            Icon(if (r.only != null) Icons.Rounded.Lock else Icons.Rounded.GraphicEq, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (inside.isEmpty()) t("Nessuno, entra per primo") else t("{0} dentro", inside.size),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (mine) {
                                OutlinedButton(onClick = { Graph.calls.hangup() }, shape = CircleShape) { Text(t("Esci")) }
                                Spacer(Modifier.width(6.dp))
                                Button(onClick = { nav.navigate("call") { launchSingleTop = true } }, shape = CircleShape) { Text(t("Apri")) }
                            } else {
                                Button(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) join(r)
                                        else { pendingRoom = r.id; micPerm.launch(Manifest.permission.RECORD_AUDIO) }
                                    },
                                    shape = CircleShape,
                                ) { Text(t("Entra")) }
                            }
                        }
                        if (inside.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                inside.forEach { pk ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                                        Avatar(if (pk == me) Graph.identity.profile.value?.emoji ?: "🗿" else byPk[pk]?.emoji ?: "🙂", 40.dp, online = true)
                                        Text(
                                            if (pk == me) t("Tu") else byPk[pk]?.displayName ?: "…",
                                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (s.notes.isNotEmpty() || canManage) {
                item(key = "boardLabel") {
                    Row(Modifier.padding(top = 10.dp, end = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(t("Bacheca"), Modifier.weight(1f))
                        if (canManage) TextButton(onClick = { noteDialog = true }) { Text(t("Aggiungi")) }
                    }
                }
                if (s.notes.isEmpty()) item(key = "boardEmpty") {
                    Text(t("Note e link per tutti: le regole, il prossimo ritrovo, la playlist del gruppo."), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(s.notes, key = { "n" + it.id }) { n -> ShardNoteCard(n, byPk, me, canManage) { Graph.scope.launch { Graph.shards.removeNote(s.id, n.id) } } }
            }
            val upcoming = s.events.filter { !it.deleted && it.start > now() - 86_400_000L }.sortedBy { it.start }
            item(key = "eventsLabel") {
                Row(Modifier.padding(top = 10.dp, end = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(t("Eventi"), Modifier.weight(1f))
                    TextButton(onClick = { eventDialog = true }) { Text(t("Nuovo")) }
                }
            }
            if (upcoming.isEmpty()) item(key = "eventsEmpty") {
                Text(t("Nessun evento in programma."), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(upcoming, key = { "e" + it.id }) { e -> ShardEventCard(s, e, byPk, me) }
            if (!s.open && !com.monolith.app.kids.Kids.edition && (canManage || s.link.isNotBlank())) item(key = "link") {
                LinkCard(
                    link = s.link.takeIf { it.isNotBlank() }?.let { com.monolith.app.net.Invites.linkFor(it) },
                    isAdmin = canManage, approve = s.approve, qr = qr,
                    onCreate = { Graph.scope.launch { Graph.shards.setLink(s.id, true) } },
                    onCopy = {
                        ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("link", com.monolith.app.net.Invites.linkFor(s.link)))
                        Toast.makeText(ctx, t("Link copiato"), Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        runCatching {
                            ctx.startActivity(android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, t("Entra nello Shard {0} su Monolith: {1}", s.name, com.monolith.app.net.Invites.linkFor(s.link))),
                                t("Condividi il link"),
                            ))
                        }
                    },
                    onQr = { qr = !qr },
                    onReset = { Graph.scope.launch { Graph.shards.setLink(s.id, true) } },
                    onOff = { Graph.scope.launch { Graph.shards.setLink(s.id, false) } },
                    onApprove = { on -> Graph.scope.launch { Graph.shards.setApprove(s.id, on) } },
                )
            }
            item(key = "membersLabel") {
                Row(Modifier.padding(top = 10.dp, end = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(if (s.open) t("Fondatore e moderatori") else t("Membri"), Modifier.weight(1f))
                    if (s.open && isOwner) TextButton(onClick = { modPicker = true }) { Text(t("Aggiungi")) }
                }
            }
            items(s.members.sortedBy { if (it == s.owner) 0 else 1 }, key = { "m$it" }) { pk ->
                val f = byPk[pk]
                Row(
                    Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { nav.navigate(Routes.profile(pk)) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(if (pk == me) Graph.identity.profile.value?.emoji ?: "🗿" else f?.emoji ?: "🙂", 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (pk == me) t("Tu") else f?.displayName ?: t("Qualcuno"), style = MaterialTheme.typography.bodyLarge)
                        val role = listOfNotNull(
                            when (pk) {
                                s.owner -> t("Fondatore")
                                in s.mods -> t("Moderatore")
                                else -> null
                            },
                            s.titles[pk],
                        ).joinToString(" · ").ifBlank { null }
                        if (role != null) Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val canTouch = pk != me && pk != s.owner && (isOwner || (canManage && pk !in s.mods))
                    if (canManage && !canTouch) {
                        IconButton(onClick = { titleFor = pk }) { Icon(Icons.Rounded.Badge, t("Dai un titolo")) }
                    }
                    if (canTouch) Box {
                        IconButton(onClick = { memberMenu = pk }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                        DropdownMenu(expanded = memberMenu == pk, onDismissRequest = { memberMenu = null }) {
                            DropdownMenuItem(text = { Text(t("Dai un titolo")) }, onClick = { memberMenu = null; titleFor = pk })
                            if (isOwner) {
                                val mod = pk in s.mods
                                DropdownMenuItem(
                                    text = { Text(if (mod) t("Togli moderatore") else t("Rendi moderatore")) },
                                    onClick = { memberMenu = null; Graph.scope.launch { Graph.shards.setMod(s.id, pk, !mod) } },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (s.open) t("Escludi dallo Shard") else t("Togli dallo shard")) },
                                onClick = { memberMenu = null; Graph.scope.launch { Graph.shards.removeMember(s.id, pk) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editSheet) ShardEditSheet(s, onClose = { editSheet = false })
    if (modPicker) {
        // shard for everyone: any person met in the rooms can become a moderator
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val candidates = people.filter { it.pubkey != me && it.pubkey !in s.members && it.pubkey !in s.ban && it.displayName.isNotBlank() }.sortedBy { it.displayName.lowercase() }
        MonoSheet(onDismissRequest = { modPicker = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Text(t("Nuovo moderatore"), style = MaterialTheme.typography.titleLarge)
                Text(t("Modera le stanze con te: elimina messaggi ed esclude chi disturba."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (candidates.isEmpty()) Text(t("Nessuno ancora: chi scrive nelle stanze compare qui."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(candidates, key = { it.pubkey }) { f ->
                        Row(
                            Modifier.fillMaxWidth().bounceClick(0.98f) { modPicker = false; Graph.scope.launch { Graph.shards.setMod(s.id, f.pubkey, true) } }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(f.emoji, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(f.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
    if (welcomeDialog) {
        var text by remember { mutableStateOf(s.welcome) }
        MonoDialog(
            onDismissRequest = { welcomeDialog = false },
            title = { Text(t("Messaggio di benvenuto")) },
            text = {
                Column {
                    Text(t("Lo legge chi entra nello shard, nella prima stanza scritta."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it.take(400) }, label = { Text(t("Benvenuto")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { welcomeDialog = false; Graph.scope.launch { Graph.shards.setWelcome(s.id, text) } }) { Text(t("Salva")) } },
            dismissButton = { TextButton(onClick = { welcomeDialog = false }) { Text(t("Annulla")) } },
        )
    }
    titleFor?.let { pk ->
        var text by remember(pk) { mutableStateOf(s.titles[pk] ?: "") }
        MonoDialog(
            onDismissRequest = { titleFor = null },
            title = { Text(t("Titolo di {0}", if (pk == me) t("te") else byPk[pk]?.displayName ?: "")) },
            text = {
                Column {
                    Text(t("Compare accanto al nome nelle stanze, per esempio \"Capitano\" o \"DJ\"."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it.take(24) }, label = { Text(t("Titolo")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { titleFor = null; Graph.scope.launch { Graph.shards.setTitle(s.id, pk, text) } }) { Text(t("Salva")) } },
            dismissButton = { TextButton(onClick = { titleFor = null }) { Text(t("Annulla")) } },
        )
    }
    roomMenu?.let { r ->
        var chosen by remember(r.id) { mutableStateOf(r.only?.toSet() ?: emptySet()) }
        var private by remember(r.id) { mutableStateOf(r.only != null) }
        var ro by remember(r.id) { mutableStateOf(r.ro) }
        MonoDialog(
            onDismissRequest = { roomMenu = null },
            title = { Text((if (r.voice) "🔊 " else "#") + r.name) },
            text = {
                Column {
                    if (!r.voice) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Solo fondatore e moderatori scrivono"), style = MaterialTheme.typography.titleSmall)
                            Text(t("Per gli annunci: gli altri leggono e reagiscono"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = ro, onCheckedChange = { ro = it })
                    }
                    if (!s.open) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Stanza privata"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = private, onCheckedChange = { private = it })
                    }
                    if (private) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.members.filter { it != s.owner && it !in s.mods }.forEach { pk ->
                                val f = byPk[pk]
                                if (pk == me) return@forEach
                                if (f != null) FriendChip(f, pk in chosen) { chosen = if (pk in chosen) chosen - pk else chosen + pk }
                            }
                        }
                    }
                    if (s.rooms.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { roomMenu = null; Graph.scope.launch { Graph.shards.removeRoom(s.id, r.id) } }) { Text(t("Elimina la stanza")) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    roomMenu = null
                    Graph.scope.launch {
                        if (ro != r.ro) Graph.shards.setRoomReadOnly(s.id, r.id, ro)
                        if (!s.open && private != (r.only != null) || (private && chosen != r.only?.toSet())) {
                            Graph.shards.setRoomAccess(s.id, r.id, if (private) (chosen + listOfNotNull(me)).toList() else null)
                        }
                    }
                }) { Text(t("Salva")) }
            },
            dismissButton = { TextButton(onClick = { roomMenu = null }) { Text(t("Annulla")) } },
        )
    }
    if (noteDialog) {
        var text by remember { mutableStateOf("") }
        MonoDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text(t("Nuova nota in bacheca")) },
            text = { OutlinedTextField(text, { text = it.take(500) }, label = { Text(t("Testo o link")) }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { noteDialog = false; Graph.scope.launch { Graph.shards.addNote(s.id, text) } }, enabled = text.isNotBlank()) { Text(t("Fissa")) } },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text(t("Annulla")) } },
        )
    }
    if (eventDialog) ShardEventDialog(onDismiss = { eventDialog = false }) { title, emoji, start, allDay ->
        eventDialog = false
        Graph.scope.launch { Graph.shards.createEvent(s.id, title, emoji, start, allDay) }
    }
    if (inviteSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var chosen by remember { mutableStateOf(setOf<String>()) }
        val candidates = people.filter { it.status == FriendStatus.FRIEND && it.pubkey !in s.members }
        MonoSheet(onDismissRequest = { inviteSheet = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Text(t("Invita in {0}", s.name), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                if (candidates.isEmpty()) Text(t("Tutti i tuoi amici sono già qui."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { f -> FriendChip(f, f.pubkey in chosen) { chosen = if (f.pubkey in chosen) chosen - f.pubkey else chosen + f.pubkey } }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { inviteSheet = false; Graph.scope.launch { Graph.shards.invite(s.id, chosen.toList()) } },
                    enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
                ) { Text(t("Invita")) }
            }
        }
    }
    if (roomDialog) {
        var name by remember { mutableStateOf("") }
        var voice by remember { mutableStateOf(false) }
        var private by remember { mutableStateOf(false) }
        var ro by remember { mutableStateOf(false) }
        var chosen by remember { mutableStateOf(setOf<String>()) }
        MonoDialog(
            onDismissRequest = { roomDialog = false },
            title = { Text(t("Nuova stanza")) },
            text = {
                Column {
                    if (!s.open) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(t("Scritta"), t("Vocale")).forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = voice == (i == 1), onClick = { voice = i == 1 },
                                shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text(t("Nome della stanza")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (!voice) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Solo fondatore e moderatori scrivono"), style = MaterialTheme.typography.titleSmall)
                            Text(t("Per gli annunci: gli altri leggono e reagiscono"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = ro, onCheckedChange = { ro = it })
                    }
                    if (!s.open) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Stanza privata"), style = MaterialTheme.typography.titleSmall)
                            Text(t("La vedono solo i membri scelti, il fondatore e i moderatori"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = private, onCheckedChange = { private = it })
                    }
                    if (private) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.members.filter { it != me && it != s.owner && it !in s.mods }.forEach { pk ->
                                byPk[pk]?.let { f -> FriendChip(f, pk in chosen) { chosen = if (pk in chosen) chosen - pk else chosen + pk } }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    roomDialog = false
                    Graph.scope.launch {
                        val before = Graph.shards.get(s.id)?.rooms?.map { it.id }?.toSet() ?: emptySet()
                        if (private && !s.open) Graph.shards.addPrivateRoom(s.id, name, voice, chosen.toList()) else Graph.shards.addRoom(s.id, name, voice && !s.open)
                        if (ro && !voice) Graph.shards.get(s.id)?.rooms?.firstOrNull { it.id !in before }?.let { Graph.shards.setRoomReadOnly(s.id, it.id, true) }
                    }
                }, enabled = name.isNotBlank()) { Text(t("Crea")) }
            },
            dismissButton = { TextButton(onClick = { roomDialog = false }) { Text(t("Annulla")) } },
        )
    }
    if (confirmLeave) {
        MonoDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(t("Uscire da {0}?", s.name)) },
            text = { Text(if (s.open) t("Non vedrai più le stanze. Potrai rientrare dalla scheda Shard.") else t("Non vedrai più le stanze. Potranno invitarti di nuovo.")) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; Graph.scope.launch { Graph.shards.leave(s.id) }; if (!embedded) nav.popBackStack() }) { Text(t("Esci")) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(t("Annulla")) } },
        )
    }
}


@Composable
private fun ShardNoteCard(n: com.monolith.app.net.ShardNote, byPk: Map<String, Friend>, me: String?, canManage: Boolean, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val url = remember(n.text) { Regex("https?://\\S+").find(n.text)?.value }
    var confirm by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).combinedClickable(
            onClick = { url?.let { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it))) } } },
            onLongClick = { if (canManage) confirm = true },
        ),
    ) {
        Row(Modifier.padding(14.dp)) {
            Text("📌", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(n.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (n.by == me) t("Tu") else byPk[n.by]?.displayName ?: t("Qualcuno")) + " · " + com.monolith.app.core.Fmt.dayShort(n.at),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) MonoDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t("Togliere la nota dalla bacheca?")) },
        confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text(t("Togli")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Annulla")) } },
    )
}

@Composable
private fun ShardEventCard(s: com.monolith.app.net.Shard, e: com.monolith.app.net.ShardEvent, byPk: Map<String, Friend>, me: String?) {
    val going = me != null && me in e.going
    var confirm by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).combinedClickable(
            onClick = {},
            onLongClick = { if (e.by == me || s.canManage(me)) confirm = true },
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(e.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    com.monolith.app.core.Fmt.dayShort(e.start) + (if (e.allDay) "" else " · " + com.monolith.app.core.Fmt.time(e.start)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val names = e.going.map { if (it == me) t("tu") else byPk[it]?.displayName?.substringBefore(' ') ?: "?" }
                if (names.isNotEmpty()) Text(t("Ci saranno: {0}", names.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (going) OutlinedButton(onClick = { Graph.scope.launch { Graph.shards.setGoing(s.id, e.id, false) } }, shape = CircleShape) { Text(t("Ci sono ✓")) }
            else Button(onClick = { Graph.scope.launch { Graph.shards.setGoing(s.id, e.id, true) } }, shape = CircleShape) { Text(t("Partecipo")) }
        }
    }
    if (confirm) MonoDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t("Eliminare l'evento per tutti?")) },
        confirmButton = { TextButton(onClick = { confirm = false; Graph.scope.launch { Graph.shards.deleteEvent(s.id, e.id) } }) { Text(t("Elimina")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Annulla")) } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShardEventDialog(onDismiss: () -> Unit, onCreate: (String, String, Long, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📅") }
    var allDay by remember { mutableStateOf(false) }
    val cal = remember { java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 20); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) } }
    var start by remember { mutableStateOf(cal.timeInMillis) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Nuovo evento")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("📅", "🎉", "🍕", "🎮", "🎬", "⚽", "🎵", "✈️").forEach { e ->
                        Text(e, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).background(if (e == emoji) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent).bounceClick(0.85f) { emoji = e }.padding(5.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(title, { title = it.take(80) }, label = { Text(t("Titolo")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickDate = true }) { Text(com.monolith.app.core.Fmt.dayShort(start)) }
                    if (!allDay) TextButton(onClick = { pickTime = true }) { Text(com.monolith.app.core.Fmt.time(start)) }
                    Spacer(Modifier.weight(1f))
                    Text(t("Tutto il giorno"), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.material3.Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title, emoji, if (allDay) com.monolith.app.core.Fmt.startOfDay(start) else start, allDay) }, enabled = title.isNotBlank()) { Text(t("Crea")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
    if (pickDate) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = start + java.util.TimeZone.getDefault().getOffset(start))
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val u = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        val c = java.util.Calendar.getInstance().apply { timeInMillis = start; set(u.get(java.util.Calendar.YEAR), u.get(java.util.Calendar.MONTH), u.get(java.util.Calendar.DAY_OF_MONTH)) }
                        start = c.timeInMillis
                    }
                    pickDate = false
                }) { Text(t("OK")) }
            },
        ) { androidx.compose.material3.DatePicker(state) }
    }
    if (pickTime) {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = start }
        val state = androidx.compose.material3.rememberTimePickerState(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true)
        MonoDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    start = java.util.Calendar.getInstance().apply { timeInMillis = start; set(java.util.Calendar.HOUR_OF_DAY, state.hour); set(java.util.Calendar.MINUTE, state.minute) }.timeInMillis
                    pickTime = false
                }) { Text(t("OK")) }
            },
            text = { androidx.compose.material3.TimePicker(state) },
        )
    }
}


// ====================================================================== the Shard tab: rail of shards on the left, content on the right

private object ShardTab {
    private val sp by lazy { Graph.app.getSharedPreferences("shard_tab", android.content.Context.MODE_PRIVATE) }
    var last: String?
        get() = sp.getString("last", null)
        set(v) { sp.edit().putString("last", v).apply() }
}

/** a shard to open in the tab (from a notification or an invite) */
object ShardFocus {
    val id = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

@Composable
fun ShardsHome(nav: NavHostController) {
    val shards by Graph.shards.all.collectAsState()
    val voice by Graph.shards.voice.collectAsState()
    val unread by remember { Graph.db.messages().unread() }.collectAsState(initial = emptyList())
    val focus by ShardFocus.id.collectAsState()
    val mentions by Graph.mentions.convs.collectAsState()
    val unreadBy = unread.associate { it.convId to it.n }
    val left = remember(shards) { Graph.hubs.leftShards() }
    val sorted = shards.sortedBy { it.name.lowercase() }
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ShardTab.last) }
    LaunchedEffect(focus) { focus?.let { selected = it; ShardTab.last = it; ShardFocus.id.value = null } }
    val cur = sorted.firstOrNull { it.id == selected } ?: sorted.firstOrNull()
    var create by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.width(76.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).statusBarsPadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(com.monolith.app.ui.components.ShardIcons.Filled, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            Box(Modifier.width(32.dp).height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            sorted.forEach { s ->
                // in a shard for everyone only announcements and tags count
                val n = s.rooms.sumOf { r -> if (s.open && !r.ro && s.conv(r) !in mentions) 0 else unreadBy[s.conv(r)] ?: 0 }
                val live = voice.isNotEmpty() && s.rooms.any { r -> r.voice && Graph.shards.inRoom(s.id, r.id).isNotEmpty() }
                RailItem(s, s.id == cur?.id, n, live) { selected = s.id; ShardTab.last = s.id }
            }
            Box(
                Modifier.size(50.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape).bounceClick(0.9f) { create = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, t("Nuovo shard")) }
        }
        Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
            if (cur == null) {
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(com.monolith.app.ui.components.ShardIcons.Outline, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(t("Nessuno shard"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("Uno shard è uno spazio per un gruppo di amici, con stanze scritte e vocali, bacheca ed eventi."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { create = true }, shape = CircleShape) { Text(t("Crea uno shard")) }
                    left.forEach { (id, name) ->
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { Graph.scope.launch { Graph.shards.rejoin(id) } }, shape = CircleShape) { Text(t("Rientra in {0}", name)) }
                    }
                }
            } else {
                androidx.compose.runtime.key(cur.id) { ShardBody(nav, cur.id, embedded = true) }
            }
        }
    }
    if (create) ShardEditSheet(null, onClose = { create = false }, left = left) { id -> selected = id; ShardTab.last = id }
}

/** one shard in the rail: a circle that becomes a rounded square when selected, with the pill on the left */
@Composable
private fun RailItem(s: Shard, selected: Boolean, unread: Int, live: Boolean, onClick: () -> Unit) {
    val corner by androidx.compose.animation.core.animateDpAsState(if (selected) 16.dp else 25.dp, com.monolith.app.ui.theme.Motion.bouncy(), label = "corner")
    val pill by androidx.compose.animation.core.animateDpAsState(if (selected) 36.dp else if (unread > 0) 8.dp else 0.dp, com.monolith.app.ui.theme.Motion.bouncy(), label = "pill")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.align(Alignment.CenterStart).width(4.dp).height(pill)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(MaterialTheme.colorScheme.onSurface),
        )
        Box {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(corner))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .bounceClick(0.9f, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Text(s.emoji, fontSize = 24.sp) }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(20.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface).border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(if (unread > 9) "9+" else "$unread", color = MaterialTheme.colorScheme.surface, fontSize = 9.sp) }
            } else if (live) {
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp)) { PulseDot(size = 12.dp) }
            }
        }
    }
}
