package com.monolith.app.ui.screens

import androidx.compose.foundation.layout.heightIn

import androidx.compose.material.icons.rounded.Wifi

import androidx.compose.material.icons.rounded.Notifications

import androidx.compose.material.icons.rounded.Vibration

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.rounded.Edit

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight

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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.MusicNote
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
import com.monolith.app.ui.components.MonoDialog

@Composable
fun LanguageDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    MonoDialog(
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

    var showCode by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    var mapSheet by remember { mutableStateOf(false) }
    val myBday by Graph.birthdays.mine.collectAsState()
    var bdayPicker by remember { mutableStateOf(false) }
    val mapStyleIdx by Graph.prefs.mapStyle.collectAsState()
    val mapTone by Graph.prefs.mapTone.collectAsState()
    var alwaysOnline by remember { mutableStateOf(Graph.prefs.alwaysOnline) }
    var iconSheet by remember { mutableStateOf(false) }
    var themeSheet by remember { mutableStateOf(false) }
    var feedbackDialog by remember { mutableStateOf(false) }
    var verifyDialog by remember { mutableStateOf(false) }
    var medSheet by remember { mutableStateOf(false) }
    var shakeOn by remember { mutableStateOf(com.monolith.app.ui.screens.ChatLooks.shake) }
    var weeklyOn by remember { mutableStateOf(com.monolith.app.net.Weekly.enabled(ctx)) }
    var lockOn by remember { mutableStateOf(Graph.prefs.appLock) }
    var backupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
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
            Text(t("Impostazioni"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Surface(
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
                    .bounceClick(0.98f) { nav.navigate(com.monolith.app.ui.Routes.profile(myPub)) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.emoji, 60.dp, Modifier.sharedBoundsKey("me-avatar"))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            com.monolith.app.ui.components.VerifiedBadge(myPub, 20.dp)
                        }
                        Text("@${p.username}  ·  #${tagOf(myPub)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t("Vedi il tuo profilo"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingRow(Icons.Rounded.Edit, t("Modifica profilo"), t("Immagine, nome, bio, copertina e distintivi")) { nav.navigate("editprofile") }
            SettingRow(Icons.Rounded.QrCode2, t("Il mio codice QR"), t("Fatti aggiungere di persona")) { showQr = true }
            val devNow by Graph.dev.state.collectAsState()
            val myVerified = devNow.verified[myPub]
            if (myVerified != null) {
                SettingRow(Icons.Rounded.Verified, t("Account verificato"), myVerified.title.ifBlank { t("Verificato dallo sviluppatore") }, trailing = { com.monolith.app.ui.components.VerifiedBadge(myPub, 22.dp) }) {}
            } else {
                val asked = Graph.dev.verifyAskedAt
                SettingRow(
                    Icons.Rounded.Verified, t("Richiedi la verifica"),
                    if (asked > 0) t("Richiesta inviata il {0}", java.text.SimpleDateFormat("d MMM", I18n.locale).format(java.util.Date(asked)))
                    else t("Conferma che sei davvero tu e ottieni il simbolo di verificato"),
                ) {
                    if (devNow.active) verifyDialog = true
                    else Toast.makeText(ctx, t("Le verifiche non sono ancora aperte: riprova tra poco"), Toast.LENGTH_SHORT).show()
                }
            }

            SectionLabel(t("Aspetto"), Modifier.padding(top = 20.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                listOf(t("Sistema"), t("Chiaro"), t("Scuro")).forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = theme == i, onClick = { Graph.prefs.setTheme(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text(label) }
                }
            }
            val paletteKey by Graph.prefs.palette.collectAsState()
            SettingRow(Icons.Rounded.Palette, t("Colori"), t(com.monolith.app.ui.theme.Palettes.byKey(paletteKey).name), trailing = { PaletteSwatch() }) { themeSheet = true }
            SettingRow(Icons.Rounded.Translate, t("Lingua"), I18n.LANGS.first { it.first == I18n.lang }.second) { langDialog = true }
            SettingRow(Icons.Rounded.Map, t("Mappa"), t(MapLook.style(mapStyleIdx).name) + " · " + MapLook.toneLabel(mapTone)) { mapSheet = true }
            SettingRow(Icons.Rounded.MusicNote, t("Suoni e vibrazione"), t("Tasti, suoneria e notifiche")) { nav.navigate("sounds") }
            SettingRow(Icons.Rounded.Apps, t("Icona dell'app"), t("Scegli come appare sulla schermata Home")) { iconSheet = true }
            SettingRow(Icons.Rounded.Wallpaper, t("Sfondo animato"), t("Il monolite tra le stelle come sfondo del telefono")) {
                val comp = android.content.ComponentName(ctx, com.monolith.app.widget.MonolithWallpaper::class.java)
                val i = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp)
                runCatching { ctx.startActivity(i) }.onFailure {
                    runCatching { ctx.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                }
            }

            SectionLabel(t("Funzioni"), Modifier.padding(top = 20.dp))
            SettingSwitch(Icons.Rounded.Vibration, t("Scuoti per un trillo"), t("In una chat, scuoti il telefono per mandare un trillo"), shakeOn) {
                shakeOn = it
                com.monolith.app.ui.screens.ChatLooks.shake = it
            }
            SettingSwitch(Icons.Rounded.Notifications, t("Riepilogo della domenica"), t("Una notifica ogni domenica alle 18"), weeklyOn) {
                weeklyOn = it
                com.monolith.app.net.Weekly.setEnabled(ctx, it)
            }
            SettingRow(Icons.Rounded.AutoAwesome, t("La tua settimana"), t("Gli ultimi 7 giorni, raccontati in breve")) { nav.navigate("wrapped") }
            SettingRow(Icons.Rounded.Route, t("Diario dei luoghi"), t("Dove ti sei fermato, solo su questo telefono")) { nav.navigate("diary") }
            SettingRow(Icons.Rounded.Movie, t("Rivedi il filmato"), t("Tutte le funzioni di Monolith in un minuto")) { nav.navigate("film") }

            SectionLabel(t("Privacy e sicurezza"), Modifier.padding(top = 20.dp))
            SettingSwitch(Icons.Rounded.Fingerprint, t("Blocco app"), t("Impronta, volto o PIN all'apertura"), lockOn) { on ->
                if (!Unlock.available(ctx)) {
                    Toast.makeText(ctx, t("Imposta prima un blocco schermo sul telefono"), Toast.LENGTH_LONG).show()
                } else {
                    Unlock.prompt(ctx, if (on) t("Attiva il blocco") else t("Disattiva il blocco")) {
                        lockOn = on
                        AppLock.setEnabled(on)
                    }
                }
            }
            SettingRow(Icons.Rounded.MedicalServices, t("Scheda medica"), t("Inviata solo insieme all'SOS")) { medSheet = true }
            SettingRow(Icons.Rounded.Key, t("Codice di recupero"), t("Serve per ripristinare il profilo su un altro telefono")) {
                if (Unlock.available(ctx)) Unlock.prompt(ctx, t("Mostra il codice di recupero")) { showCode = true } else showCode = true
            }
            SettingRow(Icons.Rounded.Backup, t("Backup cifrato"), t("Salva chat, agenda, liste e amici in un file protetto da password")) { backupDialog = true }
            AutoBackupRow()

            SectionLabel(t("Connessione"), Modifier.padding(top = 20.dp))
            SettingSwitch(Icons.Rounded.Wifi, t("Sempre attivo"), t("Ricevi messaggi e chiamate anche ad app chiusa"), alwaysOnline) {
                alwaysOnline = it
                Graph.prefs.alwaysOnline = it
                if (it) MonolithService.start(ctx) else if (!Graph.location.sharing.value) MonolithService.stop(ctx)
            }
            SettingRow(Icons.Rounded.Shield, t("Gestisci permessi"), t("Notifiche, posizione, fotocamera, microfono, batteria")) { nav.navigate("permissions?first=false") }

            SectionLabel(t("Monolith"), Modifier.padding(top = 20.dp))
            SettingRow(Icons.Rounded.AutoStories, t("Journal"), t("Tutte le novità, versione per versione")) { nav.navigate("journal") }
            SettingRow(Icons.Rounded.Favorite, t("Crediti"), "Christian “Xoushal” Vedovi") { nav.navigate("credits") }
            val devState by Graph.dev.state.collectAsState()
            if (devState.latest != null && Graph.dev.updateAvailable) {
                SettingRow(Icons.Rounded.SystemUpdate, t("Aggiornamento disponibile"), t("Versione {0}: tocca per scaricarla", devState.latest ?: "")) {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Graph.dev.updateUrl))) }
                }
            }
            if (devState.active) SettingRow(Icons.Rounded.Mail, t("Scrivi allo sviluppatore"), t("Idee, problemi o complimenti, cifrati")) { feedbackDialog = true }
            Text(
                t("Versione {0}", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
            TextButton(onClick = { wipe = true }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(t("Esci ed elimina i dati da questo telefono"), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showQr) MyQrDialog { showQr = false }
    if (mapSheet) MapStyleSheet { mapSheet = false }
    if (iconSheet) com.monolith.app.ui.screens.IconSheet { iconSheet = false }
    if (themeSheet) ThemeSheet { themeSheet = false }
    if (feedbackDialog) FeedbackDialog { feedbackDialog = false }
    if (verifyDialog) VerifyRequestDialog { verifyDialog = false }
    if (medSheet) com.monolith.app.ui.screens.MedicalSheet { medSheet = false }
    if (bdayPicker) BirthdayPickerDialog { bdayPicker = false }
    if (langDialog) LanguageDialog(onDismiss = { langDialog = false }) { I18n.set(ctx, it); langDialog = false }

    if (showCode) {
        val code = remember { Graph.identity.recoveryCode() }
        MonoDialog(
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
        MonoDialog(
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
        MonoDialog(
            onDismissRequest = { wipe = false },
            title = { Text(t("Eliminare tutto?")) },
            text = { Text(t("Chat, agenda, liste e amici verranno cancellati da questo telefono. Senza il codice di recupero o un backup non potrai recuperare questo profilo.")) },
            confirmButton = {
                TextButton(onClick = {
                    wipe = false
                    Graph.scope.launch {
                        Graph.relays.stop()
                        Graph.db.clearAllTables()
                        Graph.places.clear()
                        Graph.scheduled.clear(); Graph.pins.clear(); Graph.statuses.clear(); Graph.together.clear(); Graph.bondDays.clear(); Graph.favorites.clear(); Graph.saved.clear(); Graph.guard.clear(); Graph.medical.clear(); Graph.diary.setEnabled(false); Graph.diary.clear(); com.monolith.app.ui.screens.Countdowns.clear(); Graph.profileExtras.clear(); com.monolith.app.net.Badges.clear(ctx); Graph.nearby.clear(); com.monolith.app.core.AutoBackup.clear(ctx)
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
internal fun SettingRow(icon: ImageVector, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** a settings row with a switch: tapping the row flips it too */
@Composable
internal fun SettingSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, trailing = { Switch(checked = checked, onCheckedChange = onChange) }) { onChange(!checked) }
}

// ====================================================================== journal

private class Release(val version: String, val date: String, val notes: List<String>)

private val JOURNAL = listOf(
    Release(
        "2.4.0", "2026-11-26",
        listOf(
            "Colori: dodici temi da scegliere in Impostazioni, più uno che prende i colori dal tuo sfondo",
            "Il cambio dell'icona ora funziona: Monolith si chiude un attimo e si riapre dalla nuova icona",
            "Chiamate e videochiamate ridisegnate: cinque tasti uguali in basso, il resto in «Altro», e durante il video i comandi si nascondono con un tocco",
            "Cuffie Bluetooth, con filo o USB: in chiamata Monolith le trova da solo e ci porta l'audio, e puoi scegliere da dove ascoltare e quale microfono usare",
            "Videocamera anche nelle chiamate vocali e nelle stanze vocali degli Shard: la accendi quando vuoi",
            "Avvisi dallo sviluppatore su novità e aggiornamenti; puoi scrivergli dalle Impostazioni e segnalare un account dal suo profilo",
        ),
    ),
    Release(
        "2.3.1", "2026-11-21",
        listOf(
            "Una piccola vibrazione quando tocchi un tasto, leggera come quella della tastiera",
            "Si spegne da Impostazioni, in Suoni e vibrazione, con Vibrazione dei tasti",
        ),
    ),
    Release(
        "2.3.0", "2026-11-21",
        listOf(
            "Profilo rifatto: copertina, immagine e informazioni più ordinate, con i numeri in evidenza e i tasti ben separati",
            "Tasto Modifica in alto a destra nel tuo profilo: immagine, nome, bio, stato, compleanno, copertina e distintivi in un'unica schermata",
            "Distintivi disegnati a mano uno per uno, e fino a 3 in vetrina sotto al tuo nome",
            "Dal profilo di un amico scrivi, chiami o videochiami con un tocco; legame e amico nei paraggi stanno nella parte Voi due",
            "Nuova storia da un unico tasto: foto, testo o canzone dal telefono",
            "Clubhouse ora si chiama Chat e nella barra in basso non c'è più la linea prima di Shard",
            "Impostazioni divise in gruppi chiari: Aspetto, Funzioni, Privacy e sicurezza, Connessione",
            "Spaziature e allineamenti sistemati in Chat, Shard, Liste, storie e filmato",
            "Più fluida: l'effetto inclinazione e le barre di avanzamento non ridisegnano più tutta la schermata",
        ),
    ),
    Release(
        "2.2.0", "2026-11-21",
        listOf(
            "Un filmato animato alla prima apertura racconta tutte le funzioni; lo rivedi quando vuoi dalle Impostazioni",
            "Distintivi: tanti emblemi da sbloccare usando l'app, visibili nel tuo profilo e in quello degli amici",
            "Copertina del profilo: scegli tra le copertine preimpostate, dietro alla tua foto come su Facebook",
            "Sticker personali: crea sticker dalle tue foto, con forme, filtri e scritte, e salva quelli che ricevi",
            "Rispondi alle storie: una risposta o una reazione arriva in chat con l'anteprima della storia",
            "Inoltra messaggi, foto, vocali e sticker ad altre chat",
            "Date nei messaggi: «domani alle 9» o «sabato» diventano un tocco per creare l'evento",
            "Shard: stanze private visibili solo ad alcuni membri, messaggio di benvenuto e titoli personalizzati",
            "Ascolto insieme: in chiamata scegli una canzone e parte nello stesso momento per tutti",
            "Anniversari d'amicizia: un avviso nel giorno in cui siete diventati amici, e da quanto nel profilo",
            "Amico nei paraggi: se lo attivate in due, un avviso quando siete a meno di 500 m",
            "Backup automatico: ogni settimana un backup cifrato nella cartella che scegli, con le ultime 4 copie",
        ),
    ),
    Release(
        "2.1.0", "2026-11-21",
        listOf(
            "Gli Shard hanno la loro scheda nella barra in basso, separata dalle altre, con l'icona di un frammento di monolite",
            "Come su Discord: a sinistra la colonna con tutti i tuoi Shard, a destra stanze scritte, vocali, bacheca, eventi e membri",
            "Gli Shard non sono più in Clubhouse e i loro messaggi da leggere hanno un contatore a parte",
        ),
    ),
    Release(
        "2.0.0", "2026-11-21",
        listOf(
            "Giochi in chat: Tris, Forza 4 e Impiccato a turni, con l'avviso quando tocca a te",
            "Dadi e moneta: un lancio con lo stesso risultato per tutti, animato",
            "Foto a vista unica: si apre una volta, senza screenshot, poi sparisce",
            "Lavagna condivisa: disegnate insieme in tempo reale e inviate il disegno in chat",
            "Menzioni @ nei gruppi e negli Shard, che arrivano anche con le notifiche silenziate",
            "Gruppi silenziabili, sfondi in bianco e nero per ogni chat, trillo scuotendo il telefono",
            "Vocali a 1,5× e 2×; avvicinando il telefono all'orecchio si ascoltano come una chiamata",
            "Blocca screenshot in una chat (per entrambi) e avviso quando qualcuno fa uno screenshot (Android 14 e successivi)",
            "Conto alla rovescia per gli eventi, in cima all'agenda e come widget",
            "Meteo del giorno e del luogo dell'evento, direttamente in Agenda",
            "Icone alternative per la schermata Home",
            "Scheda medica inviata insieme all'SOS",
            "Diario dei luoghi: le tue soste, solo su questo telefono",
            "Filtri in bianco e nero per foto e storie, con didascalia",
            "Musica nelle storie: anteprime da iTunes o un pezzo di canzone dal telefono",
            "Shard: bacheca, eventi con chi partecipa, fondatore e moderatori",
            "Condivisione dello schermo nelle chiamate e nelle stanze vocali",
            "Bussola che punta verso l'amico che condivide la posizione",
            "Batti cinque per aggiungere un amico senza QR",
            "Arrivo in diretta: la notifica si riempie mentre l'amico si avvicina",
            "Video in una finestrella quando esci dall'app durante una videochiamata",
            "In chiamata lo schermo si spegne vicino alla faccia",
            "Profondità: profilo, Legame e riepilogo si muovono inclinando il telefono",
            "Sfondo animato per il telefono con il monolite tra le stelle",
        ),
    ),
    Release(
        "1.1.0", "2026-11-21",
        listOf(
            "Legame accanto ai nomi: il piccolo monolite con la serie compare quando vi scrivete da almeno 100 giorni di fila",
            "La serie del Legame non si perde più con i messaggi che si autoeliminano",
            "Riepilogo della domenica: nuova slide con il legame più forte",
            "Amici preferiti: stella nel profilo, in cima a Clubhouse e nel widget Amici",
            "Batteria degli amici mentre condividono la posizione, con avviso quando il telefono sta per spegnersi",
            "Salvati: tieni premuto un messaggio o una foto e salvalo nel profilo dell'amico, resta lì anche se la chat sparisce",
        ),
    ),
    Release(
        "1.0.0", "2026-11-20",
        listOf(
            "Riepilogo della domenica: ogni domenica alle 18 la tua settimana in slide animate",
            "Le persone con cui hai parlato di più, la tua emoji, chiamate, ritmo, luoghi, foto, agenda e abitudini",
            "Un titolo per la tua settimana, da condividere con chi vuoi",
            "Widget Amici per la schermata Home: stato, dove si trovano, messaggi da leggere; un tocco apre la chat",
            "Il riepilogo si guarda quando vuoi da Impostazioni, e l'avviso della domenica si può spegnere",
        ),
    ),
    Release(
        "0.9.0", "2026-11-20",
        listOf(
            "Profili: tocca un amico per vedere bio, stato, compleanno e storie",
            "Storie di 24 ore, con foto o solo testo, che vedono i tuoi amici",
            "Legame: per ogni amico un monolite che cresce con i giorni in cui vi sentite",
            "Messaggio da grattare: arriva coperto e si scopre strisciando col dito",
        ),
    ),
    Release(
        "0.8.0", "2026-11-20",
        listOf(
            "Spese condivise: chi ha pagato cosa, saldi e il modo più semplice per pareggiare",
            "Album condivisi: ognuno aggiunge le sue foto, con i commenti",
            "Abitudini da soli o con gli amici, con la serie di giorni di fila",
        ),
    ),
    Release(
        "0.7.0", "2026-11-20",
        listOf(
            "Shard: spazi per te e i tuoi amici, con stanze scritte e stanze vocali",
            "Stanze vocali sempre aperte: entri ed esci quando vuoi, fino a 4 persone insieme",
            "Chi crea lo shard aggiunge stanze, lo rinomina e gestisce i membri; tutti possono invitare amici",
        ),
    ),
    Release(
        "0.6.0", "2026-11-20",
        listOf(
            "Messaggi programmati: scrivi ora, parte all'ora che scegli",
            "Cerca nei messaggi di tutte le chat",
            "Messaggi fissati in cima alla chat, per tutti",
            "Stato di 24 ore: un'emoji e una frase che vedono gli amici",
            "Agenda più semplice: i promemoria su luogo ora stanno nella scheda del luogo, sulla mappa",
            "Suoni spenti di serie: si attivano da Impostazioni → Suoni",
        ),
    ),
    Release(
        "0.5.0", "2026-11-20",
        listOf(
            "Promemoria: «+10 min», «+1 ora» e «Fatto» direttamente dalla notifica",
            "Eventi da spuntare come fatti toccando la loro emoji",
            "Vista a ore della giornata, con gli eventi come blocchi",
            "Compleanni degli amici in agenda, con un avviso alle 9 del giorno giusto",
            "Promemoria su luogo: «quando arrivo a casa ricordami di…»",
            "Luoghi anche solo tuoi, senza amici",
            "Tasto SOS sulla mappa: tieni premuto e tutti i tuoi amici ricevono un avviso con la tua posizione",
        ),
    ),
    Release(
        "0.4.0", "2026-11-20",
        listOf(
            "Suoni calmi creati per Monolith: tasti leggeri, 5 suonerie, suoni per messaggi, incontri e promemoria",
            "Nuova pagina Suoni nelle impostazioni, con ascolto di prova",
            "Toni discreti durante le chiamate: attesa, collegamento e fine",
            "Avviso «Sei con…» quando raggiungi un luogo comune dove ci sono già amici",
        ),
    ),
    Release(
        "0.3.0", "2026-11-20",
        listOf(
            "Luoghi comuni: segna casa, ufficio o palestra con un raggio da 10 a 100 metri e condividili con gli amici",
            "Quando tu o un amico arrivate in un luogo comune, l'altro riceve un messaggio in chat e una notifica",
            "La mappa ora ha due sezioni: Persone e Luoghi comuni",
            "I vecchi avvisi di arrivo sono stati sostituiti dai luoghi comuni",
            "Corretto il promemoria degli eventi che arrivava più volte",
        ),
    ),
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

/** "14 marzo" in the app's language */
fun birthdayText(b: com.monolith.app.data.Birthday): String {
    val c = java.util.Calendar.getInstance().apply { clear(); set(2000, b.month - 1, b.day) }
    return java.text.SimpleDateFormat("d MMMM", java.util.Locale(com.monolith.app.core.I18n.lang)).format(c.time)
}
