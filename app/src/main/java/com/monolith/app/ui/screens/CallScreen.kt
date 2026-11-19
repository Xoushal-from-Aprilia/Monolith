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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.rtc.CallPhase
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.ui.theme.Grotesk
import kotlinx.coroutines.delay

@Composable
fun CallScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val st by Graph.calls.state.collectAsState()
    val peer = st.peer ?: ""
    val friend by remember(peer) { Graph.db.friends().observe(peer) }.collectAsState(initial = null)

    LaunchedEffect(st.phase) {
        if (st.phase == CallPhase.IDLE) nav.popBackStack()
    }
    val elapsed by produceState(0L, st.phase, st.since) {
        while (st.phase == CallPhase.ACTIVE) {
            value = (now() - st.since) / 1000
            delay(500)
        }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Graph.calls.accept() else Graph.calls.decline()
    }

    val ringing = st.phase == CallPhase.OUTGOING || st.phase == CallPhase.INCOMING || st.phase == CallPhase.CONNECTING
    val inf = rememberInfiniteTransition(label = "rings")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring")
    val breathe by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")

    val bg = Color(0xFF0A0A0A)
    val fg = Color(0xFFF2F2F2)

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text("CHIAMATA VOCALE · P2P CIFRATA", color = fg.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(0.6f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                if (ringing) {
                    repeat(3) { i ->
                        val p = (ring + i / 3f) % 1f
                        Box(
                            Modifier.size(150.dp).graphicsLayer {
                                scaleX = 1f + p * 0.75f; scaleY = 1f + p * 0.75f; alpha = (1f - p) * 0.35f
                            }.clip(CircleShape).background(fg)
                        )
                    }
                }
                val s = if (st.phase == CallPhase.ACTIVE) breathe else 1f
                Avatar(friend?.emoji ?: "🙂", 150.dp, Modifier.graphicsLayer { scaleX = s; scaleY = s })
            }
            Spacer(Modifier.height(28.dp))
            Text(friend?.displayName ?: "…", color = fg, fontFamily = Grotesk, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                when (st.phase) {
                    CallPhase.OUTGOING -> "Squilla…"
                    CallPhase.INCOMING -> "Ti sta chiamando"
                    CallPhase.CONNECTING -> "Connessione diretta…"
                    CallPhase.ACTIVE -> "%d:%02d".format(elapsed / 60, elapsed % 60)
                    CallPhase.ENDED -> st.reason ?: "Chiamata terminata"
                    CallPhase.IDLE -> ""
                },
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "status",
            ) { Text(it, color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.weight(1f))

            if (st.phase == CallPhase.INCOMING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallButton(Icons.Rounded.CallEnd, "Rifiuta", fg.copy(alpha = 0.16f), fg) { Graph.calls.decline() }
                    val bounce by inf.animateFloat(0f, -10f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
                    CallButton(Icons.Rounded.Call, "Rispondi", fg, bg, Modifier.graphicsLayer { translationY = bounce * density }) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) Graph.calls.accept()
                        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            } else if (st.phase != CallPhase.ENDED && st.phase != CallPhase.IDLE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ToggleButton(if (st.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (st.muted) "Riattiva" else "Muto", st.muted, fg, bg) { Graph.calls.toggleMute() }
                    CallButton(Icons.Rounded.CallEnd, "Chiudi", fg, bg) { Graph.calls.hangup() }
                    ToggleButton(Icons.AutoMirrored.Rounded.VolumeUp, "Vivavoce", st.speaker, fg, bg) { Graph.calls.toggleSpeaker() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, container: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(container).bounceClick(0.86f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = content, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color(0xFFF2F2F2).copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, on: Boolean, fg: Color, bg: Color, onClick: () -> Unit) {
    val t by animateFloatAsState(if (on) 1f else 0f, label = "t")
    val container = Color(
        red = fg.red * t + 0.16f * fg.red * (1 - t),
        green = fg.green * t + 0.16f * fg.green * (1 - t),
        blue = fg.blue * t + 0.16f * fg.blue * (1 - t),
    )
    CallButton(icon, label, container, if (on) bg else fg, onClick = onClick)
}
