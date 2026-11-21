package com.monolith.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.rtc.CallPhase
import com.monolith.app.rtc.Participant
import com.monolith.app.rtc.RtcFactory
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Grotesk
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val BG = Color(0xFF0A0A0A)
private val FG = Color(0xFFF2F2F2)

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

@Composable
fun CallScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val st by Graph.calls.state.collectAsState()
    val people by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val groups by remember { Graph.db.groups().all() }.collectAsState(initial = emptyList())
    val byPk = people.associateBy { it.pubkey }
    val group = st.group?.let { g -> groups.firstOrNull { it.id == g } }

    LaunchedEffect(st.phase) { if (st.phase == CallPhase.IDLE) nav.popBackStack() }
    val elapsed by produceState(0L, st.phase, st.since) {
        while (st.phase == CallPhase.ACTIVE) {
            value = now() - st.since
            kotlinx.coroutines.delay(500)
        }
    }
    val track by com.monolith.app.net.ListenTogether.track.collectAsState()
    var musicSheet by remember { mutableStateOf(false) }
    if (musicSheet) MusicPickerSheet(onDismiss = { musicSheet = false }, onPick = { s ->
        musicSheet = false
        com.monolith.app.net.ListenTogether.start(s.title, s.artist, s.art, s.preview)
    })
    val shareScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val d = res.data
        if (res.resultCode == android.app.Activity.RESULT_OK && d != null) Graph.calls.startScreenShare(d)
    }
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

    val title = when {
        group != null -> group.emoji + " " + group.name
        else -> byPk[st.peer ?: st.caller ?: ""]?.displayName ?: "…"
    }
    val ringing = st.phase == CallPhase.OUTGOING || st.phase == CallPhase.INCOMING || st.phase == CallPhase.CONNECTING
    val remotes = st.participants.values.toList()
    val showGrid = st.phase == CallPhase.ACTIVE && (st.video || remotes.size > 1 || remotes.any { it.video != null && it.camOn })
    CallProximity(!st.video && !st.speaker && !st.sharing && (st.phase == CallPhase.ACTIVE || st.phase == CallPhase.CONNECTING || st.phase == CallPhase.OUTGOING))
    val pip by com.monolith.app.ui.NavBus.pip.collectAsState()
    if (pip) {
        Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
            val main = remotes.firstOrNull { it.video != null && it.camOn }
            val v = main?.video
            if (v != null) VideoView(v, mirror = false, modifier = Modifier.fillMaxSize())
            else Avatar(byPk[remotes.firstOrNull()?.pk ?: ""]?.emoji ?: "🙂", 64.dp)
        }
        return
    }

    val inf = rememberInfiniteTransition(label = "rings")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring")
    val breathe by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val bounce by inf.animateFloat(0f, -10f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")

    Box(Modifier.fillMaxSize().background(BG)) {
        if (showGrid) {
            CallGrid(remotes, byPk, st.localVideo, st.camOn, st.frontCam, Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 150.dp))
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                Text((if (st.video) t("VIDEOCHIAMATA") else t("CHIAMATA VOCALE")) + if (st.group != null) " · " + t("GRUPPO") else "", color = FG.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(0.6f))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                    if (ringing) {
                        repeat(3) { i ->
                            val p = (ring + i / 3f) % 1f
                            Box(Modifier.size(150.dp).graphicsLayer { scaleX = 1f + p * 0.75f; scaleY = 1f + p * 0.75f; alpha = (1f - p) * 0.35f }.clip(CircleShape).background(FG))
                        }
                    }
                    val s = if (st.phase == CallPhase.ACTIVE) breathe else 1f
                    Avatar(group?.emoji ?: byPk[st.peer ?: st.caller ?: ""]?.emoji ?: "🙂", 150.dp, Modifier.graphicsLayer { scaleX = s; scaleY = s })
                }
                Spacer(Modifier.height(28.dp))
                Text(title, color = FG, fontFamily = Grotesk, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Spacer(Modifier.weight(1f))
            }
        }

        track?.let { tr ->
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (st.sharing) 44.dp else 8.dp)
                    .clip(CircleShape).background(FG.copy(alpha = 0.92f)).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tr.art.isNotBlank()) coil.compose.AsyncImage(tr.art, null, Modifier.size(28.dp).clip(CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("♪  " + tr.title + if (tr.artist.isNotBlank()) " · " + tr.artist else "", color = BG, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        if (st.sharing) {
            Text(
                "📱  " + t("Stai mostrando il tuo schermo"), color = BG, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).clip(CircleShape).background(FG).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                when (st.phase) {
                    CallPhase.OUTGOING -> t("Squilla…")
                    CallPhase.INCOMING -> if (st.group != null) t("{0} ti invita a una chiamata di gruppo", byPk[st.caller ?: ""]?.displayName ?: "") else t("Ti sta chiamando")
                    CallPhase.CONNECTING -> t("Connessione…")
                    CallPhase.ACTIVE -> Fmt.duration(elapsed) + if (remotes.size > 1) " · " + t("{0} persone", remotes.size + 1) else ""
                    CallPhase.ENDED -> st.reason ?: t("Chiamata terminata")
                    CallPhase.IDLE -> ""
                },
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "status",
            ) { Text(it, color = FG.copy(alpha = 0.75f), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(24.dp))
            if (st.phase == CallPhase.INCOMING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallButton(Icons.Rounded.CallEnd, t("Rifiuta"), FG.copy(alpha = 0.16f), FG) { Graph.calls.decline() }
                    CallButton(if (st.video) Icons.Rounded.Videocam else Icons.Rounded.Call, t("Rispondi"), FG, BG, Modifier.graphicsLayer { translationY = bounce * density }) { accept() }
                }
            } else if (st.phase != CallPhase.ENDED && st.phase != CallPhase.IDLE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ToggleButton(if (st.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (st.muted) t("Riattiva") else t("Muto"), st.muted) { Graph.calls.toggleMute() }
                    if (st.video && st.localVideo != null) {
                        ToggleButton(if (st.camOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, t("Video"), !st.camOn) { Graph.calls.toggleCamera() }
                    }
                    CallButton(Icons.Rounded.CallEnd, t("Chiudi"), FG, BG) { Graph.calls.hangup() }
                    if (st.video && st.localVideo != null) {
                        ToggleButton(Icons.Rounded.Cameraswitch, t("Gira"), false) { Graph.calls.switchCamera() }
                    }
                    ToggleButton(Icons.AutoMirrored.Rounded.VolumeUp, t("Vivavoce"), st.speaker) { Graph.calls.toggleSpeaker() }
                    if (st.phase == CallPhase.ACTIVE) {
                        ToggleButton(Icons.Rounded.MusicNote, t("Musica"), track != null) {
                            if (track != null) com.monolith.app.net.ListenTogether.stop() else musicSheet = true
                        }
                        ToggleButton(if (st.sharing) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare, t("Schermo"), st.sharing) {
                            if (st.sharing) Graph.calls.stopScreenShare()
                            else {
                                val mpm = ctx.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                                runCatching { shareScreen.launch(mpm.createScreenCaptureIntent()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallGrid(remotes: List<Participant>, byPk: Map<String, com.monolith.app.data.Friend>, local: VideoTrack?, camOn: Boolean, front: Boolean, modifier: Modifier) {
    @Composable
    fun Tile(p: Participant, m: Modifier) {
        Box(m.clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
            val v = p.video
            if (v != null && p.camOn) VideoView(v, mirror = false, modifier = Modifier.fillMaxSize())
            else Avatar(byPk[p.pk]?.emoji ?: "🙂", 88.dp)
            Text(
                byPk[p.pk]?.displayName ?: "", color = FG, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (!p.connected) Text(t("Connessione…"), color = FG.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopCenter).padding(10.dp))
        }
    }
    Box(modifier.padding(12.dp)) {
        when (remotes.size) {
            0 -> {}
            1 -> Tile(remotes[0], Modifier.fillMaxSize())
            2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(remotes[0], Modifier.weight(1f).fillMaxWidth())
                Tile(remotes[1], Modifier.weight(1f).fillMaxWidth())
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(remotes[0], Modifier.weight(1f).fillMaxSize())
                    Tile(remotes[1], Modifier.weight(1f).fillMaxSize())
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(remotes[2], Modifier.weight(1f).fillMaxSize())
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (local != null && camOn) {
            val scale by animateFloatAsState(1f, label = "pip")
            Box(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).width(110.dp).height(160.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xFF222222)),
            ) {
                VideoView(local, mirror = front, modifier = Modifier.fillMaxSize(), onTop = true)
            }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, container: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(container).bounceClick(0.86f, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = content, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = FG.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val tr by animateFloatAsState(if (on) 1f else 0f, label = "t")
    val container = Color(
        red = FG.red * tr + 0.16f * FG.red * (1 - tr),
        green = FG.green * tr + 0.16f * FG.green * (1 - tr),
        blue = FG.blue * tr + 0.16f * FG.blue * (1 - tr),
    )
    CallButton(icon, label, container, if (on) BG else FG, onClick = onClick)
}
