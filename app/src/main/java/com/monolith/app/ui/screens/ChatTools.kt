package com.monolith.app.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.Group
import com.monolith.app.data.Message
import com.monolith.app.data.MsgStatus
import com.monolith.app.net.Media
import com.monolith.app.net.Shard
import com.monolith.app.net.ShardRoom
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.Rich
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// ====================================================================== files and videos

fun readableSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(java.util.Locale.ITALY, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1000} KB"
    else -> "$bytes B"
}

/** open a received file with the app the phone prefers for it */
fun openFile(ctx: android.content.Context, path: String, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, t("Apri con")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(ctx, t("Nessuna app per aprirlo"), Toast.LENGTH_SHORT).show() }
}

/** a document: its kind written on a sheet drawn by the app, the name, the size */
@Composable
internal fun FileBubble(m: Message, ex: JSONObject, color: Color, bubble: Color, shape: Shape, onLongPress: (Message) -> Unit) {
    val ctx = LocalContext.current
    val name = ex.optString("name").ifBlank { t("File") }
    val ext = name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "FILE" }
    val ready = m.mediaPath != null && (m.outgoing || m.progress >= 1f)
    Surface(
        shape = shape, color = bubble,
        modifier = Modifier.widthIn(min = 220.dp, max = 290.dp).combinedClickable(
            onClick = { if (ready) m.mediaPath?.let { openFile(ctx, it, ex.optString("mime")) } },
            onLongClick = { onLongPress(m) },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(ext, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = color, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (!ready) t("Arriva… {0}%", (m.progress * 100).toInt()) else readableSize(ex.optLong("size")) + " · " + t("Tocca per aprire"),
                        color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!ready) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { m.progress.coerceIn(0.02f, 1f) }, color = color, trackColor = color.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth())
            }
            MetaLine(m, color)
        }
    }
}

/** a video: its first frame, the play mark and the length */
@Composable
internal fun VideoBubble(m: Message, ex: JSONObject, shape: Shape, onOpen: (String) -> Unit, onLongPress: (Message) -> Unit) {
    val (w, h) = Media.approxSize(ex.optInt("w"), ex.optInt("h"), 240)
    val thumb = remember(m.id) {
        runCatching {
            val b = java.util.Base64.getDecoder().decode(ex.optString("tb"))
            BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        }.getOrNull()
    }
    val ready = m.mediaPath != null && (m.outgoing || m.progress >= 1f)
    Column(horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        Box(
            Modifier.size(w.dp, h.dp).clip(shape).background(Color.Black)
                .combinedClickable(onClick = { if (ready) m.mediaPath?.let(onOpen) }, onLongClick = { onLongPress(m) }),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                if (ready) Icon(Icons.Rounded.PlayArrow, t("Guarda"), tint = Color.White, modifier = Modifier.size(34.dp))
                else CircularProgressIndicator(progress = { m.progress.coerceIn(0.02f, 1f) }, color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Fmt.duration(ex.optLong("dur")) + " · " + Fmt.time(m.ts), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** a video on the whole screen */
@Composable
fun VideoViewer(nav: NavHostController, path: String) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                android.widget.VideoView(c).apply {
                    setMediaController(android.widget.MediaController(c).also { it.setAnchorView(this) })
                    setVideoPath(path)
                    setOnPreparedListener { start() }
                }
            },
            modifier = Modifier.fillMaxSize().padding(vertical = 40.dp),
        )
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro"), tint = Color.White)
        }
    }
}

// ====================================================================== threads

/** under a message with replies in a thread */
@Composable
internal fun ThreadChip(n: Int, last: Long, onClick: () -> Unit) {
    Surface(
        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.padding(top = 3.dp, start = 6.dp, end = 6.dp).bounceClick(0.95f, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                (if (n == 1) t("1 risposta") else t("{0} risposte", n)) + " · " + Fmt.ago(last),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** the extra of a message, read once */
internal fun extraOf(m: Message): JSONObject = runCatching { JSONObject(m.extra ?: "{}") }.getOrDefault(JSONObject())

/** a thread: the first message and its replies, with a box to answer */
@Composable
fun ThreadScreen(nav: NavHostController, conv: String, root: String) {
    val ctx = LocalContext.current
    val all by remember(conv) { Graph.db.messages().conversation(conv, 2000) }.collectAsState(initial = emptyList())
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val reactions by remember(conv) { Graph.db.social().reactions(conv) }.collectAsState(initial = emptyList())
    val votes by remember(conv) { Graph.db.social().votes(conv) }.collectAsState(initial = emptyList())
    val shards by Graph.shards.all.collectAsState()
    val names = people.associateBy { it.pubkey }
    val myPub = Graph.identity.keys?.pub ?: ""
    val first = all.firstOrNull { it.id == root }
    val replies = all.filter { it.id != root && extraOf(it).optString("th") == root }.sortedBy { it.ts }
    val roomOf = remember(conv, shards) { Graph.shards.ofConv(conv) }
    val canWrite = roomOf?.let { (s, r) -> s.canWrite(r, myPub) } ?: true
    var text by remember { mutableStateOf("") }
    LaunchedEffect(all.size) { Graph.messenger.markRead(conv) }
    val reactBy = reactions.groupBy { it.msgId }
    val votesBy = votes.groupBy { it.msgId }
    val title = first?.let { extraOf(it).optString("tt") }.orEmpty()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                Column(Modifier.weight(1f)) {
                    Text(title.ifBlank { t("Thread") }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (replies.size == 1) t("1 risposta") else t("{0} risposte", replies.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
            if (first != null) item(key = "root") {
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
                MessageRow(
                    first, true, names, myPub, reactBy[first.id].orEmpty(), votesBy[first.id].orEmpty(), isNew = false, modifier = Modifier,
                    onLongPress = {}, onPhoto = { p -> nav.navigate(Routes.photo(p, "photo-" + first.id)) }, onAddEvent = {}, onOpenMap = { _, _ -> }, onQuote = {},
                    onReact = { m, e -> Graph.scope.launch { Graph.messenger.react(m, e) } }, onRoute = { nav.navigate(it) },
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            items(replies, key = { it.id }) { m ->
                MessageRow(
                    m, !m.outgoing, names, myPub, reactBy[m.id].orEmpty(), votesBy[m.id].orEmpty(), isNew = m.ts > now() - 3000, modifier = Modifier.animateItem(),
                    onLongPress = {}, onPhoto = { p -> nav.navigate(Routes.photo(p, "photo-" + m.id)) }, onAddEvent = {}, onOpenMap = { _, _ -> }, onQuote = {},
                    onReact = { mm, e -> Graph.scope.launch { Graph.messenger.react(mm, e) } }, onRoute = { nav.navigate(it) },
                )
            }
        }
        if (!canWrite) ReadOnlyBar(t("Qui scrivono solo il fondatore e i moderatori"))
        else Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> if (text.isEmpty()) Text(t("Rispondi nel thread"), color = MaterialTheme.colorScheme.onSurfaceVariant); inner() },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f) {
                        val tx = text
                        if (tx.isBlank()) return@bounceClick
                        roomOf?.first?.let { s -> Graph.shards.badWord(s, tx)?.let { Toast.makeText(ctx, t("Questa parola non è permessa qui: {0}", it), Toast.LENGTH_SHORT).show(); return@bounceClick } }
                        text = ""
                        Graph.scope.launch { Graph.messenger.sendText(conv, tx, thread = root) }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Send, t("Invia"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

// ====================================================================== forum rooms

/** a forum room: posts with a title, the most active on top; each post opens its thread */
@Composable
fun ForumScreen(nav: NavHostController, conv: String, s: Shard, r: ShardRoom) {
    val ctx = LocalContext.current
    val all by remember(conv) { Graph.db.messages().conversation(conv, 2000) }.collectAsState(initial = emptyList())
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val names = people.associateBy { it.pubkey }
    val me = Graph.identity.keys?.pub ?: ""
    var newPost by remember { mutableStateOf(false) }
    var rulesOpen by remember { mutableStateOf(false) }
    LaunchedEffect(all.size) { Graph.messenger.markRead(conv) }
    val replies = all.filter { extraOf(it).optString("th").isNotBlank() }.groupBy { extraOf(it).optString("th") }
    val posts = all.filter { it.kind != "system" && it.kind != "deleted" && extraOf(it).optString("th").isBlank() }
        .sortedByDescending { p -> maxOf(p.ts, replies[p.id]?.maxOfOrNull { it.ts } ?: 0L) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                Column(Modifier.weight(1f)) {
                    Text("#" + r.name, style = MaterialTheme.typography.titleMedium)
                    Text(s.emoji + " " + s.name + " · " + t("Forum"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (posts.isEmpty()) item(key = "empty") {
                    Text(t("Ancora nessun post. Apri tu la prima discussione."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
                items(posts, key = { it.id }) { p ->
                    val ex = extraOf(p)
                    val rs = replies[p.id].orEmpty()
                    val author = if (p.outgoing) t("Tu") else names[p.sender]?.displayName ?: t("Qualcuno")
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().animateItem().bounceClick(0.98f) { nav.navigate("thread/" + Uri.encode(conv) + "/" + p.id) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(ex.optString("tt").ifBlank { Rich.strip(p.text).lineSequence().first().take(80) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(Rich.strip(p.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(if (p.outgoing) Graph.identity.profile.value?.emoji ?: "🗿" else names[p.sender]?.emoji ?: "🙂", 22.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(author + " · " + Fmt.ago(p.ts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text(if (rs.size == 1) t("1 risposta") else t("{0} risposte", rs.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            val canPost = s.canWrite(r, me)
            if (canPost) ExtendedFloatingActionButton(
                onClick = { if (Graph.shards.mustAcceptRules(s)) rulesOpen = true else newPost = true },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("Nuovo post")) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
    }
    if (rulesOpen) RulesDialog(s, onDismiss = { rulesOpen = false }) { rulesOpen = false; newPost = true }
    if (newPost) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        MonoDialog(
            onDismissRequest = { newPost = false },
            title = { Text(t("Nuovo post")) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it.take(120) }, label = { Text(t("Titolo")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(body, { body = it.take(4000) }, label = { Text(t("Messaggio")) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Graph.shards.badWord(s, "$title $body")?.let { Toast.makeText(ctx, t("Questa parola non è permessa qui: {0}", it), Toast.LENGTH_SHORT).show(); return@TextButton }
                    newPost = false
                    Graph.scope.launch { Graph.messenger.sendText(conv, body.ifBlank { title }, title = title) }
                }, enabled = title.isNotBlank()) { Text(t("Pubblica")) }
            },
            dismissButton = { TextButton(onClick = { newPost = false }) { Text(t("Annulla")) } },
        )
    }
}

// ====================================================================== shard rules and slow mode

@Composable
internal fun RulesDialog(s: Shard, onDismiss: () -> Unit, onAccepted: () -> Unit) {
    MonoDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Gavel, null) },
        title = { Text(t("Le regole di {0}", s.name)) },
        text = { Column(Modifier.heightIn(max = 380.dp).padding(top = 4.dp)) { LazyColumn { item { Text(s.rules, style = MaterialTheme.typography.bodyMedium) } } } },
        confirmButton = { TextButton(onClick = { Graph.shards.acceptRules(s); onAccepted() }) { Text(t("Accetto")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Non ora")) } },
    )
}

/** in place of the box to write in: read and accept the rules first */
@Composable
internal fun RulesBar(onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.bounceClick(0.99f, onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Gavel, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(t("Leggi e accetta le regole per scrivere"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        }
    }
}

/** slow mode: how long until the next message */
@Composable
internal fun SlowBar(untilMs: Long, onDone: () -> Unit) {
    val left by produceState(((untilMs - now()) / 1000).coerceAtLeast(0), untilMs) {
        while (true) {
            value = ((untilMs - now() + 999) / 1000).coerceAtLeast(0)
            if (value <= 0L) { onDone(); break }
            delay(250)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HourglassTop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(t("Modalità lenta: puoi scrivere tra {0} s", left), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ====================================================================== message tools

/** who received and read a message of mine in a group */
@Composable
internal fun ReadBySheet(m: Message, group: Group?, names: Map<String, Friend>, onDismiss: () -> Unit) {
    val receipts by Graph.receipts.all.collectAsState()
    val mine = receipts[m.id].orEmpty()
    val me = Graph.identity.keys?.pub ?: ""
    val others = group?.memberList()?.filter { it != me }.orEmpty()
    val read = others.filter { (mine[it]?.first ?: 0) >= MsgStatus.READ }
    val delivered = others.filter { (mine[it]?.first ?: 0) == MsgStatus.DELIVERED }
    val waiting = others - read.toSet() - delivered.toSet()
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(Modifier.heightIn(max = 520.dp).padding(bottom = 24.dp)) {
            item { Text(t("Info messaggio"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp)) }
            item { Text(Graph.messenger.previewText(m), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
            listOf(Triple(t("Letto da"), read, true), Triple(t("Consegnato a"), delivered, false), Triple(t("Non ancora arrivato"), waiting, false)).forEach { (label, list, withTime) ->
                if (list.isNotEmpty()) {
                    item { com.monolith.app.ui.components.SectionLabel(label + " · " + list.size, Modifier.padding(top = 8.dp)) }
                    items(list, key = { label + it }) { pk ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(names[pk]?.emoji ?: "🙂", 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(names[pk]?.displayName ?: t("Qualcuno"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            mine[pk]?.second?.let { at -> Text((if (withTime) "" else "") + Fmt.chatStamp(at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

/** "Ricordamelo": when the message should come back */
@Composable
internal fun RemindDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val cal = { h: Int, addDays: Int -> java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, addDays); set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis }
    val evening = cal(20, 0).let { if (it <= now() + 600_000L) cal(20, 1) else it }
    val options = listOf(
        t("Tra un'ora") to now() + 3_600_000L,
        t("Tra 3 ore") to now() + 3 * 3_600_000L,
        (if (Fmt.sameDay(evening, now())) t("Stasera alle 20") else t("Domani alle 20")) to evening,
        t("Domani alle 9") to cal(9, 1),
        t("Tra una settimana") to now() + 7 * 86_400_000L,
    )
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ricordamelo")) },
        text = {
            Column {
                Text(t("Il messaggio torna come notifica e ti porta al punto giusto della chat."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                options.forEach { (label, at) ->
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) { onPick(at) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(Fmt.dayShort(at) + " · " + Fmt.time(at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

/** the notifications of a group or a room: everything, only tags, nothing */
@Composable
internal fun NotifyDialog(conv: String, onDismiss: () -> Unit) {
    val modes by Graph.roomNotify.all.collectAsState()
    val setting by remember(conv) { Graph.db.social().setting(conv) }.collectAsState(initial = null)
    val cur = modes[conv] ?: if (setting?.muted == true) 1 else 0
    val options = listOf(t("Tutti i messaggi"), t("Solo quando mi taggano"), t("Niente"))
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Notifiche")) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) {
                        Graph.roomNotify.set(conv, i)
                        Graph.scope.launch { Graph.messenger.setMuted(conv, i > 0) }
                        onDismiss()
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = cur == i, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Chiudi")) } },
    )
}

/** at the top of the chat while choosing messages */
@Composable
internal fun SelectionBar(count: Int, canCopy: Boolean, canDeleteForAll: Boolean, onClose: () -> Unit, onCopy: () -> Unit, onForward: () -> Unit, onDeleteMe: () -> Unit, onDeleteAll: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, t("Annulla")) }
            Text(if (count == 1) t("1 selezionato") else t("{0} selezionati", count), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (canCopy) IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, t("Copia")) }
            IconButton(onClick = onForward) { Icon(Icons.AutoMirrored.Rounded.Forward, t("Inoltra")) }
            IconButton(onClick = onDeleteMe) { Icon(Icons.Rounded.DeleteOutline, t("Elimina per me")) }
            if (canDeleteForAll) TextButton(onClick = onDeleteAll) { Text(t("Per tutti")) }
        }
    }
}

// ====================================================================== voice messages without holding the button

/** recording hands-free: time, pause, stop to listen, cancel, send */
@Composable
internal fun VoiceLockedBar(elapsed: Long, paused: Boolean, onPause: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.DeleteOutline, t("Annulla")) }
            if (!paused) com.monolith.app.ui.components.PulseDot(size = 12.dp) else Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.width(10.dp))
            Text(Fmt.duration(elapsed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onPause) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) t("Riprendi") else t("Pausa")) }
            IconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, t("Ascolta prima di inviare")) }
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f, onClick = onSend), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, t("Invia"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** listen to the voice message before sending it */
@Composable
internal fun VoicePreviewBar(file: File, durationMs: Long, onDelete: () -> Unit, onSend: () -> Unit) {
    val playing by com.monolith.app.net.VoicePlayer.playing.collectAsState()
    val progress by com.monolith.app.net.VoicePlayer.progress.collectAsState()
    val on = playing == "preview"
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { com.monolith.app.net.VoicePlayer.stop(); onDelete() }) { Icon(Icons.Rounded.DeleteOutline, t("Elimina")) }
            IconButton(onClick = { com.monolith.app.net.VoicePlayer.toggle("preview", file.absolutePath) }) {
                Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (on) t("Pausa") else t("Ascolta"))
            }
            LinearProgressIndicator(progress = { if (on) progress else 0f }, modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(Fmt.duration(durationMs), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f) { com.monolith.app.net.VoicePlayer.stop(); onSend() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, t("Invia"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** copy the text of several messages, oldest first */
internal fun copyMessages(ctx: android.content.Context, list: List<Message>, names: Map<String, Friend>) {
    val me = t("Tu")
    val text = list.sortedBy { it.ts }.joinToString("\n") { m ->
        (if (m.outgoing) me else names[m.sender]?.displayName ?: "") + ": " + Graph.messenger.previewText(m)
    }
    ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("msgs", text))
    Toast.makeText(ctx, t("Copiato"), Toast.LENGTH_SHORT).show()
}
