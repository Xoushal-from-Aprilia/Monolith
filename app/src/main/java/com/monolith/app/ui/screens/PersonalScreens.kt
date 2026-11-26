package com.monolith.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.t
import com.monolith.app.data.AppIcons
import com.monolith.app.data.Medical
import com.monolith.app.service.MonolithService
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick

// ====================================================================== medical card

@Composable
fun MedicalBox(med: Medical, ink: Color) {
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = 0.1f)).padding(12.dp)) {
        Text("⚕️  " + t("Scheda medica"), color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        listOf(
            t("Gruppo sanguigno") to med.blood, t("Allergie") to med.allergies, t("Farmaci") to med.medicines,
            t("Condizioni") to med.conditions, t("Contatto di emergenza") to med.contact, t("Note") to med.notes,
        ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
            Spacer(Modifier.height(4.dp))
            Text(k, color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Text(v, color = ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MedicalSheet(onDismiss: () -> Unit) {
    val cur by Graph.medical.card.collectAsState()
    var blood by remember { mutableStateOf(cur.blood) }
    var allergies by remember { mutableStateOf(cur.allergies) }
    var medicines by remember { mutableStateOf(cur.medicines) }
    var conditions by remember { mutableStateOf(cur.conditions) }
    var contact by remember { mutableStateOf(cur.contact) }
    var notes by remember { mutableStateOf(cur.notes) }
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(t("Scheda medica"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("Resta sul telefono e viene inviata solo insieme all'SOS, a tutti i tuoi amici."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("0+", "0−", "A+", "A−", "B+", "B−", "AB+", "AB−").forEach { g ->
                    val sel = blood == g
                    Surface(
                        shape = CircleShape,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.bounceClick(0.9f) { blood = if (sel) "" else g },
                    ) {
                        Text(g, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(allergies, { allergies = it.take(200) }, label = { Text(t("Allergie")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(medicines, { medicines = it.take(200) }, label = { Text(t("Farmaci")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(conditions, { conditions = it.take(200) }, label = { Text(t("Condizioni")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(contact, { contact = it.take(120) }, label = { Text(t("Contatto di emergenza")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it.take(300) }, label = { Text(t("Note")) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Button(onClick = {
                Graph.medical.save(Medical(blood, allergies.trim(), medicines.trim(), conditions.trim(), contact.trim(), notes.trim()))
                onDismiss()
            }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(t("Salva")) }
        }
    }
}

// ====================================================================== app icon

@Composable
fun IconSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cur = remember { AppIcons.actual(ctx) }
    var ask by remember { mutableStateOf<AppIcons.Look?>(null) }
    ask?.let { look ->
        MonoDialog(
            onDismissRequest = { ask = null },
            title = { Text(t("Cambiare icona?")) },
            text = { Text(t("Per cambiare l'icona Monolith si chiude un attimo: riaprila dalla nuova icona. Se sparisce dalla schermata Home, la trovi tra tutte le app e puoi rimetterla.")) },
            confirmButton = { TextButton(onClick = { ask = null; AppIcons.apply(ctx, look.key) }) { Text(t("Cambia")) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(t("Annulla")) } },
        )
    }
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(t("Icona dell'app"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("Dopo la scelta Monolith si chiude e si riapre dalla nuova icona."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 3) {
                AppIcons.LOOKS.forEach { look ->
                    val sel = look.key == cur
                    val res = remember(look.fg) { ctx.resources.getIdentifier(look.fg, "drawable", ctx.packageName) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)).background(Color(look.bg))
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
                                .bounceClick(0.92f) { if (!sel) ask = look },
                            contentAlignment = Alignment.Center,
                        ) { if (res != 0) Image(painterResource(res), null, Modifier.fillMaxSize()) }
                        Spacer(Modifier.height(4.dp))
                        Text(t(look.label), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ====================================================================== diary of places

private fun stayText(ms: Long): String {
    val min = (ms / 60_000L).coerceAtLeast(1)
    return if (min < 60) t("{0} min", min) else t("{0} h {1} min", min / 60, "%02d".format(min % 60))
}

@Composable
fun DiaryScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val on by Graph.diary.enabled.collectAsState()
    val visits by Graph.diary.visits.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<com.monolith.app.location.Visit?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            Graph.diary.setEnabled(true)
            MonolithService.start(ctx)
        }
    }
    val byDay = visits.sortedByDescending { it.start }.groupBy { Fmt.startOfDay(it.start) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Diario dei luoghi"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("Registra dove mi fermo"), style = MaterialTheme.typography.titleMedium)
                                Text(t("Le soste di almeno 10 minuti"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = on, onCheckedChange = {
                                if (it) {
                                    val perms = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                                    perm.launch(perms.toTypedArray())
                                } else Graph.diary.setEnabled(false)
                            })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "🔒  " + t("Il diario resta solo su questo telefono e non viene mai inviato a nessuno. Si cancella da solo dopo 90 giorni."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (on && Build.VERSION.SDK_INT >= 29) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                t("Per registrare anche ad app chiusa, consenti la posizione «Sempre» nelle impostazioni di Android."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (visits.isEmpty()) item {
                Text(
                    if (on) t("Ancora nessuna sosta: torna più tardi.") else t("Attiva il diario per iniziare."),
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            byDay.forEach { (day, list) ->
                item(key = "d$day") {
                    Text(Fmt.dayLong(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp))
                }
                items(list, key = { it.id }) { v ->
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${v.lat},${v.lon}?q=${v.lat},${v.lon}"))) } },
                            onLongClick = { remove = v },
                        ).padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(62.dp)) {
                            Text(Fmt.time(v.start), style = MaterialTheme.typography.labelLarge)
                            Text(Fmt.time(v.end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name.ifBlank { "%.4f, %.4f".format(v.lat, v.lon) }, style = MaterialTheme.typography.bodyLarge)
                            Text(stayText(v.end - v.start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (visits.isNotEmpty()) item {
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedButton(onClick = { confirmClear = true }, shape = CircleShape, modifier = Modifier.padding(horizontal = 24.dp)) { Text(t("Cancella tutto il diario")) }
            }
        }
    }
    if (confirmClear) MonoDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(t("Cancellare il diario?")) },
        confirmButton = { TextButton(onClick = { Graph.diary.clear(); confirmClear = false }) { Text(t("Cancella")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("Annulla")) } },
    )
    remove?.let { v ->
        MonoDialog(
            onDismissRequest = { remove = null },
            title = { Text(t("Togliere questa sosta?")) },
            confirmButton = { TextButton(onClick = { Graph.diary.remove(v.id); remove = null }) { Text(t("Togli")) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(t("Annulla")) } },
        )
    }
}
