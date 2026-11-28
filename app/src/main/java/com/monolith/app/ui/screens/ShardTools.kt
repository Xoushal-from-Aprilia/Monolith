package com.monolith.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.net.ModEntry
import com.monolith.app.net.Shard
import com.monolith.app.net.ShardRole
import com.monolith.app.net.ShardRoom
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Palettes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** the colour of a role, on light or dark */
@Composable
fun roleColor(key: String): Color {
    val dark = com.monolith.app.ui.theme.LocalDark.current
    if (key.isBlank()) return MaterialTheme.colorScheme.onSurfaceVariant
    val p = Palettes.byKey(key)
    return Color(if (dark) p.dark else p.light)
}

/** a small chip with a role's name in its colour */
@Composable
fun RoleChip(r: ShardRole) {
    val c = roleColor(r.color)
    Surface(shape = CircleShape, color = c.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(5.dp))
            Text(r.name, style = MaterialTheme.typography.labelSmall, color = c)
        }
    }
}

// ====================================================================== roles

/** the founder: create, change and delete the roles of the shard */
@Composable
fun RolesSheet(s: Shard, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<ShardRole?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MonoSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(t("Ruoli"), style = MaterialTheme.typography.titleLarge)
            Text(t("Ogni ruolo ha un nome, un colore e dei permessi. Li assegni dall'elenco dei membri."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (s.roles.isEmpty()) Text(t("Nessun ruolo ancora."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.roles.forEach { r ->
                val holders = s.roleOf.count { r.id in it.value }
                Row(Modifier.fillMaxWidth().bounceClick(0.98f) { editing = r }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(roleColor(r.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, color = roleColor(r.color))
                        val perms = listOfNotNull(if (r.mod) t("Modera") else null, if (r.ann) t("Scrive negli annunci") else null).joinToString(" · ").ifBlank { t("Solo il nome e il colore") }
                        Text(perms + " · " + t("{0} persone", holders), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.Edit, t("Modifica"), Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { editing = ShardRole("", "", "ocean") }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(t("Nuovo ruolo"))
            }
        }
    }
    editing?.let { r ->
        RoleDialog(r, onDismiss = { editing = null }, onDelete = if (r.id.isBlank()) null else ({
            editing = null
            Graph.scope.launch { Graph.shards.setRoles(s.id, s.roles.filter { it.id != r.id }) }
        })) { changed ->
            editing = null
            val list = if (r.id.isBlank()) s.roles + changed.copy(id = newId().take(8)) else s.roles.map { if (it.id == r.id) changed.copy(id = r.id) else it }
            Graph.scope.launch { Graph.shards.setRoles(s.id, list) }
        }
    }
}

@Composable
private fun RoleDialog(r: ShardRole, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (ShardRole) -> Unit) {
    var name by remember { mutableStateOf(r.name) }
    var color by remember { mutableStateOf(r.color) }
    var mod by remember { mutableStateOf(r.mod) }
    var ann by remember { mutableStateOf(r.ann) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r.id.isBlank()) t("Nuovo ruolo") else t("Modifica ruolo")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(24) }, label = { Text(t("Nome del ruolo")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(t("Colore"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palettes.ALL.filter { it.key != Palettes.MONO }.forEach { p ->
                        val sel = color == p.key
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(roleColor(p.key))
                                .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).bounceClick(0.85f) { color = p.key },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Modera"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Elimina messaggi, esclude, gestisce stanze e bacheca"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = mod, onCheckedChange = { mod = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Scrive negli annunci"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Anche nelle stanze dove scrivono solo fondatore e moderatori"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = ann, onCheckedChange = { ann = it })
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text(t("Elimina il ruolo")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ShardRole(r.id, name.trim(), color, mod, ann)) }, enabled = name.isNotBlank()) { Text(t("Salva")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

/** the founder: the roles of one person */
@Composable
fun AssignRolesDialog(s: Shard, pk: String, name: String, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(s.roleOf[pk].orEmpty().toSet()) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ruoli di {0}", name)) },
        text = {
            Column {
                if (s.roles.isEmpty()) Text(t("Crea prima un ruolo dal menu dello Shard."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.roles.forEach { r ->
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) { chosen = if (r.id in chosen) chosen - r.id else chosen + r.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.id in chosen, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        RoleChip(r)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); Graph.scope.launch { Graph.shards.assignRoles(s.id, pk, chosen.toList()) } }, enabled = s.roles.isNotEmpty()) { Text(t("Salva")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

// ====================================================================== order and categories

/** hold a room and drag it to move it; tap the category to change it */
@Composable
fun ReorderSheet(s: Shard, onDismiss: () -> Unit) {
    var order by remember { mutableStateOf(s.rooms) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var catFor by remember { mutableStateOf<ShardRoom?>(null) }
    val rowH = 60.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowPx = with(density) { rowH.toPx() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MonoSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(t("Ordina e categorie"), style = MaterialTheme.typography.titleLarge)
            Text(t("Tieni premuta una stanza e trascinala. Tocca la categoria per cambiarla."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                order.forEach { r ->
                    val isDragged = dragging == r.id
                    Row(
                        Modifier.fillMaxWidth().height(rowH)
                            .offset { IntOffset(0, if (isDragged) dragY.roundToInt() else 0) }
                            .graphicsLayer { if (isDragged) { scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 12f } }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .pointerInput(r.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = r.id; dragY = 0f },
                                    onDragEnd = { dragging = null; dragY = 0f },
                                    onDragCancel = { dragging = null; dragY = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                    val idx = order.indexOfFirst { it.id == r.id }
                                    val steps = (dragY / rowPx).toInt()
                                    if (steps != 0) {
                                        val target = (idx + steps).coerceIn(0, order.lastIndex)
                                        if (target != idx) {
                                            val l = order.toMutableList()
                                            val item = l.removeAt(idx)
                                            l.add(target, item)
                                            order = l
                                            dragY -= (target - idx) * rowPx
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DragIndicator, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text((if (r.voice) "🔊 " else "#") + r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.bounceClick(0.9f) { catFor = r }) {
                            Text(r.cat.ifBlank { t("Nessuna categoria") }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onDismiss(); Graph.scope.launch { Graph.shards.reorderRooms(s.id, order.map { it.id }) } }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(t("Salva l'ordine")) }
        }
    }
    catFor?.let { r ->
        val existing = s.rooms.map { it.cat }.filter { it.isNotBlank() }.distinct()
        var cat by remember(r.id) { mutableStateOf(r.cat) }
        MonoDialog(
            onDismissRequest = { catFor = null },
            title = { Text(t("Categoria di {0}", "#" + r.name)) },
            text = {
                Column {
                    OutlinedTextField(cat, { cat = it.take(30) }, label = { Text(t("Categoria")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (existing.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            existing.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    catFor = null
                    order = order.map { if (it.id == r.id) it.copy(cat = cat.trim()) else it }
                    Graph.scope.launch { Graph.shards.setRoomCategory(s.id, r.id, cat) }
                }) { Text(t("Salva")) }
            },
            dismissButton = { TextButton(onClick = { catFor = null }) { Text(t("Annulla")) } },
        )
    }
}

// ====================================================================== slow mode, rules, automatic moderation

@Composable
fun SlowDialog(s: Shard, r: ShardRoom, onDismiss: () -> Unit) {
    val options = listOf(0 to t("Spenta"), 5 to "5 s", 10 to "10 s", 30 to "30 s", 60 to "1 min", 300 to "5 min", 900 to "15 min", 3600 to "1 h")
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Modalità lenta")) },
        text = {
            Column {
                Text(t("Quanto aspetta ognuno tra un messaggio e l'altro in #{0}. Fondatore e moderatori no.", r.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                options.forEach { (sec, label) ->
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) { onDismiss(); Graph.scope.launch { Graph.shards.setSlow(s.id, r.id, sec) } }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = r.slow == sec, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Chiudi")) } },
    )
}

@Composable
fun RulesEditDialog(s: Shard, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(s.rules) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Regole")) },
        text = {
            Column {
                Text(t("Chi entra le legge e le accetta prima di poter scrivere. Se le cambi, vanno accettate di nuovo."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it.take(2000) }, label = { Text(t("Regole")) }, minLines = 5, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); Graph.scope.launch { Graph.shards.setRules(s.id, text) } }) { Text(t("Salva")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

@Composable
fun AutomodDialog(s: Shard, onDismiss: () -> Unit) {
    var words by remember { mutableStateOf(s.words.joinToString(", ")) }
    var spam by remember { mutableStateOf(s.spam) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Moderazione automatica")) },
        text = {
            Column {
                Text(t("Ogni telefono nasconde da solo i messaggi con queste parole; chi prova a scriverle viene avvisato."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(words, { words = it.take(4000) }, label = { Text(t("Parole non permesse, separate da virgole")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Ferma lo spam"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Nasconde lo stesso messaggio ripetuto e chi ne manda più di 5 in 10 secondi"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = spam, onCheckedChange = { spam = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                Graph.scope.launch { Graph.shards.setAutomod(s.id, words.split(',', '\n').map { it.trim() }, spam) }
            }) { Text(t("Salva")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

/** what the founder and the moderators did */
@Composable
fun ModLogSheet(s: Shard, names: Map<String, Friend>, onDismiss: () -> Unit) {
    val me = Graph.identity.keys?.pub
    fun who(pk: String) = if (pk == me) t("Tu") else names[pk]?.displayName ?: t("Qualcuno")
    fun line(e: ModEntry): String = when (e.k) {
        "del" -> t("{0} ha eliminato un messaggio di {1} in {2}", who(e.by), who(e.who), e.x)
        "ban" -> t("{0} ha escluso {1}", who(e.by), who(e.who))
        "unban" -> t("{0} ha fatto rientrare {1}", who(e.by), who(e.who))
        "kick" -> t("{0} ha tolto {1} dallo shard", who(e.by), who(e.who))
        "mod" -> t("{0} ha reso {1} moderatore", who(e.by), who(e.who))
        "unmod" -> t("{0} ha tolto {1} dai moderatori", who(e.by), who(e.who))
        "role" -> if (e.x.isBlank()) t("{0} ha tolto i ruoli a {1}", who(e.by), who(e.who)) else t("{0} ha dato a {1} i ruoli: {2}", who(e.by), who(e.who), e.x)
        "slow" -> t("{0} ha cambiato la modalità lenta: {1}", who(e.by), e.x)
        "words" -> t("{0} ha cambiato la moderazione automatica", who(e.by))
        "rules" -> t("{0} ha cambiato le regole", who(e.by))
        else -> e.k
    }
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(Modifier.heightIn(max = 560.dp).padding(bottom = 24.dp)) {
            item { Text(t("Registro della moderazione"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp)) }
            if (s.log.isEmpty()) item { Text(t("Ancora niente."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
            items(s.log, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(line(e), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.chatStamp(e.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ====================================================================== statistics

private class ShardStats(val members: Int, val active7: Int, val messages30: Int, val perDay: List<Int>, val rooms: List<Pair<String, Int>>, val top: List<Pair<String, Int>>)

/** the founder and the moderators: how the shard is going (counted on this phone) */
@Composable
fun ShardStatsSheet(s: Shard, names: Map<String, Friend>, onDismiss: () -> Unit) {
    val stats by produceState<ShardStats?>(null, s.id) {
        val since = now() - 30L * 86_400_000L
        val convs = s.rooms.filter { !it.voice }.associateBy { s.conv(it) }
        val msgs = Graph.db.messages().since(since).filter { it.convId in convs && it.kind != "system" }
        val day = 86_400_000L
        val start = Fmt.startOfDay(now()) - 13 * day
        val perDay = (0 until 14).map { i -> msgs.count { it.ts >= start + i * day && it.ts < start + (i + 1) * day } }
        val active7 = msgs.filter { it.ts > now() - 7 * day }.map { it.sender }.distinct().size
        val rooms = msgs.groupBy { it.convId }.map { (c, l) -> ("#" + (convs[c]?.name ?: "")) to l.size }.sortedByDescending { it.second }.take(5)
        val top = msgs.groupBy { it.sender }.map { (pk, l) -> pk to l.size }.sortedByDescending { it.second }.take(5)
        val members = if (s.open) msgs.map { it.sender }.distinct().size else s.members.size
        value = ShardStats(members, active7, msgs.size, perDay, rooms, top)
    }
    val me = Graph.identity.keys?.pub
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(t("Statistiche"), style = MaterialTheme.typography.titleLarge)
            Text(t("Ultimi 30 giorni, contati su questo telefono"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val st = stats ?: return@Column
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(if (s.open) t("Hanno scritto") else t("Membri"), st.members.toString(), Modifier.weight(1f))
                StatBox(t("Attivi in 7 giorni"), st.active7.toString(), Modifier.weight(1f))
                StatBox(t("Messaggi"), st.messages30.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text(t("Messaggi al giorno, ultime 2 settimane"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val ink = MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val maxV = (st.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
                val gap = 6.dp.toPx()
                val bw = (size.width - gap * (st.perDay.size - 1)) / st.perDay.size
                st.perDay.forEachIndexed { i, v ->
                    val x = i * (bw + gap)
                    drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 3))
                    val h = size.height * v / maxV
                    if (v > 0) drawRoundRect(ink, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
                }
            }
            if (st.rooms.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(t("Stanze più vive"), inset = false)
                st.rooms.forEach { (name, n) -> StatRow(name, n, st.rooms.first().second) }
            }
            if (st.top.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionLabel(t("Chi scrive di più"), inset = false)
                st.top.forEach { (pk, n) -> StatRow(if (pk == me) t("Tu") else names[pk]?.displayName ?: t("Qualcuno"), n, st.top.first().second) }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun StatRow(label: String, n: Int, max: Int) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(n.toFloat() / max.coerceAtLeast(1)).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
