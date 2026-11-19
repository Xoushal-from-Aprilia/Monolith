package com.monolith.app.ui.screens

import android.content.Intent
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.monolith.app.BuildConfig
import com.monolith.app.Graph
import com.monolith.app.core.Profile
import com.monolith.app.core.tagOf
import com.monolith.app.nostr.RelayState
import com.monolith.app.service.MonolithService
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.sharedBoundsKey
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val profile by Graph.identity.profile.collectAsState()
    val theme by Graph.prefs.theme.collectAsState()
    val relayInfo by Graph.relays.info.collectAsState()
    val relayList by Graph.prefs.relays.collectAsState()
    val p = profile ?: Profile("", "", "🗿")

    var emoji by rememberSaveable { mutableStateOf(p.emoji) }
    var name by rememberSaveable { mutableStateOf(p.displayName) }
    var bio by rememberSaveable { mutableStateOf(p.bio) }
    var picker by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var newRelay by remember { mutableStateOf("") }
    var turnUrl by rememberSaveable { mutableStateOf(Graph.prefs.turnUrl) }
    var turnUser by rememberSaveable { mutableStateOf(Graph.prefs.turnUser) }
    var turnPass by rememberSaveable { mutableStateOf(Graph.prefs.turnPass) }
    var alwaysOnline by remember { mutableStateOf(Graph.prefs.alwaysOnline) }
    var howOpen by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    val changed = emoji != p.emoji || name.trim() != p.displayName || bio.trim() != p.bio
    val myPub = Graph.identity.keys?.pub ?: ""

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
            Text("Profilo e impostazioni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AnimatedVisibility(changed) {
                Button(
                    onClick = {
                        val np = p.copy(emoji = emoji, displayName = name.trim().ifBlank { p.displayName }, bio = bio.trim())
                        Graph.scope.launch { Graph.messenger.updateProfile(np) }
                    },
                    shape = CircleShape,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Salva") }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(emoji, 104.dp, Modifier.sharedBoundsKey("me-avatar").bounceClick(0.9f) { picker = true })
                Spacer(Modifier.height(12.dp))
                Text(name.ifBlank { p.displayName }, style = MaterialTheme.typography.headlineSmall)
                Text("@${p.username}  #${tagOf(myPub)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(32) }, label = { Text("Nome visualizzato") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio, onValueChange = { bio = it.take(140) }, label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, minLines = 2,
                )
            }

            SectionLabel("Aspetto", Modifier.padding(top = 20.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                listOf("Sistema", "Chiaro", "Scuro").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = theme == i,
                        onClick = { Graph.prefs.setTheme(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(label) }
                }
            }

            SectionLabel("Identità", Modifier.padding(top = 20.dp))
            SettingRow(Icons.Rounded.Shield, "Chiave pubblica", Graph.identity.npub().take(22) + "…") {
                copyText(ctx, "npub", Graph.identity.npub())
            }
            SettingRow(Icons.Rounded.Key, "Chiave segreta", "Backup per ripristinare il profilo") { showSecret = true }

            SectionLabel("Connessione", Modifier.padding(top = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sempre online", style = MaterialTheme.typography.titleMedium)
                    Text("Ricevi messaggi e chiamate anche ad app chiusa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = alwaysOnline, onCheckedChange = {
                    alwaysOnline = it
                    Graph.prefs.alwaysOnline = it
                    if (it) MonolithService.start(ctx) else if (!Graph.location.sharing.value) MonolithService.stop(ctx)
                })
            }
            val connected = relayInfo.values.count { it.state == RelayState.CONNECTED }
            Text(
                "Relay Nostr: $connected di ${relayList.size} connessi. Servono solo come buca delle lettere cifrata; i dati in tempo reale passano peer-to-peer con WebRTC.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            relayList.forEach { url ->
                val st = relayInfo[url]?.state ?: RelayState.DISCONNECTED
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val alpha by animateFloatAsState(if (st == RelayState.CONNECTED) 1f else 0.25f, label = "a")
                    Box(Modifier.size(10.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                    Text(url.removePrefix("wss://"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        when (st) {
                            RelayState.CONNECTED -> "connesso"
                            RelayState.CONNECTING -> "…"
                            RelayState.DISCONNECTED -> "offline"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { Graph.prefs.setRelays(relayList - url) }, enabled = relayList.size > 1) {
                        Icon(Icons.Rounded.Close, "Rimuovi", Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newRelay, onValueChange = { newRelay = it.trim() }, placeholder = { Text("wss://…") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                )
                IconButton(onClick = {
                    if (newRelay.startsWith("wss://")) {
                        Graph.prefs.setRelays(relayList + newRelay); newRelay = ""
                    }
                }) { Icon(Icons.Rounded.Add, "Aggiungi relay") }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Server TURN (opzionale): aiuta le connessioni dirette dietro reti molto restrittive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                OutlinedTextField(turnUrl, { turnUrl = it.trim(); Graph.prefs.turnUrl = turnUrl }, placeholder = { Text("turn:server:3478") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(turnUser, { turnUser = it; Graph.prefs.turnUser = it }, placeholder = { Text("utente") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(turnPass, { turnPass = it; Graph.prefs.turnPass = it }, placeholder = { Text("password") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                }
            }

            SectionLabel("Permessi", Modifier.padding(top = 12.dp))
            SettingRow(Icons.Rounded.Shield, "Gestisci permessi", "Notifiche, posizione, fotocamera, microfono, batteria") {
                nav.navigate("permissions?first=false")
            }

            SectionLabel("Info", Modifier.padding(top = 20.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().animateContentSize(Motion.smooth()).bounceClick(0.99f) { howOpen = !howOpen },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Hub, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Come funziona Monolith", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val rot by animateFloatAsState(if (howOpen) 180f else 0f, Motion.bouncy(), label = "r")
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = rot })
                    }
                    if (howOpen) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "• La tua identità è una chiave crittografica creata sul telefono: niente account, email o numeri.\n" +
                                "• Messaggi, inviti e richieste sono cifrati end-to-end (secp256k1 + ChaCha20) e lasciati in buche delle lettere pubbliche (relay Nostr), così arrivano anche se l'amico è offline.\n" +
                                "• Quando siete entrambi online i telefoni si collegano direttamente con WebRTC: posizione live, foto e chiamate vocali viaggiano peer-to-peer.\n" +
                                "• Nessun server di Monolith: non c'è niente da configurare o pagare.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Monolith ${BuildConfig.VERSION_NAME} · mappe © OpenStreetMap, © CARTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { wipe = true }, modifier = Modifier.padding(12.dp)) {
                Text("Esci ed elimina i dati da questo telefono")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }

    if (showSecret) {
        AlertDialog(
            onDismissRequest = { showSecret = false; revealed = false },
            title = { Text("Chiave segreta") },
            text = {
                Column {
                    Text("Chi ha questa chiave può essere te. Salvala in un posto sicuro (es. password manager) e non condividerla con nessuno.")
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            if (revealed) Graph.identity.nsec() else "nsec1••••••••••••••••••••••••",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp).bounceClick(0.98f) { revealed = true },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyText(ctx, "nsec", Graph.identity.nsec()); showSecret = false; revealed = false }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copia")
                }
            },
            dismissButton = {
                if (!revealed) TextButton(onClick = { revealed = true }) { Text("Mostra") }
                else TextButton(onClick = { showSecret = false; revealed = false }) { Text("Chiudi") }
            },
        )
    }

    if (wipe) {
        AlertDialog(
            onDismissRequest = { wipe = false },
            title = { Text("Eliminare tutto?") },
            text = { Text("Chat, agenda e amici verranno cancellati da questo telefono. Senza la chiave segreta non potrai recuperare questo profilo.") },
            confirmButton = {
                TextButton(onClick = {
                    wipe = false
                    Graph.scope.launch {
                        Graph.relays.stop()
                        Graph.db.clearAllTables()
                        Graph.identity.reset()
                        Graph.prefs.lastWrapSec = 0
                        Graph.prefs.permissionsDone = false
                        withContext(Dispatchers.Main) {
                            MonolithService.stop(ctx)
                            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                ctx.startActivity(it)
                            }
                            Process.killProcess(Process.myPid())
                        }
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { wipe = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
