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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.monolith.app.Graph
import com.monolith.app.core.Backup
import com.monolith.app.core.I18n
import com.monolith.app.core.IdentityStore
import com.monolith.app.core.Profile
import com.monolith.app.core.t
import com.monolith.app.crypto.Hex
import com.monolith.app.crypto.Secp256k1
import com.monolith.app.nostr.Envelope
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.MonolithMark
import com.monolith.app.ui.components.PopIn
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun OnboardingFlow() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var imported by remember { mutableStateOf<ByteArray?>(null) }
    val ctx = LocalContext.current
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
            0 -> Welcome(onStart = { imported = null; step = if (FilmPrefs.seen(ctx)) 1 else 3 }, onRestore = { step = 2 })
            3 -> FeatureFilm(onDone = { FilmPrefs.markSeen(ctx); step = 1 })
            1 -> ProfileSetup(imported = imported, onBack = { step = if (imported != null) 2 else 0 })
            else -> Restore(onBack = { step = 0 }, onKey = { imported = it; step = 1 })
        }
    }
}

@Composable
private fun LanguageButton() {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    TextButton(onClick = { open = true }) {
        Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(I18n.LANGS.first { it.first == I18n.lang }.second)
    }
    if (open) LanguageDialog(onDismiss = { open = false }) { I18n.set(ctx, it); open = false }
}

@Composable
private fun Welcome(onStart: () -> Unit, onRestore: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val inf = rememberInfiniteTransition(label = "welcome")
    val sweep by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep")
    val float by inf.animateFloat(0f, -8f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(900, easing = Motion.EmphasizedDecel)) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            LanguageButton()
        }
        Spacer(Modifier.weight(0.5f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MonolithMark(Modifier.height(170.dp).graphicsLayer { translationY = float * density }, rise = rise.value, sweep = sweep)
        }
        Spacer(Modifier.height(32.dp))
        PopIn(250) {
            Column {
                Text("Monolith", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Agenda, chat e posizione in tempo reale.\nSolo tu e i tuoi amici."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        val features = listOf(
            Triple(Icons.Rounded.CalendarMonth, t("Agenda"), t("Eventi, liste condivise, promemoria con emoji")),
            Triple(Icons.Rounded.Forum, t("Clubhouse"), t("Chat, gruppi, vocali e videochiamate")),
            Triple(Icons.Rounded.LocationOn, t("Posizione live"), t("Condividi dove sei solo con chi vuoi")),
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
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape) {
                    Text(t("Crea il tuo profilo"), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Ho già un profilo Monolith"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

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
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
        Spacer(Modifier.height(8.dp))
        Text(t("Chi sei?"), style = MaterialTheme.typography.displaySmall)
        Text(t("Il tuo username serve agli amici per trovarti."), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    emoji,
                    transitionSpec = { (scaleIn(Motion.bouncy(), initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 1.2f) + fadeOut()) },
                    label = "avatar",
                ) { e -> Avatar(e, 112.dp, Modifier.bounceClick(0.9f) { picker = true }) }
                Spacer(Modifier.height(8.dp))
                Text(t("Tocca per cambiare"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(32) }, label = { Text(t("Nome visualizzato")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20) },
            label = { Text(t("Username")) },
            prefix = { Text("@") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            isError = state == NameState.TAKEN || state == NameState.INVALID,
            supportingText = {
                AnimatedContent(state, label = "nameState") { s ->
                    Text(
                        when (s) {
                            NameState.IDLE, NameState.INVALID -> t("3–20 caratteri: lettere, numeri, _ e .")
                            NameState.CHECKING -> t("Controllo disponibilità…")
                            NameState.FREE -> "✓ " + t("Disponibile")
                            NameState.TAKEN -> t("Già usato da qualcun altro")
                            NameState.OFFLINE -> t("Non riesco a verificare ora, puoi continuare")
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
                    Graph.identity.create(Profile(Profile.normalizeUsername(username), name.trim(), emoji), imported)
                    Graph.startEngine()
                }
            },
            enabled = ok,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
        ) { Text(if (creating) t("Preparo il tuo profilo…") else t("Entra in Monolith"), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
        Text(
            t("Il tuo profilo vive solo su questo telefono: niente email, niente numero di telefono."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }, onPick = { emoji = it; picker = false })
}

@Composable
private fun Restore(onBack: () -> Unit, onKey: (ByteArray) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    val parsed = remember(code) { IdentityStore.parseSecret(code) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { fileUri = uri; error = null } }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
        Spacer(Modifier.height(8.dp))
        Text(t("Ripristina"), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(20.dp))

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(t("Da un file di backup"), style = MaterialTheme.typography.titleMedium)
                Text(t("Recupera profilo, chat, agenda, liste e amici."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, shape = CircleShape) {
                    Text(if (fileUri == null) t("Scegli file") else t("File selezionato ✓"))
                }
                if (fileUri != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text(t("Password del backup")) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    )
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        ctx.contentResolver.openInputStream(fileUri!!)?.use { Backup.import(ctx, it, password.toCharArray()) } ?: false
                                    }.getOrDefault(false)
                                }
                                busy = false
                                if (ok) Graph.startEngine() else error = t("Password errata o file non valido")
                            }
                        },
                        enabled = password.isNotEmpty() && !busy,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) t("Ripristino in corso…") else t("Ripristina backup")) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Con il codice di recupero"), style = MaterialTheme.typography.titleMedium)
                }
                Text(t("Recupera il profilo e gli amici, non le chat."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it.trim() }, label = { Text(t("Codice di recupero")) },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    isError = code.isNotEmpty() && parsed == null,
                    supportingText = { if (code.isNotEmpty() && parsed == null) Text(t("Codice non valido")) },
                )
                Button(onClick = { parsed?.let(onKey) }, enabled = parsed != null, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Continua"))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
        add(Perm("notif", Icons.Rounded.Notifications, t("Notifiche"), t("Messaggi, inviti e promemoria con la tua emoji"),
            listOf(Manifest.permission.POST_NOTIFICATIONS), { granted(it, Manifest.permission.POST_NOTIFICATIONS) }))
    }
    add(Perm("loc", Icons.Rounded.LocationOn, t("Posizione"), t("Per condividere dove sei in tempo reale"),
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        { granted(it, Manifest.permission.ACCESS_FINE_LOCATION) || granted(it, Manifest.permission.ACCESS_COARSE_LOCATION) }))
    if (Build.VERSION.SDK_INT >= 29) {
        // optional, asked on its own: Android ignores it when bundled with other permissions
        add(Perm("bgloc", Icons.Rounded.MyLocation, t("Posizione sempre"), t("Per i luoghi comuni: avvisa gli amici quando arrivi, anche ad app chiusa"),
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), { granted(it, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }))
    }
    add(Perm("cam", Icons.Rounded.PhotoCamera, t("Fotocamera"), t("Foto, videochiamate e codici QR"),
        listOf(Manifest.permission.CAMERA), { granted(it, Manifest.permission.CAMERA) }))
    add(Perm("mic", Icons.Rounded.Mic, t("Microfono"), t("Chiamate e messaggi vocali"),
        listOf(Manifest.permission.RECORD_AUDIO), { granted(it, Manifest.permission.RECORD_AUDIO) }))
    add(Perm("battery", Icons.Rounded.BatteryChargingFull, t("Sempre attivo"), t("Evita che Android chiuda Monolith in background"),
        check = { c -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) },
        settingsIntent = { c -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)) }))
    if (Build.VERSION.SDK_INT in 31..32) {
        add(Perm("alarm", Icons.Rounded.Alarm, t("Promemoria precisi"), t("Notifiche dell'agenda all'orario esatto"),
            check = { c -> c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
            settingsIntent = { c -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.packageName)) }))
    }
    if (Build.VERSION.SDK_INT >= 34) {
        add(Perm("fsi", Icons.Rounded.Call, t("Chiamate a schermo intero"), t("Mostra le chiamate in arrivo anche a schermo bloccato"),
            check = { c -> c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
            settingsIntent = { c -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + c.packageName)) }))
    }
}

@Composable
fun PermissionsScreen(first: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val perms = remember(I18n.lang) { permissionList() }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val states = remember(tick, perms) { perms.associate { it.key to it.check(ctx) } }
    var chainBattery by remember { mutableStateOf(false) }

    val batteryPerm = perms.first { it.key == "battery" }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val all = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (chainBattery && !batteryPerm.check(ctx)) runCatching { ctx.startActivity(batteryPerm.settingsIntent!!(ctx)) }
        chainBattery = false
    }

    fun request(p: Perm) {
        if (p.key == "bgloc" && !granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            single.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (p.runtime.isNotEmpty()) single.launch(p.runtime.toTypedArray())
        else p.settingsIntent?.let { runCatching { ctx.startActivity(it(ctx)) } }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (!first) {
                IconButton(onClick = onDone, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            } else Spacer(Modifier.height(32.dp))
            Text(t("Permessi"), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                t("Ti chiedo solo ciò che serve. Puoi cambiarli quando vuoi dalle impostazioni."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            perms.forEachIndexed { i, p ->
                PopIn(80 * i) { PermissionCard(p, states[p.key] == true) { request(p) } }
                Spacer(Modifier.height(10.dp))
            }
        }
        Column(Modifier.padding(20.dp)) {
            val missingRuntime = perms.filter { states[it.key] != true && it.key != "bgloc" }.flatMap { it.runtime }
            val anyMissing = perms.any { states[it.key] != true && it.key != "bgloc" }
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
            ) { Text(if (anyMissing) t("Consenti tutto") else t("Tutto pronto · Continua"), style = MaterialTheme.typography.titleMedium) }
            if (anyMissing) {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (first) t("Più tardi") else t("Chiudi")) }
            }
        }
    }
}

@Composable
private fun PermissionCard(p: Perm, ok: Boolean, onRequest: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(p.icon, null, Modifier.size(22.dp))
            }
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
            ) { g ->
                if (g) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, t("Concesso"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                } else {
                    OutlinedButton(onClick = onRequest, shape = CircleShape, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(t("Consenti")) }
                }
            }
        }
    }
}
