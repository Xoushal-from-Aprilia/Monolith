package com.monolith.app.kids

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.FriendQr
import com.monolith.app.core.Profile
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.NavRequest
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.BigTitle
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.PopIn
import com.monolith.app.ui.components.PulseDot
import com.monolith.app.ui.components.QrImage
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** the colour of the child's slab, used across Monolith Kids */
val KidCoral = Color(0xFFF0604F)

// ====================================================================== mark

/** the parent's slab and the child's, side by side, drawn in code */
@Composable
fun KidsMark(modifier: Modifier = Modifier, rise: Float = 1f) {
    val ink = MaterialTheme.colorScheme.onBackground
    val ground = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.aspectRatio(1f)) {
        val u = size.width / 108f
        fun p(vararg xy: Float) = Path().apply {
            moveTo(xy[0] * u, xy[1] * u)
            var i = 2
            while (i < xy.size) { lineTo(xy[i] * u, xy[i + 1] * u); i += 2 }
            close()
        }
        drawRect(ground, Offset(26 * u, 78.4f * u), Size(56 * u, 0.9f * u))
        val lift = (1f - rise) * 40 * u
        translate(0f, lift) {
            drawPath(p(36f, 32f, 41f, 28f, 58f, 28f, 53f, 32f), ink.copy(alpha = 0.55f))
            drawPath(p(53f, 32f, 58f, 28f, 58f, 74.5f, 53f, 78.5f), ink.copy(alpha = 0.78f))
            drawPath(p(36f, 32f, 53f, 32f, 53f, 78.5f, 36f, 78.5f), ink)
        }
        translate(0f, lift * 1.6f) {
            drawPath(p(60f, 55f, 63.5f, 52f, 74f, 52f, 70.5f, 55f), Color(0xFFFF9B8A))
            drawPath(p(70.5f, 55f, 74f, 52f, 74f, 75.5f, 70.5f, 78.5f), Color(0xFFC9453A))
            drawPath(p(60f, 55f, 70.5f, 55f, 70.5f, 78.5f, 60f, 78.5f), KidCoral)
        }
    }
}

// ====================================================================== first launch

@Composable
fun KidsOnboarding() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var role by rememberSaveable { mutableStateOf(KidRole.NONE) }
    LaunchedEffect(Unit) { Graph.relays.start(Graph.prefs.relays.value) }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val fwd = targetState > initialState
            (slideInHorizontally(tween(450, easing = Motion.Emphasized)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(450, easing = Motion.Emphasized)) { if (fwd) -it / 4 else it / 4 } + fadeOut(tween(200)))
        },
        label = "kidsOnboarding",
    ) { s ->
        if (s == 0) RoleChoice { role = it; step = 1 } else KidsProfileSetup(role) { step = 0 }
    }
}

@Composable
internal fun RoleChoice(onPick: (KidRole) -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(900, easing = Motion.EmphasizedDecel)) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KidsMark(Modifier.size(150.dp), rise.value) }
        Spacer(Modifier.height(20.dp))
        PopIn(200) {
            Column {
                Text("Monolith Kids", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    t("Chat, chiamate, agenda e posizione per i più piccoli, con un genitore sempre accanto."),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(t("Chi userà questo telefono?"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        PopIn(380) {
            RoleCard(Icons.Rounded.Shield, t("Account Genitore"), t("Per il tuo telefono. Vedi messaggi, posizione e agenda di tuo figlio e decidi tu cosa può fare."), MaterialTheme.colorScheme.primary) { onPick(KidRole.PARENT) }
        }
        Spacer(Modifier.height(12.dp))
        PopIn(480) {
            RoleCard(Icons.Rounded.Face, t("Account Kids"), t("Per il telefono di tuo figlio. Si collega al telefono del genitore con un codice QR."), KidCoral) { onPick(KidRole.KID) }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            t("Prima crea l'account genitore sul tuo telefono, poi quello Kids sul telefono del bambino."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, desc: String, accent: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().bounceClick(0.97f, onClick = onClick),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KidsProfileSetup(role: KidRole, onBack: () -> Unit) {
    val parent = role == KidRole.PARENT
    var emoji by rememberSaveable { mutableStateOf(if (parent) "🧑" else "🐣") }
    var name by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
        Spacer(Modifier.height(8.dp))
        Text(if (parent) t("Il tuo account genitore") else t("Ciao! Come ti chiami?"), style = MaterialTheme.typography.displaySmall)
        Text(
            if (parent) t("I bambini vedranno questo nome e questa immagine.") else t("Scegli un nome e un'immagine. Li vedranno i tuoi amici."),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(emoji, 112.dp, Modifier.bounceClick(0.9f) { picker = true })
                Spacer(Modifier.height(8.dp))
                Text(t("Tocca per cambiare"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(32) },
            label = { Text(if (parent) t("Come ti chiamano? (es. Mamma, Papà)") else t("Il tuo nome")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                creating = true
                scope.launch {
                    Kids.setRole(role)
                    val prefix = if (parent) "genitore" else "kids"
                    Graph.identity.create(Profile(Kids.hiddenUsername(prefix), name.trim(), emoji), null)
                    Graph.startEngine()
                }
            },
            enabled = name.isNotBlank() && !creating,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape,
        ) { Text(if (creating) t("Preparo l'account…") else t("Continua"), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
        Text(
            t("Niente email e niente numero di telefono. L'account vive solo su questo telefono e non compare nelle ricerche."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }, onPick = { emoji = it; picker = false })
}

// ====================================================================== child's phone: pairing

@Composable
fun KidPairScreen() {
    val ctx = LocalContext.current
    val profile by Graph.identity.profile.collectAsState()
    var reset by remember { mutableStateOf(false) }
    val code = remember { Kids.pairCode }
    val ask by Kids.pendingPair.collectAsState()
    val parent by Kids.parent.collectAsState()
    // findable from the code too, for when the camera cannot read the QR
    LaunchedEffect(Unit) {
        while (parent == null) {
            runCatching { Kids.publishBeacon() }
            delay(4 * 60_000L)
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Avatar(profile?.emoji ?: "🐣", 72.dp)
        Spacer(Modifier.height(12.dp))
        Text(t("Ciao {0}!", profile?.displayName ?: ""), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            t("Chiedi a un genitore di aprire Monolith Kids sul suo telefono, toccare Aggiungi un bambino e inquadrare questo codice, oppure scrivere il codice di controllo."),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
            QrImage(Kids.pairQr(), Modifier.padding(18.dp).fillMaxWidth().aspectRatio(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text(t("Codice di controllo"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(code.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineSmall, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(size = 10.dp, color = KidCoral)
            Spacer(Modifier.width(10.dp))
            Text(t("In attesa del genitore…"), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            t("Tieni aperta questa schermata finché il genitore non ti collega."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { reset = true }) { Text(t("Ho scelto il ruolo sbagliato")) }
        Spacer(Modifier.height(16.dp))
    }
    ask?.let { a -> PairConfirmDialog(a) }
    if (reset) MonoDialog(
        onDismissRequest = { reset = false },
        title = { Text(t("Ricominciare?")) },
        text = { Text(t("Questo account Kids verrà eliminato e potrai scegliere di nuovo.")) },
        confirmButton = { TextButton(onClick = { reset = false; Graph.scope.launch { Graph.wipeEverything(ctx) } }) { Text(t("Ricomincia")) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(t("Annulla")) } },
    )
}

/** on the child's phone: the request to connect arrived, is it really the parent? */
@Composable
internal fun PairConfirmDialog(a: PairAsk) {
    MonoDialog(
        onDismissRequest = {},
        title = { Text(t("È il tuo genitore?")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(a.emoji, 72.dp)
                Spacer(Modifier.height(10.dp))
                Text(a.name.ifBlank { t("Genitore") }, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Vuole collegarsi a questo telefono: vedrà i tuoi messaggi, l'agenda e dove sei, e sceglierà le regole."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = { Graph.scope.launch { Kids.answerPair(true) } }) { Text(t("Sì, collegati")) } },
        dismissButton = { TextButton(onClick = { Graph.scope.launch { Kids.answerPair(false) } }) { Text(t("No")) } },
    )
}

// ====================================================================== child's phone: limits

/** in place of the message box, when the parent paused the chats */
@Composable
fun KidPausedBar(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** the rest time: the app stays closed, only the parent can be reached */
@Composable
fun KidQuietOverlay() {
    if (!Kids.isKid) return
    val rules by Kids.rules.collectAsState()
    val parent by Kids.parent.collectAsState()
    val active by Graph.messenger.activeConv.collectAsState()
    val call by Graph.calls.state.collectAsState()
    val tick by produceState(0) { while (true) { delay(30_000); value++ } }
    val quiet = remember(rules, tick) { rules.quietNow() }
    val withParent = parent != null && (active == parent?.pk || call.peer == parent?.pk)
    AnimatedVisibility(quiet && parent != null && !withParent, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
        QuietWall(parent?.name ?: "", rules.quietTo) { parent?.pk?.let { NavBus.requests.value = NavRequest(conv = it) } }
    }
}

@Composable
internal fun QuietWall(parentName: String, until: Int, onWrite: () -> Unit) {
    val night = Color(0xFF0E1430)
    Box(
        Modifier.fillMaxSize().background(night).clickable(remember { MutableInteractionSource() }, null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(120.dp)) {
                // a crescent moon and three stars
                val r = size.minDimension * 0.32f
                val c = Offset(size.width * 0.5f, size.height * 0.52f)
                drawCircle(Color(0xFFFFE08A), r, c)
                drawCircle(night, r * 0.86f, c + Offset(r * 0.48f, -r * 0.34f))
                listOf(Offset(0.80f, 0.20f) to 4f, Offset(0.16f, 0.28f) to 3f, Offset(0.86f, 0.70f) to 2.5f).forEach { (o, s) ->
                    drawCircle(Color.White.copy(alpha = 0.85f), s * density, Offset(size.width * o.x, size.height * o.y))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(t("È ora di riposare"), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                t("Monolith si riapre alle {0}. Buonanotte!", hhmm(until)),
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            )
            if (parentName.isNotBlank()) {
                Spacer(Modifier.height(28.dp))
                Button(onClick = onWrite, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = night)) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Scrivi a {0}", parentName))
                }
            }
        }
    }
}

private fun hhmm(min: Int) = if (min < 0) "--:--" else "%02d:%02d".format(min / 60, min % 60)

/** on the child's settings: who follows this account, said plainly */
@Composable
fun KidFollowedCard(modifier: Modifier = Modifier) {
    val parent by Kids.parent.collectAsState()
    val p = parent ?: return
    Surface(shape = RoundedCornerShape(24.dp), color = KidCoral.copy(alpha = 0.12f), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.emoji, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Il tuo genitore: {0}", p.name), style = MaterialTheme.typography.titleMedium)
                Text(
                    t("Vede i tuoi messaggi, la tua agenda e dove sei, e sceglie con te chi puoi aggiungere."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ====================================================================== parent's phone: home

@Composable
fun ParentHome(nav: NavHostController) {
    val ctx = LocalContext.current
    val kids by Kids.children.collectAsState()
    val profile by Graph.identity.profile.collectAsState()
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    var codeDialog by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { res ->
        val c = res.contents ?: return@rememberLauncherForActivityResult
        Graph.scope.launch {
            val ok = Kids.pairWith(c)
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, if (ok) t("Richiesta inviata: conferma sul telefono del bambino") else t("Questo non è il codice di un account Kids"), Toast.LENGTH_LONG).show()
            }
        }
    }
    fun scan() = scanner.launch(
        com.monolith.app.ui.components.qrScanOptions(t("Inquadra il codice sul telefono di tuo figlio"))
    )
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BigTitle(t("Bambini"), Modifier.weight(1f), subtitle = when (kids.size) { 0 -> t("Nessun bambino collegato"); 1 -> t("1 bambino collegato"); else -> t("{0} collegati", kids.size) })
                Avatar(profile?.emoji ?: "🧑", 44.dp, Modifier.padding(end = 20.dp).bounceClick { nav.navigate("settings") })
            }
        }
        if (kids.isEmpty()) item(key = "empty") { HowToPair(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        items(kids, key = { it.pk }) { c ->
            val f = friends.firstOrNull { it.pubkey == c.pk }
            ChildCard(c, f, Modifier.animateItem().padding(horizontal = 16.dp, vertical = 6.dp)) { nav.navigate("kid/" + c.pk) }
        }
        item(key = "add") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Button(onClick = { scan() }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Aggiungi un bambino"))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { codeDialog = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Rounded.Dialpad, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Scrivi il codice"))
                }
            }
        }
    }
    if (codeDialog) PairCodeDialog { codeDialog = false }
}

/** the parent types the six digits shown on the child's phone */
@Composable
internal fun PairCodeDialog(initial: PairAsk? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(initial) }
    var missing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val f = found
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (f == null) t("Scrivi il codice") else t("Collegare {0}?", f.name)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (f == null) {
                    Text(
                        t("Sul telefono del bambino, sotto il QR, c'è il codice di controllo di 6 cifre."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code, onValueChange = { v -> code = v.filter(Char::isDigit).take(6); missing = false },
                        singleLine = true, placeholder = { Text("123 456") }, modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 4.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    )
                    if (missing) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            t("Nessun telefono con questo codice. Controlla le cifre e che Monolith Kids sia aperto sul telefono del bambino."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Avatar(f.emoji, 72.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(f.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("Sul suo telefono comparirà una richiesta: tocca Sì, collegati."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            if (f == null) TextButton(enabled = code.length == 6 && !busy, onClick = {
                busy = true
                scope.launch {
                    val r = Kids.findByCode(code)
                    busy = false
                    if (r == null) missing = true else found = r
                }
            }) { Text(if (busy) t("Cerco…") else t("Cerca")) }
            else TextButton(onClick = {
                val c = code
                onDismiss()
                Graph.scope.launch {
                    Kids.pair(f.pk, c, f.name, f.emoji)
                    withContext(Dispatchers.Main) { Toast.makeText(ctx, t("Richiesta inviata: conferma sul telefono del bambino"), Toast.LENGTH_LONG).show() }
                }
            }) { Text(t("Collega")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

@Composable
private fun HowToPair(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(t("Come collegare tuo figlio"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            listOf(
                t("Installa Monolith Kids sul telefono del bambino"),
                t("Scegli Account Kids e scrivi il suo nome"),
                t("Tocca Aggiungi un bambino qui sotto e inquadra il codice"),
            ).forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(KidCoral), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChildCard(c: Child, f: com.monolith.app.data.Friend?, modifier: Modifier, onClick: () -> Unit) {
    val tick by produceState(0) { while (true) { delay(30_000); value++ } }
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.emoji, 58.dp, ring = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (c.seenAt > 0 && tick >= 0) t("Attivo {0}", Fmt.ago(c.seenAt)) else t("In attesa di conferma sul suo telefono"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (c.pending.isNotEmpty()) {
                    Surface(shape = CircleShape, color = KidCoral) {
                        Text(
                            if (c.pending.size == 1) t("1 richiesta") else t("{0} richieste", c.pending.size), color = Color.White,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                if (c.battery >= 0) InfoPill(if (c.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, "${c.battery}%")
                InfoPill(
                    Icons.Rounded.LocationOn,
                    when {
                        !c.rules.location -> t("Posizione spenta")
                        f != null && f.locAt > 0 -> t("Posizione delle {0}", Fmt.time(f.locAt))
                        else -> t("Posizione in arrivo")
                    },
                )
                InfoPill(Icons.Rounded.Forum, t("{0} contatti", c.contacts.size))
                c.events.firstOrNull { it.start >= Fmt.startOfDay(now()) }?.let { InfoPill(Icons.Rounded.EventNote, it.title) }
            }
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ====================================================================== parent's phone: one child

@Composable
fun ChildScreen(nav: NavHostController, pk: String, startSection: Int = 0) {
    val ctx = LocalContext.current
    val kids by Kids.children.collectAsState()
    val child = kids.firstOrNull { it.pk == pk }
    if (child == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val friend by remember(pk) { Graph.db.friends().observe(pk) }.collectAsState(initial = null)
    var section by rememberSaveable { mutableIntStateOf(startSection) }
    var openConv by rememberSaveable { mutableStateOf<String?>(null) }
    var wipe by remember { mutableStateOf(false) }

    openConv?.let { conv ->
        BackHandler { openConv = null }
        KidConversation(child, conv) { openConv = null }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                Graph.scope.launch { Kids.askSync(pk) }
                Toast.makeText(ctx, t("Chiedo i dati aggiornati…"), Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Rounded.Refresh, t("Aggiorna")) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(child.emoji, 92.dp, ring = true)
                Spacer(Modifier.height(10.dp))
                Text(child.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    buildList {
                        if (child.seenAt > 0) add(t("Attivo {0}", Fmt.ago(child.seenAt)))
                        if (child.battery >= 0) add(t("batteria {0}%", child.battery))
                        if (child.version.isNotBlank()) add("Kids " + child.version)
                    }.joinToString(" · ").ifBlank { t("In attesa di conferma sul suo telefono") },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    QuickAction(Icons.AutoMirrored.Rounded.Chat, t("Scrivi")) { nav.navigate(Routes.chat(pk)) }
                    QuickAction(Icons.Rounded.Call, t("Chiama")) { nav.navigate(Routes.chat(pk)) }
                    QuickAction(Icons.Rounded.Map, t("Mappa")) {
                        nav.popBackStack("home", inclusive = false)
                        NavBus.requests.value = NavRequest(tab = 2)
                    }
                }
                friend?.takeIf { it.locAt > 0 && child.rules.location }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(t("Posizione aggiornata alle {0}", Fmt.time(it.locAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            // four sections, always all in view: a coral dot says there is something to approve
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
            ) {
                listOf(t("Messaggi"), t("Agenda"), t("Contatti"), t("Regole")).forEachIndexed { i, label ->
                    val on = section == i
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { section = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (i == 2 && child.pending.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.size(7.dp).clip(CircleShape).background(KidCoral))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(section, label = "childSection") { s ->
                Column {
                    when (s) {
                        0 -> MessagesSection(child) { openConv = it }
                        1 -> AgendaSection(child)
                        2 -> ContactsSection(child)
                        else -> RulesSection(child)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
            if (child.seenAt == 0L) {
                // never answered: nothing to delete over there, just forget it here
                TextButton(onClick = { Graph.scope.launch { Kids.forgetChild(pk) }; nav.popBackStack() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(t("Annulla il collegamento"), color = MaterialTheme.colorScheme.error)
                }
            } else TextButton(onClick = { wipe = true }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(t("Elimina l'account di {0}", child.name), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (wipe) MonoDialog(
        onDismissRequest = { wipe = false },
        title = { Text(t("Eliminare l'account di {0}?", child.name)) },
        text = { Text(t("Chat, agenda e amici verranno cancellati anche dal suo telefono. Non si può annullare.")) },
        confirmButton = {
            TextButton(onClick = {
                wipe = false
                Graph.scope.launch { Kids.wipeChild(pk) }
                nav.popBackStack()
            }) { Text(t("Elimina"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { wipe = false }) { Text(t("Annulla")) } },
    )
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bounceClick(0.9f, onClick = onClick)) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------------- messages

internal fun kindLabel(kind: String): String = when (kind) {
    "photo" -> t("Foto")
    "voice" -> t("Messaggio vocale")
    "sticker" -> t("Sticker")
    "loc" -> t("Posizione")
    "event" -> t("Evento")
    "poll" -> t("Sondaggio")
    "call" -> t("Chiamata")
    "nudge" -> t("Trillo")
    "text", "" -> ""
    else -> t("Gioco")
}

private fun preview(m: MirrorMsg) = m.text.ifBlank { kindLabel(m.kind) }.ifBlank { "…" }

@Composable
private fun MessagesSection(child: Child, onOpen: (String) -> Unit) {
    val all by remember(child.pk) { Kids.mirrorOf(child.pk) }.collectAsState()
    val convs = remember(all) { all.groupBy { it.conv }.map { (k, v) -> k to v }.sortedByDescending { it.second.last().ts } }
    Note(t("Una copia dei messaggi di {0}. Se qualcosa non va bene, puoi eliminarlo: sparisce anche dal suo telefono.", child.name))
    if (convs.isEmpty()) {
        EmptyNote(t("Ancora nessun messaggio"))
        return
    }
    convs.forEach { (conv, list) ->
        val last = list.last()
        val name = last.convName.ifBlank { child.contacts.firstOrNull { it.pk == conv }?.name ?: t("Chat") }
        Row(Modifier.fillMaxWidth().clickable { onOpen(conv) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(child.contacts.firstOrNull { it.pk == conv }?.emoji ?: if (conv.startsWith("g:")) "👥" else "🙂", 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (last.out) child.name + ": " else "") + preview(last),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.chatStamp(last.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${list.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KidConversation(child: Child, conv: String, onBack: () -> Unit) {
    val all by remember(child.pk) { Kids.mirrorOf(child.pk) }.collectAsState()
    val list = remember(all, conv) { all.filter { it.conv == conv } }
    var del by remember { mutableStateOf<MirrorMsg?>(null) }
    val state = rememberLazyListState()
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.scrollToItem(list.size - 1) }
    val title = list.lastOrNull()?.convName?.ifBlank { null } ?: child.contacts.firstOrNull { it.pk == conv }?.name ?: t("Chat")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t("Chat di {0}", child.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            t("Tieni premuto un messaggio per eliminarlo."), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(list, key = { it.id }) { m -> MirrorBubble(m, child) { del = m } }
        }
    }
    del?.let { m ->
        MonoDialog(
            onDismissRequest = { del = null },
            title = { Text(t("Eliminare questo messaggio?")) },
            text = { Text(t("Sparirà anche dal telefono di {0}.", child.name)) },
            confirmButton = { TextButton(onClick = { del = null; Graph.scope.launch { Kids.deleteMessage(child.pk, m) } }) { Text(t("Elimina"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
private fun MirrorBubble(m: MirrorMsg, child: Child, onLong: () -> Unit) {
    val mine = m.out
    val thumb = remember(m.thumb) {
        if (m.thumb.isBlank()) null else runCatching {
            val b = Base64.decode(m.thumb, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine && m.conv.startsWith("g:")) {
            Text(child.contacts.firstOrNull { it.pk == m.from }?.name ?: t("Altro membro"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Column(
            Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp))
                .background(if (mine) KidCoral else MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(onClick = {}, onLongClick = onLong)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            val fg = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
            thumb?.let {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(180.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.height(6.dp))
            }
            val label = kindLabel(m.kind)
            if (label.isNotEmpty() && thumb == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (m.kind) { "photo", "sticker" -> Icons.Rounded.Image; "voice" -> Icons.Rounded.Mic; "loc" -> Icons.Rounded.LocationOn; "call" -> Icons.Rounded.Call; "event" -> Icons.Rounded.EventNote; else -> Icons.Rounded.SportsEsports },
                        null, tint = fg, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (m.text.isNotBlank()) Text(m.text, color = fg, style = MaterialTheme.typography.bodyLarge)
            Text(Fmt.chatStamp(m.ts).let { if (Fmt.sameDay(m.ts, now())) it else it + " " + Fmt.time(m.ts) }, color = fg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
        }
    }
}

// ---------------------------------------------------------------------- agenda

@Composable
private fun AgendaSection(child: Child) {
    var edit by remember { mutableStateOf<KidEvent?>(null) }
    var del by remember { mutableStateOf<KidEvent?>(null) }
    val today = Fmt.startOfDay(now())
    val upcoming = child.events.filter { (it.end ?: it.start) >= today }.sortedBy { it.start }
    Note(t("L'agenda di {0}. Quello che aggiungi o cambi qui compare subito sul suo telefono.", child.name))
    if (upcoming.isEmpty()) EmptyNote(t("Nessun impegno in arrivo"))
    upcoming.forEach { e ->
        Row(Modifier.fillMaxWidth().clickable { edit = e }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Text(e.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Fmt.dayShort(e.start) + if (e.allDay) " · " + t("tutto il giorno") else " · " + Fmt.time(e.start),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { del = e }) { Icon(Icons.Rounded.Delete, t("Elimina"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    OutlinedButton(
        onClick = { edit = KidEvent("", "", "📌", nextHour(), null, false, "") },
        shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Aggiungi un impegno")) }

    edit?.let { e ->
        KidEventDialog(e, onDismiss = { edit = null }) { saved ->
            edit = null
            Graph.scope.launch { Kids.putEvent(child.pk, saved.copy(id = saved.id.ifBlank { newId() })) }
        }
    }
    del?.let { e ->
        MonoDialog(
            onDismissRequest = { del = null },
            title = { Text(t("Eliminare {0}?", e.title)) },
            text = { Text(t("Sparirà anche dall'agenda di {0}.", child.name)) },
            confirmButton = { TextButton(onClick = { del = null; Graph.scope.launch { Kids.deleteEvent(child.pk, e) } }) { Text(t("Elimina"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text(t("Annulla")) } },
        )
    }
}

private fun nextHour() = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private val EVENT_EMOJI = listOf("📌", "🏫", "⚽", "🎹", "🎂", "🦷", "🏊", "📚", "🎨", "🍕")

@Composable
private fun KidEventDialog(initial: KidEvent, onDismiss: () -> Unit, onSave: (KidEvent) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var start by remember { mutableStateOf(initial.start) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) t("Nuovo impegno") else t("Modifica impegno")) },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(60) }, label = { Text(t("Titolo")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EVENT_EMOJI.forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = start }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            start = Calendar.getInstance().apply { timeInMillis = start; set(y, m, d) }.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }, shape = CircleShape) { Text(Fmt.dayShort(start)) }
                    if (!allDay) OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = start }
                        TimePickerDialog(ctx, { _, h, mi ->
                            start = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi) }.timeInMillis
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    }, shape = CircleShape) { Text(Fmt.time(start)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Tutto il giorno"), Modifier.weight(1f))
                    Switch(allDay, { allDay = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = if (allDay) Fmt.startOfDay(start) else start
                onSave(initial.copy(title = title.trim(), emoji = emoji, start = s, end = null, allDay = allDay))
            }, enabled = title.isNotBlank()) { Text(t("Salva")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

// ---------------------------------------------------------------------- contacts

@Composable
private fun ContactsSection(child: Child) {
    val ctx = LocalContext.current
    var remove by remember { mutableStateOf<KContact?>(null) }
    var search by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { res ->
        val parsed = FriendQr.parse(res.contents) ?: run {
            if (res.contents != null) Toast.makeText(ctx, t("Questo non è un codice Monolith"), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        Graph.scope.launch {
            val found = Graph.messenger.searchUsers(parsed.first).firstOrNull()
            val c = KContact(parsed.first, found?.profile?.displayName ?: parsed.second, found?.profile?.emoji ?: "🙂", found?.profile?.username ?: parsed.second, "parent")
            Kids.allow(child.pk, c)
            withContext(Dispatchers.Main) { Toast.makeText(ctx, t("{0} aggiunto ai contatti di {1}", c.name, child.name), Toast.LENGTH_SHORT).show() }
        }
    }
    Note(t("{0} può scrivere e chiamare solo le persone che approvi tu.", child.name))
    if (child.pending.isNotEmpty()) {
        SectionLabel(t("Da approvare"))
        child.pending.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.emoji, 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        if (c.how == "incoming") t("Vuole aggiungere {0}", child.name) else t("{0} vuole aggiungerlo", child.name),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                com.monolith.app.ui.components.RoundAction(Icons.Rounded.Close, t("Rifiuta"), size = 40.dp) { Graph.scope.launch { Kids.deny(child.pk, c.pk) } }
                Spacer(Modifier.width(8.dp))
                com.monolith.app.ui.components.RoundAction(Icons.Rounded.Check, t("Approva"), size = 40.dp, filled = true) { Graph.scope.launch { Kids.allow(child.pk, c) } }
            }
        }
    }
    SectionLabel(t("Contatti approvati"))
    if (child.contacts.isEmpty()) EmptyNote(t("Ancora nessuno"))
    child.contacts.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.emoji, 46.dp)
            Spacer(Modifier.width(14.dp))
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = { remove = c }) { Icon(Icons.Rounded.Delete, t("Rimuovi"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = {
            scanner.launch(com.monolith.app.ui.components.qrScanOptions(t("Inquadra il codice QR del contatto")))
        }, shape = CircleShape, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scansiona"))
        }
        OutlinedButton(onClick = { search = true }, shape = CircleShape, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Username"))
        }
    }
    remove?.let { c ->
        MonoDialog(
            onDismissRequest = { remove = null },
            title = { Text(t("Rimuovere {0}?", c.name)) },
            text = { Text(t("{0} non potrà più scrivergli né chiamarlo.", child.name)) },
            confirmButton = { TextButton(onClick = { remove = null; Graph.scope.launch { Kids.removeContact(child.pk, c.pk) } }) { Text(t("Rimuovi"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(t("Annulla")) } },
        )
    }
    if (search) ContactSearchDialog(child, onDismiss = { search = false })
}

@Composable
private fun ContactSearchDialog(child: Child, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf<List<com.monolith.app.net.UserResult>?>(null) }
    val scope = rememberCoroutineScope()
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Aggiungi un contatto")) },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it.take(40) }, singleLine = true, prefix = { Text("@") },
                    placeholder = { Text(t("username")) }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            busy = true
                            scope.launch { res = Graph.messenger.searchUsers(q); busy = false }
                        }, enabled = q.trim().length >= 3 && !busy) { Icon(Icons.Rounded.Search, t("Cerca")) }
                    },
                )
                res?.let { list ->
                    Spacer(Modifier.height(8.dp))
                    if (list.isEmpty()) Text(t("Nessun risultato"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.take(5).forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                Graph.scope.launch { Kids.allow(child.pk, KContact(u.pubkey, u.profile.displayName, u.profile.emoji, u.profile.username, "parent")) }
                                onDismiss()
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(u.profile.emoji, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.profile.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("@" + u.profile.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.Add, null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Chiudi")) } },
    )
}

// ---------------------------------------------------------------------- rules

@Composable
private fun RulesSection(child: Child) {
    val ctx = LocalContext.current
    val r = child.rules
    fun set(n: KidRules) { Graph.scope.launch { Kids.setRules(child.pk, n) } }
    Note(t("Scegli cosa può fare {0}. Le modifiche arrivano subito sul suo telefono.", child.name))
    RuleSwitch(Icons.Rounded.Forum, t("Chat"), t("Scrivere ai contatti approvati"), r.chat) { set(r.copy(chat = it)) }
    RuleSwitch(Icons.Rounded.Image, t("Foto e sticker"), t("Mandare immagini nelle chat"), r.photos) { set(r.copy(photos = it)) }
    RuleSwitch(Icons.Rounded.Mic, t("Messaggi vocali"), t("Registrare e mandare vocali"), r.voice) { set(r.copy(voice = it)) }
    RuleSwitch(Icons.Rounded.SportsEsports, t("Giochi"), t("Giochi e dadi nelle chat"), r.games) { set(r.copy(games = it)) }
    RuleSwitch(Icons.Rounded.Call, t("Chiamate"), t("Chiamare e ricevere chiamate"), r.calls) { set(r.copy(calls = it)) }
    RuleSwitch(Icons.Rounded.Videocam, t("Videochiamate"), t("Accendere la videocamera"), r.video && r.calls, enabled = r.calls) { set(r.copy(video = it)) }
    RuleSwitch(Icons.Rounded.Edit, t("Modificare l'agenda"), t("Aggiungere e cambiare i propri impegni"), r.agendaEdit) { set(r.copy(agendaEdit = it)) }
    RuleSwitch(Icons.Rounded.LocationOn, t("Posizione"), t("Condivisa con te tutto il giorno"), r.location) { set(r.copy(location = it)) }
    val quietOn = r.quietFrom >= 0 && r.quietTo >= 0
    RuleSwitch(Icons.Rounded.Bedtime, t("Ora di riposo"), if (quietOn) t("Dalle {0} alle {1} l'app resta chiusa", hhmm(r.quietFrom), hhmm(r.quietTo)) else t("L'app si chiude la sera, tranne la chat con te"), quietOn) {
        set(if (it) r.copy(quietFrom = 21 * 60, quietTo = 7 * 60) else r.copy(quietFrom = -1, quietTo = -1))
    }
    if (quietOn) {
        Row(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                TimePickerDialog(ctx, { _, h, m -> set(r.copy(quietFrom = h * 60 + m)) }, r.quietFrom / 60, r.quietFrom % 60, true).show()
            }, shape = CircleShape) { Text(t("Dalle {0}", hhmm(r.quietFrom))) }
            OutlinedButton(onClick = {
                TimePickerDialog(ctx, { _, h, m -> set(r.copy(quietTo = h * 60 + m)) }, r.quietTo / 60, r.quietTo % 60, true).show()
            }, shape = CircleShape) { Text(t("Alle {0}", hhmm(r.quietTo))) }
        }
    }
}

@Composable
private fun RuleSwitch(icon: ImageVector, title: String, desc: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!on) }.padding(horizontal = 20.dp, vertical = 10.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange, enabled = enabled)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
}
