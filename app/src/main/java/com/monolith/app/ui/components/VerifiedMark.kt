package com.monolith.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The verified symbol, drawn by hand. Shared word for word with the developer dashboard,
// which copies this file at build time: keep it free of anything but Compose graphics.

/** the looks the developer can give to a verified account */
object VerifiedStyles {
    val SHAPES = listOf("seal" to "Sigillo", "mono" to "Monolite", "star" to "Stella", "bolt" to "Fulmine", "crown" to "Corona", "heart" to "Cuore")
    /** 0 means the colour of the text */
    val COLORS = listOf("blue" to 0xFF1D9BF0, "gold" to 0xFFE0A800, "green" to 0xFF2DA44E, "purple" to 0xFF8957E5, "red" to 0xFFE5484D, "pink" to 0xFFDB61A2, "ink" to 0L)
    fun color(key: String): Long = COLORS.firstOrNull { it.first == key }?.second ?: COLORS[0].second
}

private fun DrawScope.check(mark: Color, cx: Float, cy: Float, s: Float) {
    val p = Path().apply {
        moveTo(cx - s * 0.42f, cy + s * 0.02f)
        lineTo(cx - s * 0.12f, cy + s * 0.32f)
        lineTo(cx + s * 0.46f, cy - s * 0.3f)
    }
    drawPath(p, mark, style = Stroke(width = s * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** draws the symbol in the whole (square) area: [fill] for the shape, [mark] for what is drawn on it */
fun DrawScope.verifiedMark(shape: String, fill: Color, mark: Color) {
    val w = min(size.width, size.height)
    val cx = size.width / 2
    val cy = size.height / 2
    val r = w / 2
    when (shape) {
        "star" -> {
            val p = Path()
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val rr = if (i % 2 == 0) r else r * 0.52f
                val x = cx + cos(a).toFloat() * rr
                val y = cy + 0.06f * w + sin(a).toFloat() * rr
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, fill)
            drawPath(p, fill, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
            check(mark, cx, cy + w * 0.06f, w * 0.42f)
        }
        "heart" -> {
            val p = Path().apply {
                moveTo(cx, cy + r * 0.86f)
                cubicTo(cx - r * 1.25f, cy + r * 0.05f, cx - r * 0.9f, cy - r * 1.05f, cx, cy - r * 0.45f)
                cubicTo(cx + r * 0.9f, cy - r * 1.05f, cx + r * 1.25f, cy + r * 0.05f, cx, cy + r * 0.86f)
                close()
            }
            drawPath(p, fill)
            check(mark, cx, cy - w * 0.02f, w * 0.44f)
        }
        "mono" -> {
            drawCircle(fill, r)
            // a small monolith: front, top and side
            val bw = w * 0.26f
            val bh = w * 0.5f
            val left = cx - bw / 2 - w * 0.03f
            val top = cy - bh / 2 + w * 0.05f
            val d = bw * 0.32f
            drawPath(Path().apply { moveTo(left, top); lineTo(left + d, top - d * 0.8f); lineTo(left + bw + d, top - d * 0.8f); lineTo(left + bw, top); close() }, mark.copy(alpha = mark.alpha * 0.7f))
            drawPath(Path().apply { moveTo(left + bw, top); lineTo(left + bw + d, top - d * 0.8f); lineTo(left + bw + d, top + bh - d * 0.8f); lineTo(left + bw, top + bh); close() }, mark.copy(alpha = mark.alpha * 0.45f))
            drawRect(mark, Offset(left, top), Size(bw, bh))
        }
        "bolt" -> {
            drawCircle(fill, r)
            val s = w
            drawPath(Path().apply {
                moveTo(cx + s * 0.06f, cy - s * 0.34f)
                lineTo(cx - s * 0.2f, cy + s * 0.05f)
                lineTo(cx - s * 0.01f, cy + s * 0.05f)
                lineTo(cx - s * 0.07f, cy + s * 0.34f)
                lineTo(cx + s * 0.2f, cy - s * 0.06f)
                lineTo(cx + s * 0.01f, cy - s * 0.06f)
                close()
            }, mark)
        }
        "crown" -> {
            drawCircle(fill, r)
            val s = w
            drawPath(Path().apply {
                moveTo(cx - s * 0.26f, cy + s * 0.18f)
                lineTo(cx - s * 0.28f, cy - s * 0.16f)
                lineTo(cx - s * 0.12f, cy - s * 0.02f)
                lineTo(cx, cy - s * 0.24f)
                lineTo(cx + s * 0.12f, cy - s * 0.02f)
                lineTo(cx + s * 0.28f, cy - s * 0.16f)
                lineTo(cx + s * 0.26f, cy + s * 0.18f)
                close()
            }, mark)
            drawRect(mark, Offset(cx - s * 0.26f, cy + s * 0.21f), Size(s * 0.52f, s * 0.07f))
        }
        else -> {
            // seal: a round stamp with a wavy edge
            val p = Path()
            val n = 24
            for (i in 0..n) {
                val a = 2 * PI * i / n
                val rr = if (i % 2 == 0) r else r * 0.86f
                val x = cx + cos(a).toFloat() * rr
                val y = cy + sin(a).toFloat() * rr
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, fill)
            drawPath(p, fill, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
            check(mark, cx, cy, w * 0.5f)
        }
    }
}
