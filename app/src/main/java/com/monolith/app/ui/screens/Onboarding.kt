package com.monolith.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.monolith.app.Graph
import com.monolith.app.core.IdentityStore
import com.monolith.app.core.Profile
import com.monolith.app.crypto.Hex
import com.monolith.app.crypto.Secp256k1
import com.monolith.app.nostr.Envelope
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.MonolithMark
import com.monolith.app.ui.components.PopIn
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun OnboardingFlow() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var imported by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(Unit) { Graph.relays.start(Graph.prefs.relays.value) }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val fwd = targetState > initialState
            (slideInHorizontally(tween(450, easing = Motion.Emphasized)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(450, easing = Motion.Emphasized)) { if (fwd) -it / 4 else it / 4 } + fadeOut(tween(200)))
        },
        label = "onboarding",
    ) { s ->
        when (s) {
            0 -> Welcome(onStart = { imported = null; step = 1 }, onImport = { step = 2 })
            1 -> ProfileSetup(imported = imported, onBack = { step = if (imported != null) 2 else 0 })
            else -> ImportKey(onBack = { step = 0 }, onKey = { imported = it; step = 1 })
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onImport: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val inf = rememberInfiniteTransition(label = "welcome")
    val sweep by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep")
    val float by inf.animateFloat(0f, -8f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(900, easing = Motion.EmphasizedDecel)) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MonolithMark(Modifier.height(180.dp).graphicsOffset(float), rise = rise.value, sweep = sweep)
        }
        Spacer(Modifier.height(36.dp))
        PopIn(250) {
            Column {
                Text("Monolith", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Agenda, chat e posizione in tempo reale.\nSolo tu e i tuoi amici, senza server.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        val features = listOf(
            Triple(Icons.Rounded.CalendarMonth, "Agenda", "Eventi, promemoria con emoji, inviti agli amici"),
            Triple(Icons.Rounded.Forum, "Clubhouse", "Chat private e di gruppo, foto, trilli"),
            Triple(Icons.Rounded.LocationOn, "Posizione live", "Peer-to-peer con WebRTC, cifrata end-to-end"),
        )
        features.forEachIndexed { i, (icon, title, desc) ->
            PopIn(450 + i * 120) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PopIn(900) {
            Column {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                ) { Text("Crea il tuo profilo", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Key, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ho già una chiave Monolith")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun Modifier.graphicsOffset(dy: Float): Modifier = this.graphicsLayer { translationY = dy * density }

private enum class NameState { IDLE, INVALID, CHECKING, FREE, TAKEN, OFFLINE }

@Composable
private fun ProfileSetup(imported: ByteArray?, onBack: () -> Unit) {
    var emoji by rememberSaveable { mutableStateOf("🗿") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(NameState.IDLE) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val importedPub = remember(imported) { imported?.let { Hex.encode(Secp256k1.publicKey(it)) } }

    // restore a profile previously published with this key
    LaunchedEffect(importedPub) {
        val pk = importedPub ?: return@LaunchedEffect
        val evs = Graph.relays.query(
            listOf(
                JSONObject().put("kinds", JSONArray().put(Envelope.KIND_PROFILE)).put("authors", JSONArray().put(pk))
                    .put("#d", JSONArray().put("monolith-profile"))
            )
        )
        evs.filter { it.verify() }.maxByOrNull { it.createdAt }?.let {
            val p = Profile.fromJson(JSONObject(it.content))
            emoji = p.emoji; name = p.displayName; username = p.username
        }
    }

    LaunchedEffect(username) {
        val u = Profile.normalizeUsername(username)
        if (u.isEmpty()) { state = NameState.IDLE; return@LaunchedEffect }
        if (!Profile.isValidUsername(u)) { state = NameState.INVALID; return@LaunchedEffect }
        state = NameState.CHECKING
        delay(650)
        if (Graph.relays.connectedCount == 0) delay(1500)
        if (Graph.relays.connectedCount == 0) { state = NameState.OFFLINE; return@LaunchedEffect }
        val found = Graph.messenger.searchUsers(u).filter { it.pubkey != importedPub }
        state = if (found.isEmpty()) NameState.FREE else NameState.TAKEN
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
        Spacer(Modifier.height(8.dp))
        Text("Chi sei?", style = MaterialTheme.typography.displaySmall)
        Text(
            "Il tuo username serve agli amici per trovarti.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    emoji,
                    transitionSpec = { (scaleIn(Motion.bouncy(), initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 1.2f) + fadeOut()) },
                    label = "avatar",
                ) { e -> Avatar(e, 112.dp, Modifier.bounceClick(0.9f) { picker = true }) }
                Spacer(Modifier.height(8.dp))
                Text("Tocca per cambiare", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = { Text("Nome visualizzato") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20) },
            label = { Text("Username") },
            prefix = { Text("@") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            isError = state == NameState.TAKEN || state == NameState.INVALID,
            supportingText = {
                AnimatedContent(state, label = "nameState") { s ->
                    Text(
                        when (s) {
                            NameState.IDLE -> "3–20 caratteri: lettere, numeri, _ e ."
                            NameState.INVALID -> "Usa 3–20 caratteri: lettere, numeri, _ e ."
                            NameState.CHECKING -> "Controllo disponibilità…"
                            NameState.FREE -> "✓ Disponibile"
                            NameState.TAKEN -> "Già usato da qualcun altro"
                            NameState.OFFLINE -> "Non riesco a verificare ora, puoi continuare"
                        }
                    )
                }
            },
        )
        Spacer(Modifier.height(28.dp))
        val ok = name.isNotBlank() && (state == NameState.FREE || state == NameState.OFFLINE) && !creating
        Button(
            onClick = {
                creating = true
                scope.launch {
                    val p = Profile(Profile.normalizeUsername(username), name.trim(), emoji)
                    Graph.identity.create(p, imported)
                    Graph.startEngine()
                }
            },
            enabled = ok,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
        ) { Text(if (creating) "Creo la tua identità…" else "Entra in Monolith", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Monolith crea una chiave crittografica solo sul tuo telefono: è la tua identità. Nessuna email, nessun numero, nessun server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }, onPick = { emoji = it; picker = false })
}

@Composable
private fun ImportKey(onBack: () -> Unit, onKey: (ByteArray) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { IdentityStore.parseSecret(text) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .imePadding().padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
        Spacer(Modifier.height(8.dp))
        Text("Ripristina", style = MaterialTheme.typography.displaySmall)
        Text(
            "Incolla la chiave segreta (nsec…) che hai salvato dalle impostazioni di Monolith.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.trim() },
            label = { Text("Chiave segreta") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            isError = text.isNotEmpty() && parsed == null,
            supportingText = { if (text.isNotEmpty() && parsed == null) Text("Chiave non valida") },
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { parsed?.let(onKey) },
            enabled = parsed != null,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 0.dp),
            shape = CircleShape,
        ) { Text("Continua", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
    }
}

// ====================================================================== permissions

private class Perm(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val runtime: List<String> = emptyList(),
    val check: (Context) -> Boolean,
    val settingsIntent: ((Context) -> Intent)? = null,
)

private fun granted(ctx: Context, p: String) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@SuppressLint("BatteryLife")
private fun permissionList(): List<Perm> = buildList {
    if (Build.VERSION.SDK_INT >= 33) {
        add(Perm("notif", Icons.Rounded.Notifications, "Notifiche", "Messaggi, inviti e promemoria con la tua emoji",
            listOf(Manifest.permission.POST_NOTIFICATIONS), { granted(it, Manifest.permission.POST_NOTIFICATIONS) }))
    }
    add(Perm("loc", Icons.Rounded.LocationOn, "Posizione", "Per condividere dove sei in tempo reale",
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        { granted(it, Manifest.permission.ACCESS_FINE_LOCATION) || granted(it, Manifest.permission.ACCESS_COARSE_LOCATION) }))
    add(Perm("cam", Icons.Rounded.PhotoCamera, "Fotocamera", "Scatta foto da mandare in chat",
        listOf(Manifest.permission.CAMERA), { granted(it, Manifest.permission.CAMERA) }))
    add(Perm("mic", Icons.Rounded.Mic, "Microfono", "Chiamate vocali peer-to-peer",
        listOf(Manifest.permission.RECORD_AUDIO), { granted(it, Manifest.permission.RECORD_AUDIO) }))
    add(Perm("battery", Icons.Rounded.BatteryChargingFull, "Sempre online", "Evita che Android chiuda Monolith in background",
        check = { c -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) },
        settingsIntent = { c -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)) }))
    if (Build.VERSION.SDK_INT in 31..32) {
        add(Perm("alarm", Icons.Rounded.Alarm, "Promemoria precisi", "Notifiche dell'agenda all'orario esatto",
            check = { c -> c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
            settingsIntent = { c -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.packageName)) }))
    }
    if (Build.VERSION.SDK_INT >= 34) {
        add(Perm("fsi", Icons.Rounded.Call, "Chiamate a schermo intero", "Mostra le chiamate in arrivo anche a schermo bloccato",
            check = { c -> c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
            settingsIntent = { c -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + c.packageName)) }))
    }
}

@Composable
fun PermissionsScreen(first: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val perms = remember { permissionList() }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val states = remember(tick) { perms.associate { it.key to it.check(ctx) } }
    var chainBattery by remember { mutableStateOf(false) }

    val batteryPerm = perms.first { it.key == "battery" }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val all = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (chainBattery && !batteryPerm.check(ctx)) {
            runCatching { ctx.startActivity(batteryPerm.settingsIntent!!(ctx)) }
        }
        chainBattery = false
    }

    fun request(p: Perm) {
        if (p.runtime.isNotEmpty()) single.launch(p.runtime.toTypedArray())
        else p.settingsIntent?.let { runCatching { ctx.startActivity(it(ctx)) } }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (!first) {
                IconButton(onClick = onDone, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
            } else Spacer(Modifier.height(32.dp))
            Text("Permessi", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                "Ti chiedo solo ciò che serve. Puoi cambiarli quando vuoi dalle impostazioni.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            perms.forEachIndexed { i, p ->
                PopIn(80 * i) {
                    PermissionCard(p, states[p.key] == true) { request(p) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Column(Modifier.padding(20.dp)) {
            val missingRuntime = perms.filter { states[it.key] != true }.flatMap { it.runtime }
            val anyMissing = perms.any { states[it.key] != true }
            Button(
                onClick = {
                    if (missingRuntime.isNotEmpty()) {
                        chainBattery = true
                        all.launch(missingRuntime.toTypedArray())
                    } else if (!batteryPerm.check(ctx)) {
                        runCatching { ctx.startActivity(batteryPerm.settingsIntent!!(ctx)) }
                    } else onDone()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) { Text(if (anyMissing) "Consenti tutto" else "Tutto pronto · Continua", style = MaterialTheme.typography.titleMedium) }
            if (anyMissing) {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (first) "Più tardi" else "Chiudi") }
            }
        }
    }
}

@Composable
private fun PermissionCard(p: Perm, ok: Boolean, onRequest: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(p.icon, null, Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text(p.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                ok,
                transitionSpec = { (scaleIn(Motion.bouncy(), initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                label = "perm",
            ) { granted ->
                if (granted) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, "Concesso", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
                } else {
                    OutlinedButton(onClick = onRequest, shape = CircleShape, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text("Consenti")
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val keepArrangement = Arrangement.Center
@Suppress("unused")
private val keepAlign = TextAlign.Center
