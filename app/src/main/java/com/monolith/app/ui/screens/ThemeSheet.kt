package com.monolith.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.LocalDark
import com.monolith.app.ui.theme.Palette
import com.monolith.app.ui.theme.Palettes
import com.monolith.app.ui.theme.schemeFor

/** the colour themes, each shown as a small picture of the app in its colours */
@Composable
fun ThemeSheet(onDismiss: () -> Unit) {
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(t("Colori"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("Scegli il colore di Monolith. Chiaro o scuro lo decidi sopra, in Aspetto."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ThemeGrid()
        }
    }
}

@Composable
internal fun ThemeGrid() {
    val cur by Graph.prefs.palette.collectAsState()
    val list = Palettes.ALL + if (Palettes.dynamicAvailable) listOf(Palettes.DYNAMIC_PALETTE) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        list.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> ThemeCard(p, p.key == cur, Modifier.weight(1f)) { Graph.prefs.setPalette(p.key) } }
                // keeps the last row aligned to the grid
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeCard(p: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val c = remember(p.key, dark) { schemeFor(ctx, p.key, dark) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(22.dp)).background(c.background)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                .bounceClick(0.94f, onClick = onClick),
        ) {
            MiniApp(c)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(t(p.name), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** a tiny drawing of a chat screen in the theme's colours */
@Composable
private fun MiniApp(c: ColorScheme) {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(c.primaryContainer))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(34.dp).height(6.dp).clip(CircleShape).background(c.onSurface.copy(alpha = 0.7f)))
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(48.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.surfaceContainerHighest))
        Box(Modifier.align(Alignment.End).width(40.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.primary))
        Box(Modifier.width(30.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.surfaceContainerHighest))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(18.dp).clip(CircleShape).background(c.surfaceContainerHigh), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(3.dp).size(12.dp).clip(CircleShape).background(c.primary))
        }
    }
}

/** the swatch next to «Colori» in the settings */
@Composable
internal fun PaletteSwatch() {
    val cur by Graph.prefs.palette.collectAsState()
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val c = remember(cur, dark) { schemeFor(ctx, cur, dark) }
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        listOf(c.primary, c.primaryContainer, c.surfaceContainerHighest).forEach {
            Box(Modifier.size(20.dp).clip(CircleShape).background(it).border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape))
        }
    }
}
