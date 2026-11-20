package com.monolith.app.ui.screens

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.t
import com.monolith.app.ui.components.MonoSheet
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private fun bestLocation(ctx: Context): Location? {
    Graph.location.me.value?.let { return it }
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.time }
}

/** SOS: hold the button to alert every friend with your position */
@Composable
fun SosSheet(onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var sent by remember { mutableStateOf<Int?>(null) }
    var sending by remember { mutableStateOf(false) }

    MonoSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(sent, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sos") { n ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (n == null) {
                        Text(t("Chiedi aiuto"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            t("Tieni premuto: tutti i tuoi amici ricevono un avviso con la tua posizione, e la vedono in tempo reale per un'ora."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(if (n > 0) t("Avviso inviato") else t("Nessun amico da avvisare"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (n > 0) t("{0} amici hanno ricevuto l'SOS e vedono dove sei per un'ora.", n) else t("Aggiungi prima un amico."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (sent == null) {
                val ring = MaterialTheme.colorScheme.onSurface
                val track = MaterialTheme.colorScheme.outlineVariant
                Box(
                    Modifier.size(168.dp).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (sending) return@detectTapGestures
                            val job = scope.launch {
                                progress.animateTo(1f, tween(1600, easing = LinearEasing))
                                sending = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val loc = bestLocation(ctx)
                                // the app-wide scope: the alert goes out even if the sheet is closed now
                                sent = Graph.scope.async { Graph.messenger.sendSos(loc?.latitude, loc?.longitude, loc?.accuracy) }.await()
                                sending = false
                            }
                            tryAwaitRelease()
                            if (progress.value < 1f) {
                                job.cancel()
                                scope.launch { progress.animateTo(0f, tween(250)) }
                            }
                        })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 8.dp.toPx()
                        val inset = w / 2
                        val sz = Size(size.width - w, size.height - w)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                        drawArc(ring, -90f, 360f * progress.value, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Box(
                        Modifier.size(128.dp).clip(CircleShape).background(MaterialTheme.colorScheme.inverseSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("SOS", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(t("Tieni premuto per inviare"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(onClick = onClose, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Chiudi")) }
            }
        }
    }
}
