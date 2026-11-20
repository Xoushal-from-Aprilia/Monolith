package com.monolith.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.monolith.app.audio.SoundDef
import com.monolith.app.audio.Sounds
import com.monolith.app.core.t
import com.monolith.app.ui.components.bounceClick

@Composable
fun SoundsScreen(nav: NavHostController) {
    val s by Sounds.settings.collectAsState()
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { Sounds.stopPreview() } }
    fun play(id: String) {
        playing = id
        Sounds.preview(id)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Suoni"), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(
                    t("Di serie Monolith usa i suoni del telefono. Qui puoi scegliere i suoni calmi creati per l'app: tocca un suono per ascoltarlo."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                SoundGroup(Icons.Rounded.TouchApp, t("Tasti"), t("Un tocco leggero quando premi qualcosa")) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Suoni dei tasti"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = s.taps, onCheckedChange = { on -> Sounds.update { it.copy(taps = on) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                    Column(Modifier.alpha(if (s.taps) 1f else 0.45f)) {
                        Sounds.TAPS.forEach { d ->
                            SoundRow(d.name, d.id == s.tap, playing == d.id) {
                                Sounds.update { it.copy(tap = d.id, taps = true) }
                                play(d.id)
                            }
                        }
                    }
                }
            }
            item {
                SoundGroup(Icons.Rounded.Call, t("Suoneria"), t("Per le chiamate e le videochiamate")) {
                    Sounds.RINGTONES.forEach { d -> SoundOption(d, s.ringtone, playing) { Sounds.update { it.copy(ringtone = d.id) }; play(d.id) } }
                    SoundRow(t("Suoneria del telefono"), s.ringtone == Sounds.SYSTEM, false, translate = false) {
                        Sounds.stopPreview(); playing = null
                        Sounds.update { it.copy(ringtone = Sounds.SYSTEM) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Toni delle chiamate"), style = MaterialTheme.typography.bodyLarge)
                            Text(t("Attesa, collegamento e fine chiamata"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = s.callTones, onCheckedChange = { on -> Sounds.update { it.copy(callTones = on) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            item {
                SoundGroup(Icons.Rounded.ChatBubble, t("Messaggi"), null) {
                    Sounds.MESSAGES.forEach { d -> SoundOption(d, s.message, playing) { Sounds.update { it.copy(message = d.id) }; play(d.id) } }
                    SystemAndSilent(s.message) { v -> Sounds.stopPreview(); playing = null; Sounds.update { it.copy(message = v) } }
                }
            }
            item {
                SoundGroup(Icons.Rounded.People, t("Incontri e arrivi"), t("Quando un amico raggiunge un luogo comune o vi ritrovate")) {
                    Sounds.MEETINGS.forEach { d -> SoundOption(d, s.meeting, playing) { Sounds.update { it.copy(meeting = d.id) }; play(d.id) } }
                    SystemAndSilent(s.meeting) { v -> Sounds.stopPreview(); playing = null; Sounds.update { it.copy(meeting = v) } }
                }
            }
            item {
                SoundGroup(Icons.Rounded.Event, t("Promemoria"), null) {
                    Sounds.REMINDERS.forEach { d -> SoundOption(d, s.reminder, playing) { Sounds.update { it.copy(reminder = d.id) }; play(d.id) } }
                    SystemAndSilent(s.reminder) { v -> Sounds.stopPreview(); playing = null; Sounds.update { it.copy(reminder = v) } }
                }
            }
        }
    }
}

@Composable
private fun SoundGroup(icon: ImageVector, title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SoundOption(d: SoundDef, selectedId: String, playing: String?, onClick: () -> Unit) =
    SoundRow(d.name, d.id == selectedId, playing == d.id, onClick = onClick)

@Composable
private fun SystemAndSilent(current: String, onPick: (String) -> Unit) {
    SoundRow(t("Suono del telefono"), current == Sounds.SYSTEM, false, translate = false) { onPick(Sounds.SYSTEM) }
    SoundRow(t("Nessun suono"), current == Sounds.NONE, false, translate = false) { onPick(Sounds.NONE) }
}

@Composable
private fun SoundRow(name: String, selected: Boolean, playing: Boolean, translate: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow, label = "soundBg",
    )
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, label = "check")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large).background(bg)
            .bounceClick(0.98f, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PlayArrow, null,
            Modifier.size(18.dp).alpha(if (translate) (if (playing) 1f else 0.45f) else 0f),
        )
        Spacer(Modifier.width(12.dp))
        Text(if (translate) t(name) else name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Check, null, Modifier.size(20.dp).scale(checkScale))
    }
}
