package com.monolith.app.ui.screens

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.LocalDark
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import com.monolith.app.ui.components.MonoSheet

/**
 * Free map services that need no account or key, always drawn in Monolith's
 * monochrome palette: white paper by day, black ink by night.
 */
object MapLook {
    class Style(
        val name: String,          // Italian key, translated with t()
        val provider: String,
        val icon: ImageVector,
        val source: XYTileSource,
        val attribution: String,
        val infoUrl: String,
    )

    // fair-use rules of the public tile servers: few parallel downloads, no bulk/preventive fetching,
    // a meaningful User-Agent (set in Graph)
    private fun policy() = TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    )

    val STYLES: List<Style> = listOf(
        Style(
            "Classica", "OpenStreetMap", Icons.Rounded.Map,
            XYTileSource("OSM-Standard", 0, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/"), "© OpenStreetMap", policy()),
            "© OpenStreetMap", "https://www.openstreetmap.org/copyright",
        ),
        Style(
            "Essenziale", "Humanitarian · OpenStreetMap France", Icons.Rounded.Explore,
            XYTileSource(
                "OSM-HOT", 0, 19, 256, ".png",
                arrayOf("https://a.tile.openstreetmap.fr/hot/", "https://b.tile.openstreetmap.fr/hot/", "https://c.tile.openstreetmap.fr/hot/"),
                "© OpenStreetMap · HOT · OSM France", policy(),
            ),
            "© OpenStreetMap · HOT · OSM France", "https://www.openstreetmap.org/copyright",
        ),
        Style(
            "Topografica", "OpenTopoMap", Icons.Rounded.Terrain,
            XYTileSource("OpenTopoMap", 0, 17, 256, ".png", arrayOf("https://tile.opentopomap.org/"), "© OpenStreetMap · SRTM · © OpenTopoMap (CC-BY-SA)", policy()),
            "© OpenStreetMap · SRTM · © OpenTopoMap (CC-BY-SA)", "https://opentopomap.org/about",
        ),
    )

    fun style(i: Int): Style = STYLES.getOrElse(i) { STYLES[0] }

    /** greyscale (Rec. 709 luma) with contrast; [invert] turns the paper map into a black one */
    private fun mono(scale: Float, offset: Float, invert: Boolean): ColorMatrixColorFilter {
        val r = 0.2126f; val g = 0.7152f; val b = 0.0722f
        val k = if (invert) -scale else scale
        val c = (if (invert) scale + offset else offset) * 255f
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r * k, g * k, b * k, 0f, c,
                    r * k, g * k, b * k, 0f, c,
                    r * k, g * k, b * k, 0f, c,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    val WHITE: ColorMatrixColorFilter = mono(1.35f, -0.33f, invert = false)
    val BLACK: ColorMatrixColorFilter = mono(1.1f, 0f, invert = true)
    fun filter(dark: Boolean) = if (dark) BLACK else WHITE

    /** colour of tiles still loading (before the filter): ends up as the map's own paper colour */
    val LOADING: Int = 0xFFEFEFEF.toInt()
    fun backdrop(dark: Boolean) = if (dark) 0xFF111111.toInt() else 0xFFEEEEEE.toInt()

    fun toneLabel(tone: Int) = when (tone) {
        1 -> t("Bianca")
        2 -> t("Nera")
        else -> t("Automatica")
    }
}

/** true when the map should be drawn black */
@Composable
fun rememberMapDark(): Boolean {
    val tone by Graph.prefs.mapTone.collectAsState()
    val appDark = LocalDark.current
    return when (tone) {
        1 -> false
        2 -> true
        else -> appDark
    }
}

/** the visible credit the map services ask for; tapping opens their terms */
@Composable
fun MapAttribution(style: MapLook.Style, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Surface(
        modifier = modifier.clip(CircleShape).bounceClick(0.95f) { runCatching { uri.openUri(style.infoUrl) } },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
    ) {
        Text(
            style.attribution,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun MapStyleSheet(onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val styleIdx by Graph.prefs.mapStyle.collectAsState()
    val tone by Graph.prefs.mapTone.collectAsState()
    val mapDark = rememberMapDark()
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(t("Stile della mappa"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("Mappe gratuite, senza account, sempre in bianco e nero come Monolith."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Text(t("Colore"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (0..2).forEach { i ->
                    SegmentedButton(
                        selected = tone == i, onClick = { Graph.prefs.setMapTone(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                        icon = {},
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ToneDot(
                                when (i) { 1 -> false; 2 -> true; else -> mapDark },
                                half = i == 0,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(MapLook.toneLabel(i))
                        }
                    }
                }
            }
            Text(
                t("Automatica segue il tema dell'app: bianca di giorno, nera con il tema scuro."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(18.dp))
            Text(t("Stile"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapLook.STYLES.forEachIndexed { i, s ->
                    val sel = i == styleIdx
                    val bg by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                        label = "styleBg",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(bg)
                            .bounceClick(0.98f) { Graph.prefs.setMapStyle(i) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(s.icon, null, Modifier.size(20.dp), tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t(s.name), style = MaterialTheme.typography.titleSmall)
                            Text(s.provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** small round swatch: white, black, or half/half for "automatic" */
@Composable
private fun ToneDot(dark: Boolean, half: Boolean) {
    val ring = MaterialTheme.colorScheme.outline
    Box(Modifier.size(14.dp).clip(CircleShape).border(1.dp, ring, CircleShape)) {
        if (half) {
            Row(Modifier.size(14.dp)) {
                Box(Modifier.weight(1f).height(14.dp).background(Color.White))
                Box(Modifier.weight(1f).height(14.dp).background(Color.Black))
            }
        } else {
            Box(Modifier.size(14.dp).background(if (dark) Color.Black else Color.White))
        }
    }
}
