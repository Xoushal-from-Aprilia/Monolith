package com.monolith.app.ui.screens

import android.Manifest
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.EmojiArt
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.PulseDot
import com.monolith.app.ui.components.RoundAction
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.LocalDark
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private fun cartoSource(dark: Boolean): XYTileSource {
    val style = if (dark) "dark_all" else "light_all"
    return XYTileSource(
        "Carto-$style", 0, 20, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/$style/",
            "https://b.basemaps.cartocdn.com/$style/",
            "https://c.basemaps.cartocdn.com/$style/",
        ),
        "© OpenStreetMap contributors © CARTO",
    )
}

private fun distanceText(a: Location?, lat: Double, lon: Double): String? {
    a ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, lat, lon, out)
    val d = out[0]
    return if (d < 1000) "${d.toInt()} m" else String.format(java.util.Locale.ITALIAN, "%.1f km", d / 1000f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    val me by Graph.location.me.collectAsState()
    val sharing by Graph.location.sharing.collectAsState()
    val p2p by Graph.rtc.connected.collectAsState()
    val profile by Graph.identity.profile.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var hasPerm by remember { mutableStateOf(Graph.location.hasPermission()) }
    var shareSheet by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000); tick++
        }
    }
    val t = now() + tick * 0
    val sharingWithMe = friends.filter { it.sharingUntil > t && it.lat != null && it.lon != null }
    val iShareWith = friends.filter { it.iShareUntil > t }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = Graph.location.hasPermission()
    }

    DisposableEffect(hasPerm) {
        Graph.location.watch(true)
        onDispose { Graph.location.watch(false) }
    }

    // keep peers connected while someone shares live location
    LaunchedEffect(sharingWithMe.map { it.pubkey }) {
        sharingWithMe.forEach { Graph.rtc.ensureConnected(it.pubkey) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                MapView(c).apply {
                    setTileSource(cartoSource(dark))
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    controller.setZoom(5.5)
                    controller.setCenter(GeoPoint(42.5, 12.5))
                    mapView = this
                }
            },
            update = { mv ->
                val src = cartoSource(dark)
                if (mv.tileProvider.tileSource.name() != src.name()) mv.setTileSource(src)
                mv.overlays.clear()
                val res = mv.context.resources
                sharingWithMe.forEach { f ->
                    val m = Marker(mv)
                    m.position = GeoPoint(f.lat!!, f.lon!!)
                    m.icon = BitmapDrawable(res, EmojiArt.bitmap(f.emoji, 132, background = if (dark) 0xFF1D1D1D.toInt() else 0xFFFFFFFF.toInt(), ring = if (dark) 0xFFF2F2F2.toInt() else 0xFF0A0A0A.toInt()))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = f.displayName
                    m.snippet = "Aggiornato " + Fmt.ago(f.locAt)
                    mv.overlays.add(m)
                }
                me?.let { loc ->
                    val m = Marker(mv)
                    m.position = GeoPoint(loc.latitude, loc.longitude)
                    m.icon = BitmapDrawable(res, EmojiArt.bitmap(profile?.emoji ?: "🗿", 120, background = if (dark) 0xFFF2F2F2.toInt() else 0xFF0A0A0A.toInt()))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = "Tu"
                    mv.overlays.add(m)
                    if (!centeredOnce) {
                        centeredOnce = true
                        mv.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.0, 900L)
                    }
                }
                mv.invalidate()
            },
        )

        // ---- top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 6.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mappa", style = MaterialTheme.typography.titleLarge)
                    if (sharing) {
                        Spacer(Modifier.width(10.dp))
                        PulseDot(size = 10.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            RoundAction(Icons.Rounded.MyLocation, "La mia posizione", size = 48.dp) {
                if (!hasPerm) permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else me?.let { mapView?.controller?.animateTo(GeoPoint(it.latitude, it.longitude), 16.0, 700L) }
            }
        }

        // ---- bottom panel
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).shadow(12.dp, MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(vertical = 14.dp)) {
                if (!hasPerm) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Attiva la posizione per vederti sulla mappa e condividerla.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = CircleShape,
                    ) { Text("Consenti posizione") }
                } else {
                    AnimatedVisibility(iShareWith.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(size = 12.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Condividi con " + iShareWith.joinToString { it.displayName }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val until = iShareWith.maxOf { it.iShareUntil }
                                Text(
                                    "Ancora ${Fmt.remaining(until)} · " + if (iShareWith.any { it.pubkey in p2p }) "peer-to-peer" else "via relay cifrato",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { Graph.location.stopSharing() }) {
                                Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }
                    if (sharingWithMe.isNotEmpty()) {
                        SectionLabel("Condividono con te")
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            items(sharingWithMe, key = { it.pubkey }) { f ->
                                FriendLocationRow(f, me, f.pubkey in p2p, Modifier.animateItem()) {
                                    mapView?.controller?.animateTo(GeoPoint(f.lat!!, f.lon!!), 16.5, 700L)
                                }
                            }
                        }
                    } else if (iShareWith.isEmpty()) {
                        Text(
                            "Nessuno sta condividendo la posizione con te in questo momento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { shareSheet = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Rounded.NearMe, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (iShareWith.isEmpty()) "Condividi la mia posizione" else "Gestisci condivisione")
                    }
                }
            }
        }
    }

    if (shareSheet) ShareSheet(friends.filter { it.status == FriendStatus.FRIEND }, iShareWith.map { it.pubkey }.toSet()) { shareSheet = false }
}

@Composable
private fun FriendLocationRow(f: Friend, me: Location?, direct: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(f.emoji, 42.dp, online = now() - f.locAt < 60_000)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    "aggiornato " + Fmt.ago(f.locAt),
                    distanceText(me, f.lat ?: 0.0, f.lon ?: 0.0),
                    if (direct) "diretto" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("fino " + Fmt.time(f.sharingUntil), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val DURATIONS = listOf(15 to "15 min", 60 to "1 ora", 480 to "8 ore", 1440 to "24 ore")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareSheet(friends: List<Friend>, active: Set<String>, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(active.ifEmpty { friends.map { it.pubkey }.take(1).toSet() }) }
    var duration by remember { mutableIntStateOf(1) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Posizione in tempo reale", style = MaterialTheme.typography.titleLarge)
            Text(
                "Gli aggiornamenti viaggiano cifrati, direttamente tra i telefoni quando possibile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Con chi", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (friends.isEmpty()) Text("Aggiungi prima un amico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { f ->
                    val sel = f.pubkey in selected
                    FilterChip(
                        selected = sel,
                        onClick = { selected = if (sel) selected - f.pubkey else selected + f.pubkey },
                        label = { Text(f.displayName) },
                        leadingIcon = { Text(f.emoji) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Per quanto tempo", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DURATIONS.forEachIndexed { i, (_, label) ->
                    SegmentedButton(
                        selected = duration == i,
                        onClick = { duration = i },
                        shape = SegmentedButtonDefaults.itemShape(i, DURATIONS.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val stopped = active - selected
                    stopped.forEach { Graph.location.stopSharing(it) }
                    if (selected.isNotEmpty()) Graph.location.startSharing(selected.toList(), DURATIONS[duration].first)
                    onClose()
                },
                enabled = selected.isNotEmpty() || active.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
            ) { Text(if (selected.isEmpty()) "Interrompi condivisione" else "Avvia condivisione") }
        }
    }
}
