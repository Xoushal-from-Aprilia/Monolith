package com.monolith.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.net.Expense
import com.monolith.app.net.Shard
import com.monolith.app.net.dayCode
import com.monolith.app.net.money
import com.monolith.app.net.parseMoney
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.EmptyState
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
private fun names(): Map<String, String> {
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val me = Graph.identity.keys?.pub
    return people.associate { it.pubkey to it.displayName } + (me.orEmpty() to t("Tu"))
}

@Composable
private fun emojiOf(pk: String, people: List<Friend>): String =
    if (pk == Graph.identity.keys?.pub) Graph.identity.profile.value?.emoji ?: "🗿" else people.firstOrNull { it.pubkey == pk }?.emoji ?: "🙂"

/** name + emoji + friends: used to start expenses, albums and habits */
@Composable
fun TogetherNewSheet(title: String, hint: String, suggestions: List<String>, friendsRequired: Boolean, onClose: () -> Unit, onCreate: (String, String, List<String>) -> Unit) {
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(suggestions.first()) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var picker by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.9f) { picker = true },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 28.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Nome")) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { e ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (e == emoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bounceClick(0.85f) { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(t("Con chi"), style = MaterialTheme.typography.titleSmall)
            val list = friends.filter { it.status == FriendStatus.FRIEND }
            if (list.isEmpty()) Text(t("Aggiungi prima un amico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { f ->
                    val sel = f.pubkey in chosen
                    FilterChip(
                        selected = sel, onClick = { chosen = if (sel) chosen - f.pubkey else chosen + f.pubkey },
                        label = { Text(f.displayName) }, leadingIcon = { Text(f.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onClose(); onCreate(name, emoji, chosen.toList()) },
                enabled = name.isNotBlank() && (!friendsRequired || chosen.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
            ) { Text(t("Crea")) }
        }
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
}

// ====================================================================== expenses

@Composable
fun BillsPane(nav: NavHostController) {
    val bills by Graph.together.bills.collectAsState()
    val expenses by Graph.together.expenses.collectAsState()
    val me = Graph.identity.keys?.pub ?: ""
    if (bills.isEmpty()) {
        EmptyState(t("Nessuna spesa condivisa"), t("Una cena, un viaggio, la casa: segnate chi paga cosa e Monolith vi dice chi deve quanto a chi."))
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)) {
        items(bills.sortedByDescending { it.updatedAt }, key = { it.id }) { b ->
            val mine = remember(expenses, b) { Graph.together.balances(b.id)[me] ?: 0L }
            val total = expenses.filter { it.billId == b.id && !it.deleted }.sumOf { it.cents }
            Surface(
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().animateItem().padding(horizontal = 16.dp, vertical = 5.dp).bounceClick(0.97f) { nav.navigate(Routes.bill(b.id)) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Text(b.emoji, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t("Totale {0} · {1} persone", money(total), b.members.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        when {
                            mine > 0 -> t("ti devono {0}", money(mine))
                            mine < 0 -> t("devi {0}", money(-mine))
                            else -> t("in pari")
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun BillScreen(nav: NavHostController, id: String) {
    val bills by Graph.together.bills.collectAsState()
    val expenses by Graph.together.expenses.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val nm = names()
    val b = bills.firstOrNull { it.id == id }
    var add by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Expense?>(null) }
    if (b == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val me = Graph.identity.keys?.pub ?: ""
    val bal = remember(expenses, b) { Graph.together.balances(b.id) }
    val transfers = remember(expenses, b) { Graph.together.settle(b.id) }
    val list = remember(expenses, b) { Graph.together.expensesOf(b.id) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                Text(b.emoji + "  " + b.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(t("Esci")) }, onClick = { menu = false; Graph.scope.launch { Graph.together.leaveBill(b.id) }; nav.popBackStack() })
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                item(key = "balLabel") { SectionLabel(t("Saldi")) }
                items(b.members.sortedByDescending { bal[it] ?: 0L }, key = { "b$it" }) { pk ->
                    val v = bal[pk] ?: 0L
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(emojiOf(pk, people), 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(nm[pk] ?: t("Qualcuno"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(if (v > 0) "+" + money(v) else money(v), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (transfers.isNotEmpty()) {
                    item(key = "setLabel") { SectionLabel(t("Per pareggiare"), Modifier.padding(top = 10.dp)) }
                    items(transfers, key = { "s" + it.from + it.to }) { tr ->
                        Surface(
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    t("{0} dà {1} a {2}", nm[tr.from] ?: "…", money(tr.cents), nm[tr.to] ?: "…"),
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    Graph.scope.launch { Graph.together.saveExpense(b.id, null, tr.from, tr.cents, t("Rimborso"), listOf(tr.to)) }
                                }) { Text(t("Fatto")) }
                            }
                        }
                    }
                }
                item(key = "expLabel") { SectionLabel(t("Spese"), Modifier.padding(top = 10.dp)) }
                if (list.isEmpty()) item(key = "none") { Text(t("Ancora niente. Aggiungi la prima spesa."), Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(list, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { del = e }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.desc, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                t("Pagato da {0}", nm[e.payer] ?: "…") + " · " + Fmt.dayShort(e.at) +
                                    if (e.split.size < b.members.size) " · " + t("diviso tra {0}", e.split.size) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(money(e.cents), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        Button(
            onClick = { add = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth().height(56.dp), shape = CircleShape,
        ) { Text(t("Aggiungi spesa")) }
    }

    if (add) {
        var desc by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var payer by remember { mutableStateOf(me) }
        var split by remember { mutableStateOf(b.members.toSet()) }
        val cents = parseMoney(amount)
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        MonoSheet(onDismissRequest = { add = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).imePadding()) {
                Text(t("Nuova spesa"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(desc, { desc = it.take(60) }, label = { Text(t("Per cosa?")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount, { amount = it.take(12) }, label = { Text(t("Quanto (€)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                Text(t("Chi ha pagato"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.members.forEach { pk ->
                        FilterChip(
                            selected = payer == pk, onClick = { payer = pk }, label = { Text(nm[pk] ?: "…") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(t("Diviso tra"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.members.forEach { pk ->
                        val sel = pk in split
                        FilterChip(
                            selected = sel, onClick = { split = if (sel && split.size > 1) split - pk else split + pk }, label = { Text(nm[pk] ?: "…") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
                if (cents != null) {
                    Text(t("{0} a testa", money(cents / split.size)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        add = false
                        Graph.scope.launch { Graph.together.saveExpense(b.id, null, payer, cents ?: return@launch, desc, split.toList()) }
                    },
                    enabled = cents != null && desc.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
                ) { Text(t("Aggiungi")) }
            }
        }
    }
    del?.let { e ->
        MonoDialog(
            onDismissRequest = { del = null },
            title = { Text(e.desc) },
            text = { Text(t("Pagato da {0}", nm[e.payer] ?: "…") + " · " + money(e.cents)) },
            confirmButton = {
                TextButton(onClick = {
                    del = null
                    Graph.scope.launch { Graph.together.saveExpense(b.id, e.id, e.payer, e.cents, e.desc, e.split, deleted = true) }
                }) { Text(t("Elimina")) }
            },
            dismissButton = { TextButton(onClick = { del = null }) { Text(t("Chiudi")) } },
        )
    }
}

// ====================================================================== albums

@Composable
fun AlbumsPane(nav: NavHostController) {
    val groups by remember { Graph.db.groups().all() }.collectAsState(initial = emptyList())
    val last by remember { Graph.db.messages().lastMessages() }.collectAsState(initial = emptyList())
    val albums = groups.filter { it.id.startsWith(Shard.ALBUM) }.sortedByDescending { g -> last.firstOrNull { it.convId == "g:" + g.id }?.ts ?: g.createdAt }
    if (albums.isEmpty()) {
        EmptyState(t("Nessun album"), t("Raccogliete insieme le foto di una serata o di un viaggio: ognuno aggiunge le sue."))
        return
    }
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { g ->
            val cover = last.firstOrNull { it.convId == "g:" + g.id && it.kind == "photo" }?.mediaPath
            Column(Modifier.animateItem().bounceClick(0.96f) { nav.navigate(Routes.album(g.id)) }) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) AsyncImage(model = File(cover), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(g.emoji, fontSize = 40.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("{0} persone", g.memberList().size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AlbumScreen(nav: NavHostController, id: String) {
    val conv = "g:$id"
    val group by remember(id) { Graph.db.groups().observe(id) }.collectAsState(initial = null)
    val msgs by remember(conv) { Graph.db.messages().conversation(conv, 2000) }.collectAsState(initial = emptyList())
    val nm = names()
    val photos = msgs.filter { it.kind == "photo" }
    var menu by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        Graph.scope.launch { uris.forEach { Graph.messenger.sendPhoto(conv, it, "") } }
    }
    val g = group
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Column(Modifier.weight(1f)) {
                Text((g?.emoji ?: "") + "  " + (g?.name ?: ""), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("{0} foto · {1} persone", photos.size, g?.memberList()?.size ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { nav.navigate(Routes.chat(conv)) }) { Icon(Icons.AutoMirrored.Rounded.Chat, t("Commenti")) }
            IconButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Rounded.AddPhotoAlternate, t("Aggiungi foto")) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, t("Altro")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(t("Esci dall'album")) }, onClick = {
                        menu = false
                        Graph.scope.launch { Graph.messenger.leaveGroup(id) }
                        nav.popBackStack()
                    })
                }
            }
        }
        if (photos.isEmpty()) {
            EmptyState(t("Album vuoto"), t("Tocca l'icona della foto in alto per aggiungere le prime."))
        } else {
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(2.dp, 2.dp, 2.dp, 40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(photos, key = { it.id }) { m ->
                    val path = m.mediaPath
                    Box(
                        Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bounceClick(0.97f) { if (path != null) nav.navigate(Routes.photo(path, "album-" + m.id)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (path != null) AsyncImage(model = File(path), contentDescription = t("Foto"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else CircularProgressIndicator(progress = { m.progress }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(
                            if (m.outgoing) t("Tu") else nm[m.sender] ?: "",
                            style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).alpha(0.85f),
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================== habits

@Composable
fun HabitsPane(nav: NavHostController) {
    val habits by Graph.together.habits.collectAsState()
    val checks by Graph.together.checks.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val me = Graph.identity.keys?.pub ?: ""
    var open by remember { mutableStateOf<String?>(null) }
    val list = habits.filter { !it.deleted }
    if (list.isEmpty()) {
        EmptyState(t("Nessuna abitudine"), t("Bere acqua, camminare, leggere: spunta ogni giorno e tieni viva la serie, da solo o con gli amici."))
        return
    }
    val today = dayCode(now())
    val week = (6 downTo 0).map { d -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }.timeInMillis }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)) {
        items(list.sortedBy { it.name.lowercase() }, key = { it.id }) { h ->
            val doneToday = checks[h.id]?.get(me)?.contains(today) == true
            val streak = remember(checks, h.id) { Graph.together.streak(h.id, me) }
            Surface(
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().animateItem().padding(horizontal = 16.dp, vertical = 5.dp).bounceClick(0.98f) { open = h.id },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (streak > 0) t("🔥 {0} giorni di fila", streak) else t("Inizia oggi"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .bounceClick(0.85f) { Graph.scope.launch { Graph.together.check(h.id, !doneToday) } },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Check, t("Fatto"), tint = if (doneToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        week.forEach { ts ->
                            val on = checks[h.id]?.get(me)?.contains(dayCode(ts)) == true
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(Fmt.weekdayLetter(ts).take(1), fontSize = 10.sp, color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        h.members.filter { it != me }.take(4).forEach { pk ->
                            val ok = checks[h.id]?.get(pk)?.contains(today) == true
                            Box(Modifier.alpha(if (ok) 1f else 0.35f)) { Avatar(emojiOf(pk, people), 24.dp) }
                        }
                    }
                }
            }
        }
    }
    open?.let { id ->
        val h = list.firstOrNull { it.id == id }
        if (h == null) { open = null; return }
        val nm = names()
        MonoDialog(
            onDismissRequest = { open = null },
            title = { Text(h.emoji + "  " + h.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    h.members.sortedBy { if (it == me) 0 else 1 }.forEach { pk ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nm[pk] ?: t("Qualcuno"), modifier = Modifier.weight(1f))
                            Text(t("🔥 {0}", Graph.together.streak(h.id, pk)), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(t("Chiudi")) } },
            dismissButton = {
                TextButton(onClick = { open = null; Graph.scope.launch { Graph.together.leaveHabit(h.id) } }) {
                    Text(if (h.members.size > 1) t("Esci") else t("Elimina"))
                }
            },
        )
    }
}

/** create an album (a hidden group) and open it */
fun createAlbum(nav: NavHostController, name: String, emoji: String, friends: List<String>) {
    Graph.scope.launch {
        val id = Graph.messenger.createGroup(name, emoji, friends, idPrefix = Shard.ALBUM) ?: return@launch
        withContext(Dispatchers.Main) { nav.navigate(Routes.album(id)) }
    }
}
