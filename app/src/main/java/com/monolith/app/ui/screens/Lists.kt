package com.monolith.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.ListItem
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.EmptyState
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.sharedBoundsKey
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.monolith.app.ui.components.MonoDialog

@Composable
fun ListsPane(nav: NavHostController) {
    val lists by remember { Graph.db.lists().lists() }.collectAsState(initial = emptyList())
    val progress by remember { Graph.db.lists().progress() }.collectAsState(initial = emptyList())
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val byPk = people.associateBy { it.pubkey }
    val prog = progress.associateBy { it.listId }
    val myPub = Graph.identity.keys?.pub ?: ""

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp)) {
        if (lists.isEmpty()) {
            item(key = "empty") {
                EmptyState(t("Nessuna lista"), t("Crea una lista della spesa o delle cose da fare e condividila con gli amici."))
            }
        }
        items(lists, key = { it.id }) { l ->
            val p = prog[l.id]
            val total = p?.total ?: 0
            val done = p?.done ?: 0
            val frac by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, Motion.smooth(), label = "p")
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                    .sharedBoundsKey("list-" + l.id).bounceClick(0.97f) { nav.navigate(Routes.list(l.id)) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (total == 0) t("Vuota") else t("{0} di {1} completati", done, total),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            l.memberList().filter { it != myPub }.take(3).forEachIndexed { i, pk ->
                                Avatar(byPk[pk]?.emoji ?: "🙂", 26.dp, Modifier.offset(x = (-8 * i).dp))
                            }
                        }
                    }
                    if (total > 0) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ListDetailScreen(nav: NavHostController, id: String) {
    val isNew = id == "new"
    val list by remember(id) { Graph.db.lists().observe(id) }.collectAsState(initial = null)
    val items by remember(id) { Graph.db.lists().items(id) }.collectAsState(initial = emptyList())
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val byPk = people.associateBy { it.pubkey }
    var editing by rememberSaveable { mutableStateOf(isNew) }
    var title by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("📝") }
    var members by remember { mutableStateOf(setOf<String>()) }
    var picker by remember { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    val myPub = Graph.identity.keys?.pub ?: ""

    LaunchedEffect(list?.id) {
        list?.let { l -> title = l.title; emoji = l.emoji; members = l.memberList().filter { it != myPub }.toSet() }
    }

    Surface(Modifier.fillMaxSize().sharedBoundsKey(if (isNew) "list-new" else "list-$id"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                if (!editing) {
                    Text(list?.emoji ?: "", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(list?.title ?: "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, t("Modifica")) }
                    IconButton(onClick = { confirmLeave = true }) { Icon(Icons.Rounded.Logout, t("Esci dalla lista")) }
                } else {
                    Text(if (isNew) t("Nuova lista") else t("Modifica lista"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            Graph.scope.launch {
                                val newId = Graph.messenger.saveList(if (isNew) null else id, title, emoji, members.toList())
                                withContext(Dispatchers.Main) {
                                    if (isNew && newId != null) {
                                        nav.popBackStack()
                                        nav.navigate(Routes.list(newId))
                                    } else editing = false
                                }
                            }
                        },
                        enabled = title.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(t("Salva")) }
                }
            }
            AnimatedVisibility(editing) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.88f) { picker = true },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 28.sp) }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = title, onValueChange = { title = it.take(40) }, label = { Text(t("Nome della lista")) },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                    SectionLabel(t("Condividi con"), Modifier.padding(top = 12.dp), inset = false)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.filter { it.status == FriendStatus.FRIEND }.forEach { f ->
                            val sel = f.pubkey in members
                            FilterChip(
                                selected = sel,
                                onClick = { members = if (sel) members - f.pubkey else members + f.pubkey },
                                label = { Text(f.displayName) },
                                leadingIcon = { Text(f.emoji) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (!isNew) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (items.isEmpty()) {
                        item(key = "empty") { EmptyState(t("Lista vuota"), t("Aggiungi il primo elemento qui sotto.")) }
                    }
                    items(items, key = { it.id }) { item ->
                        ItemRow(item, byPk[item.by]?.displayName, item.by == myPub, Modifier.animateItem())
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newItem, onValueChange = { newItem = it.take(120) },
                            placeholder = { Text(t("Aggiungi un elemento")) }, singleLine = true,
                            modifier = Modifier.weight(1f), shape = CircleShape,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val tx = newItem
                                newItem = ""
                                if (tx.isNotBlank()) Graph.scope.launch { Graph.messenger.saveItem(id, null, text = tx.trim()) }
                            }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f) {
                                val tx = newItem
                                newItem = ""
                                if (tx.isNotBlank()) Graph.scope.launch { Graph.messenger.saveItem(id, null, text = tx.trim()) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Add, t("Aggiungi"), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
    if (confirmLeave) {
        MonoDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(t("Uscire dalla lista?")) },
            text = { Text(t("La lista verrà rimossa da questo telefono. Gli altri continueranno a vederla.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    Graph.scope.launch { Graph.messenger.leaveList(id) }
                    nav.popBackStack()
                }) { Text(t("Esci")) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
private fun ItemRow(item: ListItem, byName: String?, mine: Boolean, modifier: Modifier) {
    val bg by animateColorAsState(if (item.done) MaterialTheme.colorScheme.primary else Color.Transparent, label = "chk")
    val alpha by animateFloatAsState(if (item.done) 0.45f else 1f, label = "a")
    Row(
        modifier.fillMaxWidth().bounceClick(0.98f) { Graph.scope.launch { Graph.messenger.saveItem(item.listId, item.id, done = !item.done) } }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(bg).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (!mine && byName != null) Text(byName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { Graph.scope.launch { Graph.messenger.saveItem(item.listId, item.id, deleted = true) } }) {
            Icon(Icons.Rounded.Close, t("Rimuovi"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
