package com.monolith.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Message
import com.monolith.app.data.Scheduled
import com.monolith.app.data.Status
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

// ====================================================================== search

private fun highlight(text: String, q: String): AnnotatedString = buildAnnotatedString {
    val i = text.indexOf(q, ignoreCase = true)
    if (q.isBlank() || i < 0) { append(text); return@buildAnnotatedString }
    // show the match with a little context
    val from = (i - 40).coerceAtLeast(0)
    if (from > 0) append("…")
    append(text.substring(from, i))
    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    append(text.substring(i, i + q.length))
    pop()
    append(text.substring(i + q.length))
}

@Composable
fun SearchScreen(nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val groups by remember { Graph.db.groups().all() }.collectAsState(initial = emptyList())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) {
        delay(200)
        results = if (q.trim().length < 2) emptyList() else Graph.db.messages().search(q.trim())
    }
    val byPk = friends.associateBy { it.pubkey }
    val byGroup = groups.associateBy { "g:" + it.id }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            OutlinedTextField(
                q, { q = it.take(60) }, singleLine = true,
                placeholder = { Text(t("Cerca nei messaggi")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, t("Annulla")) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(focus),
                shape = CircleShape,
            )
        }
        if (q.trim().length >= 2 && results.isEmpty()) {
            Text(t("Nessun risultato"), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.id }) { m ->
                val g = byGroup[m.convId]
                val f = byPk[m.convId]
                val who = if (m.outgoing) t("Tu") else byPk[m.sender]?.displayName ?: t("Qualcuno")
                Row(
                    Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { nav.navigate(Routes.chat(m.convId, m.id)) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Avatar(g?.emoji ?: f?.emoji ?: "💬", 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g?.name ?: f?.displayName ?: "…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.chatStamp(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            buildAnnotatedString { append("$who: "); append(highlight(m.text, q.trim())) },
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================== 24-hour status

/** my status first, then friends with a status, then the ones online */
@Composable
fun StatusStrip(friends: List<Friend>, isOnline: (String) -> Boolean, onMine: () -> Unit, onFriend: (Friend, Status?) -> Unit) {
    val mine by Graph.statuses.mine.collectAsState()
    val statuses by Graph.statuses.friends.collectAsState()
    val profile by Graph.identity.profile.collectAsState()
    val last by remember { Graph.db.messages().lastMessages() }.collectAsState(initial = emptyList())
    val seen by com.monolith.app.net.Stories.seen.collectAsState()
    LaunchedEffect(Unit) { while (true) { Graph.statuses.refresh(); delay(60_000) } }
    val pals = friends.filter { it.status == FriendStatus.FRIEND }
    // latest story of each friend in the last 24 hours
    val storyAt = pals.associate { f ->
        f.pubkey to (last.firstOrNull { it.convId == com.monolith.app.net.Stories.convOf(f.pubkey) && it.sender == f.pubkey && it.ts > now() - 86_400_000L && (it.kind == "photo" || it.kind == "text") }?.ts ?: 0L)
    }
    val unseen = { pk: String -> (storyAt[pk] ?: 0L) > (seen[pk] ?: 0L) }
    val withStory = pals.filter { (storyAt[it.pubkey] ?: 0L) > 0L }.sortedByDescending { if (unseen(it.pubkey)) 1 else 0 }
    val withStatus = pals.filter { it !in withStory && statuses[it.pubkey]?.active == true }.sortedByDescending { statuses[it.pubkey]?.until ?: 0 }
    val onlineOnly = pals.filter { it !in withStory && statuses[it.pubkey]?.active != true && isOnline(it.pubkey) }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        item(key = "me") {
            Column(Modifier.width(66.dp).bounceClick(onClick = onMine), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(profile?.emoji ?: "🗿", 58.dp, ring = mine?.active == true)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        val m = mine
                        if (m != null && m.active) Text(m.emoji, fontSize = 12.sp) else Icon(Icons.Rounded.Add, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    mine?.takeIf { it.active }?.text?.ifBlank { null } ?: t("Il tuo stato"),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        items(withStory + withStatus + onlineOnly, key = { it.pubkey }) { f ->
            val st = statuses[f.pubkey]?.takeIf { it.active }
            Column(Modifier.width(66.dp).animateItem().bounceClick { onFriend(f, st) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(f.emoji, 58.dp, online = isOnline(f.pubkey), ring = unseen(f.pubkey) || (st != null && (storyAt[f.pubkey] ?: 0L) == 0L))
                    if (st != null) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(24.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) { Text(st.emoji, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(f.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val STATUS_EMOJI = listOf("😊", "😴", "💻", "🏃", "🎧", "📚", "🍕", "✈️", "🎉", "🤒", "❤️", "🌙")

@Composable
fun StatusSheet(onClose: () -> Unit) {
    val mine by Graph.statuses.mine.collectAsState()
    val cur = mine?.takeIf { it.active }
    var emoji by remember { mutableStateOf(cur?.emoji ?: "😊") }
    var text by remember { mutableStateOf(cur?.text ?: "") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).imePadding()) {
            Text(t("Il tuo stato"), style = MaterialTheme.typography.titleLarge)
            Text(
                if (cur != null) t("Visibile agli amici fino alle {0}", Fmt.time(cur.until)) else t("Una frase per oggi: la vedono i tuoi amici e sparisce dopo 24 ore."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STATUS_EMOJI.forEach { e ->
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(if (e == emoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bounceClick(0.85f) { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                text, { text = it.take(80) }, singleLine = true,
                label = { Text(t("Cosa stai facendo?")) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { Graph.scope.launch { Graph.messenger.setStatus(null, null) }; onClose() },
                    enabled = cur != null, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape,
                ) { Text(t("Togli")) }
                Button(
                    onClick = { Graph.scope.launch { Graph.messenger.setStatus(emoji, text) }; onClose() },
                    modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape,
                ) { Text(t("Pubblica")) }
            }
        }
    }
}

@Composable
fun StatusDialog(f: Friend, st: Status?, onClose: () -> Unit, onWrite: () -> Unit) {
    MonoDialog(
        onDismissRequest = onClose,
        icon = { Avatar(f.emoji, 56.dp) },
        title = { Text(f.displayName) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (st != null) {
                    Text(st.emoji, fontSize = 40.sp)
                    if (st.text.isNotBlank()) Text(st.text, style = MaterialTheme.typography.titleMedium)
                    Text(t("fino alle {0}", Fmt.time(st.until)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(t("Online adesso"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onClose(); onWrite() }) { Text(t("Scrivi")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(t("Chiudi")) } },
    )
}

// ====================================================================== scheduled messages

private fun at(daysFromToday: Int, h: Int, m: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, daysFromToday); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun ScheduleDialog(initialText: String, onClose: () -> Unit, onSchedule: (String, Long) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    var whenAt by remember { mutableStateOf<Long?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Long?>(null) }   // chosen day (UTC midnight) waiting for the time
    val options = buildList {
        add(t("Tra 1 ora") to (now() + 3_600_000L))
        at(0, 20, 0).takeIf { it > now() + 60_000 }?.let { add(t("Stasera alle 20") to it) }
        add(t("Domani alle 9") to at(1, 9, 0))
        add(t("Domani alle 20") to at(1, 20, 0))
    }
    MonoDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Rounded.Schedule, null) },
        title = { Text(t("Programma messaggio")) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text(t("Messaggio")) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (label, ts) ->
                        AssistChip(
                            onClick = { whenAt = ts }, label = { Text(label) },
                            colors = if (whenAt == ts) androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onPrimary,
                            ) else androidx.compose.material3.AssistChipDefaults.assistChipColors(),
                        )
                    }
                    AssistChip(onClick = { pickDate = true }, label = { Text(t("Scegli…")) })
                }
                whenAt?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(t("Partirà {0} alle {1}", Fmt.dayShort(it), Fmt.time(it)), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { whenAt?.let { onSchedule(text, it) }; onClose() }, enabled = text.isNotBlank() && (whenAt ?: 0) > now()) { Text(t("Programma")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(t("Annulla")) } },
    )
    if (pickDate) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { pickDate = false; pickTime = ds.selectedDateMillis }) { Text(t("Avanti")) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(t("Annulla")) } },
        ) { DatePicker(ds, showModeToggle = false) }
    }
    pickTime?.let { utcDay ->
        val ts = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        MonoDialog(
            onDismissRequest = { pickTime = null },
            title = { Text(t("A che ora?")) },
            text = { TimePicker(ts) },
            confirmButton = {
                TextButton(onClick = {
                    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
                    whenAt = Calendar.getInstance().apply {
                        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), ts.hour, ts.minute)
                    }.timeInMillis
                    pickTime = null
                }) { Text(t("Fatto")) }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
fun ScheduledBar(items: List<Scheduled>, onCancel: (Scheduled) -> Unit) {
    var open by remember { mutableStateOf(false) }
    AnimatedVisibility(items.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().bounceClick(0.98f) { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (items.size == 1) t("1 messaggio programmato · {0}", Fmt.dayShort(items[0].at) + " " + Fmt.time(items[0].at))
                        else t("{0} messaggi programmati", items.size),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    )
                    Text(if (open) t("Chiudi") else t("Vedi"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(open) {
                    Column {
                        items.sortedBy { it.at }.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(Fmt.dayShort(s.at) + " · " + Fmt.time(s.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onCancel(s) }) { Text(t("Annulla")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== pinned message

@Composable
fun PinnedBar(text: String?, onOpen: () -> Unit, onUnpin: () -> Unit) {
    AnimatedVisibility(text != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                Modifier.fillMaxWidth().bounceClick(0.99f, onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PushPin, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Messaggio fissato"), style = MaterialTheme.typography.labelMedium)
                    Text(text ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onUnpin) { Icon(Icons.Rounded.Close, t("Togli dalla cima")) }
            }
        }
    }
}
