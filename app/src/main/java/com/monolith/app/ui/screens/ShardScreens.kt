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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Tag
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

private val SHARD_EMOJI = listOf("🪨", "🎮", "🎧", "📚", "⚽", "🍕", "🎬", "🏔️", "💼", "🎨", "🌙", "🔥")

/** create (shard = null) or edit a shard */
@Composable
fun ShardEditSheet(shard: Shard?, onClose: () -> Unit, onCreated: (String) -> Unit = {}) {
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(shard?.name ?: "") }
    var emoji by remember { mutableStateOf(shard?.emoji ?: "🪨") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var picker by remember { mutableStateOf(false) }
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
            if (shard == null) {
                Spacer(Modifier.height(14.dp))
                Text(t("Invita"), style = MaterialTheme.typography.titleSmall)
                val list = friends.filter { it.status == FriendStatus.FRIEND }
                if (list.isEmpty()) Text(t("Aggiungi prima un amico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 220.dp)) {
                    list.forEach { f -> FriendChip(f, f.pubkey in chosen) { chosen = if (f.pubkey in chosen) chosen - f.pubkey else chosen + f.pubkey } }
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    onClose()
                    Graph.scope.launch {
                        if (shard == null) Graph.shards.create(name, emoji, chosen.toList())?.let { id -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onCreated(id) } }
                        else Graph.shards.edit(shard.id, name, emoji)
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
    val current = if (voiceMap.isNotEmpty() || call.cid != null) Graph.shards.currentRoom() else null

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
                Text(s.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("{0} membri", s.members.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { inviteSheet = true }) { Icon(Icons.Rounded.PersonAdd, t("Invita")) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (isOwner) DropdownMenuItem(text = { Text(t("Modifica shard")) }, onClick = { menu = false; editSheet = true })
                    if (canManage) {
                        DropdownMenuItem(text = { Text(t("Nuova stanza")) }, onClick = { menu = false; roomDialog = true })
                        DropdownMenuItem(text = { Text(t("Nuova nota in bacheca")) }, onClick = { menu = false; noteDialog = true })
                    }
                    DropdownMenuItem(text = { Text(t("Nuovo evento")) }, onClick = { menu = false; eventDialog = true })
                    DropdownMenuItem(text = { Text(t("Esci dallo shard")) }, onClick = { menu = false; confirmLeave = true })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "textLabel") { SectionLabel(t("Stanze scritte")) }
            items(s.rooms.filter { !it.voice }, key = { "t" + it.id }) { r ->
                val conv = s.conv(r)
                val n = unreadBy[conv] ?: 0
                Row(
                    Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { nav.navigate(Routes.chat(conv)) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tag, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        lastBy[conv]?.let { m ->
                            Text(
                                (if (m.outgoing) t("Tu") else byPk[m.sender]?.displayName ?: "") + ": " + Graph.messenger.previewText(m),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (n > 0) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }
            item(key = "voiceLabel") { SectionLabel(t("Stanze vocali"), Modifier.padding(top = 10.dp)) }
            items(s.rooms.filter { it.voice }, key = { "v" + it.id }) { r ->
                val inside = (Graph.shards.inRoom(s.id, r.id) + listOfNotNull(me.takeIf { current == s.id to r.id })).distinct()
                val mine = current == s.id to r.id
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (mine) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().animateItem().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(22.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(t("Bacheca"), Modifier.weight(1f).padding(top = 10.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(t("Eventi"), Modifier.weight(1f).padding(top = 10.dp))
                    TextButton(onClick = { eventDialog = true }) { Text(t("Nuovo")) }
                }
            }
            if (upcoming.isEmpty()) item(key = "eventsEmpty") {
                Text(t("Nessun evento in programma."), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(upcoming, key = { "e" + it.id }) { e -> ShardEventCard(s, e, byPk, me) }
            item(key = "membersLabel") { SectionLabel(t("Membri"), Modifier.padding(top = 10.dp)) }
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
                        val role = when (pk) {
                            s.owner -> t("Fondatore")
                            in s.mods -> t("Moderatore")
                            else -> null
                        }
                        if (role != null) Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val canTouch = pk != me && pk != s.owner && (isOwner || (canManage && pk !in s.mods))
                    if (canTouch) Box {
                        IconButton(onClick = { memberMenu = pk }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                        DropdownMenu(expanded = memberMenu == pk, onDismissRequest = { memberMenu = null }) {
                            if (isOwner) {
                                val mod = pk in s.mods
                                DropdownMenuItem(
                                    text = { Text(if (mod) t("Togli moderatore") else t("Rendi moderatore")) },
                                    onClick = { memberMenu = null; Graph.scope.launch { Graph.shards.setMod(s.id, pk, !mod) } },
                                )
                            }
                            DropdownMenuItem(text = { Text(t("Togli dallo shard")) }, onClick = { memberMenu = null; Graph.scope.launch { Graph.shards.removeMember(s.id, pk) } })
                        }
                    }
                }
            }
        }
    }

    if (editSheet) ShardEditSheet(s, onClose = { editSheet = false })
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
        MonoDialog(
            onDismissRequest = { roomDialog = false },
            title = { Text(t("Nuova stanza")) },
            text = {
                Column {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
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
                }
            },
            confirmButton = {
                TextButton(onClick = { roomDialog = false; Graph.scope.launch { Graph.shards.addRoom(s.id, name, voice) } }, enabled = name.isNotBlank()) { Text(t("Crea")) }
            },
            dismissButton = { TextButton(onClick = { roomDialog = false }) { Text(t("Annulla")) } },
        )
    }
    if (confirmLeave) {
        MonoDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(t("Uscire da {0}?", s.name)) },
            text = { Text(t("Non vedrai più le stanze. Potranno invitarti di nuovo.")) },
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
    val unreadBy = unread.associate { it.convId to it.n }
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
                val n = s.rooms.sumOf { unreadBy[s.conv(it)] ?: 0 }
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
                }
            } else {
                androidx.compose.runtime.key(cur.id) { ShardBody(nav, cur.id, embedded = true) }
            }
        }
    }
    if (create) ShardEditSheet(null, onClose = { create = false }) { id -> selected = id; ShardTab.last = id }
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
