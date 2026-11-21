package com.monolith.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.core.tagOf
import com.monolith.app.net.Badges
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.BadgeEmblem
import com.monolith.app.ui.components.CoverArt
import com.monolith.app.ui.components.Covers
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.launch

/**
 * Edit my profile: picture, name, bio, status, birthday, cover and the badges in the showcase.
 * Name, picture, bio, cover and showcase are saved together with "Save"; status and birthday
 * have their own sheets and are saved right away.
 */
@Composable
fun EditProfileScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val profile by Graph.identity.profile.collectAsState()
    val p = profile ?: return
    val myPub = Graph.identity.keys?.pub ?: ""
    var emoji by rememberSaveable { mutableStateOf(p.emoji) }
    var name by rememberSaveable { mutableStateOf(p.displayName) }
    var bio by rememberSaveable { mutableStateOf(p.bio) }
    val savedCover by Graph.profileExtras.cover.collectAsState()
    var cover by rememberSaveable { mutableStateOf(savedCover) }
    val savedFeatured by Graph.profileExtras.featured.collectAsState()
    var featured by remember { mutableStateOf(savedFeatured) }
    val earned by Badges.earned.collectAsState()
    val myStatus by Graph.statuses.mine.collectAsState()
    val myBday by Graph.birthdays.mine.collectAsState()
    var picker by remember { mutableStateOf(false) }
    var statusSheet by remember { mutableStateOf(false) }
    var bdayPicker by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val changed = emoji != p.emoji || name.trim() != p.displayName || bio.trim() != p.bio || cover != savedCover || featured != savedFeatured
    fun save() {
        Graph.profileExtras.setCover(cover)
        Graph.profileExtras.setFeatured(featured)
        val np = p.copy(emoji = emoji, displayName = name.trim().ifBlank { p.displayName }, bio = bio.trim())
        Graph.scope.launch { if (np != p) Graph.messenger.updateProfile(np) else Graph.messenger.sendProfileToFriends() }
        Toast.makeText(ctx, t("Profilo aggiornato"), Toast.LENGTH_SHORT).show()
        nav.popBackStack()
    }
    BackHandler(changed) { discard = true }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (changed) discard = true else nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Modifica profilo"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Button(onClick = { save() }, enabled = changed, shape = CircleShape, modifier = Modifier.padding(end = 12.dp)) { Text(t("Salva")) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ------------------------------------------------ preview
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(186.dp)) {
                CoverArt(cover, Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(24.dp)))
                Box(Modifier.align(Alignment.BottomCenter).size(112.dp).bounceClick(0.94f) { picker = true }) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(5.dp), contentAlignment = Alignment.Center) {
                        Avatar(emoji, 102.dp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(3.dp)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Edit, t("Cambia immagine"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface) }
                }
            }
            Text(
                t("Tocca l'immagine per cambiarla"), style = MaterialTheme.typography.labelMedium, color = muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            // ------------------------------------------------ name and bio
            EditSection(t("Informazioni")) {
                OutlinedTextField(
                    name, { name = it.take(32) }, label = { Text(t("Nome visualizzato")) }, singleLine = true,
                    supportingText = { Text("${name.length}/32") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    bio, { bio = it.take(140) }, label = { Text(t("Bio")) }, minLines = 3, maxLines = 5,
                    supportingText = { Text("${bio.length}/140") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = MaterialTheme.shapes.medium,
                )
                Text(
                    "@${p.username}  ·  #${tagOf(myPub)}  ·  " + t("il nome utente non si può cambiare"),
                    style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            // ------------------------------------------------ status and birthday
            EditSection(t("Stato e compleanno")) {
                val st = myStatus?.takeIf { it.active }
                EditRow(Icons.Rounded.Mood, t("Stato"), st?.let { it.emoji + "  " + it.text.ifBlank { t("Il tuo stato") } } ?: t("Nessuno stato")) { statusSheet = true }
                EditRow(Icons.Rounded.Cake, t("Compleanno"), myBday?.let { birthdayText(it) + " · " + t("visibile solo agli amici") } ?: t("Non impostato")) { bdayPicker = true }
            }

            // ------------------------------------------------ cover
            EditSection(t("Copertina"), t("La vedono anche i tuoi amici")) {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Covers.IDS, key = { it }) { id ->
                        val sel = id == cover
                        Column(Modifier.width(150.dp)) {
                            CoverArt(
                                id,
                                Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(16.dp))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .bounceClick(0.95f) { cover = id },
                            )
                            Text(
                                Covers.name(id), style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.onSurface else muted,
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------ showcase
            EditSection(t("Distintivi in vetrina"), t("Scegli fino a 3 distintivi da mostrare sotto al tuo nome")) {
                val defs = earned.entries.sortedByDescending { it.value }.mapNotNull { Badges.def(it.key) }
                if (defs.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) {
                        Text(t("Usa Monolith e sbloccherai i primi distintivi."), style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(20.dp))
                    }
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 4,
                    ) {
                        defs.forEach { d ->
                            val idx = featured.indexOf(d.id)
                            Column(
                                Modifier.weight(1f).bounceClick(0.93f) {
                                    featured = when {
                                        idx >= 0 -> featured - d.id
                                        featured.size < 3 -> featured + d.id
                                        else -> { Toast.makeText(ctx, t("Al massimo 3 distintivi"), Toast.LENGTH_SHORT).show(); featured }
                                    }
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    BadgeEmblem(d, true, 64.dp, Modifier.padding(4.dp))
                                    if (idx >= 0) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp)
                                                .clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                                            contentAlignment = Alignment.Center,
                                        ) { Text("${idx + 1}", color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    t(d.name), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    fontWeight = if (idx >= 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (idx >= 0) MaterialTheme.colorScheme.onSurface else muted,
                                )
                            }
                        }
                        repeat((4 - defs.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { emoji = it; picker = false }
    if (statusSheet) StatusSheet { statusSheet = false }
    if (bdayPicker) BirthdayPickerDialog { bdayPicker = false }
    if (discard) {
        MonoDialog(
            onDismissRequest = { discard = false },
            title = { Text(t("Scartare le modifiche?")) },
            text = { Text(t("Le modifiche al profilo non sono ancora salvate.")) },
            confirmButton = { TextButton(onClick = { discard = false; save() }) { Text(t("Salva")) } },
            dismissButton = { TextButton(onClick = { discard = false; nav.popBackStack() }) { Text(t("Scarta")) } },
        )
    }
}

@Composable
private fun EditSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun EditRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** pick (or remove) my birthday: only day and month, sent to friends */
@Composable
fun BirthdayPickerDialog(onDismiss: () -> Unit) {
    val myBday by Graph.birthdays.mine.collectAsState()
    val init = myBday?.let {
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(2000, it.month - 1, it.day) }.timeInMillis
    }
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = init, initialDisplayedMonthMillis = init)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                state.selectedDateMillis?.let { ms ->
                    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                    val b = com.monolith.app.data.Birthday(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
                    Graph.scope.launch { Graph.messenger.setMyBirthday(b) }
                }
            }) { Text(t("Salva")) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                if (myBday != null) Graph.scope.launch { Graph.messenger.setMyBirthday(null) }
            }) { Text(if (myBday != null) t("Togli") else t("Annulla")) }
        },
    ) {
        androidx.compose.material3.DatePicker(state, showModeToggle = false, title = {
            Text(t("Il tuo compleanno"), modifier = Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium)
        })
    }
}
