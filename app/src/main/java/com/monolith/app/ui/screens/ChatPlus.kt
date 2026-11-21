package com.monolith.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.FriendStatus
import com.monolith.app.net.Shard
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import org.json.JSONObject
import java.util.Calendar

// ====================================================================== dates written in messages

/** finds "domani alle 8", "sabato alle 21:30", "12 dicembre", "il 3/5 alle 18" (and the English basics) */
object DateHints {
    private val WEEKDAYS = mapOf(
        "lunedì" to Calendar.MONDAY, "lunedi" to Calendar.MONDAY, "martedì" to Calendar.TUESDAY, "martedi" to Calendar.TUESDAY,
        "mercoledì" to Calendar.WEDNESDAY, "mercoledi" to Calendar.WEDNESDAY, "giovedì" to Calendar.THURSDAY, "giovedi" to Calendar.THURSDAY,
        "venerdì" to Calendar.FRIDAY, "venerdi" to Calendar.FRIDAY, "sabato" to Calendar.SATURDAY, "domenica" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY, "sunday" to Calendar.SUNDAY,
    )
    private val MONTHS = listOf(
        listOf("gennaio", "gen", "january", "jan"), listOf("febbraio", "feb", "february"), listOf("marzo", "mar", "march"),
        listOf("aprile", "apr", "april"), listOf("maggio", "mag", "may"), listOf("giugno", "giu", "june", "jun"),
        listOf("luglio", "lug", "july", "jul"), listOf("agosto", "ago", "august", "aug"), listOf("settembre", "set", "september", "sep", "sept"),
        listOf("ottobre", "ott", "october", "oct"), listOf("novembre", "nov", "november"), listOf("dicembre", "dic", "december", "dec"),
    )
    private val monthIndex = MONTHS.flatMapIndexed { i, l -> l.map { it to i } }.toMap()
    private val TIME_WORD = Regex("\\b(?:alle|ore|verso le|per le|dalle|at)\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\b")
    private val TIME_COLON = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
    private val DATE_NUM = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
    private val DATE_WORD = Regex("\\b(\\d{1,2})\\s+(" + monthIndex.keys.sortedByDescending { it.length }.joinToString("|") + ")\\b")
    private val WEEKDAY = Regex("\\b(" + WEEKDAYS.keys.joinToString("|") + ")\\b")

    data class Hint(val start: Long, val allDay: Boolean)

    fun find(text: String, nowMs: Long = now()): Hint? {
        if (text.length > 400) return null
        var s = text.lowercase()
        var hour = -1
        var minute = 0
        (TIME_WORD.find(s) ?: TIME_COLON.find(s))?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: -1
            val mi = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (h in 0..23 && mi in 0..59) {
                hour = h; minute = mi
                if (h in 1..11 && Regex("\\b(di sera|stasera|pm|del pomeriggio|tonight)\\b").containsMatchIn(s)) hour = h + 12
            }
            s = s.removeRange(m.range)
        }
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        var day: Calendar? = null
        fun base() = Calendar.getInstance().apply { timeInMillis = nowMs; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        when {
            Regex("\\bdopodomani\\b").containsMatchIn(s) -> day = base().apply { add(Calendar.DAY_OF_YEAR, 2) }
            Regex("\\b(domani|tomorrow)\\b").containsMatchIn(s) -> day = base().apply { add(Calendar.DAY_OF_YEAR, 1) }
            Regex("\\b(oggi|stasera|stamattina|oggi pomeriggio|today|tonight)\\b").containsMatchIn(s) -> day = base()
        }
        if (day == null) DATE_NUM.find(s)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt() - 1
            if (d in 1..31 && mo in 0..11) {
                day = base().apply {
                    m.groupValues[3].toIntOrNull()?.let { y -> set(Calendar.YEAR, if (y < 100) 2000 + y else y) }
                    set(Calendar.MONTH, mo); set(Calendar.DAY_OF_MONTH, d)
                    if (m.groupValues[3].isBlank() && timeInMillis < cal.timeInMillis - 86_400_000L) add(Calendar.YEAR, 1)
                }
            }
        }
        if (day == null) DATE_WORD.find(s)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = monthIndex[m.groupValues[2]] ?: return@let
            if (d in 1..31) day = base().apply {
                set(Calendar.MONTH, mo); set(Calendar.DAY_OF_MONTH, d)
                if (timeInMillis < cal.timeInMillis - 86_400_000L) add(Calendar.YEAR, 1)
            }
        }
        if (day == null) WEEKDAY.find(s)?.let { m ->
            val wd = WEEKDAYS[m.groupValues[1]] ?: return@let
            day = base().apply { do add(Calendar.DAY_OF_YEAR, 1) while (get(Calendar.DAY_OF_WEEK) != wd) }
        }
        if (day == null && hour < 0) return null
        val d = day ?: base().also { b ->
            // only a time: today, or tomorrow if that time has passed
            b.set(Calendar.HOUR_OF_DAY, hour); b.set(Calendar.MINUTE, minute)
            if (b.timeInMillis < nowMs) b.add(Calendar.DAY_OF_YEAR, 1)
            b.set(Calendar.HOUR_OF_DAY, 0); b.set(Calendar.MINUTE, 0)
        }
        val allDay = hour < 0
        if (!allDay) { d.set(Calendar.HOUR_OF_DAY, hour); d.set(Calendar.MINUTE, minute) }
        if (d.timeInMillis < nowMs - (if (allDay) 0L else 3_600_000L) && !(allDay && Fmt.startOfDay(nowMs) == d.timeInMillis)) return null
        if (d.timeInMillis - nowMs > 400L * 86_400_000L) return null
        return Hint(d.timeInMillis, allDay)
    }
}

/** the small "add to Agenda" pill under a message that mentions a date */
@Composable
fun DateChip(text: String, ink: Color, onAdd: (AgendaEvent) -> Unit) {
    val hint = remember(text) { DateHints.find(text) } ?: return
    var ask by remember { mutableStateOf(false) }
    Surface(
        shape = CircleShape, color = ink.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 6.dp).bounceClick(0.92f) { ask = true },
    ) {
        Text(
            "📅  " + Fmt.dayShort(hint.start) + (if (hint.allDay) "" else " · " + Fmt.time(hint.start)) + "  ·  " + t("Aggiungi"),
            color = ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (ask) {
        var title by remember { mutableStateOf(text.replace('\n', ' ').take(60)) }
        MonoDialog(
            onDismissRequest = { ask = false },
            title = { Text(t("Aggiungere in Agenda?")) },
            text = {
                Column {
                    Text(Fmt.dayShort(hint.start) + if (hint.allDay) " · " + t("Tutto il giorno") else " · " + Fmt.time(hint.start), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text(t("Titolo")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    onAdd(
                        AgendaEvent(
                            id = newId(), title = title.ifBlank { t("Evento") }, description = text.take(500), start = hint.start, allDay = hint.allDay,
                            emoji = "📅", reminders = if (hint.allDay) "" else "30", createdAt = now(), updatedAt = now(),
                        ),
                    )
                }) { Text(t("Aggiungi")) }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text(t("Annulla")) } },
        )
    }
}

// ====================================================================== forwarding

private data class Target(val conv: String, val emoji: String, val name: String)

@Composable
fun ForwardSheet(onDismiss: () -> Unit, onSend: (List<String>) -> Unit) {
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val groups by remember { Graph.db.groups().all() }.collectAsState(initial = emptyList())
    val shards by Graph.shards.all.collectAsState()
    var chosen by remember { mutableStateOf(setOf<String>()) }
    val targets = remember(friends, groups, shards) {
        friends.filter { it.status == FriendStatus.FRIEND }.sortedBy { it.displayName.lowercase() }.map { Target(it.pubkey, it.emoji, it.displayName) } +
            groups.filter { !Shard.isHiddenGroup(it.id) }.map { Target("g:" + it.id, it.emoji, it.name) } +
            shards.flatMap { s -> s.rooms.filter { !it.voice }.map { r -> Target(s.conv(r), s.emoji, s.name + " · #" + r.name) } }
    }
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Text(t("Inoltra a…"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(targets, key = { it.conv }) { tg ->
                    val sel = tg.conv in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).bounceClick(0.98f) { chosen = if (sel) chosen - tg.conv else chosen + tg.conv }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(tg.emoji, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(tg.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null)
                    }
                }
            }
            Spacer(Modifier.padding(6.dp))
            Button(onClick = { onSend(chosen.toList()) }, enabled = chosen.isNotEmpty(), shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text(if (chosen.size <= 1) t("Inoltra") else t("Inoltra a {0} chat", chosen.size))
            }
        }
    }
}

// ====================================================================== replies to stories

/** the story a message answers: a small thumbnail or the first words, above the reply */
@Composable
fun StoryRefCard(ref: JSONObject, out: Boolean, ink: Color) {
    val thumb = remember(ref.optString("th")) {
        ref.optString("th").takeIf { it.isNotBlank() }?.let { b ->
            runCatching { Base64.decode(b, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
        }
    }
    Row(
        Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.1f)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            if (thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp, 56.dp))
            else Text(if (ref.optString("k") == "voice") "♪" else "Aa", color = ink, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(170.dp)) {
            Text(if (out) t("Hai risposto alla sua storia") else t("Ha risposto alla tua storia"), color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
            val x = ref.optString("x")
            if (x.isNotBlank()) Text(x, color = ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** at the bottom of a friend's story: quick reactions and a reply that goes to the chat */
@Composable
fun StoryReplyBar(name: String, modifier: Modifier, onFocus: (Boolean) -> Unit, onSend: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            listOf("❤️", "😂", "😮", "😢", "🔥", "👏").forEach { e ->
                Text(e, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).bounceClick(0.8f) { onSend(e, true) }.padding(4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    text, { text = it.take(500) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text.trim(), false); text = "" } }),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(t("Rispondi a {0}…", name), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyLarge)
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
                )
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.White).bounceClick(0.88f) { onSend(text.trim(), false); text = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Send, t("Invia"), tint = Color.Black) }
            }
        }
    }
}
