package com.monolith.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.EmojiArt
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Place
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.EmojiPickerSheet
import com.monolith.app.ui.components.PulseDot
import com.monolith.app.ui.components.RoundAction
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.MonoDialog

private fun metres(a: Location?, lat: Double, lon: Double): Float? {
    a ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, lat, lon, out)
    return out[0]
}

private fun distanceTo(a: Location?, lat: Double, lon: Double): String? = metres(a, lat, lon)?.let { Fmt.distance(it) }

private fun etaText(sec: Int): String = if (sec < 60) t("meno di 1 min") else if (sec < 3600) "${sec / 60} min" else "${sec / 3600} h ${(sec % 3600) / 60} min"

/** a common place being created or edited; lat/lon are fixed when leaving the "position" step */
private data class PlaceDraft(
    val id: String?,
    val name: String,
    val emoji: String,
    val radius: Int,
    val members: Set<String>,
    val lat: Double? = null,
    val lon: Double? = null,
)

private val PLACE_EMOJI = listOf("🏠", "🏢", "🏫", "🏋️", "☕", "🍕", "🛒", "🌳", "⚽", "⛪", "🏥", "❤️")

@Composable
fun MapScreen(nav: NavHostController) {
    val mapDark = rememberMapDark()
    val styleIdx by Graph.prefs.mapStyle.collectAsState()
    val style = MapLook.style(styleIdx)
    val friends by remember { Graph.db.friends().friends() }.collectAsState(initial = emptyList())
    val places by Graph.places.all.collectAsState()
    val me by Graph.location.me.collectAsState()
    val sharing by Graph.location.sharing.collectAsState()
    val otw by Graph.location.onTheWay.collectAsState()
    val profile by Graph.identity.profile.collectAsState()
    val openShare by NavBus.openShareSheet.collectAsState()
    val focus by NavBus.focusPlace.collectAsState()
    val myPub = Graph.identity.keys?.pub
    var tick by remember { mutableIntStateOf(0) }
    var hasPerm by remember { mutableStateOf(Graph.location.hasPermission()) }
    var hasBg by remember { mutableStateOf(Graph.placeWatch.hasBackground()) }
    var shareSheet by remember { mutableStateOf(false) }
    var otwSheet by remember { mutableStateOf(false) }
    var styleSheet by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableIntStateOf(0) }          // 0 = people, 1 = common places
    var draft by remember { mutableStateOf<PlaceDraft?>(null) }
    var draftDetails by remember { mutableStateOf(false) }
    var openPlace by remember { mutableStateOf<String?>(null) }
    var askBackground by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }
    // bumped on every pan/zoom; only the draft circle reads it, so the map itself doesn't recompose
    val moveTick = remember { mutableIntStateOf(0) }
    val icons = remember { HashMap<String, Bitmap>() }

    LaunchedEffect(openShare) {
        if (openShare) { panel = 0; shareSheet = true; NavBus.openShareSheet.value = false }
    }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val tNow = now() + tick * 0
    val sharingWithMe = friends.filter { it.sharingUntil > tNow && it.lat != null && it.lon != null }
    val iShareWith = friends.filter { it.iShareUntil > tNow }
    val names = remember(friends, myPub) { friends.associate { it.pubkey to it.displayName } + (myPub.orEmpty() to t("tu")) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = Graph.location.hasPermission()
        Graph.placeWatch.refresh()
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasBg = Graph.placeWatch.hasBackground()
        Graph.placeWatch.refresh()
    }
    fun requestForeground() = permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    fun requestBackground() {
        if (!Graph.location.hasPermission()) requestForeground()
        else if (Build.VERSION.SDK_INT >= 29) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    DisposableEffect(hasPerm) {
        Graph.location.watch(true)
        onDispose { Graph.location.watch(false) }
    }
    LaunchedEffect(sharingWithMe.map { it.pubkey }) { sharingWithMe.forEach { Graph.rtc.ensureConnected(it.pubkey) } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    // the user may come back from the system settings
                    hasPerm = Graph.location.hasPermission()
                    hasBg = Graph.placeWatch.hasBackground()
                    Graph.placeWatch.refresh()
                }
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // a chat bubble asked to show a place
    LaunchedEffect(focus, mapView) {
        val id = focus ?: return@LaunchedEffect
        val mv = mapView ?: return@LaunchedEffect
        NavBus.focusPlace.value = null
        val p = Graph.places.get(id) ?: return@LaunchedEffect
        panel = 1
        centeredOnce = true
        mv.controller.animateTo(GeoPoint(p.lat, p.lon), 17.5, 700L)
        openPlace = p.id
    }

    fun center(): GeoPoint? = mapView?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) }

    fun icon(emoji: String, size: Int, bg: Int?, ring: Int?): Bitmap =
        icons.getOrPut("$emoji|$size|$bg|$ring") { EmojiArt.bitmap(emoji, size, background = bg, ring = ring) }

    fun startDraft(d: PlaceDraft, lat: Double?, lon: Double?) {
        draft = d
        draftDetails = false
        openPlace = null
        panel = 1
        val mv = mapView ?: return
        val loc = me
        val target = when {
            lat != null && lon != null -> GeoPoint(lat, lon)
            loc != null -> GeoPoint(loc.latitude, loc.longitude)
            else -> null
        }
        if (target != null) mv.controller.animateTo(target, 18.0, 700L)
        else if (mv.zoomLevelDouble < 16.0) mv.controller.zoomTo(17.0, 600L)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                MapView(c).apply {
                    setTileSource(style.source)
                    overlayManager.tilesOverlay.setLoadingBackgroundColor(MapLook.LOADING)
                    overlayManager.tilesOverlay.setLoadingLineColor(MapLook.LOADING)
                    overlayManager.tilesOverlay.setColorFilter(MapLook.filter(mapDark))
                    setBackgroundColor(MapLook.backdrop(mapDark))
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    controller.setZoom(5.5)
                    controller.setCenter(GeoPoint(42.5, 12.5))
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean { moveTick.intValue++; return false }
                        override fun onZoom(event: ZoomEvent?): Boolean { moveTick.intValue++; return false }
                    })
                    mapView = this
                }
            },
            update = { mv ->
                if (mv.tileProvider.tileSource.name() != style.source.name()) {
                    mv.setTileSource(style.source)
                    if (mv.zoomLevelDouble > mv.maxZoomLevel) mv.controller.setZoom(mv.maxZoomLevel)
                }
                mv.overlayManager.tilesOverlay.setColorFilter(MapLook.filter(mapDark))
                mv.setBackgroundColor(MapLook.backdrop(mapDark))
                val dark = mapDark
                val editing = draft != null
                mv.overlays.clear()
                val res = mv.context.resources
                val ink = if (dark) 0xFFF2F2F2.toInt() else 0xFF0A0A0A.toInt()
                val paper = if (dark) 0xFF1D1D1D.toInt() else 0xFFFFFFFF.toInt()

                // common places: soft circle + marker like the people's ones
                places.filter { it.id != draft?.id }.forEach { p ->
                    val someoneIn = p.insideNow().isNotEmpty()
                    val circle = Polygon(mv)
                    circle.points = Polygon.pointsAsCircle(GeoPoint(p.lat, p.lon), p.radius.toDouble())
                    circle.fillPaint.color = (ink and 0x00FFFFFF) or (if (someoneIn) 0x30000000 else 0x18000000)
                    circle.outlinePaint.color = (ink and 0x00FFFFFF) or 0x80000000.toInt()
                    circle.outlinePaint.strokeWidth = 3f
                    circle.setOnClickListener { _, _, _ -> if (!editing) openPlace = p.id; true }
                    mv.overlays.add(circle)
                    val m = Marker(mv)
                    m.position = GeoPoint(p.lat, p.lon)
                    m.icon = BitmapDrawable(res, icon(p.emoji, 104, paper, ink))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.setOnMarkerClickListener { _, _ -> if (!editing) openPlace = p.id; true }
                    mv.overlays.add(m)
                }
                otw?.let { o ->
                    val m = Marker(mv)
                    m.position = GeoPoint(o.lat, o.lon)
                    m.icon = BitmapDrawable(res, icon("🏁", 110, paper, ink))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = o.label
                    mv.overlays.add(m)
                }
                sharingWithMe.forEach { f ->
                    val m = Marker(mv)
                    m.position = GeoPoint(f.lat!!, f.lon!!)
                    m.icon = BitmapDrawable(res, icon(f.emoji, 132, paper, ink))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = f.displayName
                    m.snippet = t("Aggiornato {0}", Fmt.ago(f.locAt))
                    mv.overlays.add(m)
                }
                me?.let { loc ->
                    val m = Marker(mv)
                    m.position = GeoPoint(loc.latitude, loc.longitude)
                    m.icon = BitmapDrawable(res, icon(profile?.emoji ?: "🗿", 120, ink, null))
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

        val d = draft
        if (d != null) {
            DraftCircle(mapView, d.radius, moveTick, mapDark)
        } else {
            // centre crosshair (used to pick a destination)
            Icon(Icons.Rounded.Add, null, Modifier.align(Alignment.Center).size(22.dp).alpha(0.35f))
        }
        // credit to the map services, always visible
        MapAttribution(style, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 70.dp, end = 16.dp))

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
            RoundAction(Icons.Rounded.Layers, t("Stile della mappa"), size = 48.dp) { styleSheet = true }
            Spacer(Modifier.width(10.dp))
            RoundAction(Icons.Rounded.MyLocation, t("La mia posizione"), size = 48.dp) {
                if (!hasPerm) requestForeground()
                else me?.let { mapView?.controller?.animateTo(GeoPoint(it.latitude, it.longitude), if (draft != null) 18.0 else 16.0, 700L) }
            }
        }

        // ---------------------------------------------------------------- bottom: placing a common place
        AnimatedVisibility(
            d != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val cur = draft ?: return@AnimatedVisibility
            PositionPanel(
                cur,
                onRadius = { draft = cur.copy(radius = it) },
                onCancel = { draft = null },
                onNext = {
                    val c = center()
                    if (c != null) {
                        draft = cur.copy(lat = c.latitude, lon = c.longitude)
                        draftDetails = true
                    }
                },
            )
        }
        AnimatedVisibility(
            d != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
                Text(
                    t("Sposta la mappa: il luogo è al centro"),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // ---------------------------------------------------------------- bottom: people / common places
        AnimatedVisibility(
            d == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp).shadow(12.dp, MaterialTheme.shapes.extraLarge),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(vertical = 14.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        listOf(t("Persone"), t("Luoghi comuni")).forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = panel == i, onClick = { panel = i },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                                icon = {},
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (!hasPerm) {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, null)
                            Spacer(Modifier.width(12.dp))
                            Text(t("Attiva la posizione per vederti sulla mappa e condividerla."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { requestForeground() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = CircleShape,
                        ) { Text(t("Consenti posizione")) }
                        Spacer(Modifier.height(10.dp))
                    }
                    AnimatedContent(
                        panel,
                        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                        label = "mapPanel",
                    ) { tab ->
                        if (tab == 0) {
                            PeoplePanel(
                                hasPerm, friends, sharingWithMe, iShareWith, me, otw,
                                onFriend = { f -> mapView?.controller?.animateTo(GeoPoint(f.lat!!, f.lon!!), 16.5, 700L) },
                                onShare = { shareSheet = true },
                                onOtw = { otwSheet = true },
                            )
                        } else {
                            PlacesPanel(
                                places, names, me, myPub, hasBg && hasPerm,
                                onPlace = { p ->
                                    mapView?.controller?.animateTo(GeoPoint(p.lat, p.lon), 17.5, 700L)
                                    openPlace = p.id
                                },
                                onAllowAlways = { requestBackground() },
                                onAdd = {
                                    if (!hasPerm) requestForeground()
                                    startDraft(PlaceDraft(null, "", "🏠", 30, emptySet()), null, null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (styleSheet) MapStyleSheet { styleSheet = false }
    if (shareSheet) ShareSheet(friends.filter { it.status == FriendStatus.FRIEND }, iShareWith.map { it.pubkey }.toSet()) { shareSheet = false }
    if (otwSheet) OnTheWaySheet(friends.filter { it.status == FriendStatus.FRIEND }, center(), onClose = { otwSheet = false })

    val dd = draft
    if (dd != null && draftDetails) {
        PlaceDetailsSheet(
            dd, friends.filter { it.status == FriendStatus.FRIEND },
            onChange = { draft = it },
            onBack = { draftDetails = false },
            onSave = { fin ->
                draftDetails = false
                draft = null
                Graph.scope.launch {
                    Graph.messenger.savePlace(fin.id, fin.name, fin.emoji, fin.lat ?: return@launch, fin.lon ?: return@launch, fin.radius, fin.members.toList())
                }
                if (!Graph.placeWatch.hasBackground() || !Graph.location.hasPermission()) askBackground = true
            },
        )
    }

    LaunchedEffect(places, openPlace) {
        val id = openPlace ?: return@LaunchedEffect
        if (places.none { it.id == id }) openPlace = null
    }
    places.firstOrNull { it.id == openPlace }?.let { p ->
        PlaceSheet(
            p, names, me, myPub,
            onClose = { openPlace = null },
            onEdit = {
                openPlace = null
                startDraft(PlaceDraft(p.id, p.name, p.emoji, p.radius, p.members.filter { it != myPub }.toSet()), p.lat, p.lon)
            },
            onDelete = {
                openPlace = null
                Graph.scope.launch { Graph.messenger.deletePlace(p.id) }
            },
        )
    }

    if (askBackground) {
        MonoDialog(
            onDismissRequest = { askBackground = false },
            icon = { Icon(Icons.Rounded.AddLocationAlt, null) },
            title = { Text(t("Avvisi anche ad app chiusa")) },
            text = {
                Text(
                    t("Per accorgersi di quando arrivi anche con Monolith chiuso, Android chiede di consentire la posizione «Sempre». Nella prossima schermata scegli «Consenti sempre».")
                )
            },
            confirmButton = { TextButton(onClick = { askBackground = false; requestBackground() }) { Text(t("Consenti")) } },
            dismissButton = { TextButton(onClick = { askBackground = false }) { Text(t("Non ora")) } },
        )
    }
}

/** the radius preview drawn at the centre of the map while placing */
@Composable
private fun DraftCircle(map: MapView?, radius: Int, moveTick: IntState, dark: Boolean) {
    @Suppress("UNUSED_VARIABLE") val tickRead = moveTick.intValue
    val px = map?.projection?.metersToPixels(radius.toFloat()) ?: 0f
    val ink = if (dark) Color.White else Color.Black
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        drawCircle(ink.copy(alpha = 0.12f), px, c)
        drawCircle(ink.copy(alpha = 0.75f), px, c, style = Stroke(2.dp.toPx()))
        drawCircle(ink, 5.dp.toPx(), c)
    }
}

@Composable
private fun PositionPanel(d: PlaceDraft, onRadius: (Int) -> Unit, onCancel: () -> Unit, onNext: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp).shadow(12.dp, MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(if (d.id == null) t("Nuovo luogo comune") else t("Modifica luogo"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Raggio"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                AnimatedContent(d.radius, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "radius") { r ->
                    Text("$r m", style = MaterialTheme.typography.titleMedium)
                }
            }
            Slider(
                value = d.radius.toFloat(),
                onValueChange = { onRadius((Math.round(it / 5f) * 5).coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS)) },
                valueRange = Place.MIN_RADIUS.toFloat()..Place.MAX_RADIUS.toFloat(),
                steps = (Place.MAX_RADIUS - Place.MIN_RADIUS) / 5 - 1,
            )
            Text(
                t("L'avviso scatta quando qualcuno entra nel cerchio."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text(t("Annulla")) }
                Button(onClick = onNext, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text(t("Avanti")) }
            }
        }
    }
}

@Composable
private fun PeoplePanel(
    hasPerm: Boolean,
    friends: List<Friend>,
    sharingWithMe: List<Friend>,
    iShareWith: List<Friend>,
    me: Location?,
    otw: com.monolith.app.location.OnTheWay?,
    onFriend: (Friend) -> Unit,
    onShare: () -> Unit,
    onOtw: () -> Unit,
) {
    Column {
        AnimatedVisibility(otw != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val f = friends.firstOrNull { it.pubkey == otw?.friend }
            Row(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsWalk, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Stai arrivando da {0}", f?.displayName ?: ""), style = MaterialTheme.typography.titleSmall)
                    Text(
                        (otw?.let { distanceTo(me, it.lat, it.lon) } ?: "…") + " · " + t("si ferma da solo all'arrivo"),
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
                    FriendLocationRow(f, me, Modifier.animateItem()) { onFriend(f) }
                }
            }
        } else if (iShareWith.isEmpty() && otw == null) {
            Text(
                t("Nessuno sta condividendo la posizione con te in questo momento."), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (hasPerm) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onShare, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.NearMe, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (iShareWith.isEmpty()) t("Condividi") else t("Gestisci"))
                }
                OutlinedButton(onClick = onOtw, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.DirectionsWalk, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(t("Sto arrivando"))
                }
            }
        }
    }
}

@Composable
private fun FriendLocationRow(f: Friend, me: Location?, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** "Marco, tu" for the people currently inside a place */
private fun insideNames(p: Place, names: Map<String, String>): String =
    p.insideNow().mapNotNull { names[it] }.joinToString(", ")

private fun membersNames(p: Place, names: Map<String, String>, myPub: String?): String =
    p.members.filter { it != myPub }.map { names[it] ?: t("Qualcuno") }.joinToString(", ")

@Composable
private fun PlacesPanel(
    places: List<Place>,
    names: Map<String, String>,
    me: Location?,
    myPub: String?,
    canWatchAlways: Boolean,
    onPlace: (Place) -> Unit,
    onAllowAlways: () -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        if (places.isEmpty()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Place, null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Nessun luogo comune"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        t("Segna casa, l'ufficio o la palestra e scegli con chi condividerli. Quando uno di voi arriva, l'altro riceve un messaggio in chat."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            if (!canWatchAlways) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Per gli avvisi ad app chiusa serve la posizione «Sempre»."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onAllowAlways) { Text(t("Consenti")) }
                }
                Spacer(Modifier.height(6.dp))
            }
            LazyColumn(Modifier.heightIn(max = 230.dp)) {
                items(places.sortedBy { it.name.lowercase() }, key = { it.id }) { p ->
                    PlaceRow(p, names, me, myPub, Modifier.animateItem()) { onPlace(p) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp), shape = CircleShape) {
            Icon(Icons.Rounded.AddLocationAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text(t("Aggiungi luogo comune"))
        }
    }
}

@Composable
private fun PlaceRow(p: Place, names: Map<String, String>, me: Location?, myPub: String?, modifier: Modifier, onClick: () -> Unit) {
    val inside = insideNames(p, names)
    Row(modifier.fillMaxWidth().bounceClick(0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.emoji, 42.dp, online = inside.isNotEmpty())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (inside.isNotEmpty()) t("Qui ora: {0}", inside) else t("{0} · con {1}", "${p.radius} m", membersNames(p, names, myPub)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val m = metres(me, p.lat, p.lon)
        if (m != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (m <= p.radius) t("Sei qui") else Fmt.distance(m),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceDetailsSheet(d: PlaceDraft, friends: List<Friend>, onChange: (PlaceDraft) -> Unit, onBack: () -> Unit, onSave: (PlaceDraft) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picker by remember { mutableStateOf(false) }
    MonoSheet(onDismissRequest = onBack, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).imePadding().verticalScroll(rememberScrollState())) {
            Text(if (d.id == null) t("Nuovo luogo comune") else t("Modifica luogo"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("Quando qualcuno arriva qui, gli altri ricevono un messaggio in chat e una notifica."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).bounceClick(0.9f) { picker = true },
                    contentAlignment = Alignment.Center,
                ) { Text(d.emoji, fontSize = 28.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    d.name, { onChange(d.copy(name = it.take(40))) },
                    label = { Text(t("Nome del posto (es. Casa)")) }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PLACE_EMOJI.forEach { e ->
                    val sel = e == d.emoji
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bounceClick(0.85f) { onChange(d.copy(emoji = e)) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t("Con chi lo condividi"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (friends.isEmpty()) Text(t("Aggiungi prima un amico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { f ->
                    val sel = f.pubkey in d.members
                    FilterChip(
                        selected = sel, onClick = { onChange(d.copy(members = if (sel) d.members - f.pubkey else d.members + f.pubkey)) },
                        label = { Text(f.displayName) }, leadingIcon = { Text(f.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(t("Raggio {0}", "${d.radius} m"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text(t("Indietro")) }
                Button(
                    onClick = { onSave(d) },
                    enabled = d.name.isNotBlank() && d.members.isNotEmpty() && d.lat != null,
                    modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape,
                ) { Text(t("Salva")) }
            }
        }
    }
    if (picker) EmojiPickerSheet(onDismiss = { picker = false }) { onChange(d.copy(emoji = it)); picker = false }
}

@Composable
private fun PlaceSheet(p: Place, names: Map<String, String>, me: Location?, myPub: String?, onClose: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirm by remember { mutableStateOf(false) }
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.emoji, 56.dp, online = p.insideNow().isNotEmpty())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                    val m = metres(me, p.lat, p.lon)
                    Text(
                        listOfNotNull(
                            t("Raggio {0}", "${p.radius} m"),
                            m?.let { if (it <= p.radius) t("Sei qui") else t("a {0} da te", Fmt.distance(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(t("Chi c'è adesso"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val ordered = p.members.sortedBy { if (it == myPub) 0 else 1 }
            ordered.forEach { pk ->
                val pres = p.here[pk]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (pres?.inside == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(names[pk] ?: t("Qualcuno"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            pres == null -> "—"
                            pres.inside -> t("qui dalle {0}", Fmt.time(pres.at))
                            else -> t("fuori · {0}", Fmt.ago(pres.at))
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t("Quando qualcuno arriva qui, gli altri ricevono un messaggio in chat e una notifica."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text(t("Elimina")) }
                Button(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text(t("Modifica")) }
            }
        }
    }
    if (confirm) {
        MonoDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t("Eliminare il luogo?")) },
            text = { Text(t("Verrà tolto anche a {0}.", membersNames(p, names, myPub))) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(t("Elimina")) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Annulla")) } },
        )
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
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
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
    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
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
