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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
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
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.ArrivalAlert
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.PulseDot
import com.monolith.app.ui.components.RoundAction
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.LocalDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private fun cartoSource(dark: Boolean): XYTileSource {
    val style = if (dark) "dark_all" else "light_all"
    return XYTileSource(
        "Carto-$style", 0, 20, 256, ".png",
        arrayOf("https://a.basemaps.cartocdn.com/$style/", "https://b.basemaps.cartocdn.com/$style/", "https://c.basemaps.cartocdn.com/$style/"),
        "© OpenStreetMap contributors © CARTO",
    )
}

private fun distanceTo(a: Location?, lat: Double, lon: Double): String? {
    a ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, lat, lon, out)
    return Fmt.distance(out[0])
}

private fun etaText(sec: Int): String = if (sec < 60) t("meno di 1 min") else if (sec < 3600) "${sec / 60} min" else "${sec / 3600} h ${(sec % 3600) / 60} min"

@Composable
fun MapScreen(nav: NavHostController) {
    val dark = LocalDark.current
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    val alerts by remember { Graph.db.social().alerts() }.collectAsState(initial = emptyList())
    val me by Graph.location.me.collectAsState()
    val sharing by Graph.location.sharing.collectAsState()
    val otw by Graph.location.onTheWay.collectAsState()
    val profile by Graph.identity.profile.collectAsState()
    val openShare by NavBus.openShareSheet.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var hasPerm by remember { mutableStateOf(Graph.location.hasPermission()) }
    var shareSheet by remember { mutableStateOf(false) }
    var otwSheet by remember { mutableStateOf(false) }
    var alertFor by remember { mutableStateOf<Friend?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(openShare) {
        if (openShare) { shareSheet = true; NavBus.openShareSheet.value = false }
    }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val tNow = now() + tick * 0
    val sharingWithMe = friends.filter { it.sharingUntil > tNow && it.lat != null && it.lon != null }
    val iShareWith = friends.filter { it.iShareUntil > tNow }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPerm = Graph.location.hasPermission() }

    DisposableEffect(hasPerm) {
        Graph.location.watch(true)
        onDispose { Graph.location.watch(false) }
    }
    LaunchedEffect(sharingWithMe.map { it.pubkey }) { sharingWithMe.forEach { Graph.rtc.ensureConnected(it.pubkey) } }

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

    fun center(): GeoPoint? = mapView?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) }

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
                val ink = if (dark) 0xFFF2F2F2.toInt() else 0xFF0A0A0A.toInt()
                alerts.forEach { a ->
                    val circle = Polygon(mv)
                    circle.points = Polygon.pointsAsCircle(GeoPoint(a.lat, a.lon), a.radius.toDouble())
                    circle.fillPaint.color = (ink and 0x00FFFFFF) or 0x18000000
                    circle.outlinePaint.color = (ink and 0x00FFFFFF) or 0x66000000
                    circle.outlinePaint.strokeWidth = 3f
                    circle.title = a.label
                    mv.overlays.add(circle)
                }
                otw?.let { o ->
                    val m = Marker(mv)
                    m.position = GeoPoint(o.lat, o.lon)
                    m.icon = BitmapDrawable(res, EmojiArt.bitmap("🏁", 110, background = if (dark) 0xFF1D1D1D.toInt() else 0xFFFFFFFF.toInt(), ring = ink))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = o.label
                    mv.overlays.add(m)
                }
                sharingWithMe.forEach { f ->
                    val m = Marker(mv)
                    m.position = GeoPoint(f.lat!!, f.lon!!)
                    m.icon = BitmapDrawable(res, EmojiArt.bitmap(f.emoji, 132, background = if (dark) 0xFF1D1D1D.toInt() else 0xFFFFFFFF.toInt(), ring = ink))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = f.displayName
                    m.snippet = t("Aggiornato {0}", Fmt.ago(f.locAt))
                    mv.overlays.add(m)
                }
                me?.let { loc ->
                    val m = Marker(mv)
                    m.position = GeoPoint(loc.latitude, loc.longitude)
                    m.icon = BitmapDrawable(res, EmojiArt.bitmap(profile?.emoji ?: "🗿", 120, background = ink))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = t("Tu")
                    mv.overlays.add(m)
                    if (!centeredOnce) {
                        centeredOnce = true
                        mv.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.0, 900L)
                    }
                }
                mv.invalidate()
            },
        )
        // centre crosshair (used to pick places)
        Icon(Icons.Rounded.Add, null, Modifier.align(Alignment.Center).size(22.dp).alpha(0.35f))

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 6.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Mappa"), style = MaterialTheme.typography.titleLarge)
                    if (sharing) {
                        Spacer(Modifier.width(10.dp)); PulseDot(size = 10.dp); Spacer(Modifier.width(6.dp))
                        Text("LIVE", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            RoundAction(Icons.Rounded.MyLocation, t("La mia posizione"), size = 48.dp) {
                if (!hasPerm) permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else me?.let { mapView?.controller?.animateTo(GeoPoint(it.latitude, it.longitude), 16.0, 700L) }
            }
        }

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
                        Text(t("Attiva la posizione per vederti sulla mappa e condividerla."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = CircleShape,
                    ) { Text(t("Consenti posizione")) }
                } else {
                    AnimatedVisibility(otw != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val o = otw
                        val f = friends.firstOrNull { it.pubkey == o?.friend }
                        Row(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DirectionsWalk, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("Stai arrivando da {0}", f?.displayName ?: ""), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    (o?.let { distanceTo(me, it.lat, it.lon) } ?: "…") + " · " + t("si ferma da solo all'arrivo"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { Graph.location.cancelOnTheWay() }) { Text(t("Annulla")) }
                        }
                    }
                    AnimatedVisibility(iShareWith.isNotEmpty() && otw == null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(size = 12.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("Condividi con {0}", iShareWith.joinToString { it.displayName }), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t("Ancora {0}", Fmt.remaining(iShareWith.maxOfOrNull { it.iShareUntil } ?: 0L)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { Graph.location.stopSharing() }) {
                                Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Stop"))
                            }
                        }
                    }
                    if (sharingWithMe.isNotEmpty()) {
                        SectionLabel(t("Condividono con te"))
                        LazyColumn(Modifier.heightIn(max = 210.dp)) {
                            items(sharingWithMe, key = { it.pubkey }) { f ->
                                FriendLocationRow(f, me, alerts.any { it.friend == f.pubkey }, Modifier.animateItem(), onAlert = { alertFor = f }) {
                                    mapView?.controller?.animateTo(GeoPoint(f.lat!!, f.lon!!), 16.5, 700L)
                                }
                            }
                        }
                    } else if (iShareWith.isEmpty() && otw == null) {
                        Text(t("Nessuno sta condividendo la posizione con te in questo momento."), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { shareSheet = true }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                            Icon(Icons.Rounded.NearMe, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (iShareWith.isEmpty()) t("Condividi") else t("Gestisci"))
                        }
                        OutlinedButton(onClick = { otwSheet = true }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                            Icon(Icons.Rounded.DirectionsWalk, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                            Text(t("Sto arrivando"))
                        }
                    }
                }
            }
        }
    }

    if (shareSheet) ShareSheet(friends.filter { it.status == FriendStatus.FRIEND }, iShareWith.map { it.pubkey }.toSet()) { shareSheet = false }
    if (otwSheet) OnTheWaySheet(friends.filter { it.status == FriendStatus.FRIEND }, center(), onClose = { otwSheet = false })
    alertFor?.let { f -> AlertSheet(f, me, center(), alerts.filter { it.friend == f.pubkey }) { alertFor = null } }
}

@Composable
private fun FriendLocationRow(f: Friend, me: Location?, hasAlert: Boolean, modifier: Modifier, onAlert: () -> Unit, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(f.emoji, 42.dp, online = now() - f.locAt < 60_000)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, style = MaterialTheme.typography.titleMedium)
            val arriving = f.etaSec >= 0 && f.distM >= 0 && now() - f.locAt < 120_000
            Text(
                if (arriving) t("In arrivo · {0} · circa {1}", Fmt.distance(f.distM.toFloat()), etaText(f.etaSec))
                else listOfNotNull(t("aggiornato {0}", Fmt.ago(f.locAt)), distanceTo(me, f.lat ?: 0.0, f.lon ?: 0.0)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAlert) {
            Icon(if (hasAlert) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationAdd, t("Avvisami quando arriva"))
        }
    }
}

private val DURATIONS = listOf(15, 60, 480, 1440)
private fun durationLabel(m: Int) = when (m) {
    15 -> "15 min"
    60 -> t("1 ora")
    480 -> t("8 ore")
    else -> t("24 ore")
}

@Composable
private fun ShareSheet(friends: List<Friend>, active: Set<String>, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val last = remember { Graph.prefs.lastShareFriends.toSet() }
    var selected by remember { mutableStateOf(active.ifEmpty { last.filter { pk -> friends.any { it.pubkey == pk } }.toSet() }) }
    var duration by remember { mutableIntStateOf(DURATIONS.indexOf(Graph.prefs.lastShareMinutes).coerceAtLeast(1)) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(t("Posizione in tempo reale"), style = MaterialTheme.typography.titleLarge)
            Text(t("Solo gli amici che scegli vedono dove sei, finché lo decidi tu."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(t("Con chi"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (friends.isEmpty()) Text(t("Aggiungi prima un amico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { f ->
                    val sel = f.pubkey in selected
                    FilterChip(
                        selected = sel, onClick = { selected = if (sel) selected - f.pubkey else selected + f.pubkey },
                        label = { Text(f.displayName) }, leadingIcon = { Text(f.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t("Per quanto tempo"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DURATIONS.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = duration == i, onClick = { duration = i },
                        shape = SegmentedButtonDefaults.itemShape(i, DURATIONS.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text(durationLabel(m)) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    (active - selected).forEach { Graph.location.stopSharing(it) }
                    if (selected.isNotEmpty()) Graph.location.startSharing(selected.toList(), DURATIONS[duration])
                    onClose()
                },
                enabled = selected.isNotEmpty() || active.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
            ) { Text(if (selected.isEmpty()) t("Interrompi condivisione") else t("Avvia condivisione")) }
        }
    }
}

@Composable
private fun OnTheWaySheet(friends: List<Friend>, mapCenter: GeoPoint?, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var friend by remember { mutableStateOf(friends.firstOrNull()) }
    var useFriend by remember { mutableStateOf(true) }
    var label by remember { mutableStateOf("") }
    val f = friend
    val friendLive = f != null && f.sharingUntil > now() && f.lat != null
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(t("Sto arrivando"), style = MaterialTheme.typography.titleLarge)
            Text(t("L'amico vede dove sei, quanto manca e quando arrivi. Si ferma da solo all'arrivo."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(t("Da chi stai andando"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { fr ->
                    FilterChip(
                        selected = fr.pubkey == f?.pubkey, onClick = { friend = fr },
                        label = { Text(fr.displayName) }, leadingIcon = { Text(fr.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(t("Destinazione"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = useFriend && friendLive, onClick = { useFriend = true }, enabled = friendLive, shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text(t("Dov'è l'amico"))
                }
                SegmentedButton(selected = !(useFriend && friendLive), onClick = { useFriend = false }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text(t("Centro della mappa"))
                }
            }
            if (!friendLive) {
                Text(t("Per usare la posizione dell'amico, deve condividerla con te."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(label, { label = it.take(40) }, label = { Text(t("Nome del posto (facoltativo)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    f?.let { target ->
                        val dest = if (useFriend && friendLive) GeoPoint(target.lat!!, target.lon!!) else mapCenter
                        if (dest != null) {
                            Graph.location.startOnTheWay(target.pubkey, dest.latitude, dest.longitude, label.ifBlank { if (useFriend && friendLive) target.displayName else t("Destinazione") })
                        }
                    }
                    onClose()
                },
                enabled = f != null && (friendLive && useFriend || mapCenter != null),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
            ) { Text(t("Parti")) }
        }
    }
}

@Composable
private fun AlertSheet(f: Friend, me: Location?, mapCenter: GeoPoint?, existing: List<ArrivalAlert>, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var where by remember { mutableIntStateOf(if (me != null) 0 else 1) }
    var radius by remember { mutableIntStateOf(1) }
    var label by remember { mutableStateOf("") }
    val radii = listOf(100, 250, 500, 1000)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(t("Avvisami quando {0} arriva", f.displayName), style = MaterialTheme.typography.titleLarge)
            Text(t("Funziona mentre condivide la posizione con te."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (existing.isNotEmpty()) {
                SectionLabel(t("Avvisi attivi"), Modifier.padding(top = 8.dp))
                existing.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔 " + a.label + " · " + Fmt.distance(a.radius.toFloat()), modifier = Modifier.weight(1f))
                        TextButton(onClick = { Graph.scope.launch { Graph.db.social().deleteAlert(a.id) } }) { Text(t("Rimuovi")) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(t("Dove"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = where == 0, onClick = { where = 0 }, enabled = me != null, shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)) { Text(t("Dove sono io")) }
                SegmentedButton(selected = where == 1, onClick = { where = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)) { Text(t("Centro della mappa")) }
            }
            Spacer(Modifier.height(12.dp))
            Text(t("Raggio"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                radii.forEachIndexed { i, r ->
                    SegmentedButton(selected = radius == i, onClick = { radius = i }, shape = SegmentedButtonDefaults.itemShape(i, radii.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Text(Fmt.distance(r.toFloat()))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(label, { label = it.take(40) }, label = { Text(t("Nome del posto (es. Casa)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val p = if (where == 0) me?.let { GeoPoint(it.latitude, it.longitude) } else mapCenter
                    if (p != null) {
                        val a = ArrivalAlert(newId(), f.pubkey, p.latitude, p.longitude, radii[radius], label.ifBlank { if (where == 0) t("da te") else t("al punto scelto") }, now())
                        Graph.scope.launch { Graph.db.social().saveAlert(a) }
                    }
                    onClose()
                },
                enabled = (where == 0 && me != null) || (where == 1 && mapCenter != null),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
            ) { Text(t("Crea avviso")) }
        }
    }
}
