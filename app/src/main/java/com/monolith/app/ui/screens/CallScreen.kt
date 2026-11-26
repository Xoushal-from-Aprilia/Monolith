package com.monolith.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.rtc.AudioRoute
import com.monolith.app.rtc.CallPhase
import com.monolith.app.rtc.CallUi
import com.monolith.app.rtc.Participant
import com.monolith.app.rtc.RouteKind
import com.monolith.app.rtc.RtcFactory
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.MonoSheet
import com.monolith.app.ui.components.SectionLabel
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Grotesk
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val BG = Color(0xFF0A0A0A)
private val FG = Color(0xFFF2F2F2)
private val TILE = Color(0xFF1A1A1A)
private val HANG = Color(0xFFE5484D)

/** renders a video track; removes itself from the track when it leaves the screen */
@Composable
private fun VideoView(track: VideoTrack, mirror: Boolean, modifier: Modifier = Modifier, onTop: Boolean = false) {
    val renderer = remember(track) { mutableListOf<SurfaceViewRenderer>() }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            SurfaceViewRenderer(c).apply {
                init(RtcFactory.egl.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                if (onTop) setZOrderMediaOverlay(true)
                runCatching { track.addSink(this) }
                renderer.add(this)
            }
        },
        update = { it.setMirror(mirror) },
    )
    DisposableEffect(track) {
        onDispose {
            renderer.forEach { r ->
                runCatching { track.removeSink(r) }
                runCatching { r.release() }
            }
            renderer.clear()
        }
    }
}

fun routeIcon(kind: RouteKind?): ImageVector = when (kind) {
    RouteKind.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
    RouteKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    RouteKind.WIRED -> Icons.Rounded.Headphones
    RouteKind.USB -> Icons.Rounded.Usb
    RouteKind.HEARING -> Icons.Rounded.Hearing
    else -> Icons.Rounded.PhoneInTalk
}

@Composable
fun CallScreen(nav: NavHostController) {
    val st by Graph.calls.state.collectAsState()
    LaunchedEffect(st.phase) { if (st.phase == CallPhase.IDLE) nav.popBackStack() }
    CallContent(st)
}

/** the call, video call or voice room on screen; split from [CallScreen] so it can be drawn in the screenshots */
@Composable
internal fun CallContent(st: CallUi) {
    val ctx = LocalContext.current
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val groups by remember { Graph.db.groups().all() }.collectAsState(initial = emptyList())
    val shards by Graph.shards.all.collectAsState()
    val output by Graph.calls.routes.output.collectAsState()
    val outputs by Graph.calls.routes.outputs.collectAsState()
    val mics by Graph.calls.routes.mics.collectAsState()
    val byPk = people.associateBy { it.pubkey }
    val group = st.group?.let { g -> groups.firstOrNull { it.id == g } }
    val isRoom = st.cid?.startsWith("room.") == true
    val room = if (isRoom) Graph.shards.currentRoom()?.let { (sid, rid) -> shards.firstOrNull { it.id == sid }?.let { s -> s to s.rooms.firstOrNull { it.id == rid } } } else null

    val elapsed by produceState(0L, st.phase, st.since) {
        while (st.phase == CallPhase.ACTIVE) {
            value = now() - st.since
            delay(500)
        }
    }
    val track by com.monolith.app.net.ListenTogether.track.collectAsState()
    var musicSheet by remember { mutableStateOf(false) }
    var audioSheet by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Graph.calls.routes.notice.collect { notice = it; delay(2800); notice = null } }

    if (musicSheet) MusicPickerSheet(onDismiss = { musicSheet = false }, onPick = { s ->
        musicSheet = false
        com.monolith.app.net.ListenTogether.start(s.title, s.artist, s.art, s.preview)
    })
    val shareScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val d = res.data
        if (res.resultCode == android.app.Activity.RESULT_OK && d != null) Graph.calls.startScreenShare(d)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) Graph.calls.setCamera(true) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) Graph.calls.accept()
        else Graph.calls.decline()
    }
    fun accept() {
        val need = buildList {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (st.video && ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
        }
        if (need.isEmpty()) Graph.calls.accept() else perms.launch(need.toTypedArray())
    }
    fun camera() {
        if (st.camOn) Graph.calls.setCamera(false)
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) Graph.calls.setCamera(true)
        else camPerm.launch(Manifest.permission.CAMERA)
    }
    fun audioTap() {
        // with only the phone and the loudspeaker a tap switches; with headphones around it asks
        if (outputs.any { it.kind.isHeadset } || mics.size > 1) audioSheet = true else Graph.calls.toggleSpeaker()
    }

    val title = when {
        room != null -> room.second?.name ?: room.first.name
        group != null -> group.emoji + " " + group.name
        else -> byPk[st.peer ?: st.caller ?: ""]?.displayName ?: "…"
    }
    val kicker = when {
        room != null -> t("STANZA VOCALE") + " · " + room.first.name.uppercase()
        st.video -> t("VIDEOCHIAMATA")
        else -> t("CHIAMATA VOCALE")
    } + if (st.group != null && room == null) " · " + t("GRUPPO") else ""
    val ringing = st.phase == CallPhase.OUTGOING || st.phase == CallPhase.INCOMING || st.phase == CallPhase.CONNECTING
    val remotes = st.participants.values.toList()
    val remoteVideo = remotes.any { it.video != null && it.camOn }
    val showGrid = st.phase == CallPhase.ACTIVE && (remoteVideo || remotes.size > 1 || (st.camOn && remotes.isNotEmpty()))
    val videoMode = showGrid && (remoteVideo || st.camOn)
    val fullBleed = showGrid && remotes.size == 1 && remoteVideo
    LaunchedEffect(videoMode) { if (!videoMode) chrome = true }
    CallProximity(!st.camOn && !remoteVideo && output?.kind == RouteKind.EARPIECE && !st.sharing && (st.phase == CallPhase.ACTIVE || st.phase == CallPhase.CONNECTING || st.phase == CallPhase.OUTGOING))

    val pip by com.monolith.app.ui.NavBus.pip.collectAsState()
    if (pip) {
        Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
            val v = remotes.firstOrNull { it.video != null && it.camOn }?.video
            if (v != null) VideoView(v, mirror = false, modifier = Modifier.fillMaxSize())
            else Avatar(byPk[remotes.firstOrNull()?.pk ?: ""]?.emoji ?: "🙂", 64.dp)
        }
        return
    }

    val inf = rememberInfiniteTransition(label = "rings")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring")
    val breathe by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val bounce by inf.animateFloat(0f, -10f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")

    Box(Modifier.fillMaxSize().background(BG).pointerInput(videoMode) { detectTapGestures { if (videoMode) chrome = !chrome } }) {
        // ---------------------------------------------------------------- picture
        when {
            fullBleed -> remotes[0].video?.let { VideoView(it, mirror = false, modifier = Modifier.fillMaxSize()) }
            showGrid -> CallGrid(remotes, byPk, Modifier.fillMaxSize().statusBarsPadding().padding(top = 72.dp, bottom = 128.dp))
            else -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(0.8f))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                    if (ringing) {
                        repeat(3) { i ->
                            val p = (ring + i / 3f) % 1f
                            Box(Modifier.size(150.dp).graphicsLayer { scaleX = 1f + p * 0.75f; scaleY = 1f + p * 0.75f; alpha = (1f - p) * 0.35f }.clip(CircleShape).background(FG))
                        }
                    }
                    val s = if (st.phase == CallPhase.ACTIVE) breathe else 1f
                    val emoji = room?.first?.emoji ?: group?.emoji ?: byPk[st.peer ?: st.caller ?: ""]?.emoji ?: "🙂"
                    Avatar(emoji, 150.dp, Modifier.graphicsLayer { scaleX = s; scaleY = s })
                }
                Spacer(Modifier.height(24.dp))
                if (room != null && remotes.isEmpty() && st.phase == CallPhase.ACTIVE) {
                    Text(t("Sei solo nella stanza: gli altri ti sentono appena entrano"), color = FG.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                } else if (remotes.size == 1 && st.phase == CallPhase.ACTIVE && room != null) {
                    Text(byPk[remotes[0].pk]?.displayName ?: "", color = FG.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1.2f))
            }
        }
        if (fullBleed) {
            Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        }

        // ---------------------------------------------------------------- my camera, with the flip button
        val local = st.localVideo
        if (local != null && st.camOn && showGrid) {
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 76.dp, end = 14.dp).width(104.dp).height(150.dp)
                    .clip(RoundedCornerShape(18.dp)).background(TILE),
            ) {
                VideoView(local, mirror = st.frontCam, modifier = Modifier.fillMaxSize(), onTop = true)
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                        .bounceClick(0.85f) { Graph.calls.switchCamera() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Cameraswitch, t("Gira"), tint = FG, modifier = Modifier.size(18.dp)) }
            }
        }

        // ---------------------------------------------------------------- top: who, how long, where the audio goes
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(kicker, color = FG.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(title, color = FG, fontFamily = Grotesk, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AnimatedContent(
                            when (st.phase) {
                                CallPhase.OUTGOING -> t("Squilla…")
                                CallPhase.INCOMING -> if (st.group != null) t("{0} ti invita a una chiamata di gruppo", byPk[st.caller ?: ""]?.displayName ?: "") else t("Ti sta chiamando")
                                CallPhase.CONNECTING -> t("Connessione…")
                                CallPhase.ACTIVE -> Fmt.duration(elapsed) + if (remotes.size > 1 || room != null) " · " + t("{0} persone", remotes.size + 1) else ""
                                CallPhase.ENDED -> st.reason ?: t("Chiamata terminata")
                                CallPhase.IDLE -> ""
                            },
                            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                            label = "status",
                        ) { Text(it, color = FG.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                    }
                    if (st.phase != CallPhase.INCOMING && st.phase != CallPhase.ENDED && output != null) {
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier.clip(CircleShape).background(FG.copy(alpha = 0.12f)).bounceClick(0.92f) { audioSheet = true }
                                .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(routeIcon(output?.kind), null, tint = FG, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(output?.name ?: "", color = FG, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (st.sharing) StatusPill(t("Stai mostrando il tuo schermo"))
                    track?.let { tr -> StatusPill("♪  " + tr.title + if (tr.artist.isNotBlank()) " · " + tr.artist else "") }
                    AnimatedVisibility(notice != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) { StatusPill(notice ?: "") }
                }
            }
        }

        // ---------------------------------------------------------------- bottom: the controls
        AnimatedVisibility(
            chrome || st.phase == CallPhase.INCOMING, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 18.dp)) {
                if (st.phase == CallPhase.INCOMING) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        BigButton(Icons.Rounded.CallEnd, t("Rifiuta"), HANG, FG) { Graph.calls.decline() }
                        BigButton(if (st.video) Icons.Rounded.Videocam else Icons.Rounded.Call, t("Rispondi"), FG, BG, Modifier.graphicsLayer { translationY = bounce * density }) { accept() }
                    }
                } else if (st.phase != CallPhase.ENDED && st.phase != CallPhase.IDLE) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color(0xFF1C1C1C).copy(alpha = 0.94f)).padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        DockButton(if (st.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (st.muted) t("Muto") else t("Microfono"), st.muted, Modifier.weight(1f)) { Graph.calls.toggleMute() }
                        DockButton(if (st.camOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, t("Video"), st.camOn, Modifier.weight(1f)) { camera() }
                        DockButton(routeIcon(output?.kind), output?.name ?: t("Audio"), output?.kind == RouteKind.SPEAKER, Modifier.weight(1f)) { audioTap() }
                        DockButton(Icons.Rounded.MoreHoriz, t("Altro"), st.sharing || track != null, Modifier.weight(1f)) { moreSheet = true }
                        DockButton(Icons.Rounded.CallEnd, if (room != null) t("Esci") else t("Chiudi"), false, Modifier.weight(1f), danger = true) { Graph.calls.hangup() }
                    }
                }
            }
        }
    }

    if (audioSheet) AudioSheet(outputs, output, mics) { audioSheet = false }
    if (moreSheet) {
        MonoSheet(onDismissRequest = { moreSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(t("Altro"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                if (st.camOn) SheetRow(Icons.Rounded.Cameraswitch, t("Gira la fotocamera"), t("Passa da quella davanti a quella dietro")) { moreSheet = false; Graph.calls.switchCamera() }
                if (st.phase == CallPhase.ACTIVE) {
                    SheetRow(
                        if (st.sharing) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                        if (st.sharing) t("Smetti di mostrare lo schermo") else t("Mostra lo schermo"),
                        if (st.sharing) t("Gli altri non vedono più il tuo schermo") else t("Gli altri vedono quello che fai sul telefono"),
                    ) {
                        moreSheet = false
                        if (st.sharing) Graph.calls.stopScreenShare()
                        else {
                            val mpm = ctx.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                            runCatching { shareScreen.launch(mpm.createScreenCaptureIntent()) }
                        }
                    }
                    SheetRow(
                        if (track != null) Icons.Rounded.MusicOff else Icons.Rounded.MusicNote,
                        if (track != null) t("Ferma la musica") else t("Ascoltate insieme"),
                        if (track != null) track?.title ?: "" else t("Una canzone che parte nello stesso momento per tutti"),
                    ) {
                        moreSheet = false
                        if (track != null) com.monolith.app.net.ListenTogether.stop() else musicSheet = true
                    }
                } else {
                    Text(t("Le altre funzioni arrivano quando la chiamata è iniziata"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }
        }
    }
}

/** where to hear the call and which microphone to use */
@Composable
internal fun AudioSheet(outputs: List<AudioRoute>, output: AudioRoute?, mics: List<AudioRoute>, onDismiss: () -> Unit) {
    val mic by Graph.calls.routes.mic.collectAsState()
    MonoSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(t("Audio"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            Text(t("Collega le cuffie quando vuoi: Monolith le trova e ci passa da solo."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            SectionLabel(t("Ascolta da"), Modifier.padding(top = 14.dp))
            outputs.forEach { r -> ChoiceRow(routeIcon(r.kind), r.name, r.key == output?.key) { Graph.calls.routes.select(r); onDismiss() } }
            if (mics.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SectionLabel(t("Microfono"))
                ChoiceRow(Icons.Rounded.Mic, t("Automatico"), mic == null, t("Quello che va con l'audio scelto")) { Graph.calls.routes.selectMic(null); onDismiss() }
                mics.forEach { r -> ChoiceRow(if (r.kind == RouteKind.BLUETOOTH) Icons.Rounded.Bluetooth else if (r.kind.isHeadset) Icons.Rounded.Headphones else Icons.Rounded.Mic, r.name, mic?.key == r.key) { Graph.calls.routes.selectMic(r); onDismiss() } }
            }
        }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).bounceClick(0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text, color = BG, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 320.dp).clip(CircleShape).background(FG.copy(alpha = 0.94f)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun CallGrid(remotes: List<Participant>, byPk: Map<String, com.monolith.app.data.Friend>, modifier: Modifier) {
    @Composable
    fun Tile(p: Participant, m: Modifier) {
        Box(m.clip(RoundedCornerShape(24.dp)).background(TILE), contentAlignment = Alignment.Center) {
            val v = p.video
            if (v != null && p.camOn) VideoView(v, mirror = false, modifier = Modifier.fillMaxSize())
            else Avatar(byPk[p.pk]?.emoji ?: "🙂", 80.dp)
            Text(
                byPk[p.pk]?.displayName ?: "…", color = FG, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (!p.connected) Text(t("Connessione…"), color = FG.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopCenter).padding(10.dp))
        }
    }
    Box(modifier.padding(horizontal = 12.dp)) {
        when (remotes.size) {
            0 -> {}
            1 -> Tile(remotes[0], Modifier.fillMaxSize())
            2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(remotes[0], Modifier.weight(1f).fillMaxWidth())
                Tile(remotes[1], Modifier.weight(1f).fillMaxWidth())
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(remotes[0], Modifier.weight(1f).fillMaxHeight())
                    Tile(remotes[1], Modifier.weight(1f).fillMaxHeight())
                }
                Tile(remotes[2], Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** one control of the bottom bar: all the same size, the label under it */
@Composable
private fun DockButton(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(
                when {
                    danger -> HANG
                    on -> FG
                    else -> FG.copy(alpha = 0.12f)
                },
            ).bounceClick(0.86f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on && !danger) BG else FG, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = FG.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun BigButton(icon: ImageVector, label: String, container: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp).clip(CircleShape).background(container).bounceClick(0.86f, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = content, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = FG.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
    }
}
