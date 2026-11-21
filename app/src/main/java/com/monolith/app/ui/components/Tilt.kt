package com.monolith.app.ui.components

import kotlinx.coroutines.flow.collectLatest

import androidx.compose.runtime.remember

import androidx.compose.runtime.State

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.core.VectorConverter

import androidx.compose.animation.core.Animatable

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** how the phone is tilted, -1..1 on both axes, shared by everything on screen */
object Tilt {
    private val _value = MutableStateFlow(Offset.Zero)
    val value: StateFlow<Offset> = _value.asStateFlow()
    private var users = 0
    private var sm: SensorManager? = null
    private var base: Offset? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val g = SensorManager.GRAVITY_EARTH
            val x = (-e.values[0] / g).coerceIn(-1f, 1f)
            val y = (e.values[1] / g).coerceIn(-1f, 1f)
            // relative to how the phone was held when the screen opened
            val b = base ?: Offset(x, y).also { base = it }
            val nx = ((x - b.x) * 2.2f).coerceIn(-1f, 1f)
            val ny = ((y - b.y) * 2.2f).coerceIn(-1f, 1f)
            val cur = _value.value
            _value.value = Offset(cur.x + (nx - cur.x) * 0.15f, cur.y + (ny - cur.y) * 0.15f)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    @Synchronized
    fun acquire(ctx: Context) {
        users++
        if (users > 1) return
        val m = ctx.getSystemService(SensorManager::class.java) ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        base = null
        m.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
        sm = m
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        sm?.unregisterListener(listener)
        sm = null
        _value.value = Offset.Zero
    }
}

/** the tilt as a state: read it inside graphicsLayer / draw blocks so the screen doesn't recompose at every sensor event */
@Composable
fun rememberTiltState(): State<Offset> {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        Tilt.acquire(ctx)
        onDispose { Tilt.release() }
    }
    return Tilt.value.collectAsState()
}

@Composable
fun rememberTilt(): Offset = rememberTiltState().value

/**
 * moves and leans the content a little as the phone tilts: [depth] in dp, [lean] in degrees.
 * The tilt is only read while drawing, so nothing recomposes when the phone moves.
 */
fun Modifier.tiltDepth(depth: Float = 10f, lean: Float = 6f): Modifier = composed {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        Tilt.acquire(ctx)
        onDispose { Tilt.release() }
    }
    val anim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(Unit) {
        Tilt.value.collectLatest { anim.animateTo(it, spring(stiffness = 120f)) }
    }
    graphicsLayer {
        val v = anim.value
        translationX = v.x * depth.dp.toPx()
        translationY = v.y * depth.dp.toPx()
        rotationY = v.x * lean
        rotationX = -v.y * lean
        cameraDistance = 16f * density
    }
}
