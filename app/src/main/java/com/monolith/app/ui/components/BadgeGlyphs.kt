package com.monolith.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The symbols inside the badges, drawn by hand with lines and shapes (no emoji, no icon font):
 * one monoline style for all of them, so the set looks like one family.
 * Coordinates go from -1 to 1 inside a square of side [g] centred in [c]; y grows downwards.
 */
internal class Glyph(val ds: DrawScope, val c: Offset, val g: Float, val ink: Color, val bg: Color) {
    val sw = g * 0.085f
    fun p(x: Float, y: Float) = Offset(c.x + x * g / 2, c.y + y * g / 2)
    fun r(v: Float) = v * g / 2
    val line get() = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin get() = Stroke(sw * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun path(vararg pts: Pair<Float, Float>, close: Boolean = true) = Path().apply {
        pts.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        if (close) close()
    }

    fun seg(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = sw, color: Color = ink) =
        ds.drawLine(color, p(x1, y1), p(x2, y2), w, StrokeCap.Round)

    fun ring(x: Float, y: Float, rad: Float, stroke: Stroke = line, color: Color = ink) = ds.drawCircle(color, r(rad), p(x, y), style = stroke)
    fun dot(x: Float, y: Float, rad: Float, color: Color = ink) = ds.drawCircle(color, r(rad), p(x, y))

    fun box(x1: Float, y1: Float, x2: Float, y2: Float, corner: Float, fill: Color? = null, stroke: Stroke? = line) {
        val tl = p(x1, y1)
        val size = Size(r(x2 - x1), r(y2 - y1))
        fill?.let { ds.drawRoundRect(it, tl, size, CornerRadius(r(corner))) }
        stroke?.let { ds.drawRoundRect(ink, tl, size, CornerRadius(r(corner)), style = it) }
    }

    fun arc(x: Float, y: Float, rad: Float, start: Float, sweep: Float, stroke: Stroke = line, color: Color = ink) {
        val o = p(x, y)
        ds.drawArc(color, start, sweep, false, Offset(o.x - r(rad), o.y - r(rad)), Size(r(rad) * 2, r(rad) * 2), style = stroke)
    }

    fun sparkle(x: Float, y: Float, s: Float, color: Color = ink) {
        val k = 0.16f
        val pp = Path().apply {
            val o = p(x, y)
            val h = r(s)
            moveTo(o.x, o.y - h)
            quadraticBezierTo(o.x + h * k, o.y - h * k, o.x + h, o.y)
            quadraticBezierTo(o.x + h * k, o.y + h * k, o.x, o.y + h)
            quadraticBezierTo(o.x - h * k, o.y + h * k, o.x - h, o.y)
            quadraticBezierTo(o.x - h * k, o.y - h * k, o.x, o.y - h)
            close()
        }
        ds.drawPath(pp, color)
    }
}

/** the hand-drawn symbol of badge [id] */
internal fun DrawScope.badgeGlyph(id: String, c: Offset, g: Float, ink: Color, bg: Color) {
    val q = Glyph(this, c, g, ink, bg)
    with(q) {
        when (id) {
            "first_word" -> {
                box(-0.82f, -0.7f, 0.82f, 0.38f, 0.34f)
                ds.drawPath(path(-0.42f to 0.38f, -0.58f to 0.8f, -0.08f to 0.38f, close = false), ink, style = line)
                dot(0f, -0.16f, 0.12f)
            }
            "chatter" -> {
                box(-0.9f, -0.8f, 0.32f, 0.1f, 0.3f)
                ds.drawPath(path(-0.6f to 0.1f, -0.7f to 0.42f, -0.36f to 0.1f, close = false), ink, style = line)
                box(-0.28f, -0.28f, 0.9f, 0.6f, 0.3f, fill = bg)
                ds.drawPath(path(0.6f to 0.6f, 0.72f to 0.9f, 0.36f to 0.6f, close = false), ink, style = line)
                listOf(0.06f, 0.31f, 0.56f).forEach { dot(it, 0.16f, 0.07f) }
            }
            "novelist" -> {
                val left = Path().apply {
                    val a = p(0f, -0.5f); moveTo(a.x, a.y)
                    val b = p(-0.92f, -0.62f); val k = p(-0.45f, -0.78f); quadraticBezierTo(k.x, k.y, b.x, b.y)
                    val d = p(-0.92f, 0.58f); lineTo(d.x, d.y)
                    val e = p(0f, 0.72f); val k2 = p(-0.45f, 0.42f); quadraticBezierTo(k2.x, k2.y, e.x, e.y)
                    close()
                }
                val right = Path().apply {
                    val a = p(0f, -0.5f); moveTo(a.x, a.y)
                    val b = p(0.92f, -0.62f); val k = p(0.45f, -0.78f); quadraticBezierTo(k.x, k.y, b.x, b.y)
                    val d = p(0.92f, 0.58f); lineTo(d.x, d.y)
                    val e = p(0f, 0.72f); val k2 = p(0.45f, 0.42f); quadraticBezierTo(k2.x, k2.y, e.x, e.y)
                    close()
                }
                ds.drawPath(left, ink, style = line)
                ds.drawPath(right, ink, style = line)
                listOf(-0.25f, 0.02f, 0.29f).forEach { y ->
                    seg(-0.7f, y - 0.06f, -0.22f, y, sw * 0.55f)
                    seg(0.22f, y, 0.7f, y - 0.06f, sw * 0.55f)
                }
            }
            "night_owl" -> {
                dot(-0.12f, 0.05f, 0.72f)
                dot(0.22f, -0.2f, 0.6f, bg)
                sparkle(0.58f, 0.42f, 0.24f)
                sparkle(0.5f, -0.7f, 0.16f)
            }
            "early_bird" -> {
                seg(-0.92f, 0.5f, 0.92f, 0.5f)
                ds.drawArc(ink, 180f, 180f, true, p(-0.46f, 0.04f), Size(r(0.92f), r(0.92f)))
                for (k in 0..4) {
                    val a = (PI + PI / 6 * (k + 1)).toFloat()
                    seg(cos(a) * 0.66f, 0.5f + sin(a) * 0.66f, cos(a) * 0.9f, 0.5f + sin(a) * 0.9f)
                }
                seg(-0.55f, 0.78f, 0.55f, 0.78f, sw * 0.6f)
            }
            "photographer" -> {
                ds.drawPath(path(-0.34f to -0.44f, -0.2f to -0.7f, 0.2f to -0.7f, 0.34f to -0.44f, close = false), ink, style = line)
                box(-0.88f, -0.44f, 0.88f, 0.7f, 0.22f)
                ring(0f, 0.12f, 0.34f)
                dot(0f, 0.12f, 0.1f)
                dot(0.6f, -0.2f, 0.08f)
            }
            "first_call" -> {
                val h = Path().apply {
                    val a = p(-0.5f, -0.72f); moveTo(a.x, a.y)
                    val k = p(-0.95f, 0.75f); val b = p(0.62f, 0.55f); quadraticBezierTo(k.x, k.y, b.x, b.y)
                }
                ds.drawPath(h, ink, style = Stroke(sw * 2.3f, cap = StrokeCap.Round))
                arc(-0.15f, 0.12f, 0.55f, -80f, 70f)
                arc(-0.15f, 0.12f, 0.85f, -78f, 66f)
            }
            "marathon" -> {
                ring(0f, 0.14f, 0.7f)
                seg(0f, -0.56f, 0f, -0.74f)
                seg(-0.16f, -0.84f, 0.16f, -0.84f)
                seg(0.55f, -0.5f, 0.68f, -0.63f)
                seg(0f, 0.14f, 0.3f, -0.2f)
                dot(0f, 0.14f, 0.1f)
                for (k in 0 until 12) {
                    val a = (2 * PI / 12 * k).toFloat()
                    dot(cos(a) * 0.52f, 0.14f + sin(a) * 0.52f, 0.035f)
                }
            }
            "friends5" -> {
                ring(0f, 0f, 0.62f, thin)
                for (k in 0 until 5) {
                    val a = (-PI / 2 + 2 * PI / 5 * k).toFloat()
                    dot(cos(a) * 0.62f, sin(a) * 0.62f, 0.2f, bg)
                    dot(cos(a) * 0.62f, sin(a) * 0.62f, 0.15f)
                }
                dot(0f, 0f, 0.1f)
            }
            "friends20" -> {
                ring(0f, 0f, 0.4f, thin)
                for (k in 0 until 5) {
                    val a = (-PI / 2 + 2 * PI / 5 * k).toFloat()
                    dot(cos(a) * 0.4f, sin(a) * 0.4f, 0.13f)
                }
                for (k in 0 until 12) {
                    val a = (-PI / 2 + 2 * PI / 12 * k + PI / 12).toFloat()
                    dot(cos(a) * 0.82f, sin(a) * 0.82f, 0.09f)
                }
            }
            "streak7", "streak30", "streak100", "streak365" -> {
                val f = Path().apply {
                    fun m(x: Float, y: Float) = p(x, y)
                    val a = m(0.05f, -0.9f); moveTo(a.x, a.y)
                    val c1 = m(0.5f, -0.4f); val c2 = m(0.72f, 0f); val b = m(0.58f, 0.4f); cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
                    val c3 = m(0.48f, 0.74f); val c4 = m(0.22f, 0.86f); val d = m(0f, 0.86f); cubicTo(c3.x, c3.y, c4.x, c4.y, d.x, d.y)
                    val c5 = m(-0.26f, 0.86f); val c6 = m(-0.6f, 0.72f); val e = m(-0.62f, 0.32f); cubicTo(c5.x, c5.y, c6.x, c6.y, e.x, e.y)
                    val c7 = m(-0.66f, -0.04f); val c8 = m(-0.36f, -0.18f); val ff = m(-0.24f, -0.5f); cubicTo(c7.x, c7.y, c8.x, c8.y, ff.x, ff.y)
                    val c9 = m(-0.02f, -0.24f); val c10 = m(0.1f, -0.5f); cubicTo(c9.x, c9.y, c10.x, c10.y, a.x, a.y)
                    close()
                }
                ds.drawPath(f, ink)
                val inner = Path().apply {
                    val a = p(0.02f, -0.02f); moveTo(a.x, a.y)
                    val c1 = p(0.32f, 0.2f); val c2 = p(0.36f, 0.66f); val b = p(0f, 0.68f); cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
                    val c3 = p(-0.34f, 0.66f); val c4 = p(-0.3f, 0.26f); cubicTo(c3.x, c3.y, c4.x, c4.y, a.x, a.y)
                    close()
                }
                ds.drawPath(inner, bg)
            }
            "monolith" -> {
                ds.drawPath(path(-0.3f to 0.84f, -0.3f to -0.58f, 0.06f to -0.86f, 0.3f to -0.74f, 0.3f to 0.84f), ink)
                seg(-0.82f, 0.84f, 0.82f, 0.84f)
                seg(0.52f, -0.6f, 0.66f, -0.6f, sw * 0.7f)
                seg(0.59f, -0.67f, 0.59f, -0.53f, sw * 0.7f)
            }
            "anniversary" -> {
                ring(-0.3f, 0.14f, 0.48f)
                ring(0.3f, 0.14f, 0.48f)
                sparkle(0f, -0.72f, 0.22f)
            }
            "highfive" -> {
                ds.rotate(-16f, p(-0.38f, 0.5f)) {
                    box(-0.66f, -0.5f, -0.1f, 0.86f, 0.28f)
                    seg(-0.38f, -0.22f, -0.38f, 0.2f, sw * 0.5f)
                }
                ds.rotate(16f, p(0.38f, 0.5f)) {
                    box(0.1f, -0.5f, 0.66f, 0.86f, 0.28f)
                    seg(0.38f, -0.22f, 0.38f, 0.2f, sw * 0.5f)
                }
                seg(0f, -0.92f, 0f, -0.74f)
                seg(-0.3f, -0.84f, -0.2f, -0.68f)
                seg(0.3f, -0.84f, 0.2f, -0.68f)
            }
            "first_place" -> {
                ds.drawPath(path(-0.5f to -0.12f, 0.5f to -0.12f, 0f to 0.88f), ink)
                dot(0f, -0.3f, 0.56f)
                dot(0f, -0.3f, 0.22f, bg)
            }
            "explorer" -> {
                ring(0f, 0f, 0.82f)
                ds.rotate(35f, c) {
                    ds.drawPath(path(0f to -0.6f, 0.2f to 0f, -0.2f to 0f), ink)
                    ds.drawPath(path(0.2f to 0f, 0f to 0.6f, -0.2f to 0f), ink, style = thin)
                }
                dot(0f, 0f, 0.07f, bg)
            }
            "cartographer" -> {
                ds.drawPath(
                    path(-0.88f to -0.6f, -0.3f to -0.78f, 0.3f to -0.6f, 0.88f to -0.78f, 0.88f to 0.6f, 0.3f to 0.78f, -0.3f to 0.6f, -0.88f to 0.78f),
                    ink, style = line,
                )
                seg(-0.3f, -0.78f, -0.3f, 0.6f, sw * 0.55f)
                seg(0.3f, -0.6f, 0.3f, 0.78f, sw * 0.55f)
                listOf(-0.62f to 0.4f, -0.42f to 0.22f, -0.2f to 0.12f, 0.02f to 0.0f, 0.22f to -0.14f).forEach { (x, y) -> dot(x, y, 0.06f) }
                seg(0.4f, -0.4f, 0.62f, -0.18f, sw * 0.7f)
                seg(0.62f, -0.4f, 0.4f, -0.18f, sw * 0.7f)
            }
            "founder" -> {
                seg(-0.58f, -0.82f, -0.58f, 0.86f)
                ds.drawPath(path(-0.58f to -0.8f, 0.78f to -0.8f, 0.46f to -0.48f, 0.78f to -0.16f, -0.58f to -0.16f), ink)
                seg(-0.86f, 0.86f, -0.3f, 0.86f)
            }
            "keeper" -> {
                ring(-0.44f, -0.36f, 0.32f)
                seg(-0.22f, -0.14f, 0.7f, 0.78f)
                seg(0.46f, 0.54f, 0.66f, 0.34f)
                seg(0.24f, 0.32f, 0.42f, 0.14f)
            }
            "community" -> {
                fun shard(x: Float, y: Float, s: Float, filled: Boolean) {
                    val pth = path(x + 0.06f * s to y - 1f * s, x + 0.82f * s to y - 0.28f * s, x - 0.06f * s to y + 1f * s, x - 0.82f * s to y - 0.12f * s)
                    if (filled) ds.drawPath(pth, ink) else { ds.drawPath(pth, bg); ds.drawPath(pth, ink, style = line) }
                }
                shard(-0.42f, 0.3f, 0.44f, false)
                shard(0.42f, 0.3f, 0.44f, false)
                shard(0f, -0.3f, 0.5f, true)
            }
            "architect" -> {
                for (row in 0..2) for (col in 0..1) {
                    val x = -0.62f + col * 0.68f
                    val y = -0.86f + row * 0.6f
                    box(x, y, x + 0.56f, y + 0.48f, 0.12f, fill = if (row == 0 && col == 0) ink else null, stroke = if (row == 0 && col == 0) null else thin)
                }
            }
            "storyteller" -> {
                for (k in 0 until 8) arc(0f, 0f, 0.78f, -90f + k * 45f + 6f, 33f)
                dot(0f, 0f, 0.42f)
                dot(0.0f, 0.0f, 0.14f, bg)
            }
            "narrator" -> {
                box(-0.36f, -0.86f, 0.66f, 0.46f, 0.18f, stroke = thin)
                box(-0.66f, -0.58f, 0.38f, 0.86f, 0.18f, fill = bg)
                seg(-0.42f, -0.24f, 0.12f, -0.24f, sw * 0.6f)
                seg(-0.42f, 0.04f, 0.14f, 0.04f, sw * 0.6f)
                seg(-0.42f, 0.32f, -0.06f, 0.32f, sw * 0.6f)
            }
            "artist" -> {
                val w = Path().apply {
                    val a = p(-0.82f, 0.36f); moveTo(a.x, a.y)
                    val c1 = p(-0.5f, -0.6f); val c2 = p(-0.1f, 1.0f); val b = p(0.2f, 0.04f); cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
                    val c3 = p(0.38f, -0.5f); val c4 = p(0.6f, -0.4f); val d = p(0.84f, -0.7f); cubicTo(c3.x, c3.y, c4.x, c4.y, d.x, d.y)
                }
                ds.drawPath(w, ink, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))
                sparkle(0.56f, 0.5f, 0.24f)
                dot(-0.6f, -0.62f, 0.09f)
            }
            "collector" -> {
                ds.drawPath(path(-0.76f to -0.76f, 0.76f to -0.76f, 0.76f to 0.22f, 0.22f to 0.76f, -0.76f to 0.76f), ink, style = line)
                ds.drawPath(path(0.76f to 0.22f, 0.22f to 0.22f, 0.22f to 0.76f), ink)
                dot(-0.32f, -0.26f, 0.08f)
                dot(0.12f, -0.26f, 0.08f)
                arc(-0.1f, -0.06f, 0.3f, 20f, 140f, thin)
            }
            "winner" -> {
                for (k in listOf(-0.28f, 0.28f)) {
                    seg(k, -0.82f, k, 0.82f, sw * 0.5f)
                    seg(-0.82f, k, 0.82f, k, sw * 0.5f)
                }
                seg(-0.72f, -0.72f, 0.72f, 0.72f, sw * 1.2f)
                ring(0.56f, -0.56f, 0.16f, thin)
                ring(-0.56f, 0.56f, 0.16f, thin)
            }
            "champion" -> {
                ds.drawPath(path(-0.82f to 0.5f, -0.82f to -0.42f, -0.4f to 0.02f, 0f to -0.72f, 0.4f to 0.02f, 0.82f to -0.42f, 0.82f to 0.5f), ink)
                seg(-0.82f, 0.76f, 0.82f, 0.76f)
                dot(0f, 0.18f, 0.12f, bg)
            }
            "organizer" -> {
                box(-0.82f, -0.62f, 0.82f, 0.82f, 0.18f)
                seg(-0.82f, -0.22f, 0.82f, -0.22f, sw * 0.7f)
                seg(-0.4f, -0.86f, -0.4f, -0.48f)
                seg(0.4f, -0.86f, 0.4f, -0.48f)
                for (row in 0..1) for (col in 0..2) {
                    val x = -0.44f + col * 0.44f
                    val y = 0.12f + row * 0.36f
                    if (row == 1 && col == 2) dot(x, y, 0.12f) else dot(x, y, 0.07f)
                }
            }
            "done" -> {
                ring(0f, 0f, 0.8f)
                ds.drawPath(path(-0.4f to 0.02f, -0.1f to 0.32f, 0.44f to -0.3f, close = false), ink, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "habit7" -> {
                arc(0f, 0f, 0.66f, -50f, 300f)
                val a = Math.toRadians(250.0).toFloat()
                val e = Offset(cos(a) * 0.66f, sin(a) * 0.66f)
                ds.drawPath(path(e.x + 0.06f to e.y - 0.3f, e.x + 0.3f to e.y + 0.08f, e.x - 0.16f to e.y + 0.14f), ink)
                dot(0f, 0f, 0.14f)
            }
            "habit30" -> {
                box(-0.92f, -0.26f, -0.06f, 0.26f, 0.26f)
                box(-0.42f, -0.26f, 0.42f, 0.26f, 0.26f)
                box(0.06f, -0.26f, 0.92f, 0.26f, 0.26f)
            }
            "accountant" -> {
                seg(0f, -0.76f, 0f, 0.72f)
                seg(-0.42f, 0.8f, 0.42f, 0.8f)
                seg(-0.78f, -0.52f, 0.78f, -0.52f)
                dot(0f, -0.76f, 0.1f)
                for (s in listOf(-1f, 1f)) {
                    seg(0.62f * s, -0.52f, 0.36f * s, 0.06f, sw * 0.5f)
                    seg(0.62f * s, -0.52f, 0.88f * s, 0.06f, sw * 0.5f)
                    ds.drawArc(ink, 0f, 180f, true, p(0.62f * s - 0.28f, 0.06f - 0.22f), Size(r(0.56f), r(0.44f)))
                }
            }
            "weekly" -> {
                val pivot = p(0f, 0.9f)
                ds.rotate(-16f, pivot) { box(-0.4f, -0.8f, 0.4f, 0.62f, 0.16f, stroke = thin) }
                ds.rotate(16f, pivot) { box(-0.4f, -0.8f, 0.4f, 0.62f, 0.16f, stroke = thin) }
                box(-0.42f, -0.84f, 0.42f, 0.62f, 0.16f, fill = bg)
                sparkle(0f, -0.16f, 0.32f)
                seg(-0.22f, 0.36f, 0.22f, 0.36f, sw * 0.6f)
            }
            "pioneer" -> {
                sparkle(-0.12f, 0.06f, 0.82f)
                sparkle(0.62f, -0.6f, 0.26f)
                dot(0.62f, 0.6f, 0.08f)
            }
            else -> {
                sparkle(0f, 0f, 0.8f)
            }
        }
    }
}
