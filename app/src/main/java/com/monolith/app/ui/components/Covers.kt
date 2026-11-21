package com.monolith.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.monolith.app.core.t
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** the preset covers: drawn by the app, in black and white, slowly moving */
object Covers {
    val IDS = listOf("monoliths", "stars", "mountains", "waves", "city", "dunes", "grid", "rings", "rain", "aurora", "marble", "dots")

    fun name(id: String) = when (id) {
        "monoliths" -> t("Monoliti")
        "stars" -> t("Stelle")
        "mountains" -> t("Montagne")
        "waves" -> t("Onde")
        "city" -> t("Città")
        "dunes" -> t("Dune")
        "grid" -> t("Orizzonte")
        "rings" -> t("Cerchi")
        "rain" -> t("Pioggia")
        "aurora" -> t("Aurora")
        "marble" -> t("Marmo")
        else -> t("Puntini")
    }
}

@Composable
fun CoverArt(id: String, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface
    val tr = rememberInfiniteTransition(label = "cover")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "ph")
    Canvas(modifier.background(bg)) { drawCover(id, ink, bg, ph) }
}

private fun DrawScope.ridge(base: Float, amp: Float, seed: Int, ink: Color, alpha: Float, shift: Float) {
    val w = size.width
    val p = Path()
    p.moveTo(0f, size.height)
    var x = 0f
    while (x <= w + 10f) {
        val k = x / w
        val y = base - amp * (0.5f + 0.5f * sin((k * (3 + seed) + shift + seed) * PI.toFloat()) * 0.6f + 0.4f * abs(sin((k * (7 + seed) + seed * 0.3f) * PI.toFloat())))
        p.lineTo(x, y)
        x += 8f
    }
    p.lineTo(w, size.height)
    p.close()
    drawPath(p, ink.copy(alpha = alpha))
}

fun DrawScope.drawCover(id: String, ink: Color, bg: Color, ph: Float) {
    val w = size.width
    val h = size.height
    val tau = (2 * PI).toFloat()
    when (id) {
        "monoliths" -> {
            drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0.02f), ink.copy(alpha = 0.14f))))
            drawCircle(ink.copy(alpha = 0.12f), h * 0.22f, Offset(w * 0.78f, h * 0.32f))
            drawRect(ink.copy(alpha = 0.35f), Offset(0f, h * 0.82f), Size(w, 2f))
            val xs = listOf(0.12f, 0.27f, 0.5f, 0.66f, 0.86f)
            val hs = listOf(0.3f, 0.45f, 0.62f, 0.38f, 0.5f)
            xs.forEachIndexed { i, fx ->
                val bw = w * 0.05f
                val bh = h * hs[i] + sin(ph * tau + i) * 3f
                drawRect(ink.copy(alpha = 0.55f + i % 2 * 0.3f), Offset(w * fx, h * 0.82f - bh), Size(bw, bh))
            }
        }
        "stars" -> {
            drawRect(ink.copy(alpha = 0.85f))
            val r = java.util.Random(11)
            repeat(130) {
                val tw = 0.4f + 0.6f * abs(sin(ph * tau * 3 + it))
                drawCircle(bg.copy(alpha = 0.8f * tw), 0.8f + r.nextFloat() * 2f, Offset(r.nextFloat() * w, r.nextFloat() * h))
            }
            drawCircle(bg.copy(alpha = 0.9f), h * 0.16f, Offset(w * 0.82f, h * 0.3f))
            drawCircle(ink.copy(alpha = 0.85f), h * 0.16f, Offset(w * 0.86f, h * 0.27f))
        }
        "mountains" -> {
            drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0.03f), ink.copy(alpha = 0.1f))))
            drawCircle(ink.copy(alpha = 0.1f), h * 0.18f, Offset(w * 0.25f, h * 0.35f))
            ridge(h * 0.62f, h * 0.45f, 1, ink, 0.2f, ph * 0.2f)
            ridge(h * 0.8f, h * 0.4f, 2, ink, 0.4f, ph * 0.4f)
            ridge(h * 0.98f, h * 0.3f, 4, ink, 0.75f, ph * 0.6f)
        }
        "waves" -> {
            for (k in 0 until 9) {
                val p = Path()
                val base = h * (0.15f + k * 0.1f)
                p.moveTo(0f, base)
                var x = 0f
                while (x <= w + 8f) {
                    p.lineTo(x, base + sin(x / w * 2 * tau + ph * tau * (1 + k % 3) + k) * h * 0.05f)
                    x += 8f
                }
                drawPath(p, ink.copy(alpha = 0.12f + k * 0.07f), style = Stroke(3f))
            }
        }
        "city" -> {
            drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0.05f), ink.copy(alpha = 0.2f))))
            val r = java.util.Random(5)
            var x = 0f
            while (x < w) {
                val bw = w * (0.05f + r.nextFloat() * 0.07f)
                val bh = h * (0.3f + r.nextFloat() * 0.55f)
                drawRect(ink.copy(alpha = 0.8f), Offset(x, h - bh), Size(bw - 3f, bh))
                var wy = h - bh + 10f
                while (wy < h - 8f) {
                    var wx = x + 5f
                    while (wx < x + bw - 10f) {
                        val on = abs(sin(wx * 13.1f + wy * 7.3f + (ph * 6).toInt())) > 0.55f
                        if (on) drawRect(bg.copy(alpha = 0.7f), Offset(wx, wy), Size(4f, 6f))
                        wx += 9f
                    }
                    wy += 13f
                }
                x += bw
            }
        }
        "dunes" -> {
            for (k in 0 until 5) {
                val p = Path()
                val base = h * (0.35f + k * 0.16f)
                p.moveTo(0f, h)
                p.lineTo(0f, base)
                p.cubicTo(w * 0.3f, base - h * 0.2f + sin(ph * tau + k) * 6f, w * 0.6f, base + h * 0.15f, w, base - h * 0.05f)
                p.lineTo(w, h)
                p.close()
                drawPath(p, ink.copy(alpha = 0.1f + k * 0.15f))
            }
        }
        "grid" -> {
            val hor = h * 0.45f
            drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0.0f), ink.copy(alpha = 0.12f)), startY = 0f, endY = hor))
            drawCircle(ink.copy(alpha = 0.35f), h * 0.28f, Offset(w / 2, hor))
            drawRect(bg, Offset(0f, hor), Size(w, h - hor))
            for (k in -10..10) drawLine(ink.copy(alpha = 0.35f), Offset(w / 2 + k * w * 0.02f, hor), Offset(w / 2 + k * w * 0.18f, h), 1.5f)
            for (k in 0 until 8) {
                val f = ((k + ph * 8) % 8) / 8f
                val y = hor + (h - hor) * f * f
                drawLine(ink.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), 1.5f)
            }
        }
        "rings" -> {
            for (k in 1..14) drawCircle(ink.copy(alpha = 0.08f + (k % 3) * 0.06f), k * h * 0.12f, Offset(w * 0.2f + sin(ph * tau) * 10f, h * 0.9f), style = Stroke(2.5f))
            for (k in 1..8) drawCircle(ink.copy(alpha = 0.1f), k * h * 0.1f, Offset(w * 0.9f, h * 0.1f), style = Stroke(2f))
        }
        "rain" -> {
            drawRect(ink.copy(alpha = 0.08f))
            val r = java.util.Random(9)
            repeat(90) {
                val x = r.nextFloat() * w
                val sp = 0.6f + r.nextFloat()
                val y = ((r.nextFloat() + ph * 6 * sp) % 1f) * (h + 40f) - 20f
                drawLine(ink.copy(alpha = 0.35f), Offset(x, y), Offset(x - 6f, y + 18f), 1.6f)
            }
        }
        "aurora" -> {
            drawRect(ink.copy(alpha = 0.88f))
            for (k in 0 until 5) {
                val p = Path()
                val base = h * (0.3f + k * 0.1f)
                p.moveTo(0f, base)
                var x = 0f
                while (x <= w + 8f) {
                    p.lineTo(x, base + sin(x / w * tau * 1.5f + ph * tau + k) * h * 0.12f)
                    x += 8f
                }
                drawPath(p, bg.copy(alpha = 0.12f + k * 0.05f), style = Stroke(h * 0.08f))
            }
        }
        "marble" -> {
            drawRect(ink.copy(alpha = 0.04f))
            val r = java.util.Random(21)
            repeat(14) {
                val p = Path()
                var x = -20f
                var y = r.nextFloat() * h
                p.moveTo(x, y)
                while (x < w + 20f) {
                    x += 30f + r.nextFloat() * 40f
                    y += (r.nextFloat() - 0.5f) * h * 0.25f
                    p.lineTo(x, y)
                }
                drawPath(p, ink.copy(alpha = 0.06f + r.nextFloat() * 0.18f), style = Stroke(0.8f + r.nextFloat() * 2.5f))
            }
        }
        else -> {
            val step = 14f
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) {
                    val f = (x / w + 0.3f * sin(y / h * tau + ph * tau)).coerceIn(0f, 1f)
                    drawCircle(ink.copy(alpha = 0.7f), 0.5f + f * 4.5f, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
}

/** a small preview of a cover, used in the picker */
@Composable
fun CoverThumb(id: String, modifier: Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.background(bg)) {
        drawCover(id, ink, bg, 0f)
        drawRoundRect(Color.Transparent, cornerRadius = CornerRadius(0f))
    }
}
