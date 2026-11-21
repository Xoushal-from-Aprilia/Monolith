package com.monolith.app.widget

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live wallpaper: the monolith under a slow sky. Stars drift and twinkle, rings breathe out of the
 * stone, everything shifts in depth when the phone tilts, and a touch sends a ripple.
 * Black at night (or with the dark theme), pale stone by day.
 */
class MonolithWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = MonoEngine()

    private inner class MonoEngine : Engine(), SensorEventListener {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var w = 1
        private var h = 1
        private var tiltX = 0f
        private var tiltY = 0f
        private var baseX: Float? = null
        private var baseY: Float? = null
        private val sm by lazy { getSystemService(SensorManager::class.java) }
        private val stars = List(140) { floatArrayOf(Random.nextFloat(), Random.nextFloat() * 0.8f, 0.4f + Random.nextFloat() * 1.8f, Random.nextFloat() * 6.28f, 0.2f + Random.nextFloat() * 0.8f) }
        private val ripples = ArrayList<FloatArray>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val start = SystemClock.elapsedRealtime()

        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                    ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                handler.removeCallbacks(frame)
                handler.post(frame)
            } else {
                sm?.unregisterListener(this)
                handler.removeCallbacks(frame)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width.coerceAtLeast(1)
            h = height.coerceAtLeast(1)
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            sm?.unregisterListener(this)
            super.onSurfaceDestroyed(holder)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN && ripples.size < 6) {
                ripples.add(floatArrayOf(event.x, event.y, 0f))
            }
        }

        override fun onSensorChanged(e: SensorEvent) {
            val x = -e.values[0] / SensorManager.GRAVITY_EARTH
            val y = e.values[1] / SensorManager.GRAVITY_EARTH
            val bx = baseX ?: x.also { baseX = it }
            val by = baseY ?: y.also { baseY = it }
            // the rest position follows slowly, so it never gets stuck off-centre
            baseX = bx + (x - bx) * 0.005f
            baseY = by + (y - by) * 0.005f
            tiltX += ((x - bx) * 2f - tiltX) * 0.12f
            tiltY += ((y - by) * 2f - tiltY) * 0.12f
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}

        private fun night(): Boolean {
            val uiNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return uiNight || hour >= 20 || hour < 7
        }

        private fun draw() {
            val holder = surfaceHolder
            val c: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                render(c)
            } finally {
                runCatching { holder.unlockCanvasAndPost(c) }
            }
        }

        private fun render(c: Canvas) {
            val t = (SystemClock.elapsedRealtime() - start) / 1000f
            val dark = night()
            val bg = if (dark) Color.rgb(6, 6, 6) else Color.rgb(236, 236, 233)
            val ink = if (dark) Color.WHITE else Color.rgb(12, 12, 12)
            c.drawColor(bg)
            val tx = tiltX.coerceIn(-1f, 1f)
            val ty = tiltY.coerceIn(-1f, 1f)

            // far layer: stars (or dust by day), moving the most
            paint.style = Paint.Style.FILL
            for (s in stars) {
                val x = ((s[0] + t * 0.004f * s[4]) % 1f) * w + tx * 40f * s[4]
                val y = s[1] * h + ty * 40f * s[4]
                val tw = 0.5f + 0.5f * sin(t * (0.6f + s[4]) + s[3])
                paint.color = ink
                paint.alpha = ((if (dark) 200 else 70) * tw * s[4]).toInt().coerceIn(0, 255)
                c.drawCircle(x, y, s[2], paint)
            }

            // the monolith, near the bottom, moving a little less
            val cx = w / 2f + tx * 16f
            val baseY = h * 0.74f + ty * 10f
            val mw = w * 0.16f
            val mh = h * 0.30f
            val depth = mw * 0.24f

            // breathing rings around the stone
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            for (k in 0 until 4) {
                val f = ((t / 7f) + k / 4f) % 1f
                paint.color = ink
                paint.alpha = ((if (dark) 60 else 45) * (1f - f)).toInt()
                c.drawCircle(cx, baseY - mh * 0.45f, mw * 0.8f + f * w * 0.7f, paint)
            }

            paint.style = Paint.Style.FILL
            // ground line
            paint.color = ink; paint.alpha = if (dark) 60 else 50
            c.drawRect(w * 0.12f, baseY + 2f, w * 0.88f, baseY + 4f, paint)
            // top face
            path.reset()
            path.moveTo(cx - mw / 2, baseY - mh)
            path.lineTo(cx - mw / 2 + depth, baseY - mh - depth * 0.8f)
            path.lineTo(cx + mw / 2 + depth, baseY - mh - depth * 0.8f)
            path.lineTo(cx + mw / 2, baseY - mh)
            path.close()
            paint.color = if (dark) Color.rgb(150, 150, 150) else Color.rgb(140, 140, 140); paint.alpha = 255
            c.drawPath(path, paint)
            // side face
            path.reset()
            path.moveTo(cx + mw / 2, baseY - mh)
            path.lineTo(cx + mw / 2 + depth, baseY - mh - depth * 0.8f)
            path.lineTo(cx + mw / 2 + depth, baseY - depth * 0.8f)
            path.lineTo(cx + mw / 2, baseY)
            path.close()
            paint.color = if (dark) Color.rgb(60, 60, 60) else Color.rgb(58, 58, 58)
            c.drawPath(path, paint)
            // front face
            paint.color = if (dark) Color.rgb(228, 228, 226) else Color.rgb(12, 12, 12)
            c.drawRect(cx - mw / 2, baseY - mh, cx + mw / 2, baseY, paint)
            // a slow light running up the stone
            val glint = ((t / 9f) % 1f)
            paint.color = if (dark) Color.BLACK else Color.WHITE
            paint.alpha = (40 * sin(glint * PI).toFloat()).toInt().coerceAtLeast(0)
            c.drawRect(cx - mw / 2, baseY - mh * glint - 6f, cx + mw / 2, baseY - mh * glint, paint)

            // touch ripples, closest layer
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            val it = ripples.iterator()
            while (it.hasNext()) {
                val r = it.next()
                r[2] += 0.02f
                if (r[2] >= 1f) { it.remove(); continue }
                paint.color = ink
                paint.alpha = (140 * (1f - r[2])).toInt()
                c.drawCircle(r[0] + tx * 8f, r[1] + ty * 8f, r[2] * w * 0.5f, paint)
            }
        }
    }
}
