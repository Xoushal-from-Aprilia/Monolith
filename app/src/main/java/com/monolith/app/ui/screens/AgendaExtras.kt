package com.monolith.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.Friend
import com.monolith.app.data.Place
import com.monolith.app.data.PlaceNote
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.NavRequest
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick

private val HOUR: Dp = 56.dp

/** one day laid out hour by hour, events as blocks; overlapping ones share the width */
@Composable
fun DayTimeline(events: List<Pair<AgendaEvent, Long>>, day: Long, onOpen: (AgendaEvent) -> Unit) {
    val allDay = events.filter { it.first.allDay }
    val timed = events.filter { !it.first.allDay }
        .map { (e, occ) ->
            val dur = if (e.end != null && e.end > e.start) e.end - e.start else 3_600_000L
            Triple(e, occ, (occ + dur).coerceAtMost(day + 86_400_000L))
        }
        .sortedBy { it.second }
    // greedy lanes
    val laneEnds = ArrayList<Long>()
    val lanes = timed.map { (_, s, end) ->
        val i = laneEnds.indexOfFirst { it <= s }
        if (i >= 0) { laneEnds[i] = end; i } else { laneEnds.add(end); laneEnds.size - 1 }
    }
    val laneCount = maxOf(1, laneEnds.size)
    val isToday = Fmt.startOfDay(now()) == day

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        allDay.forEach { (e, _) ->
            Surface(
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).bounceClick(0.98f) { onOpen(e) },
            ) {
                Text("${e.emoji}  ${e.title}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (allDay.isNotEmpty()) Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(HOUR * 24)) {
            val gutter = 44.dp
            val laneW = (maxWidth - gutter) / laneCount
            // hour grid
            for (h in 0 until 24) {
                Row(Modifier.offset(y = HOUR * h).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        "%02d".format(h), modifier = Modifier.width(gutter).offset(y = (-7).dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            timed.forEachIndexed { idx, (e, s, end) ->
                val top = HOUR * ((s - day).coerceAtLeast(0L) / 3_600_000f)
                val h = (HOUR * ((end - s.coerceAtLeast(day)) / 3_600_000f)).coerceAtLeast(28.dp)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .offset(x = gutter + laneW * lanes[idx], y = top)
                        .width(laneW - 4.dp).height(h - 2.dp)
                        .bounceClick(0.97f) { onOpen(e) },
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(
                            "${e.emoji} ${e.title}", color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (h > 44.dp) {
                            Text(
                                Fmt.time(s) + " – " + Fmt.time(end), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            if (isToday) {
                val y = HOUR * ((now() - day) / 3_600_000f)
                Row(Modifier.offset(y = y - 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(gutter - 4.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.onSurface))
                }
            }
        }
    }
}

@Composable
fun BirthdayCard(f: Friend, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().bounceClick(0.97f, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Text("🎂", fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Compleanno di {0}", f.displayName), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("Tocca per fare gli auguri"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Avatar(f.emoji, 32.dp)
        }
    }
}

@Composable
fun PlaceNoteRow(n: PlaceNote, place: Place?, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(place?.emoji ?: "📍", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t("Quando arrivi a {0}", place?.name ?: "…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { Graph.places.removeNote(n.id) }) { Text(t("Togli")) }
        }
    }
}

/** new "remind me when I get to…" from the agenda */
@Composable
fun PlaceNoteDialog(places: List<Place>, onClose: () -> Unit) {
    var chosen by remember { mutableStateOf(places.firstOrNull()?.id) }
    var text by remember { mutableStateOf("") }
    MonoDialog(
        onDismissRequest = onClose,
        title = { Text(t("Promemoria su luogo")) },
        text = {
            Column {
                if (places.isEmpty()) {
                    Text(t("Crea prima un luogo dalla Mappa, anche solo per te."))
                } else {
                    OutlinedTextField(
                        text, { text = it.take(120) }, singleLine = true,
                        label = { Text(t("Ricordami di…")) }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(t("Quando arrivo a"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        places.forEach { p ->
                            FilterChip(
                                selected = chosen == p.id, onClick = { chosen = p.id },
                                label = { Text(p.name) }, leadingIcon = { Text(p.emoji) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (places.isEmpty()) {
                TextButton(onClick = { onClose(); NavBus.requests.value = NavRequest(tab = 2) }) { Text(t("Vai alla mappa")) }
            } else {
                TextButton(
                    onClick = { chosen?.let { Graph.places.addNote(it, text) }; onClose() },
                    enabled = text.isNotBlank() && chosen != null,
                ) { Text(t("Salva")) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(t("Annulla")) } },
    )
}
