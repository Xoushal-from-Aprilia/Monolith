package com.monolith.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Amber = Color(0xFFF5C66A)
private val Danger = Color(0xFFFF8A80)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF0A0A0A),
                    secondary = Amber, onSecondary = Color(0xFF0A0A0A),
                    background = Color(0xFF0A0A0A), onBackground = Color(0xFFF2F2F2),
                    surface = Color(0xFF0A0A0A), onSurface = Color(0xFFF2F2F2),
                    surfaceVariant = Color(0xFF1F1F1F), onSurfaceVariant = Color(0xFFA3A3A3),
                    surfaceContainerLow = Color(0xFF111111), surfaceContainer = Color(0xFF151515),
                    surfaceContainerHigh = Color(0xFF1D1D1D), surfaceContainerHighest = Color(0xFF262626),
                    outline = Color(0xFF4D4D4D), outlineVariant = Color(0xFF2A2A2A),
                    secondaryContainer = Color(0xFF2B2B2B), onSecondaryContainer = Color(0xFFF2F2F2),
                    error = Danger,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

// ====================================================================== helpers

private fun copy(ctx: Context, label: String, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copiato", Toast.LENGTH_SHORT).show()
}

private fun share(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

private fun ago(sec: Long): String {
    if (sec <= 0) return "—"
    val d = System.currentTimeMillis() / 1000 - sec
    return when {
        d < 120 -> "adesso"
        d < 3600 -> "${d / 60} min fa"
        d < 86400 -> "${d / 3600} h fa"
        d < 30 * 86400 -> "${d / 86400} g fa"
        else -> SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).format(Date(sec * 1000))
    }
}

private fun date(sec: Long) = if (sec <= 0) "—" else SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN).format(Date(sec * 1000))

private fun grouped(hex: String) = hex.chunked(4).chunked(4).joinToString("\n") { it.joinToString(" ") }

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), content = content)
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Title(text: String, sub: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 30.sp, fontWeight = FontWeight.Black)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

// ====================================================================== root

@Composable
private fun Root() {
    val keys by Dev.keys.collectAsState()
    var keyIntro by rememberSaveable { mutableStateOf(false) }
    when {
        keys == null -> SetupScreen { keyIntro = true }
        keyIntro -> KeyIntro { keyIntro = false }
        else -> MainScreen()
    }
}

@Composable
private fun SetupScreen(onCreated: () -> Unit) {
    var importing by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("MONOLITH", letterSpacing = 6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text("Dashboard sviluppatore", fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Da qui vedi gli account, mandi annunci a tutti, segnali gli aggiornamenti e blocchi chi non rispetta le regole. " +
                "Tutto è firmato con la tua chiave sviluppatore: le app di Monolith seguono solo i comandi firmati da lei.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        if (!importing) {
            Button(onClick = { Dev.createKey(); onCreated() }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Crea la chiave sviluppatore") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { importing = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Ho già una chiave") }
        } else {
            OutlinedTextField(code, { code = it; error = false }, label = { Text("Codice segreto") }, minLines = 3, isError = error, modifier = Modifier.fillMaxWidth())
            if (error) Text("Codice non valido", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { if (!Dev.importKey(code)) error = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Usa questa chiave") }
            TextButton(onClick = { importing = false }) { Text("Indietro") }
        }
    }
}

@Composable
private fun KeyIntro(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val keys by Dev.keys.collectAsState()
    val pub = keys?.pub ?: ""
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("La tua chiave è pronta", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(20.dp))
        Card {
            Label("1 · Codice pubblico")
            Spacer(Modifier.height(6.dp))
            Text("Mandalo a Claude: lo mette nel progetto di Monolith e da lì tutte le app si fidano dei tuoi comandi.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            SelectionContainer { Text(grouped(pub), fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy(ctx, "codice pubblico", pub) }, shape = CircleShape) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copia") }
                OutlinedButton(onClick = { share(ctx, "Codice pubblico della dashboard di Monolith:\n$pub") }, shape = CircleShape) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Condividi") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card {
            Label("2 · Codice segreto")
            Spacer(Modifier.height(6.dp))
            Text(
                "Salvalo in un posto sicuro (un gestore di password, per esempio) e non darlo a nessuno. " +
                    "Serve se reinstalli la dashboard o cambi telefono: senza, dovresti creare una chiave nuova.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { copy(ctx, "codice segreto", Dev.secretCode()); saved = true }, shape = CircleShape) { Icon(Icons.Rounded.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copia il codice segreto") }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, enabled = saved, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (saved) "Apri la dashboard" else "Prima copia il codice segreto") }
    }
}

@Composable
private fun MainScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val inbox by Dev.inbox.collectAsState()
    val unread = inbox.count { !it.read }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                listOf<Triple<String, ImageVector, Int>>(
                    Triple("Panoramica", Icons.Rounded.Insights, 0), Triple("Utenti", Icons.Rounded.People, 0),
                    Triple("Messaggi", Icons.Rounded.Inbox, unread), Triple("Pubblica", Icons.Rounded.Campaign, 0),
                ).forEachIndexed { i, (label, icon, badge) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i }, label = { Text(label) },
                        icon = { BadgedBox(badge = { if (badge > 0) Badge(containerColor = Amber, contentColor = Color.Black) { Text("$badge") } }) { Icon(icon, label) } },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                0 -> OverviewTab()
                1 -> UsersTab()
                2 -> InboxTab()
                else -> PublishTab()
            }
        }
    }
}

// ====================================================================== overview

@Composable
private fun OverviewTab() {
    val users by Dev.users.collectAsState()
    val loading by Dev.loading.collectAsState()
    val at by Dev.usersAt.collectAsState()
    val doc by Dev.doc.collectAsState()
    val inbox by Dev.inbox.collectAsState()
    val relays by Dev.relays.info.collectAsState()
    val scope = rememberCoroutineScope()
    var keySheet by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis() / 1000
    Column(Modifier.fillMaxSize()) {
        Title("Panoramica", if (at > 0) "Aggiornata " + ago(at / 1000) else "Caricamento…") {
            IconButton(onClick = { keySheet = true }) { Icon(Icons.Rounded.Key, "Chiave") }
            if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { scope.launch { Dev.refreshUsers(); Dev.loadDoc() } }) { Icon(Icons.Rounded.Refresh, "Aggiorna") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val on = relays.values.count { it.state == com.monolith.app.nostr.RelayState.CONNECTED }
                Text("Collegamenti attivi: $on su ${Dev.RELAYS.size}", style = MaterialTheme.typography.bodySmall, color = if (on == 0) Danger else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Utenti", users.size, Modifier.weight(1f))
                    Stat("Attivi oggi", users.count { now - it.lastSeen < 86400 }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Attivi in 7 giorni", users.count { now - it.lastSeen < 7 * 86400 }, Modifier.weight(1f))
                    Stat("Nuovi in 7 giorni", users.count { it.joined > 0 && now - it.joined < 7 * 86400 }, Modifier.weight(1f))
                }
            }
            item {
                Card {
                    Label("Iscrizioni negli ultimi 30 giorni")
                    Spacer(Modifier.height(12.dp))
                    val days = (0 until 30).map { d -> users.count { it.joined > 0 && (now - it.joined) / 86400 == (29 - d).toLong() } }
                    Bars(days, Modifier.fillMaxWidth().height(110.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Solo gli account creati dalla versione 2.4.0 in poi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card {
                    Label("Ultima apertura dell'app")
                    Spacer(Modifier.height(10.dp))
                    val buckets = listOf(
                        "Oggi" to users.count { now - it.lastSeen < 86400 },
                        "Da 1 a 7 giorni" to users.count { now - it.lastSeen in 86400 until 7 * 86400 },
                        "Da 8 a 30 giorni" to users.count { now - it.lastSeen in 7 * 86400 until 30 * 86400 },
                        "Più di 30 giorni" to users.count { now - it.lastSeen >= 30 * 86400 },
                    )
                    buckets.forEach { (l, n) -> RowBar(l, n, users.size) }
                }
            }
            item {
                Card {
                    Label("Versioni dell'app")
                    Spacer(Modifier.height(10.dp))
                    val byV = users.groupBy { it.version.ifBlank { "prima della 2.4.0" } }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
                    if (byV.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    byV.forEach { (v, n) -> RowBar(v, n, users.size) }
                }
            }
            item {
                Card {
                    Label("Cosa vedono le app adesso")
                    Spacer(Modifier.height(8.dp))
                    StatusLine("Annuncio", if (doc.newsTitle.isNotBlank() || doc.newsBody.isNotBlank()) doc.newsTitle.ifBlank { doc.newsBody.take(40) } else "nessuno")
                    StatusLine("Ultima versione", doc.latest.ifBlank { "non indicata" })
                    StatusLine("Versione minima", doc.min.ifBlank { "nessuna" })
                    StatusLine("Account bloccati", doc.blocked.size.toString())
                    StatusLine("Messaggi da leggere", inbox.count { !it.read }.toString())
                    StatusLine("Pubblicato", if (doc.at > 0) date(doc.at) else "mai")
                }
            }
        }
    }
    if (keySheet) KeySheet { keySheet = false }
}

@Composable
private fun Stat(label: String, n: Int, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        Text("$n", fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RowBar(label: String, n: Int, total: Int) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("$n", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(4.dp))
        val f = if (total > 0) n.toFloat() / total else 0f
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun Bars(values: List<Int>, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = if (v == 0) 3.dp.toPx() else size.height * v / max
            drawRoundRect(if (v == 0) dim else if (i == values.lastIndex) Amber else ink, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
        }
    }
}

// ====================================================================== users

@Composable
private fun UsersTab() {
    val users by Dev.users.collectAsState()
    val doc by Dev.doc.collectAsState()
    val loading by Dev.loading.collectAsState()
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<User?>(null) }
    val shown = users.filter { q.isBlank() || it.username.contains(q.trim().removePrefix("@"), true) || it.name.contains(q.trim(), true) || it.pk.startsWith(q.trim().lowercase()) }
    Column(Modifier.fillMaxSize()) {
        Title("Utenti", "${users.size} account · ${doc.blocked.size} bloccati") {
            if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { scope.launch { Dev.refreshUsers() } }) { Icon(Icons.Rounded.Refresh, "Aggiorna") }
        }
        OutlinedTextField(
            q, { q = it }, placeholder = { Text("Cerca per nome o @utente") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.pk }) { u ->
                val blocked = u.pk in doc.blocked
                Row(Modifier.fillMaxWidth().clickable { open = u }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { Text(u.emoji, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (blocked) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Block, "Bloccato", tint = Danger, modifier = Modifier.size(16.dp)) }
                        }
                        Text("@${u.username} · #${u.tag}" + if (u.version.isNotBlank()) " · ${u.version}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(ago(u.lastSeen), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shown.isEmpty()) item {
                Text(if (loading) "Carico gli account…" else "Nessun account", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
        }
    }
    open?.let { u -> UserDialog(u) { open = null } }
}

@Composable
private fun UserDialog(u: User, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val doc by Dev.doc.collectAsState()
    val inbox by Dev.inbox.collectAsState()
    val scope = rememberCoroutineScope()
    val blocked = u.pk in doc.blocked
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val reports = inbox.filter { it.kind == "report" && it.about == u.pk }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(u.emoji + "  " + u.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StatusLine("Utente", "@${u.username} #${u.tag}")
                StatusLine("Versione", u.version.ifBlank { "prima della 2.4.0" })
                StatusLine("Ultima apertura", date(u.lastSeen))
                StatusLine("Iscritto", if (u.joined > 0) date(u.joined) else "non noto")
                StatusLine("Segnalazioni", reports.size.toString())
                if (u.bio.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(u.bio, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(10.dp))
                Text("Codice account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(u.pk, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.clickable { copy(ctx, "account", u.pk) })
                reports.take(5).forEach { r ->
                    Spacer(Modifier.height(8.dp))
                    Text("• " + r.why + if (r.text.isNotBlank()) ": " + r.text else "", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { if (blocked) { busy = true; scope.launch { val n = Dev.setBlocked(u.pk, false); busy = false; published(ctx, n) } } else confirm = true }) {
                Text(if (blocked) "Sblocca" else "Blocca", color = if (blocked) MaterialTheme.colorScheme.primary else Danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Bloccare ${u.name}?") },
            text = { Text("In tutte le app: sparisce dalla ricerca, i suoi messaggi non arrivano più a nessuno e la sua app mostra «Account bloccato». Puoi sbloccarlo quando vuoi.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false; busy = true
                    scope.launch { val n = Dev.setBlocked(u.pk, true); busy = false; published(ctx, n) }
                }) { Text("Blocca", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } },
        )
    }
}

private fun published(ctx: Context, n: Int) {
    Toast.makeText(ctx, if (n > 0) "Pubblicato ($n collegamenti)" else "Non pubblicato: controlla la connessione", Toast.LENGTH_SHORT).show()
}

// ====================================================================== inbox

@Composable
private fun InboxTab() {
    val inbox by Dev.inbox.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = inbox.filter { filter == 0 || (filter == 1 && it.kind == "report") || (filter == 2 && it.kind != "report") }
    var open by remember { mutableStateOf<User?>(null) }
    Column(Modifier.fillMaxSize()) {
        Title("Messaggi", "Segnalazioni e messaggi per te, cifrati")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tutti", "Segnalazioni", "Messaggi").forEachIndexed { i, l -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { m -> MsgCard(m) { pk -> open = Dev.userOf(pk) ?: User(pk, "?", m.aboutName.ifBlank { "Sconosciuto" }, "🙂", "", "", 0, 0) } }
            if (shown.isEmpty()) item { Text("Niente per ora", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
    }
    open?.let { u -> UserDialog(u) { open = null } }
}

@Composable
private fun MsgCard(m: Msg, onUser: (String) -> Unit) {
    val from = Dev.userOf(m.from)
    LaunchedEffect(m.id) { if (!m.read) Dev.markRead(m.id) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (m.kind == "report") Icons.Rounded.Flag else Icons.Rounded.Inbox, null, tint = if (m.kind == "report") Danger else Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (m.kind == "report") "Segnalazione" else "Messaggio", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(ago(m.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { Dev.deleteMsg(m.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Delete, "Elimina", Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Da " + (from?.let { "${it.name} (@${it.username})" } ?: m.from.take(8) + "…") + if (m.version.isNotBlank()) " · app ${m.version}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onUser(m.from) })
        if (m.kind == "report") {
            Spacer(Modifier.height(8.dp))
            Text("Su: " + m.aboutName.ifBlank { m.about.take(8) } + "  ·  " + m.why, style = MaterialTheme.typography.titleSmall)
            if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onUser(m.about) }, shape = CircleShape) { Text("Vedi l'account segnalato") }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ====================================================================== publish

@Composable
private fun PublishTab() {
    val ctx = LocalContext.current
    val doc by Dev.doc.collectAsState()
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(doc.at) { mutableStateOf(doc.newsTitle) }
    var body by rememberSaveable(doc.at) { mutableStateOf(doc.newsBody) }
    var link by rememberSaveable(doc.at) { mutableStateOf(doc.newsLink) }
    var latest by rememberSaveable(doc.at) { mutableStateOf(doc.latest) }
    var url by rememberSaveable(doc.at) { mutableStateOf(doc.latestUrl) }
    var notes by rememberSaveable(doc.at) { mutableStateOf(doc.latestNotes) }
    var min by rememberSaveable(doc.at) { mutableStateOf(doc.min) }
    var busy by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    fun draft(clearNews: Boolean = false): Doc {
        val newsChanged = title != doc.newsTitle || body != doc.newsBody || link != doc.newsLink
        return doc.copy(
            newsId = if (clearNews) "" else if (newsChanged && (title.isNotBlank() || body.isNotBlank())) "n" + System.currentTimeMillis() / 1000 else doc.newsId,
            newsTitle = if (clearNews) "" else title.trim(), newsBody = if (clearNews) "" else body.trim(), newsLink = if (clearNews) "" else link.trim(),
            latest = latest.trim(), latestUrl = url.trim(), latestNotes = notes.trim(), min = min.trim(),
        )
    }
    fun send(d: Doc) {
        busy = true
        scope.launch { val n = Dev.publish(d); busy = false; published(ctx, n) }
    }
    Column(Modifier.fillMaxSize()) {
        Title("Pubblica", if (doc.at > 0) "Ultima pubblicazione: " + date(doc.at) else "Mai pubblicato")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Label("Annuncio a tutti")
                Text("Compare una volta nell'app di ognuno, appena la apre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("Testo") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(link, { link = it }, label = { Text("Link (facoltativo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && (title.isNotBlank() || body.isNotBlank()), onClick = { send(draft()) }, shape = CircleShape) { Text("Pubblica l'annuncio") }
                    if (doc.newsId.isNotBlank()) OutlinedButton(enabled = !busy, onClick = { title = ""; body = ""; link = ""; send(draft(clearNews = true)) }, shape = CircleShape) { Text("Toglilo") }
                }
            }
            Card {
                Label("Aggiornamenti")
                Text("Chi ha una versione più vecchia dell'ultima vede l'avviso con il link. Sotto la versione minima l'app si ferma finché non aggiorni.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { pick = true }, shape = CircleShape) { Text("Scegli una build da GitHub") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(latest, { latest = it }, label = { Text("Ultima versione (es. 2.4.0)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Link per scaricarla") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Novità (facoltativo)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(min, { min = it }, label = { Text("Versione minima (vuoto = nessuna)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(enabled = !busy, onClick = { send(draft()) }, shape = CircleShape) { Text("Pubblica gli aggiornamenti") }
            }
            Card {
                Label("Account bloccati")
                Spacer(Modifier.height(6.dp))
                if (doc.blocked.isEmpty()) Text("Nessuno", color = MaterialTheme.colorScheme.onSurfaceVariant)
                doc.blocked.forEach { pk ->
                    val u = Dev.userOf(pk)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Text((u?.let { "${it.emoji} ${it.name} (@${it.username})" } ?: pk.take(12) + "…"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(enabled = !busy, onClick = { busy = true; scope.launch { val n = Dev.setBlocked(pk, false); busy = false; published(ctx, n) } }) { Text("Sblocca") }
                    }
                }
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Pubblico…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (pick) ReleasePicker(onDismiss = { pick = false }) { r ->
        pick = false
        if (r.version.isNotBlank()) latest = r.version
        url = r.url
    }
}

@Composable
private fun ReleasePicker(onDismiss: () -> Unit, onPick: (Release) -> Unit) {
    var list by remember { mutableStateOf<List<Release>?>(null) }
    LaunchedEffect(Unit) { list = Dev.releases() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Build su GitHub") },
        text = {
            val l = list
            if (l == null) CircularProgressIndicator()
            else if (l.isEmpty()) Text("Non riesco a leggere le build: controlla la connessione.")
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(l) { r ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 10.dp)) {
                        Text((if (r.version.isNotBlank()) "Monolith ${r.version} · " else "") + "build ${r.build}", style = MaterialTheme.typography.titleMedium)
                        Text(r.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

// ====================================================================== key

@Composable
private fun KeySheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val keys by Dev.keys.collectAsState()
    val pub = keys?.pub ?: ""
    var reveal by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Chiave sviluppatore", fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Label("Codice pubblico")
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Text(grouped(pub), fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { copy(ctx, "codice pubblico", pub) }, shape = CircleShape) { Text("Copia") }
                OutlinedButton(onClick = { share(ctx, "Codice pubblico della dashboard di Monolith:\n$pub") }, shape = CircleShape) { Text("Condividi") }
            }
            Spacer(Modifier.height(20.dp))
            Label("Codice segreto")
            Spacer(Modifier.height(6.dp))
            if (reveal) {
                SelectionContainer { Text(grouped(Dev.secretCode()), fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp, color = Amber) }
                OutlinedButton(onClick = { copy(ctx, "codice segreto", Dev.secretCode()) }, shape = CircleShape, modifier = Modifier.padding(top = 8.dp)) { Text("Copia il codice segreto") }
            } else {
                Text("Chi lo ha può parlare a nome tuo a tutte le app. Mostralo solo per salvarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { reveal = true }, shape = CircleShape, modifier = Modifier.padding(top = 8.dp)) { Text("Mostra") }
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { forget = true }) { Text("Usa un'altra chiave", color = Danger) }
        }
    }
    if (forget) {
        AlertDialog(
            onDismissRequest = { forget = false },
            title = { Text("Togliere questa chiave?") },
            text = { Text("La dashboard dimentica la chiave. Se non hai salvato il codice segreto non potrai più riprenderla, e servirà mettere il nuovo codice pubblico nel progetto.") },
            confirmButton = { TextButton(onClick = { forget = false; onDismiss(); Dev.forgetKey() }) { Text("Togli", color = Danger) } },
            dismissButton = { TextButton(onClick = { forget = false }) { Text("Annulla") } },
        )
    }
}
