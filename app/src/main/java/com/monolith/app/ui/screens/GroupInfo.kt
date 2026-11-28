package com.monolith.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SpeakerNotesOff
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Group
import com.monolith.app.data.GroupMeta
import com.monolith.app.net.InviteCard
import com.monolith.app.net.Invites
import com.monolith.app.net.Messenger
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.CoverArt
import com.monolith.app.ui.components.CoverThumb
import com.monolith.app.ui.components.Covers
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.QrImage
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Palettes
import com.monolith.app.ui.theme.schemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** the colours of a group's chat, chosen by its administrators ("" = the app's colours) */
@Composable
fun GroupTheme(color: String, content: @Composable () -> Unit) {
    if (color.isBlank()) { content(); return }
    val ctx = LocalContext.current
    val dark = com.monolith.app.ui.theme.LocalDark.current
    val scheme = remember(color, dark) { schemeFor(ctx, color, dark) }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

/** where only some people write: in place of the box to write in */
@Composable
fun ReadOnlyBar(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SpeakerNotesOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** an invite link inside a message: a button to see where it leads */
@Composable
fun InviteChip(secret: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 6.dp).bounceClick(0.96f) { NavBus.joinLink.value = secret },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("Invito a un gruppo · Apri"), color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun shareText(ctx: android.content.Context, text: String) {
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), t("Condividi il link")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun copy(ctx: android.content.Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", text))
    Toast.makeText(ctx, t("Link copiato"), Toast.LENGTH_SHORT).show()
}

/** info, members, invite link, settings and look of a group */
@Composable
fun GroupInfoScreen(nav: NavHostController, gid: String) {
    val ctx = LocalContext.current
    val group by remember(gid) { Graph.db.groups().observe(gid) }.collectAsState(initial = null)
    val metas by Graph.groupMeta.all.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val asks by Graph.joinAsks.all.collectAsState()
    val setting by remember(gid) { Graph.db.social().setting("g:$gid") }.collectAsState(initial = null)
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(group) { if (group != null) seen = true else if (seen) nav.popBackStack() }
    val g = group ?: return
    val meta = metas[gid] ?: GroupMeta(gid)
    val me = Graph.identity.keys?.pub ?: ""
    val byPk = people.associateBy { it.pubkey }
    val isAdmin = Graph.groupMeta.isAdmin(g, me)
    val isCreator = g.admin == me
    val canEdit = isAdmin || meta.editAll
    val canAdd = isAdmin || meta.addAll
    val kids = com.monolith.app.kids.Kids.edition
    val canLink = !kids && (isAdmin || (meta.addAll && meta.link.isNotBlank()))
    val waiting = asks.filter { it.target == "g:$gid" && it.pk !in g.memberList() }
    var editDialog by remember { mutableStateOf(false) }
    var addSheet by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var titleFor by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var reactionSlot by remember { mutableIntStateOf(-1) }

    GroupTheme(meta.color) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                    Text(t("Info gruppo"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (canEdit) IconButton(onClick = { editDialog = true }) { Icon(Icons.Rounded.Edit, t("Modifica")) }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                    item(key = "head") { GroupHeader(g, meta, canEdit) { editDialog = true } }
                    item(key = "actions") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (canAdd) PillAction(Icons.Rounded.PersonAdd, t("Aggiungi"), Modifier.weight(1f)) { addSheet = true }
                            if (canLink) PillAction(Icons.Rounded.Link, t("Link"), Modifier.weight(1f)) {
                                if (meta.link.isBlank() && isAdmin) Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(link = Invites.newSecret()) } }
                                else if (meta.link.isNotBlank()) shareText(ctx, t("Entra nel gruppo {0} su Monolith: {1}", g.name, Invites.linkFor(meta.link)))
                            }
                            val muted = setting?.muted == true
                            PillAction(if (muted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications, if (muted) t("Silenziato") else t("Notifiche"), Modifier.weight(1f)) {
                                Graph.scope.launch { Graph.messenger.setMuted("g:$gid", !muted) }
                            }
                        }
                    }
                    // people who used the link and wait for a yes
                    if (isAdmin && waiting.isNotEmpty()) {
                        item(key = "asksLabel") { SectionLabel(t("Chiedono di entrare"), Modifier.padding(top = 8.dp)) }
                        items(waiting, key = { "ask" + it.pk }) { a ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(a.emoji, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { Graph.joinAsks.remove(a.target, a.pk) }) { Text(t("Rifiuta")) }
                                Button(onClick = { Graph.scope.launch { Graph.messenger.admitToGroup(gid, a.pk) } }, shape = CircleShape) { Text(t("Accetta")) }
                            }
                        }
                    }
                    // the invite link
                    if (canLink) item(key = "link") {
                        LinkCard(
                            link = meta.link.takeIf { it.isNotBlank() }?.let { Invites.linkFor(it) },
                            isAdmin = isAdmin, approve = meta.approve, qr = qr,
                            onCreate = { Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(link = Invites.newSecret()) } } },
                            onCopy = { copy(ctx, Invites.linkFor(meta.link)) },
                            onShare = { shareText(ctx, t("Entra nel gruppo {0} su Monolith: {1}", g.name, Invites.linkFor(meta.link))) },
                            onQr = { qr = !qr },
                            onReset = { Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(link = Invites.newSecret()) } } },
                            onOff = { Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(link = "") } } },
                            onApprove = { on -> Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(approve = on) } } },
                        )
                    }
                    // members
                    item(key = "membersLabel") { SectionLabel(t("{0} membri", g.memberList().size), Modifier.padding(top = 10.dp)) }
                    val members = g.memberList().sortedWith(compareBy<String>({ if (it == g.admin) 0 else if (it in meta.admins) 1 else 2 }, { if (it == me) "" else byPk[it]?.displayName ?: "~" }))
                    items(members, key = { "m$it" }) { pk ->
                        val f = byPk[pk]
                        Row(
                            Modifier.fillMaxWidth().bounceClick(0.98f) { if (pk != me && f?.status == FriendStatus.FRIEND) nav.navigate(Routes.profile(pk)) }.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(if (pk == me) Graph.identity.profile.value?.emoji ?: "🗿" else f?.emoji ?: "🙂", 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (pk == me) t("Tu") else f?.displayName ?: t("Qualcuno"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val role = listOfNotNull(
                                    when (pk) { g.admin -> t("Creatore"); in meta.admins -> t("Amministratore"); else -> null },
                                    meta.titles[pk],
                                ).joinToString(" · ")
                                if (role.isNotBlank()) Text(role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (isAdmin && pk != me) Box {
                                IconButton(onClick = { menuFor = pk }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                                DropdownMenu(expanded = menuFor == pk, onDismissRequest = { menuFor = null }) {
                                    if (pk != g.admin) {
                                        val adm = pk in meta.admins
                                        DropdownMenuItem(
                                            text = { Text(if (adm) t("Togli da amministratore") else t("Rendi amministratore")) },
                                            leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, null) },
                                            onClick = { menuFor = null; Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { m -> m.copy(admins = if (adm) m.admins - pk else m.admins + pk) } } },
                                        )
                                    }
                                    DropdownMenuItem(text = { Text(t("Dai un titolo")) }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) }, onClick = { menuFor = null; titleFor = pk })
                                    if (pk != g.admin) DropdownMenuItem(
                                        text = { Text(t("Togli dal gruppo")) }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                                        onClick = { menuFor = null; Graph.scope.launch { Graph.messenger.removeFromGroup(gid, pk) } },
                                    )
                                }
                            }
                        }
                    }
                    // settings
                    if (isAdmin) {
                        item(key = "setLabel") { SectionLabel(t("Impostazioni"), Modifier.padding(top = 14.dp)) }
                        item(key = "set1") {
                            SettingSwitch(Icons.Rounded.Edit, t("Tutti modificano le info"), t("Nome, immagine, descrizione, colore e copertina"), meta.editAll) { on ->
                                Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(editAll = on) } }
                            }
                        }
                        item(key = "set2") {
                            SettingSwitch(Icons.Rounded.PersonAdd, t("Tutti aggiungono persone"), t("Anche chi non è amministratore può aggiungere amici e condividere il link"), meta.addAll) { on ->
                                Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(addAll = on) } }
                            }
                        }
                        item(key = "set3") {
                            SettingSwitch(Icons.Rounded.SpeakerNotesOff, t("Scrivono solo gli amministratori"), t("Gli altri leggono, reagiscono e votano"), meta.sendAdmins) { on ->
                                Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(sendAdmins = on) } }
                            }
                        }
                    }
                    // the look
                    if (canEdit) {
                        item(key = "lookLabel") { SectionLabel(t("Personalizza"), Modifier.padding(top = 14.dp)) }
                        item(key = "colors") {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                Text(t("Colore della chat"), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val dark = com.monolith.app.ui.theme.LocalDark.current
                                    (listOf("") + Palettes.ALL.map { it.key }.filter { it != Palettes.MONO }).forEach { key ->
                                        val sel = meta.color == key
                                        val c = if (key.isBlank()) MaterialTheme.colorScheme.onSurface else Palettes.byKey(key).let { Color(if (dark) it.dark else it.light) }
                                        Box(
                                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                                .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                .bounceClick(0.85f) { Graph.scope.launch { Graph.messenger.editGroup(gid, g.name, g.emoji, meta.desc, key, meta.cover) } },
                                            contentAlignment = Alignment.Center,
                                        ) { if (sel) Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) }
                                    }
                                }
                            }
                        }
                        item(key = "covers") {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(t("Copertina"), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        Modifier.size(84.dp, 52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .border(if (meta.cover.isBlank()) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                            .bounceClick(0.9f) { Graph.scope.launch { Graph.messenger.editGroup(gid, g.name, g.emoji, meta.desc, meta.color, "") } },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(t("Nessuna"), style = MaterialTheme.typography.labelSmall) }
                                    Covers.IDS.forEach { id ->
                                        CoverThumb(
                                            id,
                                            Modifier.size(84.dp, 52.dp).clip(RoundedCornerShape(12.dp))
                                                .border(if (meta.cover == id) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                                .bounceClick(0.9f) { Graph.scope.launch { Graph.messenger.editGroup(gid, g.name, g.emoji, meta.desc, meta.color, id) } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isAdmin) item(key = "reactions") {
                        val current = meta.reactions.takeIf { it.isNotEmpty() } ?: Messenger.QUICK_REACTIONS
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t("Reazioni rapide del gruppo"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (meta.reactions.isNotEmpty()) TextButton(onClick = { Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(reactions = emptyList()) } } }) { Text(t("Ripristina")) }
                            }
                            Text(t("Tocca un'emoji per cambiarla: compare a tutti quando tengono premuto un messaggio"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                current.forEachIndexed { i, e ->
                                    Box(
                                        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).bounceClick(0.85f) { reactionSlot = i },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(e, fontSize = 20.sp) }
                                }
                            }
                        }
                    }
                    item(key = "danger") {
                        Column(Modifier.padding(top = 18.dp)) {
                            SettingRow(Icons.AutoMirrored.Rounded.Logout, t("Lascia il gruppo"), t("Non riceverai più i messaggi")) { confirm = "leave" }
                            if (isCreator) SettingRow(Icons.Rounded.DeleteOutline, t("Elimina il gruppo"), t("Sparisce per tutti, con i messaggi")) { confirm = "delete" }
                        }
                    }
                }
            }
        }
    }

    if (editDialog) GroupEditDialog(g, meta, onDismiss = { editDialog = false }) { name, emoji, desc ->
        editDialog = false
        Graph.scope.launch { Graph.messenger.editGroup(gid, name, emoji, desc, meta.color, meta.cover) }
    }
    if (addSheet) AddPeopleSheet(g.memberList(), onDismiss = { addSheet = false }) { pks ->
        addSheet = false
        Graph.scope.launch { Graph.messenger.addToGroup(gid, pks) }
    }
    titleFor?.let { pk ->
        var text by remember(pk) { mutableStateOf(meta.titles[pk] ?: "") }
        MonoDialog(
            onDismissRequest = { titleFor = null },
            title = { Text(t("Titolo di {0}", byPk[pk]?.displayName ?: "")) },
            text = {
                Column {
                    Text(t("Compare accanto al nome nella chat del gruppo, per esempio «Capitano» o «Tesoriere»."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it.take(24) }, label = { Text(t("Titolo")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    titleFor = null
                    Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { m -> m.copy(titles = if (text.isBlank()) m.titles - pk else m.titles + (pk to text.trim())) } }
                }) { Text(t("Salva")) }
            },
            dismissButton = { TextButton(onClick = { titleFor = null }) { Text(t("Annulla")) } },
        )
    }
    if (reactionSlot >= 0) EmojiPickerSheet(onDismiss = { reactionSlot = -1 }) { e ->
        val slot = reactionSlot
        reactionSlot = -1
        val cur = (meta.reactions.takeIf { it.isNotEmpty() } ?: Messenger.QUICK_REACTIONS).toMutableList()
        if (slot in cur.indices) cur[slot] = e
        Graph.scope.launch { Graph.messenger.setGroupMeta(gid) { it.copy(reactions = cur) } }
    }
    confirm?.let { what ->
        MonoDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "delete") t("Eliminare il gruppo per tutti?") else t("Lasciare il gruppo?")) },
            text = { Text(if (what == "delete") t("Il gruppo e i suoi messaggi spariscono dai telefoni di tutti i membri.") else t("Non riceverai più i suoi messaggi.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    Graph.scope.launch { if (what == "delete") Graph.messenger.deleteGroup(gid) else Graph.messenger.leaveGroup(gid) }
                    nav.popBackStack("home", inclusive = false)
                }) { Text(t("Conferma")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
private fun PillAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.bounceClick(0.95f, onClick = onClick)) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** cover, picture, name, people and description */
@Composable
internal fun GroupHeader(g: Group, meta: GroupMeta, canEdit: Boolean, onEdit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(26.dp))) {
            if (meta.cover.isNotBlank()) CoverArt(meta.cover, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)))
        }
        Box(
            Modifier.offset(y = (-44).dp).size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(5.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(g.emoji, fontSize = 40.sp) }
        Column(Modifier.offset(y = (-34).dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(t("Gruppo · {0} membri", g.memberList().size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (meta.desc.isNotBlank()) {
                Text(meta.desc, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            } else if (canEdit) {
                TextButton(onClick = onEdit) { Text(t("Aggiungi una descrizione")) }
            }
        }
    }
}

/** the invite link: create, copy, share, show as a code, change or turn off */
@Composable
internal fun LinkCard(
    link: String?,
    isAdmin: Boolean,
    approve: Boolean,
    qr: Boolean,
    onCreate: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onQr: () -> Unit,
    onReset: () -> Unit,
    onOff: () -> Unit,
    onApprove: (Boolean) -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(t("Link d'invito"), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            if (link == null) {
                Text(t("Chi ha il link può entrare (o chiederlo) anche se non è tuo amico. Puoi cambiarlo o spegnerlo quando vuoi."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isAdmin) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onCreate, shape = CircleShape) { Text(t("Crea il link")) }
                }
                return@Column
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onCopy)) {
                Text(link, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy, shape = CircleShape) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Copia")) }
                OutlinedButton(onClick = onShare, shape = CircleShape) { Icon(Icons.Rounded.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Condividi")) }
                OutlinedButton(onClick = onQr, shape = CircleShape) { Icon(Icons.Rounded.QrCode2, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Codice")) }
            }
            if (qr) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QrImage(link, Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)))
                }
            }
            if (isAdmin) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().bounceClick(0.98f) { onApprove(!approve) }.padding(vertical = 6.dp)) {
                    Icon(Icons.Rounded.HowToReg, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Approva chi entra"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Chi usa il link aspetta che un amministratore lo accetti"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = approve, onCheckedChange = onApprove)
                }
                Row {
                    TextButton(onClick = onReset) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Nuovo link")) }
                    TextButton(onClick = onOff) { Icon(Icons.Rounded.LinkOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Spegni")) }
                }
            }
        }
    }
}

@Composable
private fun GroupEditDialog(g: Group, meta: GroupMeta, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(g.name) }
    var emoji by remember { mutableStateOf(g.emoji) }
    var desc by remember { mutableStateOf(meta.desc) }
    var picker by remember { mutableStateOf(false) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Modifica il gruppo")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.9f) { picker = true }, contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 28.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Nome del gruppo")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(desc, { desc = it.take(500) }, label = { Text(t("Descrizione")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, emoji, desc) }, enabled = name.isNotBlank()) { Text(t("Salva")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
}

/** choose friends to add to a group that already exists */
@Composable
internal fun AddPeopleSheet(inside: List<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    var chosen by remember { mutableStateOf(setOf<String>()) }
    val candidates = friends.filter { it.status == FriendStatus.FRIEND && it.pubkey !in inside }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MonoSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(t("Aggiungi persone"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            if (candidates.isEmpty()) Text(t("Tutti i tuoi amici sono già qui."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                candidates.forEach { f: Friend ->
                    FilterChip(
                        selected = f.pubkey in chosen, onClick = { chosen = if (f.pubkey in chosen) chosen - f.pubkey else chosen + f.pubkey },
                        label = { Text(f.displayName) }, leadingIcon = { Text(f.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onAdd(chosen.toList()) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                Text(if (chosen.size > 1) t("Aggiungi {0} persone", chosen.size) else t("Aggiungi"))
            }
        }
    }
}

// ====================================================================== joining with a link

/** what an invite leads to, with the button to go in (also used by the screenshots) */
@Composable
fun JoinCardContent(card: InviteCard, inside: Boolean, sent: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp))) {
            if (card.cover.isNotBlank()) CoverArt(card.cover, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)))
        }
        Box(
            Modifier.offset(y = (-30).dp).size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(card.emoji, fontSize = 30.sp) }
        Column(Modifier.offset(y = (-22).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                (if (card.kind == "s") t("Shard") else t("Gruppo")) + " · " + t("{0} membri", card.count),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (card.desc.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(card.desc, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    inside -> t("Sei già dentro.")
                    sent && card.approve -> t("Richiesta inviata: un amministratore deve accettarla. Ti arriva una notifica quando sei dentro.")
                    sent -> t("Richiesta inviata: entri appena un amministratore apre Monolith.")
                    card.approve -> t("Un amministratore deve accettarti.")
                    else -> t("Entri appena un amministratore apre Monolith, anche se non siete amici.")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** opened from a link (in a chat, from the site or from another app) */
@Composable
fun JoinLinkDialog(secret: String, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var state by remember(secret) { mutableIntStateOf(0) }
    var card by remember(secret) { mutableStateOf<InviteCard?>(null) }
    var inside by remember(secret) { mutableStateOf(false) }
    var sent by remember(secret) { mutableStateOf(false) }
    val kids = com.monolith.app.kids.Kids.edition
    LaunchedEffect(secret) {
        if (kids) { state = 3; return@LaunchedEffect }
        val c = withContext(Dispatchers.IO) { runCatching { Invites.resolve(secret) }.getOrNull() }
        card = c
        inside = c != null && Invites.alreadyIn(c)
        state = if (c != null) 1 else 2
    }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { if (state != 1) Text(t("Invito")) },
        text = {
            when (state) {
                0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(t("Apro l'invito…"))
                }
                2 -> Text(t("Questo link non funziona più: forse è stato cambiato o spento. Chiedi quello nuovo a chi te l'ha dato."))
                3 -> Text(t("Su Monolith Kids non si entra con i link: chiedi al tuo genitore di aggiungere chi conosci."))
                else -> card?.let { JoinCardContent(it, inside, sent) }
            }
        },
        confirmButton = {
            val c = card
            when {
                state == 1 && c != null && inside -> TextButton(onClick = { onOpen(if (c.kind == "s") Routes.shard(c.id) else Routes.chat("g:" + c.id)) }) { Text(t("Apri")) }
                state == 1 && c != null && !sent -> TextButton(onClick = {
                    sent = true
                    Graph.scope.launch { Invites.join(c, secret) }
                    Toast.makeText(ctx, t("Richiesta inviata"), Toast.LENGTH_SHORT).show()
                }) { Text(if (c.approve) t("Chiedi di entrare") else t("Entra")) }
                else -> TextButton(onClick = onDismiss) { Text(t("Chiudi")) }
            }
        },
        dismissButton = if (state == 1 && !sent && !inside) ({ TextButton(onClick = onDismiss) { Text(t("Annulla")) } }) else null,
    )
}

/** paste a link received elsewhere */
@Composable
fun PasteLinkDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clip = remember {
        runCatching { ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
    }
    var text by remember { mutableStateOf(clip?.takeIf { Invites.parse(it) != null } ?: "") }
    val secret = Invites.parse(text)
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Entra con un link")) },
        text = {
            Column {
                Text(t("Incolla il link di un gruppo o di uno Shard che ti hanno mandato."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it.take(300) }, label = { Text(t("Link")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = text.isNotBlank() && secret == null)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); NavBus.joinLink.value = secret }, enabled = secret != null) { Text(t("Apri")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}
