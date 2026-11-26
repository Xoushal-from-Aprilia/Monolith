package com.monolith.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monolith.app.BuildConfig
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.launch

private fun open(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** what the developer's channel puts on screen: blocked account, update needed, news, new version */
@Composable
fun DevOverlays() {
    val ctx = LocalContext.current
    val s by Graph.dev.state.collectAsState()
    val seenNews by Graph.dev.seenNews.collectAsState()
    val seenUpdate by Graph.dev.seenUpdate.collectAsState()
    val me = Graph.identity.keys?.pub
    when {
        me != null && me in s.blocked -> {
            var feedback by remember { mutableStateOf(false) }
            DevWall(Icons.Rounded.Block, t("Account bloccato"), t("Lo sviluppatore di Monolith ha bloccato questo account. Se pensi che sia un errore, scrivigli."), t("Scrivi allo sviluppatore")) { feedback = true }
            if (feedback) FeedbackDialog { feedback = false }
            return
        }
        Graph.dev.updateRequired -> {
            DevWall(
                Icons.Rounded.SystemUpdate, t("Aggiorna Monolith"),
                t("Questa versione ({0}) non funziona più. Scarica la nuova versione per continuare.", BuildConfig.VERSION_NAME),
                t("Scarica l'aggiornamento"),
            ) { open(ctx, Graph.dev.updateUrl) }
            return
        }
    }
    val news = s.news
    if (news != null && news.id != seenNews) {
        MonoDialog(
            onDismissRequest = { Graph.dev.markNewsSeen(news.id) },
            title = { Text(news.title.ifBlank { t("Novità da Monolith") }) },
            text = { Text(news.body, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { Graph.dev.markNewsSeen(news.id) }) { Text(t("Ok")) } },
            dismissButton = { if (news.link.isNotBlank()) TextButton(onClick = { Graph.dev.markNewsSeen(news.id); open(ctx, news.link) }) { Text(t("Apri")) } },
        )
        return
    }
    val latest = s.latest
    if (latest != null && Graph.dev.updateAvailable && latest != seenUpdate) {
        MonoDialog(
            onDismissRequest = { Graph.dev.markUpdateSeen(latest) },
            title = { Text(t("È uscita la versione {0}", latest)) },
            text = { Text(s.latestNotes.ifBlank { t("C'è una nuova versione di Monolith con novità e correzioni.") }) },
            confirmButton = { TextButton(onClick = { Graph.dev.markUpdateSeen(latest); open(ctx, Graph.dev.updateUrl) }) { Text(t("Scarica")) } },
            dismissButton = { TextButton(onClick = { Graph.dev.markUpdateSeen(latest) }) { Text(t("Più tardi")) } },
        )
    }
}

@Composable
private fun DevWall(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).bounceClick(1f) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(40.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Button(onClick = onAction, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(action) }
        }
    }
}

/** a message to the developer */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Scrivi allo sviluppatore")) },
        text = {
            Column {
                Text(t("Idee, problemi, complimenti: arriva solo allo sviluppatore, cifrato."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { if (it.length <= 1000) text = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("Scrivi qui…")) })
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val body = text.trim()
                onDismiss()
                scope.launch {
                    val ok = Graph.dev.feedback(body)
                    Toast.makeText(ctx, if (ok) t("Inviato, grazie!") else t("Non inviato: riprova più tardi"), Toast.LENGTH_SHORT).show()
                }
            }) { Text(t("Invia")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

private val REASONS = listOf("Spam" /*i18n*/, "Molestie o minacce" /*i18n*/, "Contenuti inappropriati" /*i18n*/, "Account falso" /*i18n*/, "Altro" /*i18n*/)

/** reports an account to the developer, who can block it for everyone */
@Composable
fun ReportDialog(pk: String, name: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf(REASONS[0]) }
    var text by remember { mutableStateOf("") }
    MonoDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Segnala {0}", name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("La segnalazione arriva allo sviluppatore. {0} non saprà chi l'ha mandata.", name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                REASONS.forEach { r ->
                    Row(Modifier.fillMaxWidth().bounceClick(0.98f) { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(t(r), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(text, { if (it.length <= 500) text = it }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("Dettagli (facoltativo)")) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                scope.launch {
                    val ok = Graph.dev.report(pk, name, reason, text.trim())
                    Toast.makeText(ctx, if (ok) t("Segnalazione inviata") else t("Non inviato: riprova più tardi"), Toast.LENGTH_SHORT).show()
                }
            }) { Text(t("Segnala")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annulla")) } },
    )
}

/** the small «Segnala» link at the bottom of someone's profile */
@Composable
fun ReportLink(pk: String, name: String) {
    val s by Graph.dev.state.collectAsState()
    if (!s.active) return
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { open = true }) {
            Text(t("Segnala account"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (open) ReportDialog(pk, name) { open = false }
    Spacer(Modifier.width(0.dp))
}
