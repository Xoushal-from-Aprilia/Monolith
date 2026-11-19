package com.monolith.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.InviteState
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.BigTitle
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.EmptyState
import com.monolith.app.ui.components.Pill
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.sharedBoundsKey
import com.monolith.app.ui.theme.Grotesk
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

private const val DAY = 86_400_000L

private fun dayOffset(base: Long, days: Int): Long =
    Calendar.getInstance().apply { timeInMillis = Fmt.startOfDay(base); add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

private fun occursOn(e: AgendaEvent, day: Long): Boolean {
    val s = Fmt.startOfDay(e.start)
    val end = e.end?.let { Fmt.startOfDay(it) } ?: s
    return day in s..maxOf(s, end)
}

@Composable
fun AgendaScreen(nav: NavHostController) {
    val events by remember { Graph.db.agenda().all() }.collectAsState(initial = emptyList())
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val profile by Graph.identity.profile.collectAsState()
    var selected by rememberSaveable { mutableLongStateOf(Fmt.startOfDay(now())) }
    var monthOpen by rememberSaveable { mutableStateOf(false) }
    val today = Fmt.startOfDay(now())

    val visible = events.filter { it.inviteState != InviteState.DECLINED }
    val pending = visible.filter { it.inviteState == InviteState.PENDING && it.start > now() - DAY }
    val confirmed = visible.filter { it.inviteState != InviteState.PENDING }
    val dayEvents = confirmed.filter { occursOn(it, selected) }.sortedBy { it.start }
    val upcoming = confirmed.filter { Fmt.startOfDay(it.start) > selected }.sortedBy { it.start }.take(8)
    val eventDays = remember(confirmed) { confirmed.map { Fmt.startOfDay(it.start) }.toSet() }
    val names = remember(friends) { friends.associateBy { it.pubkey } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BigTitle("Agenda", Modifier.weight(1f), subtitle = Fmt.dayLong(selected))
                    Avatar(
                        profile?.emoji ?: "🗿", 44.dp,
                        Modifier.padding(end = 20.dp).sharedBoundsKey("me-avatar").bounceClick { nav.navigate("settings") },
                    )
                }
            }
            item(key = "month") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        Fmt.monthYear(selected),
                        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                        label = "month",
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    ) { m -> Text(m, style = MaterialTheme.typography.titleMedium) }
                    AnimatedVisibility(selected != today, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(onClick = { selected = today }) { Text("Oggi") }
                    }
                    IconButton(onClick = { monthOpen = !monthOpen }) {
                        val rot by animateFloatAsState(if (monthOpen) 180f else 0f, Motion.bouncy(), label = "rot")
                        Icon(Icons.Rounded.ExpandMore, "Mese", Modifier.graphicsLayer { rotationZ = rot })
                    }
                }
            }
            item(key = "calendar") {
                Column {
                    AnimatedVisibility(!monthOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        WeekStrip(selected, today, eventDays) { selected = it }
                    }
                    AnimatedVisibility(monthOpen, enter = expandVertically(Motion.smooth()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        MonthGrid(selected, today, eventDays) { selected = it }
                    }
                }
            }
            if (pending.isNotEmpty()) {
                item(key = "invLabel") { SectionLabel("Inviti", Modifier.padding(top = 12.dp)) }
                items(pending, key = { "inv" + it.id }) { e ->
                    InviteCard(e, names[e.owner]?.displayName ?: "Un amico", Modifier.animateItem()) { yes ->
                        Graph.scope.launch { Graph.messenger.respondInvite(e.id, yes) }
                    }
                }
            }
            item(key = "dayLabel") {
                SectionLabel(if (selected == today) "Oggi" else Fmt.dayShort(selected), Modifier.padding(top = 12.dp))
            }
            if (dayEvents.isEmpty()) {
                item(key = "empty") {
                    EmptyState("Giornata libera", "Tocca + per aggiungere un evento, un promemoria o invitare un amico.", Modifier.animateItem())
                }
            } else {
                items(dayEvents, key = { "d" + it.id }) { e ->
                    EventCard(e, names, Modifier.animateItem()) { nav.navigate(Routes.event(e.id)) }
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "upLabel") { SectionLabel("In arrivo", Modifier.padding(top = 16.dp)) }
                items(upcoming, key = { "u" + it.id }) { e ->
                    EventCard(e, names, Modifier.animateItem(), showDate = true) { nav.navigate(Routes.event(e.id)) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate(Routes.event("new", selected)) },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Evento") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).sharedBoundsKey("event-new"),
        )
    }
}

@Composable
private fun WeekStrip(selected: Long, today: Long, eventDays: Set<Long>, onSelect: (Long) -> Unit) {
    val back = 60
    val days = remember(today) { (0 until 240).map { dayOffset(today, it - back) } }
    val index = days.indexOfFirst { it == selected }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))
    LaunchedEffect(selected) {
        val i = days.indexOfFirst { it == selected }
        if (i >= 0) state.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days, key = { it }) { d ->
            DayCell(d, d == selected, d == today, d in eventDays, Modifier.width(52.dp)) { onSelect(d) }
        }
    }
}

@Composable
private fun DayCell(day: Long, selected: Boolean, isToday: Boolean, hasEvents: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        tween(260), label = "bg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tween(260), label = "fg",
    )
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, Motion.bouncy(), label = "scale")
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
            .bounceClick(0.9f, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Fmt.weekdayLetter(day), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
        Spacer(Modifier.height(2.dp))
        Text(Fmt.dayNum(day), fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = fg)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (hasEvents) fg else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun MonthGrid(selected: Long, today: Long, eventDays: Set<Long>, onSelect: (Long) -> Unit) {
    var cursor by remember { mutableLongStateOf(selected) }
    val cal = Calendar.getInstance().apply { timeInMillis = cursor; set(Calendar.DAY_OF_MONTH, 1) }
    val first = Fmt.startOfDay(cal.timeInMillis)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday first
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { cursor = Calendar.getInstance().apply { timeInMillis = first; add(Calendar.MONTH, -1) }.timeInMillis }) {
                Icon(Icons.Rounded.KeyboardArrowLeft, "Mese precedente")
            }
            AnimatedContent(Fmt.monthYear(first), label = "mg", modifier = Modifier.weight(1f)) {
                Text(it, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            IconButton(onClick = { cursor = Calendar.getInstance().apply { timeInMillis = first; add(Calendar.MONTH, 1) }.timeInMillis }) {
                Icon(Icons.Rounded.KeyboardArrowRight, "Mese successivo")
            }
        }
        Row {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = lead + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 0 until daysInMonth) {
                            val d = dayOffset(first, idx)
                            val sel = d == selected
                            val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, label = "c")
                            Column(
                                Modifier.fillMaxSize().clip(CircleShape).background(bg)
                                    .then(if (d == today && !sel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .bounceClick(0.85f) { onSelect(d) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    (idx + 1).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (sel || d == today) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (d in eventDays) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun timeText(e: AgendaEvent): String = when {
    e.allDay -> "Tutto il giorno"
    e.end != null && e.end > e.start -> Fmt.time(e.start) + " – " + Fmt.time(e.end)
    else -> Fmt.time(e.start)
}

@Composable
private fun EventCard(e: AgendaEvent, names: Map<String, Friend>, modifier: Modifier = Modifier, showDate: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().sharedBoundsKey("event-" + e.id).bounceClick(0.97f, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Text(e.emoji, fontSize = 26.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (showDate) Fmt.dayShort(e.start) + " · " else "") + timeText(e),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (e.description.isNotBlank()) {
                    Text(e.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.owner.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Pill("da " + (names[e.owner]?.displayName ?: "un amico"))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (e.reminderMin >= 0) Icon(Icons.Rounded.NotificationsActive, "Promemoria", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val inv = e.inviteeList()
                if (inv.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        inv.take(3).forEachIndexed { i, pk ->
                            Avatar(names[pk]?.emoji ?: "🙂", 24.dp, Modifier.offset(x = (-8 * i).dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteCard(e: AgendaEvent, from: String, modifier: Modifier, onAnswer: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium)
                    Text("$from ti invita · ${Fmt.dayShort(e.start)} · ${timeText(e)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (e.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(e.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onAnswer(true) }, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Partecipo") }
                OutlinedButton(onClick = { onAnswer(false) }, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Non posso") }
            }
        }
    }
}

// ====================================================================== editor

private val REMINDERS = listOf(-1 to "Nessuno", 0 to "All'ora", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 ora", 1440 to "1 giorno")

private fun utcMidnight(local: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun withDate(local: Long, utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), l.get(Calendar.HOUR_OF_DAY), l.get(Calendar.MINUTE))
    }.timeInMillis
}

private fun withTime(local: Long, h: Int, m: Int): Long =
    Calendar.getInstance().apply { timeInMillis = local; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

private fun defaultStart(date: Long): Long {
    val nowCal = Calendar.getInstance()
    val base = if (date > 0) date else Fmt.startOfDay(now())
    val h = if (Fmt.sameDay(base, now())) (nowCal.get(Calendar.HOUR_OF_DAY) + 1).coerceAtMost(23) else 9
    return withTime(base, h, 0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorScreen(nav: NavHostController, id: String, date: Long) {
    val isNew = id == "new"
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    var loaded by remember { mutableStateOf<AgendaEvent?>(null) }
    var ready by remember { mutableStateOf(isNew) }

    var title by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("📌") }
    var start by rememberSaveable { mutableLongStateOf(defaultStart(date)) }
    var end by rememberSaveable { mutableLongStateOf(0L) }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableIntStateOf(15) }
    var invitees by remember { mutableStateOf(setOf<String>()) }

    var picker by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableIntStateOf(0) } // 1 start, 2 end
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!isNew) {
            val e = Graph.db.agenda().get(id)
            if (e == null) {
                nav.popBackStack(); return@LaunchedEffect
            }
            loaded = e
            title = e.title; desc = e.description; notes = e.notes; emoji = e.emoji
            start = e.start; end = e.end ?: 0L; allDay = e.allDay; reminder = e.reminderMin
            invitees = e.inviteeList().toSet()
            ready = true
        }
    }

    val invited = loaded?.let { it.owner.isNotEmpty() } ?: false
    val canSave = title.isNotBlank() && ready

    fun save() {
        val base = loaded
        val e = AgendaEvent(
            id = base?.id ?: newId(),
            title = title.trim(),
            description = desc.trim(),
            notes = notes.trim(),
            start = if (allDay) Fmt.startOfDay(start) else start,
            end = if (!allDay && end > start) end else null,
            allDay = allDay,
            emoji = emoji,
            reminderMin = reminder,
            owner = base?.owner ?: "",
            invitees = if (invited) base?.invitees ?: "" else invitees.joinToString(","),
            rsvp = base?.rsvp ?: "{}",
            inviteState = base?.inviteState ?: InviteState.MINE,
            createdAt = base?.createdAt ?: now(),
            updatedAt = now(),
        )
        Graph.scope.launch { Graph.messenger.saveEvent(e, notifyInvitees = !invited) }
        nav.popBackStack()
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().sharedBoundsKey(if (isNew) "event-new" else "event-$id"),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Chiudi") }
                Text(
                    when {
                        isNew -> "Nuovo evento"
                        invited -> "Invito"
                        else -> "Modifica evento"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Elimina") }
                Button(onClick = { save() }, enabled = canSave, shape = CircleShape, modifier = Modifier.padding(end = 8.dp)) { Text("Salva") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .bounceClick(0.88f, enabled = !invited) { picker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(emoji, transitionSpec = { (fadeIn() + androidx.compose.animation.scaleIn(Motion.bouncy(), 0.5f)) togetherWith fadeOut() }, label = "emoji") {
                            Text(it, fontSize = 32.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.take(80) },
                        readOnly = invited,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) Text("Titolo", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                inner()
                            }
                        },
                    )
                }
                Text(
                    if (invited) "Emoji del promemoria scelta da chi ti ha invitato" else "L'emoji sarà l'icona della notifica",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(20.dp))
                EditorRow(Icons.Rounded.Event, "Data", Fmt.dayLong(start), enabled = !invited) { dateDialog = true }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(40.dp))
                    Text("Tutto il giorno", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = allDay, onCheckedChange = { if (!invited) allDay = it }, enabled = !invited,
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                    )
                }
                AnimatedVisibility(!allDay) {
                    Column {
                        EditorRow(Icons.Rounded.Schedule, "Inizio", Fmt.time(start), enabled = !invited) { timeDialog = 1 }
                        EditorRow(Icons.Rounded.Schedule, "Fine", if (end > start) Fmt.time(end) else "Aggiungi", enabled = !invited, faded = true) { timeDialog = 2 }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (reminder >= 0) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Text("Promemoria", style = MaterialTheme.typography.titleSmall)
                }
                FlowRow(Modifier.padding(start = 40.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMINDERS.forEach { (m, label) ->
                        FilterChip(
                            selected = reminder == m,
                            onClick = { reminder = m },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it }, readOnly = invited,
                    label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Note personali") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, shape = MaterialTheme.shapes.medium,
                )
                if (!invited) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.People, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(18.dp))
                        Text("Invita amici", style = MaterialTheme.typography.titleSmall)
                    }
                    if (friends.isEmpty()) {
                        Text(
                            "Aggiungi amici dalla scheda Amici per invitarli ai tuoi eventi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 6.dp),
                        )
                    }
                    val rsvp = remember(loaded) { runCatching { JSONObject(loaded?.rsvp ?: "{}") }.getOrDefault(JSONObject()) }
                    FlowRow(Modifier.padding(start = 40.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.filter { it.status == FriendStatus.FRIEND }.forEach { f ->
                            val sel = f.pubkey in invitees
                            val answer = rsvp.optString(f.pubkey)
                            FilterChip(
                                selected = sel,
                                onClick = { invitees = if (sel) invitees - f.pubkey else invitees + f.pubkey },
                                label = { Text(f.displayName + when (answer) { "yes" -> " ✓"; "no" -> " ✕"; else -> "" }) },
                                leadingIcon = { Text(f.emoji) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                } else {
                    val e = loaded!!
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(e.inviteState, label = "rsvp") { st ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (st != InviteState.ACCEPTED) {
                                Button(onClick = {
                                    Graph.scope.launch { Graph.messenger.respondInvite(e.id, true) }
                                    loaded = e.copy(inviteState = InviteState.ACCEPTED)
                                }, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Partecipo") }
                            } else {
                                Pill("Partecipi ✓", Modifier.weight(1f).padding(vertical = 8.dp), strong = true)
                            }
                            OutlinedButton(onClick = {
                                Graph.scope.launch { Graph.messenger.respondInvite(e.id, false) }
                                nav.popBackStack()
                            }, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Non posso") }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }

    if (dateDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnight(start))
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val newStart = withDate(start, utc)
                        if (end > start) end = newStart + (end - start)
                        start = newStart
                    }
                    dateDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Annulla") } },
        ) { DatePicker(state = state) }
    }

    if (timeDialog != 0) {
        val ref = if (timeDialog == 1 || end <= start) start else end
        val c = Calendar.getInstance().apply { timeInMillis = if (timeDialog == 2 && end <= start) start + 3_600_000 else ref }
        val state = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), is24Hour = true)
        Dialog(onDismissRequest = { timeDialog = 0 }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (timeDialog == 1) "Ora di inizio" else "Ora di fine", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    TimePicker(state = state)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (timeDialog == 2 && end > start) TextButton(onClick = { end = 0L; timeDialog = 0 }) { Text("Rimuovi") }
                        TextButton(onClick = { timeDialog = 0 }) { Text("Annulla") }
                        TextButton(onClick = {
                            if (timeDialog == 1) {
                                val dur = if (end > start) end - start else 0L
                                start = withTime(start, state.hour, state.minute)
                                if (dur > 0) end = start + dur
                            } else {
                                var e = withTime(start, state.hour, state.minute)
                                if (e <= start) e += DAY
                                end = e
                            }
                            timeDialog = 0
                        }) { Text("OK") }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (invited) "Rimuovere l'invito?" else "Eliminare l'evento?") },
            text = { Text(if (!invited && invitees.isNotEmpty()) "Gli amici invitati riceveranno l'annullamento." else "Non potrai annullare questa azione.") },
            confirmButton = {
                TextButton(onClick = {
                    loaded?.let { e -> Graph.scope.launch { Graph.messenger.deleteEvent(e) } }
                    confirmDelete = false
                    nav.popBackStack()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun EditorRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, enabled: Boolean = true, faded: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).bounceClick(0.98f, enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (faded && value == "Aggiungi") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
