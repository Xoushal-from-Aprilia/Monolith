package com.monolith.app.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.t
import com.monolith.app.data.Message
import com.monolith.app.net.VoicePlayer
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.Rich
import com.monolith.app.ui.components.bounceClick
import java.io.File

private val URL = Regex("https?://[^\\s<>\"]+")

/** everything shared in a chat: photos and videos, voice messages, links and files; and search inside the chat */
@Composable
fun ChatMediaScreen(nav: NavHostController, conv: String, search: Boolean) {
    val ctx = LocalContext.current
    val all by remember(conv) { Graph.db.messages().conversation(conv, 5000) }.collectAsState(initial = emptyList())
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val names = people.associateBy { it.pubkey }
    var tab by remember { mutableIntStateOf(if (search) 4 else 0) }
    var query by remember { mutableStateOf("") }
    val visual = all.filter { (it.kind == "photo" && it.extra?.contains("\"once\":true") != true || it.kind == "video") && it.mediaPath != null && File(it.mediaPath).exists() }
    val voices = all.filter { it.kind == "voice" && it.mediaPath != null }
    val links = all.filter { it.kind == "text" }.flatMap { m -> URL.findAll(m.text).map { m to it.value }.toList() }
    val files = all.filter { it.kind == "file" && it.mediaPath != null }
    val tabs = listOf(t("Foto e video") + " · " + visual.size, t("Vocali") + " · " + voices.size, t("Link") + " · " + links.size, t("File") + " · " + files.size, t("Cerca"))
    fun open(m: Message) {
        nav.popBackStack()
        nav.navigate(Routes.chat(conv, m.id)) { launchSingleTop = true }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
                    Text(t("Media, link e file"), style = MaterialTheme.typography.titleMedium)
                }
                SecondaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) }) }
                }
            }
        }
        when (tab) {
            0 -> if (visual.isEmpty()) EmptyHint(t("Nessuna foto o video in questa chat.")) else LazyVerticalGrid(
                GridCells.Fixed(3), contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visual, key = { it.id }) { m ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(Color.Black).bounceClick(0.96f) {
                        val p = m.mediaPath ?: return@bounceClick
                        if (m.kind == "video") nav.navigate("video?path=" + Uri.encode(p)) else nav.navigate(Routes.photo(p, "photo-" + m.id))
                    }, contentAlignment = Alignment.Center) {
                        if (m.kind == "photo") AsyncImage(model = File(m.mediaPath!!), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else {
                            val thumb = remember(m.id) {
                                runCatching { val b = java.util.Base64.getDecoder().decode(extraOf(m).optString("tb")); BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull()
                            }
                            if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            1 -> {
                val playing by VoicePlayer.playing.collectAsState()
                if (voices.isEmpty()) EmptyHint(t("Nessun vocale in questa chat."))
                else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(voices, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.9f) { m.mediaPath?.let { VoicePlayer.toggle(m.id, it) } }, contentAlignment = Alignment.Center) {
                                Icon(if (playing == m.id) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).bounceClick(0.98f) { open(m) }) {
                                Text(if (m.outgoing) t("Tu") else names[m.sender]?.displayName ?: t("Qualcuno"), style = MaterialTheme.typography.titleSmall)
                                Text(Fmt.duration(extraOf(m).optLong("dur")) + " · " + Fmt.chatStamp(m.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            2 -> if (links.isEmpty()) EmptyHint(t("Nessun link in questa chat.")) else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(links, key = { it.first.id + it.second }) { (m, url) ->
                    Row(
                        Modifier.fillMaxWidth().bounceClick(0.98f) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Link, null) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Uri.parse(url).host ?: url, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(Fmt.chatStamp(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.bounceClick(0.9f) { open(m) })
                    }
                }
            }
            3 -> if (files.isEmpty()) EmptyHint(t("Nessun file in questa chat.")) else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(files, key = { it.id }) { m ->
                    val ex = extraOf(m)
                    Row(
                        Modifier.fillMaxWidth().bounceClick(0.98f) { m.mediaPath?.let { openFile(ctx, it, ex.optString("mime")) } }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width = 36.dp, height = 44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Text(ex.optString("name").substringAfterLast('.', "").uppercase().take(4).ifBlank { "FILE" }, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.optString("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(readableSize(ex.optLong("size")) + " · " + Fmt.chatStamp(m.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> Column {
                OutlinedTextField(
                    query, { query = it.take(80) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t("Cerca in questa chat")) },
                    singleLine = true, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                val q = query.trim().lowercase()
                val found = if (q.length < 2) emptyList() else all.filter { (it.kind == "text" || it.kind == "photo" || it.kind == "video") && Rich.strip(it.text).lowercase().contains(q) }
                if (q.length >= 2 && found.isEmpty()) EmptyHint(t("Nessun messaggio trovato."))
                LazyColumn {
                    items(found, key = { it.id }) { m ->
                        Column(Modifier.fillMaxWidth().bounceClick(0.98f) { open(m) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Row {
                                Text(if (m.outgoing) t("Tu") else names[m.sender]?.displayName ?: t("Qualcuno"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(Fmt.chatStamp(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Rich.strip(m.text), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
