package com.monolith.app.ui.screens

import android.content.Intent
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.monolith.app.BuildConfig
import com.monolith.app.Graph
import com.monolith.app.core.AppLock
import com.monolith.app.core.Backup
import com.monolith.app.core.I18n
import com.monolith.app.core.Profile
import com.monolith.app.core.t
import com.monolith.app.core.tagOf
import com.monolith.app.service.MonolithService
import com.monolith.app.ui.Unlock
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.FloatingMark
import com.monolith.app.ui.components.PopIn
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.components.sharedBoundsKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LanguageDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Lingua")) },
        text = {
            Column {
                I18n.LANGS.forEach { (code, name) ->
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) { onPick(code) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = I18n.lang == code, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Chiudi")) } },
    )
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val profile by Graph.identity.profile.collectAsState()
    val theme by Graph.prefs.theme.collectAsState()
    val p = profile ?: Profile("", "", "🗿")

    var emoji by rememberSaveable { mutableStateOf(p.emoji) }
    var name by rememberSaveable { mutableStateOf(p.displayName) }
    var bio by rememberSaveable { mutableStateOf(p.bio) }
    var picker by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    var mapSheet by remember { mutableStateOf(false) }
    val mapStyleIdx by Graph.prefs.mapStyle.collectAsState()
    val mapTone by Graph.prefs.mapTone.collectAsState()
    var alwaysOnline by remember { mutableStateOf(Graph.prefs.alwaysOnline) }
    var lockOn by remember { mutableStateOf(Graph.prefs.appLock) }
    var backupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    val changed = emoji != p.emoji || name.trim() != p.displayName || bio.trim() != p.bio
    val myPub = Graph.identity.keys?.pub ?: ""

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) { backupBusy = false; return@rememberLauncherForActivityResult }
        val pw = backupPassword.toCharArray()
        Graph.scope.launch {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { Backup.export(ctx, it, pw) } }.isSuccess
            withContext(Dispatchers.Main) {
                backupBusy = false
                backupPassword = ""
                Toast.makeText(ctx, if (ok) t("Backup salvato") else t("Backup non riuscito"), Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Profilo e impostazioni"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AnimatedVisibility(changed) {
                Button(
                    onClick = {
                        val np = p.copy(emoji = emoji, displayName = name.trim().ifBlank { p.displayName }, bio = bio.trim())
                        Graph.scope.launch { Graph.messenger.updateProfile(np) }
                    },
                    shape = CircleShape, modifier = Modifier.padding(end = 8.dp),
                ) { Text(t("Salva")) }
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
                OutlinedTextField(name, { name = it.take(32) }, label = { Text(t("Nome visualizzato")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(bio, { bio = it.take(140) }, label = { Text(t("Bio")) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, minLines = 2)
            }
            SettingRow(Icons.Rounded.QrCode2, t("Il mio codice QR"), t("Fatti aggiungere di persona")) { showQr = true }

            SectionLabel(t("Aspetto"), Modifier.padding(top = 16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                listOf(t("Sistema"), t("Chiaro"), t("Scuro")).forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = theme == i, onClick = { Graph.prefs.setTheme(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text(label) }
                }
            }
            SettingRow(Icons.Rounded.Translate, t("Lingua"), I18n.LANGS.first { it.first == I18n.lang }.second) { langDialog = true }
            SettingRow(Icons.Rounded.Map, t("Mappa"), t(MapLook.style(mapStyleIdx).name) + " · " + MapLook.toneLabel(mapTone)) { mapSheet = true }

            SectionLabel(t("Privacy e sicurezza"), Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Fingerprint, null, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Blocco app"), style = MaterialTheme.typography.titleMedium)
                    Text(t("Impronta, volto o PIN all'apertura"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = lockOn, onCheckedChange = { on ->
                    if (!Unlock.available(ctx)) {
                        Toast.makeText(ctx, t("Imposta prima un blocco schermo sul telefono"), Toast.LENGTH_LONG).show()
                    } else {
                        Unlock.prompt(ctx, if (on) t("Attiva il blocco") else t("Disattiva il blocco")) {
                            lockOn = on
                            AppLock.setEnabled(on)
                        }
                    }
                })
            }
            SettingRow(Icons.Rounded.Key, t("Codice di recupero"), t("Serve per ripristinare il profilo su un altro telefono")) {
                if (Unlock.available(ctx)) Unlock.prompt(ctx, t("Mostra il codice di recupero")) { showCode = true } else showCode = true
            }
            SettingRow(Icons.Rounded.Backup, t("Backup cifrato"), t("Salva chat, agenda, liste e amici in un file protetto da password")) { backupDialog = true }

            SectionLabel(t("Connessione"), Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Sempre attivo"), style = MaterialTheme.typography.titleMedium)
                    Text(t("Ricevi messaggi e chiamate anche ad app chiusa"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = alwaysOnline, onCheckedChange = {
                    alwaysOnline = it
                    Graph.prefs.alwaysOnline = it
                    if (it) MonolithService.start(ctx) else if (!Graph.location.sharing.value) MonolithService.stop(ctx)
                })
            }
            SettingRow(Icons.Rounded.Shield, t("Gestisci permessi"), t("Notifiche, posizione, fotocamera, microfono, batteria")) { nav.navigate("permissions?first=false") }

            SectionLabel(t("Monolith"), Modifier.padding(top = 16.dp))
            SettingRow(Icons.Rounded.AutoStories, t("Journal"), t("Tutte le novità, versione per versione")) { nav.navigate("journal") }
            SettingRow(Icons.Rounded.Favorite, t("Crediti"), "Christian “Xoushal” Vedovi") { nav.navigate("credits") }
            Text(
                t("Versione {0}", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { wipe = true }, modifier = Modifier.padding(12.dp)) { Text(t("Esci ed elimina i dati da questo telefono")) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
    if (showQr) MyQrDialog { showQr = false }
    if (mapSheet) MapStyleSheet { mapSheet = false }
    if (langDialog) LanguageDialog(onDismiss = { langDialog = false }) { I18n.set(ctx, it); langDialog = false }

    if (showCode) {
        val code = remember { Graph.identity.recoveryCode() }
        AlertDialog(
            onDismissRequest = { showCode = false; revealed = false },
            title = { Text(t("Codice di recupero")) },
            text = {
                Column {
                    Text(t("Chi ha questo codice può usare il tuo profilo. Conservalo in un posto sicuro e non condividerlo con nessuno."))
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.bounceClick(0.98f) { revealed = true }) {
                        Text(
                            if (revealed) code else code.map { if (it == '-') '-' else '•' }.joinToString(""),
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyText(ctx, "code", code); showCode = false; revealed = false }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Copia"))
                }
            },
            dismissButton = {
                if (!revealed) TextButton(onClick = { revealed = true }) { Text(t("Mostra")) }
                else TextButton(onClick = { showCode = false; revealed = false }) { Text(t("Chiudi")) }
            },
        )
    }

    if (backupDialog) {
        AlertDialog(
            onDismissRequest = { backupDialog = false },
            title = { Text(t("Backup cifrato")) },
            text = {
                Column {
                    Text(t("Scegli una password: servirà per ripristinare il backup. Senza password il file non si può aprire."))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        backupPassword, { backupPassword = it }, label = { Text(t("Password")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        supportingText = { if (backupPassword.isNotEmpty() && backupPassword.length < 6) Text(t("Almeno 6 caratteri")) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    backupDialog = false
                    backupBusy = true
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    createDoc.launch("monolith-backup-$date.mbk")
                }, enabled = backupPassword.length >= 6 && !backupBusy) { Text(t("Crea backup")) }
            },
            dismissButton = { TextButton(onClick = { backupDialog = false }) { Text(t("Annulla")) } },
        )
    }

    if (wipe) {
        AlertDialog(
            onDismissRequest = { wipe = false },
            title = { Text(t("Eliminare tutto?")) },
            text = { Text(t("Chat, agenda, liste e amici verranno cancellati da questo telefono. Senza il codice di recupero o un backup non potrai recuperare questo profilo.")) },
            confirmButton = {
                TextButton(onClick = {
                    wipe = false
                    Graph.scope.launch {
                        Graph.relays.stop()
                        Graph.db.clearAllTables()
                        Graph.identity.reset()
                        Graph.prefs.lastWrapSec = 0
                        Graph.prefs.permissionsDone = false
                        AppLock.setEnabled(false)
                        withContext(Dispatchers.Main) {
                            MonolithService.stop(ctx)
                            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                ctx.startActivity(it)
                            }
                            Process.killProcess(Process.myPid())
                        }
                    }
                }) { Text(t("Elimina")) }
            },
            dismissButton = { TextButton(onClick = { wipe = false }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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

// ====================================================================== journal

private class Release(val version: String, val date: String, val notes: List<String>)

private val JOURNAL = listOf(
    Release(
        "0.2.1", "2026-11-20",
        listOf(
            "Mappe in bianco e nero, in tono con lo stile dell'app",
            "Tre stili di mappa gratuiti e senza account: Classica, Essenziale e Topografica",
            "Colore della mappa: automatico, bianco o nero",
            "Le mappe tornano a caricarsi correttamente",
        ),
    ),
    Release(
        "0.2.0", "2026-11-20",
        listOf(
            "Messaggi vocali: tieni premuto il microfono per registrare",
            "Reazioni con emoji, risposte ai messaggi, modifica ed eliminazione per tutti",
            "Messaggi effimeri: 1 ora, 24 ore o 7 giorni",
            "Sondaggi nelle chat e nei gruppi",
            "Videochiamate e chiamate di gruppo fino a 4 persone",
            "Eventi ricorrenti e più promemoria per lo stesso evento",
            "Widget dell'agenda per la schermata Home",
            "Liste condivise con gli amici",
            "Avvisi di arrivo e «Sto arrivando» con distanza e tempo stimato",
            "Riquadro nelle impostazioni rapide per condividere la posizione",
            "Aggiungi amici con il codice QR e verifica dell'identità",
            "Blocco dell'app con impronta, volto o PIN",
            "Backup cifrato e codice di recupero",
            "7 lingue: italiano, inglese, francese, tedesco, russo, polacco e ceco",
            "Journal e Crediti nelle impostazioni",
        ),
    ),
    Release(
        "0.1.2", "2026-11-19",
        listOf(
            "Prima versione installabile",
            "Schermata dei permessi al primo avvio",
            "Inviti agli eventi con risposta Partecipo / Non posso",
            "Eventi e posizione condivisibili in chat",
        ),
    ),
    Release(
        "0.1.1", "2026-11-19",
        listOf("Correzioni di stabilità e dell'interfaccia"),
    ),
    Release(
        "0.1.0", "2026-11-19",
        listOf(
            "Nasce Monolith: logo, animazioni e interfaccia in bianco, nero e grigi",
            "Agenda con eventi, note e promemoria con emoji come icona della notifica",
            "Clubhouse: chat private e di gruppo con foto e trilli",
            "Mappa con la posizione in tempo reale degli amici",
            "Amici: ricerca per username e richieste di amicizia",
            "Chiamate vocali",
            "Tema chiaro e scuro",
        ),
    ),
)

@Composable
fun JournalScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Journal"), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            itemsIndexed(JOURNAL) { i, r ->
                PopIn(i * 90) {
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
                            Box(Modifier.padding(top = 20.dp).size(12.dp).clip(CircleShape).background(if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = MaterialTheme.shapes.large, color = if (i == 0) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("v" + r.version, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                    if (i == 0) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                            Text(t("Attuale"), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                                Text(com.monolith.app.core.Fmt.date(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(r.date)?.time ?: 0L), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                r.notes.forEach { n ->
                                    Row(Modifier.padding(vertical = 3.dp)) {
                                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                        Text(t(n), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== credits

@Composable
fun CreditsScreen(nav: NavHostController) {
    val apis = listOf(
        "OpenStreetMap" to t("Dati delle mappe · © OpenStreetMap contributors (ODbL)"),
        "Humanitarian OSM · OpenStreetMap France" to t("Mappa Essenziale"),
        "OpenTopoMap" to t("Mappa Topografica · CC-BY-SA"),
        "Android Location" to t("Posizione del telefono"),
        "Android Camera2" to t("Fotocamera e videochiamate"),
        "Android BiometricPrompt" to t("Blocco con impronta, volto o PIN"),
        "Android Keystore" to t("Protezione delle chiavi sul telefono"),
        "Android App Widgets · Quick Settings" to t("Widget e riquadro rapido"),
    )
    val libs = listOf(
        "Jetpack Compose · Material 3 · AndroidX" to "Apache 2.0",
        "Kotlin · Coroutines" to "Apache 2.0",
        "Room · Glance · Navigation" to "Apache 2.0",
        "OkHttp" to "Apache 2.0",
        "Coil" to "Apache 2.0",
        "osmdroid" to "Apache 2.0",
        "ZXing · ZXing Android Embedded" to "Apache 2.0",
        "WebRTC" to "BSD 3-Clause",
        "Space Grotesk · Inter" to "SIL Open Font License",
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Crediti"), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingMark(height = 96.dp)
                Spacer(Modifier.height(18.dp))
                Text("Monolith", style = MaterialTheme.typography.displaySmall)
                Text(t("Versione {0}", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("Ideato, progettato e sviluppato da"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Christian “Xoushal” Vedovi", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(t("Unico sviluppatore"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionLabel(t("API e servizi"), Modifier.padding(top = 20.dp))
            apis.forEach { (n, d) ->
                Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text(n, style = MaterialTheme.typography.titleSmall)
                    Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionLabel(t("Librerie open source"), Modifier.padding(top = 16.dp))
            libs.forEach { (n, l) ->
                Row(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
                    Text(n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("© 2026 Christian Vedovi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
        }
    }
}
