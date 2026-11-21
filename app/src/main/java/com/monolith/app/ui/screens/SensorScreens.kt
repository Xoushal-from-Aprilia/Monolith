package com.monolith.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.net.FiveMatch
import com.monolith.app.net.HighFive
import com.monolith.app.net.VoicePlayer
import com.monolith.app.ui.components.Avatar
import com.monolith.app.ui.components.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private fun vibrate(ctx: Context, ms: Long, amp: Int = 120) {
    val v = ctx.getSystemService(Vibrator::class.java) ?: return
    runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amp)) }
}

// ====================================================================== compass to a friend

/** a compass that points at a friend who is sharing their position */
@Composable
fun CompassScreen(nav: NavHostController, pk: String) {
    val ctx = LocalContext.current
    val friend by remember(pk) { Graph.db.friends().observe(pk) }.collectAsState(initial = null)
    val me by Graph.location.me.collectAsState()
    var azimuth by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        Graph.location.watch(true)
        val sm = ctx.getSystemService(SensorManager::class.java)
        val rv = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val m = FloatArray(9)
        val o = FloatArray(3)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(m, e.values)
                SensorManager.getOrientation(m, o)
                val deg = ((Math.toDegrees(o[0].toDouble()).toFloat() + 360f) % 360f)
                // smooth across the 0/360 seam
                var d = deg - azimuth
                if (d > 180) d -= 360
                if (d < -180) d += 360
                azimuth = (azimuth + d * 0.2f + 360f) % 360f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (rv != null) sm.registerListener(l, rv, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sm?.unregisterListener(l)
            Graph.location.watch(false)
        }
    }
    val f = friend
    val target = f?.let { fr -> if (fr.lat != null && fr.lon != null && fr.sharingUntil > now()) Location("f").apply { latitude = fr.lat!!; longitude = fr.lon!! } else null }
    val bearing = if (me != null && target != null) me!!.bearingTo(target) else null
    val dist = if (me != null && target != null) me!!.distanceTo(target) else null
    val rel = bearing?.let { ((it - azimuth) % 360f + 540f) % 360f - 180f }
    val aligned = rel != null && abs(rel) < 12f
    // the arrow takes the short way round
    var shown by remember { mutableFloatStateOf(0f) }
    if (rel != null) {
        var d = rel - (shown % 360f)
        if (d > 180) d -= 360
        if (d < -180) d += 360
        shown += d
    }
    val angle by animateFloatAsState(shown, spring(stiffness = 90f, dampingRatio = 0.8f), label = "arrow")
    LaunchedEffect(aligned) {
        while (aligned) {
            vibrate(ctx, 40, 90)
            delay(1500)
        }
    }
    val ink = MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Bussola"), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Avatar(f?.emoji ?: "🙂", 64.dp)
            Spacer(Modifier.height(8.dp))
            Text(f?.displayName ?: "", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    drawCircle(ink.copy(alpha = 0.08f), r)
                    drawCircle(ink.copy(alpha = if (aligned) 0.9f else 0.2f), r, style = Stroke(if (aligned) 6f else 2f))
                    rotate(-azimuth) {
                        for (i in 0 until 72) {
                            val a = Math.toRadians(i * 5.0)
                            val len = if (i % 18 == 0) 22f else if (i % 6 == 0) 14f else 7f
                            val c = Offset(center.x + (r - 6) * kotlin.math.sin(a).toFloat(), center.y - (r - 6) * kotlin.math.cos(a).toFloat())
                            val c2 = Offset(center.x + (r - 6 - len) * kotlin.math.sin(a).toFloat(), center.y - (r - 6 - len) * kotlin.math.cos(a).toFloat())
                            drawLine(ink.copy(alpha = if (i == 0) 1f else 0.35f), c, c2, if (i == 0) 4f else 2f)
                        }
                    }
                }
                if (rel != null) {
                    Canvas(Modifier.size(170.dp).graphicsLayer { rotationZ = angle }) {
                        val w = size.width
                        val h = size.height
                        val p = Path().apply {
                            moveTo(w / 2, 0f)
                            lineTo(w * 0.78f, h * 0.62f)
                            lineTo(w / 2, h * 0.5f)
                            lineTo(w * 0.22f, h * 0.62f)
                            close()
                        }
                        drawPath(p, ink)
                    }
                } else {
                    Text("?", fontSize = 64.sp, color = ink.copy(alpha = 0.3f))
                }
            }
            Spacer(Modifier.height(28.dp))
            when {
                f == null -> {}
                target == null -> Text(t("{0} non sta condividendo la posizione con te", f.displayName), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                me == null -> Text(t("Cerco la tua posizione…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Text(Fmt.distance(dist ?: 0f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (aligned) t("Sei nella direzione giusta") else t("Gira il telefono finché la freccia punta in alto"),
                        style = MaterialTheme.typography.bodyLarge, color = if (aligned) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(t("aggiornato {0}", Fmt.ago(f.locAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ====================================================================== high five to add a friend

@Composable
fun HighFiveScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val matches by HighFive.matches.collectAsState()
    val bumps by HighFive.bumps.collectAsState()
    var added by remember { mutableStateOf(setOf<String>()) }
    val me by Graph.location.me.collectAsState()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) Graph.location.watch(true) }
    DisposableEffect(Unit) {
        val hasLoc = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLoc) Graph.location.watch(true)
        HighFive.open(Weather.here(ctx)?.let { p -> Location("h").apply { latitude = p.lat; longitude = p.lon } })
        val sm = ctx.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val linear = acc?.type == Sensor.TYPE_LINEAR_ACCELERATION
        var last = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val jolt = if (linear) mag else abs(mag - SensorManager.GRAVITY_EARTH)
                val n = System.currentTimeMillis()
                if (jolt > 17f && n - last > 1500) {
                    last = n
                    vibrate(ctx, 60, 200)
                    HighFive.bump()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        if (!hasLoc) perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        onDispose {
            sm?.unregisterListener(l)
            HighFive.close()
            if (hasLoc) Graph.location.watch(false)
        }
    }
    LaunchedEffect(me) { HighFive.updatePosition(me) }
    val hit = remember { Animatable(0f) }
    LaunchedEffect(bumps) {
        if (bumps > 0) {
            hit.snapTo(1f)
            hit.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    val tr = rememberInfiniteTransition(label = "five")
    val wave by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")
    val ink = MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Indietro")) }
            Text(t("Batti cinque"), style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(280.dp)) {
                for (k in 0 until 3) {
                    val f = (wave + k / 3f) % 1f
                    drawCircle(ink.copy(alpha = 0.12f * (1f - f)), size.minDimension / 2 * (0.4f + f * 0.6f), style = Stroke(3f))
                }
                if (hit.value > 0f) drawCircle(ink.copy(alpha = 0.25f * hit.value), size.minDimension / 2 * (1.1f - hit.value * 0.5f))
            }
            Box(
                Modifier.size(150.dp).graphicsLayer { scaleX = 1f + hit.value * 0.25f; scaleY = 1f + hit.value * 0.25f; rotationZ = -hit.value * 18f }
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(0.85f) { vibrate(ctx, 40); HighFive.bump() },
                contentAlignment = Alignment.Center,
            ) { Text("🖐️", fontSize = 64.sp) }
        }
        Text(
            t("Tenete il telefono in mano e battete il cinque, oppure toccate la mano nello stesso momento."),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            t("Mentre questa schermata è aperta, chi batte il cinque nello stesso istante vede il tuo nome e la tua zona (circa 1 km)."),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(20.dp))
        matches.forEach { m -> FiveCard(m, m.pubkey in added) { Graph.scope.launch { HighFive.add(m) }; added = added + m.pubkey } }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FiveCard(m: FiveMatch, added: Boolean, onAdd: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f)) }
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = appear.value.coerceIn(0f, 1f) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.profile.emoji, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.profile.displayName, style = MaterialTheme.typography.titleMedium)
                Text("@" + m.profile.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (added) OutlinedButton(onClick = {}, enabled = false, shape = CircleShape) { Text(t("Richiesta inviata")) }
            else Button(onClick = onAdd, shape = CircleShape) { Text(t("Aggiungi")) }
        }
    }
}

// ====================================================================== proximity: voice to the ear, screen off in calls

/** while a voice message plays, bringing the phone to the ear moves it to the earpiece and turns the screen off */
@Composable
fun EarForVoice() {
    val ctx = LocalContext.current
    val playing by VoicePlayer.playing.collectAsState()
    if (playing == null) return
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val prox = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val lock = runCatching { pm?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "monolith:ear") }.getOrNull()
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val near = e.values[0] < minOf(5f, prox?.maximumRange ?: 5f)
                VoicePlayer.setEarpiece(near)
                runCatching { if (near) { if (lock?.isHeld == false) lock.acquire(10 * 60_000L) } else if (lock?.isHeld == true) lock.release() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (prox != null) sm.registerListener(l, prox, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sm?.unregisterListener(l)
            runCatching { if (lock?.isHeld == true) lock.release() }
            VoicePlayer.setEarpiece(false)
        }
    }
}

/** in a voice call, the screen goes dark against the face so the cheek can't press anything */
@Composable
fun CallProximity(on: Boolean) {
    val ctx = LocalContext.current
    DisposableEffect(on) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val lock = if (on) runCatching { pm?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "monolith:call") }.getOrNull() else null
        runCatching { lock?.acquire(4 * 3_600_000L) }
        onDispose { runCatching { if (lock?.isHeld == true) lock.release() } }
    }
}
