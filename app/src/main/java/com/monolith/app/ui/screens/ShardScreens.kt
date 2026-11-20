package com.monolith.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

/** the row of shards on top of the Clubhouse */
@Composable
fun ShardStrip(nav: NavHostController, unread: Map<String, Int>) {
    val shards by Graph.shards.all.collectAsState()
    val voice by Graph.shards.voice.collectAsState()
    var create by remember { mutableStateOf(false) }
    Column {
        SectionLabel(t("Shard"))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            items(shards.sortedBy { it.name.lowercase() }, key = { it.id }) { s ->
                val n = s.rooms.sumOf { unread[s.conv(it)] ?: 0 }
                val live = s.rooms.any { r -> r.voice && Graph.shards.inRoom(s.id, r.id).isNotEmpty() } && voice.isNotEmpty()
                Column(Modifier.width(72.dp).animateItem().bounceClick { nav.navigate(Routes.shard(s.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(s.emoji, fontSize = 28.sp) }
                        if (n > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (n > 99) "99+" else "$n", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp) }
                        }
                        if (live) Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp)) { PulseDot(size = 12.dp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(key = "new") {
                Column(Modifier.width(72.dp).bounceClick { create = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, t("Nuovo shard")) }
                    Spacer(Modifier.height(4.dp))
                    Text(t("Nuovo"), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (create) ShardEditSheet(null, onClose = { create = false }) { id -> nav.navigate(Routes.shard(id)) }
}

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
fun ShardScreen(nav: NavHostController, id: String) {
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
    LaunchedEffect(s == null) { if (s == null && shards.isNotEmpty()) nav.popBackStack() }
    if (s == null) return
    val byPk = people.associateBy { it.pubkey }
    val unreadBy = unread.associate { it.convId to it.n }
    val lastBy = last.associateBy { it.convId }
    val isOwner = s.owner == me
    val current = if (voiceMap.isNotEmpty() || call.cid != null) Graph.shards.currentRoom() else null

    fun join(r: ShardRoom) {
        if (!Graph.shards.joinVoice(s.id, r.id)) Toast.makeText(ctx, t("La stanza è piena (massimo 4 persone)"), Toast.LENGTH_SHORT).show()
        else nav.navigate("call") { launchSingleTop = true }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingRoom?.let { rid -> s.rooms.firstOrNull { it.id == rid }?.let { join(it) } }
        pendingRoom = null
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Text(s.emoji, fontSize = 22.sp)
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
                    if (isOwner) {
                        DropdownMenuItem(text = { Text(t("Modifica shard")) }, onClick = { menu = false; editSheet = true })
                        DropdownMenuItem(text = { Text(t("Nuova stanza")) }, onClick = { menu = false; roomDialog = true })
                    }
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
                        if (pk == s.owner) Text(t("Creatore"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isOwner && pk != me) TextButton(onClick = { Graph.scope.launch { Graph.shards.removeMember(s.id, pk) } }) { Text(t("Togli")) }
                }
            }
        }
    }

    if (editSheet) ShardEditSheet(s, onClose = { editSheet = false })
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
                TextButton(onClick = { confirmLeave = false; Graph.scope.launch { Graph.shards.leave(s.id) }; nav.popBackStack() }) { Text(t("Esci")) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(t("Annulla")) } },
        )
    }
}
