package com.monolith.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.FriendQr
import com.monolith.app.core.Profile
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.core.tagOf
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.net.UserResult
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.BigTitle
import com.monolith.app.ui.components.EmptyState
import com.monolith.app.ui.components.Pill
import com.monolith.app.ui.components.QrImage
import com.monolith.app.ui.components.RoundAction
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.monolith.app.ui.components.MonoDialog

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, t("Copiato"), Toast.LENGTH_SHORT).show()
}

/** My QR code, to be scanned by a friend in person */
@Composable
fun MyQrDialog(onDismiss: () -> Unit) {
    val profile by Graph.identity.profile.collectAsState()
    val pub = Graph.identity.keys?.pub ?: ""
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Il mio codice QR")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    QrImage(FriendQr.content(pub, profile?.username ?: ""), Modifier.padding(14.dp).fillMaxWidth().aspectRatio(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text((profile?.emoji ?: "") + " @" + (profile?.username ?: "") + "  #" + tagOf(pub), style = MaterialTheme.typography.titleMedium)
                Text(t("Fallo scansionare a un amico: vi aggiungete e verificate le identità."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Chiudi")) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendsScreen(nav: NavHostController, onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val all by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val profile by Graph.identity.profile.collectAsState()
    val online by Graph.messenger.online.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<UserResult>?>(null) }
    var remove by remember { mutableStateOf<Friend?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val tick by produceState(0) { while (true) { delay(20_000); value++ } }
    val myPub = Graph.identity.keys?.pub ?: ""

    val scanner = rememberLauncherForActivityResult(ScanContract()) { res ->
        val parsed = FriendQr.parse(res.contents) ?: run {
            if (res.contents != null) Toast.makeText(ctx, t("Questo non è un codice Monolith"), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (parsed.first == myPub) return@rememberLauncherForActivityResult
        Graph.scope.launch {
            val found = Graph.messenger.searchUsers(parsed.first).firstOrNull()
            val u = found ?: UserResult(parsed.first, Profile(parsed.second, parsed.second, "🙂"), now())
            val wasFriend = Graph.db.friends().get(u.pubkey)?.status == FriendStatus.FRIEND
            Graph.messenger.sendFriendRequest(u, verified = true)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(ctx, if (wasFriend) t("Identità verificata ✓") else t("Richiesta inviata a {0}", u.profile.displayName), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val incoming = all.filter { it.status == FriendStatus.INCOMING }
    val outgoing = all.filter { it.status == FriendStatus.OUTGOING }
    val friends = all.filter { it.status == FriendStatus.FRIEND }
        .sortedWith(compareByDescending<Friend> { now() - (online[it.pubkey] ?: 0L) < 100_000L && tick >= 0 }.thenBy { it.displayName.lowercase() })
    val byPk = all.associateBy { it.pubkey }

    fun runSearch() {
        val q = query.trim()
        if (q.length < 3) return
        focus.clearFocus()
        searching = true
        scope.launch {
            results = Graph.messenger.searchUsers(q)
            searching = false
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) results = null
        else if (Profile.isValidUsername(Profile.normalizeUsername(query).substringBefore('#'))) {
            delay(700)
            runSearch()
        }
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BigTitle(t("Amici"), Modifier.weight(1f), subtitle = t("{0} amici", friends.size))
                Avatar(profile?.emoji ?: "🗿", 44.dp, Modifier.padding(end = 20.dp).bounceClick { nav.navigate("settings") })
            }
        }
        item(key = "me") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.bounceClick(0.98f) { copyText(ctx, "username", "@" + (profile?.username ?: "")) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Il tuo username"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("@" + (profile?.username ?: "") + "  #" + tagOf(myPub), style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Rounded.ContentCopy, t("Copia"), Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showQr = true }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Il mio QR"))
                        }
                        Button(
                            onClick = {
                                scanner.launch(ScanOptions().setPrompt(t("Inquadra il codice QR del tuo amico")).setBeepEnabled(false).setOrientationLocked(true).setDesiredBarcodeFormats(ScanOptions.QR_CODE))
                            },
                            shape = CircleShape, modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scansiona"))
                        }
                    }
                }
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text(t("Cerca per username")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    AnimatedContent(searching, label = "s") { s ->
                        if (s) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = null }) { Icon(Icons.Rounded.Close, t("Cancella")) }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        results?.let { list ->
            item(key = "resLabel") { SectionLabel(if (list.isEmpty()) t("Nessun risultato") else t("Risultati")) }
            if (list.isEmpty()) {
                item(key = "resEmpty") {
                    Text(t("Controlla che l'username sia scritto giusto. Il tuo amico deve aver già creato il profilo su Monolith."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            items(list, key = { "r" + it.pubkey }) { u ->
                ResultRow(u, byPk[u.pubkey]?.status, Modifier.animateItem()) { Graph.scope.launch { Graph.messenger.sendFriendRequest(u) } }
            }
        }
        if (incoming.isNotEmpty()) {
            item(key = "inLabel") { SectionLabel(t("Richieste ricevute")) }
            items(incoming, key = { "i" + it.pubkey }) { f ->
                Row(Modifier.fillMaxWidth().animateItem().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(f.emoji, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("@${f.username}  #${tagOf(f.pubkey)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RoundAction(Icons.Rounded.Close, t("Rifiuta"), size = 40.dp) { Graph.scope.launch { Graph.messenger.declineFriend(f.pubkey) } }
                    Spacer(Modifier.width(8.dp))
                    RoundAction(Icons.Rounded.Check, t("Accetta"), size = 40.dp, filled = true) { Graph.scope.launch { Graph.messenger.acceptFriend(f.pubkey) } }
                }
            }
        }
        if (outgoing.isNotEmpty()) {
            item(key = "outLabel") { SectionLabel(t("In attesa di risposta")) }
            items(outgoing, key = { "o" + it.pubkey }) { f ->
                Row(Modifier.fillMaxWidth().animateItem().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(f.emoji, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(t("Richiesta inviata {0}", Fmt.ago(f.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { Graph.scope.launch { Graph.messenger.declineFriend(f.pubkey) } }) { Text(t("Annulla")) }
                }
            }
        }
        item(key = "fLabel") { SectionLabel(t("I tuoi amici")) }
        if (friends.isEmpty()) {
            item(key = "fEmpty") { EmptyState(t("Ancora nessun amico"), t("Cerca un amico per username o scansiona il suo QR: dovrà accettare la richiesta.")) }
        }
        items(friends, key = { "f" + it.pubkey }) { f ->
            val isOn = now() - (online[f.pubkey] ?: 0L) < 100_000L
            Row(
                Modifier.fillMaxWidth().animateItem().combinedClickable(onClick = { onOpenChat(f.pubkey) }, onLongClick = { remove = f }).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(f.emoji, 48.dp, online = isOn)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                        if (f.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Verified, t("Verificato"), Modifier.size(16.dp)) }
                    }
                    Text(
                        when {
                            isOn -> t("online")
                            f.lastSeen > 0 -> t("visto {0}", Fmt.ago(f.lastSeen))
                            else -> "@" + f.username
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (f.sharingUntil > now()) Pill("📍 live")
            }
        }
    }

    if (showQr) MyQrDialog { showQr = false }
    remove?.let { f ->
        MonoDialog(
            onDismissRequest = { remove = null },
            title = { Text(t("Rimuovere {0}?", f.displayName)) },
            text = { Text(t("Non potrete più scrivervi finché non vi aggiungerete di nuovo.")) },
            confirmButton = { TextButton(onClick = { Graph.scope.launch { Graph.messenger.removeFriend(f.pubkey) }; remove = null }) { Text(t("Rimuovi")) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(t("Annulla")) } },
        )
    }
}

@Composable
private fun ResultRow(u: UserResult, status: Int?, modifier: Modifier, onAdd: () -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(u.profile.emoji, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(u.profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text("@${u.profile.username}  #${tagOf(u.pubkey)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedContent(
            status,
            transitionSpec = { (scaleIn(Motion.bouncy(), 0.6f) + fadeIn()) togetherWith (scaleOut(tween(150)) + fadeOut()) },
            label = "req",
        ) { st ->
            when (st) {
                FriendStatus.FRIEND -> Pill(t("Amici ✓"), strong = true)
                FriendStatus.OUTGOING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HourglassTop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text(t("Inviata"), style = MaterialTheme.typography.labelLarge)
                }
                FriendStatus.INCOMING -> Button(onClick = onAdd, shape = CircleShape) { Text(t("Accetta")) }
                else -> Button(onClick = onAdd, shape = CircleShape) {
                    Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Aggiungi"))
                }
            }
        }
    }
}
