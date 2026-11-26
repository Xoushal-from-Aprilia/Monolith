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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.ProvideTextStyle
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
import com.monolith.app.ui.components.VerifiedStyles
import com.monolith.app.ui.components.verifiedMark
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
        setContent { DevTheme { Root() } }
    }
}

@Composable
internal fun DevTheme(content: @Composable () -> Unit) {
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
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

// ====================================================================== verified

@Composable
internal fun VerSymbol(v: Ver, size: androidx.compose.ui.unit.Dp) {
    val c = VerifiedStyles.color(v.color)
    val fill = if (c == 0L) Color(0xFFF2F2F2) else Color(c)
    val mark = if (c == 0L) Color(0xFF0A0A0A) else Color.White
    Canvas(Modifier.size(size)) { verifiedMark(v.shape, fill, mark) }
}

/** gives an account the verified symbol, or changes its title and look */
@Composable
internal fun VerifyDialog(pk: String, name: String, inline: Boolean = false, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val doc by Dev.doc.collectAsState()
    val scope = rememberCoroutineScope()
    val cur = doc.verified[pk]
    var title by remember { mutableStateOf(cur?.title ?: "Verificato") }
    var shape by remember { mutableStateOf(cur?.shape ?: "seal") }
    var color by remember { mutableStateOf(cur?.color ?: "blue") }
    var busy by remember { mutableStateOf(false) }
    fun save(v: Ver?) {
        busy = true
        scope.launch { val n = Dev.setVerified(pk, v); busy = false; published(ctx, n); if (n > 0) onDismiss() }
    }
    val head: @Composable () -> Unit = { Text(if (cur == null) "Verifica $name" else "Verifica di $name") }
    val body: @Composable () -> Unit = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    VerSymbol(Ver(title, shape, color), 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(title.ifBlank { "Account verificato" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(title, { if (it.length <= 40) title = it }, label = { Text("Titolo (es. Sviluppatore, Creator)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Label("Simbolo")
                Spacer(Modifier.height(6.dp))
                VerifiedStyles.SHAPES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { (key, label) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (shape == key) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { shape = key }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                VerSymbol(Ver(title, key, color), 28.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Label("Colore")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VerifiedStyles.COLORS.forEach { (key, v) ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(if (v == 0L) Color(0xFFF2F2F2) else Color(v)).clickable { color = key },
                            contentAlignment = Alignment.Center,
                        ) { if (color == key) Box(Modifier.size(10.dp).clip(CircleShape).background(if (v == 0L) Color.Black else Color.White)) }
                    }
                }
                if (cur != null) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(enabled = !busy, onClick = { save(null) }) { Text("Togli la verifica", color = Danger) }
                }
            }
        }
    val ok: @Composable () -> Unit = { TextButton(enabled = !busy, onClick = { save(Ver(title.trim(), shape, color)) }) { Text(if (cur == null) "Verifica" else "Salva") } }
    val no: @Composable () -> Unit = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    if (inline) {
        // the same content drawn in the page, for the screenshots
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(20.dp)) {
            Column(Modifier.padding(24.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { head() }
                Spacer(Modifier.height(16.dp))
                body()
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) { no(); ok() }
            }
        }
    } else AlertDialog(onDismissRequest = onDismiss, title = head, text = body, confirmButton = ok, dismissButton = no)
}

// ====================================================================== root

@Composable
internal fun Root() {
    val keys by Dev.keys.collectAsState()
    var keyIntro by rememberSaveable { mutableStateOf(false) }
    when {
        keys == null -> SetupScreen { keyIntro = true }
        keyIntro -> KeyIntro { keyIntro = false }
        else -> MainScreen()
    }
}

@Composable
internal fun SetupScreen(onCreated: () -> Unit) {
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
internal fun KeyIntro(onDone: () -> Unit) {
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
internal fun MainScreen(start: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(start) }
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
        val linked by Dev.linked.collectAsState()
        Column(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            LinkBanner()
            // the banner already sits under the status bar
            Box(Modifier.weight(1f).then(if (linked == false) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                when (tab) {
                    0 -> OverviewTab()
                    1 -> UsersTab()
                    2 -> InboxTab()
                    else -> PublishTab()
                }
            }
        }
    }
}

/**
 * Blocks, verifications, news and updates reach the apps only when they know this key, that is when its
 * public code is in dev/developer.pub. Until then the dashboard says so on every screen.
 */
@Composable
internal fun LinkBanner() {
    val ctx = LocalContext.current
    val linked by Dev.linked.collectAsState()
    val keys by Dev.keys.collectAsState()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(linked) {
        // while not linked, look again every minute: the file can arrive at any moment
        while (linked == false && !Dev.previewing) { kotlinx.coroutines.delay(60_000); Dev.checkLinked() }
    }
    if (linked != false) return
    val pub = keys?.pub ?: return
    Surface(color = Danger.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("Le app non riconoscono ancora questa chiave", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Blocchi, verifiche, annunci e aggiornamenti arrivano alle app solo quando il codice pubblico è nel file dev/developer.pub del progetto. Copialo e fallo aggiungere: è pubblico, non è il codice segreto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { copy(ctx, "codice pubblico", pub) }, shape = CircleShape) { Text("Copia il codice pubblico") }
                OutlinedButton(onClick = { share(ctx, "Codice pubblico della dashboard di Monolith:\n$pub") }, shape = CircleShape) { Text("Condividi") }
            }
            TextButton(enabled = !checking, onClick = { checking = true; scope.launch { Dev.checkLinked(); checking = false } }) {
                Text(if (checking) "Controllo…" else "Controlla di nuovo")
            }
        }
    }
}

// ====================================================================== overview

@Composable
internal fun OverviewTab() {
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
                    StatusLine("Account verificati", doc.verified.size.toString())
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
internal fun UsersTab() {
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
                            doc.verified[u.pk]?.let { Spacer(Modifier.width(5.dp)); VerSymbol(it, 16.dp) }
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
    val devices by Dev.devices.collectAsState()
    val scope = rememberCoroutineScope()
    val blocked = u.pk in doc.blocked
    val deleted = u.pk in doc.deleted
    val device = devices[u.pk]
    val phoneBlocked = device != null && device.codes.any { it in doc.devices }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var verify by remember { mutableStateOf(false) }
    var write by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var blockPhone by remember { mutableStateOf(false) }
    val ver = doc.verified[u.pk]
    val reports = inbox.filter { it.kind == "report" && it.about == u.pk }
    fun run(block: suspend () -> Int) { busy = true; scope.launch { val n = block(); busy = false; published(ctx, n) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(u.emoji + "  " + u.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (deleted) Text("Account eliminato" + doc.deleted[u.pk]?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(), color = Danger, style = MaterialTheme.typography.titleSmall)
                else if (blocked) Text("Account bloccato", color = Danger, style = MaterialTheme.typography.titleSmall)
                StatusLine("Utente", "@${u.username} #${u.tag}")
                StatusLine("Versione", u.version.ifBlank { "prima della 2.4.0" })
                StatusLine("Ultima apertura", date(u.lastSeen))
                StatusLine("Iscritto", if (u.joined > 0) date(u.joined) else "non noto")
                StatusLine("Telefono", device?.let { it.model + if (it.sdk > 0) " · Android API ${it.sdk}" else "" } ?: "non ancora noto")
                if (device != null) StatusLine("Codice telefono", device.codes.firstOrNull()?.take(10)?.plus("…") ?: "—")
                StatusLine("Segnalazioni", reports.size.toString())
                StatusLine("Verifica", ver?.title?.ifBlank { "Verificato" } ?: "no")
                if (u.bio.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(u.bio, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && !deleted, onClick = { write = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Mail, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Scrivi un messaggio")
                    }
                    OutlinedButton(enabled = !busy && !deleted, onClick = { verify = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        if (ver != null) { VerSymbol(ver, 16.dp); Spacer(Modifier.width(6.dp)) }
                        Text(if (ver == null) "Verifica l'account" else "Modifica la verifica")
                    }
                    OutlinedButton(enabled = !busy && !deleted, onClick = { if (blocked) run { Dev.setBlocked(u.pk, false) } else confirm = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(if (blocked) "Sblocca l'account" else "Blocca l'account", color = if (blocked) MaterialTheme.colorScheme.primary else Danger)
                    }
                    OutlinedButton(
                        enabled = !busy && device != null && device.codes.isNotEmpty(),
                        onClick = { if (phoneBlocked) run { Dev.setDeviceBlocked(device!!.codes, "", false) } else blockPhone = true },
                        shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (phoneBlocked) "Sblocca il telefono" else "Blocca il telefono", color = if (phoneBlocked) MaterialTheme.colorScheme.primary else Danger) }
                    if (deleted) OutlinedButton(enabled = !busy, onClick = { run { Dev.restoreAccount(u.pk) } }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Togli dagli account eliminati") }
                    else Button(
                        enabled = !busy, onClick = { delete = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                    ) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Elimina l'account") }
                }
                Spacer(Modifier.height(12.dp))
                Text("Codice account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(u.pk, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.clickable { copy(ctx, "account", u.pk) })
                reports.take(5).forEach { r ->
                    Spacer(Modifier.height(8.dp))
                    Text("• " + r.why + if (r.text.isNotBlank()) ": " + r.text else "", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
    if (verify) VerifyDialog(u.pk, u.name) { verify = false }
    if (write) MessageDialog(u.pk, u.name) { write = false }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Bloccare ${u.name}?") },
            text = { Text("In tutte le app: sparisce dalla ricerca, i suoi messaggi non arrivano più a nessuno e la sua app mostra «Account bloccato». Puoi sbloccarlo quando vuoi.") },
            confirmButton = { TextButton(onClick = { confirm = false; run { Dev.setBlocked(u.pk, true) } }) { Text("Blocca", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } },
        )
    }
    if (delete) ReasonDialog(
        title = "Eliminare l'account di ${u.name}?",
        body = "La sua app cancella tutto (chat, amici, agenda, profilo) e mostra il motivo. Sparisce dalla ricerca e nessuno riceve più i suoi messaggi. Non si può annullare sul suo telefono.",
        action = "Elimina", required = true,
        onDismiss = { delete = false },
    ) { why -> delete = false; run { Dev.deleteAccount(u.pk, why) } }
    if (blockPhone && device != null) ReasonDialog(
        title = "Bloccare il telefono di ${u.name}?",
        body = "${device.model}: Monolith smette di funzionare su quel telefono, con qualsiasi account, anche nuovo. Il motivo compare sullo schermo.",
        action = "Blocca", required = false,
        onDismiss = { blockPhone = false },
    ) { why -> blockPhone = false; run { Dev.setDeviceBlocked(device.codes, why, true) } }
}

/** asks for the reason of a deletion or of a block */
@Composable
private fun ReasonDialog(title: String, body: String, action: String, required: Boolean, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var why by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(why, { why = it.take(500) }, label = { Text(if (required) "Motivo (lo vedrà)" else "Motivo (facoltativo, lo vedrà)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !required || why.isNotBlank(), onClick = { onDone(why) }) { Text(action, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** a message to one account: it pops up on that phone as a notification */
@Composable
internal fun MessageDialog(pk: String, name: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Messaggio a $name") },
        text = {
            Column {
                Text("Arriva come notifica sul suo telefono, cifrato, e resta nell'app in Impostazioni.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Titolo (facoltativo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it.take(4000) }, label = { Text("Testo") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = body.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = Dev.message(pk, title, body)
                    busy = false
                    Toast.makeText(ctx, if (ok) "Inviato" else "Non inviato: controlla la connessione", Toast.LENGTH_SHORT).show()
                    if (ok) onDismiss()
                }
            }) { Text(if (busy) "Invio…" else "Invia") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

private fun published(ctx: Context, n: Int) {
    Toast.makeText(ctx, if (n > 0) "Pubblicato ($n collegamenti)" else "Non pubblicato: controlla la connessione", Toast.LENGTH_SHORT).show()
}

// ====================================================================== inbox

@Composable
internal fun InboxTab() {
    val inbox by Dev.inbox.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = inbox.filter {
        when (filter) {
            0 -> it.kind != "out"
            1 -> it.kind == "report"
            2 -> it.kind == "verify"
            3 -> it.kind == "feedback"
            else -> it.kind == "out"
        }
    }
    var open by remember { mutableStateOf<User?>(null) }
    var verifyFor by remember { mutableStateOf<Msg?>(null) }
    var replyTo by remember { mutableStateOf<Msg?>(null) }
    Column(Modifier.fillMaxSize()) {
        Title("Messaggi", "Segnalazioni, verifiche e messaggi per te, cifrati")
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tutti", "Segnalazioni", "Verifiche", "Messaggi", "Inviati").forEachIndexed { i, l -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { m -> MsgCard(m, onVerify = { verifyFor = m }, onReply = { replyTo = m }) { pk -> open = Dev.userOf(pk) ?: User(pk, "?", m.aboutName.ifBlank { "Sconosciuto" }, "🙂", "", "", 0, 0) } }
            if (shown.isEmpty()) item { Text("Niente per ora", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
    }
    open?.let { u -> UserDialog(u) { open = null } }
    verifyFor?.let { m -> VerifyDialog(m.from, Dev.userOf(m.from)?.name ?: m.aboutName.ifBlank { m.from.take(8) }) { verifyFor = null } }
    replyTo?.let { m -> MessageDialog(m.from, Dev.userOf(m.from)?.name ?: m.from.take(8)) { replyTo = null } }
}

@Composable
private fun MsgCard(m: Msg, onVerify: () -> Unit, onReply: () -> Unit = {}, onUser: (String) -> Unit) {
    val from = Dev.userOf(m.from)
    LaunchedEffect(m.id) { if (!m.read) Dev.markRead(m.id) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(when (m.kind) { "report" -> Icons.Rounded.Flag; "verify" -> Icons.Rounded.Verified; else -> Icons.Rounded.Inbox }, null, tint = if (m.kind == "report") Danger else Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(when (m.kind) { "report" -> "Segnalazione"; "verify" -> "Richiesta di verifica"; "out" -> "Inviato da te"; else -> "Messaggio" }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(ago(m.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { Dev.deleteMsg(m.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Delete, "Elimina", Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text((if (m.kind == "out") "A " else "Da ") + (from?.let { "${it.name} (@${it.username})" } ?: m.from.take(8) + "…") + if (m.version.isNotBlank()) " · app ${m.version}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onUser(m.from) })
        if (m.kind == "verify") {
            val doc by Dev.doc.collectAsState()
            Spacer(Modifier.height(8.dp))
            if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            val ver = doc.verified[m.from]
            if (ver != null) Row(verticalAlignment = Alignment.CenterVertically) { VerSymbol(ver, 18.dp); Spacer(Modifier.width(6.dp)); Text("Già verificato: " + ver.title.ifBlank { "Verificato" }, style = MaterialTheme.typography.labelLarge) }
            else Button(onClick = onVerify, shape = CircleShape) { Text("Verifica…") }
        } else if (m.kind == "report") {
            Spacer(Modifier.height(8.dp))
            Text("Su: " + m.aboutName.ifBlank { m.about.take(8) } + "  ·  " + m.why, style = MaterialTheme.typography.titleSmall)
            if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onUser(m.about) }, shape = CircleShape) { Text("Vedi l'account segnalato") }
        } else if (m.kind == "out") {
            Spacer(Modifier.height(8.dp))
            if (m.why.isNotBlank()) Text(m.why, style = MaterialTheme.typography.titleSmall)
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
        } else {
            Spacer(Modifier.height(8.dp))
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
        }
        if (m.kind != "out") {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Rounded.Mail, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Rispondi")
            }
        }
    }
}

// ====================================================================== publish

@Composable
internal fun PublishTab() {
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
    var editVer by remember { mutableStateOf<String?>(null) }
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
                Text("Arriva come notifica sul telefono di tutti e compare una volta nell'app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Label("Account verificati")
                Spacer(Modifier.height(6.dp))
                if (doc.verified.isEmpty()) Text("Nessuno: le richieste arrivano in Messaggi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                doc.verified.forEach { (pk, v) ->
                    val u = Dev.userOf(pk)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        VerSymbol(v, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u?.let { "${it.name} (@${it.username})" } ?: pk.take(12) + "…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(v.title.ifBlank { "Verificato" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { editVer = pk }) { Text("Modifica") }
                    }
                }
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
            Card {
                Label("Account eliminati")
                Spacer(Modifier.height(6.dp))
                if (doc.deleted.isEmpty()) Text("Nessuno", color = MaterialTheme.colorScheme.onSurfaceVariant)
                doc.deleted.forEach { (pk, why) ->
                    val u = Dev.userOf(pk)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(u?.let { "${it.emoji} ${it.name} (@${it.username})" } ?: pk.take(12) + "…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (why.isNotBlank()) Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(enabled = !busy, onClick = { busy = true; scope.launch { val n = Dev.restoreAccount(pk); busy = false; published(ctx, n) } }) { Text("Togli") }
                    }
                }
            }
            Card {
                Label("Telefoni bloccati")
                Spacer(Modifier.height(6.dp))
                val devs by Dev.devices.collectAsState()
                if (doc.devices.isEmpty()) Text("Nessuno: si bloccano dalla scheda di un utente", color = MaterialTheme.colorScheme.onSurfaceVariant)
                // one row per phone: its codes are grouped by the account that used it
                val groups = doc.devices.keys.groupBy { code -> devs.entries.firstOrNull { code in it.value.codes }?.key }
                groups.forEach { (pk, codes) ->
                    val info = pk?.let { devs[it] }
                    val u = pk?.let { Dev.userOf(it) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(info?.model ?: ("Telefono " + codes.first().take(8) + "…"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((u?.let { "usato da ${it.name}" } ?: "") + (doc.devices[codes.first()]?.takeIf { it.isNotBlank() }?.let { (if (u != null) " · " else "") + it } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(enabled = !busy, onClick = { busy = true; scope.launch { val n = Dev.setDeviceBlocked(codes, "", false); busy = false; published(ctx, n) } }) { Text("Sblocca") }
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
    editVer?.let { pk -> VerifyDialog(pk, Dev.userOf(pk)?.name ?: pk.take(8)) { editVer = null } }
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
internal fun KeySheet(onDismiss: () -> Unit) {
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
