package com.monolith.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.monolith.app.net.BadgeDef
import com.monolith.app.net.BadgeFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun numberFor(id: String): String? = when (id) {
    "streak7" -> "7"
    "streak30" -> "30"
    "streak100" -> "100"
    "streak365" -> "365"
    else -> null
}

/** the outline of each family of badges, in a 0..1 square */
private fun framePath(f: BadgeFrame, w: Float): Path = Path().apply {
    fun p(x: Float, y: Float) = Offset(x * w, y * w)
    when (f) {
        BadgeFrame.HEX -> {
            for (i in 0 until 6) {
                val a = (PI / 3 * i - PI / 2).toFloat()
                val o = p(0.5f + 0.47f * cos(a), 0.5f + 0.47f * sin(a))
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            close()
        }
        BadgeFrame.CIRCLE -> addOval(androidx.compose.ui.geometry.Rect(w * 0.04f, w * 0.04f, w * 0.96f, w * 0.96f))
        BadgeFrame.DIAMOND -> { moveTo(w * 0.5f, w * 0.02f); lineTo(w * 0.98f, w * 0.5f); lineTo(w * 0.5f, w * 0.98f); lineTo(w * 0.02f, w * 0.5f); close() }
        BadgeFrame.SHIELD -> {
            moveTo(w * 0.5f, w * 0.03f); lineTo(w * 0.9f, w * 0.17f); lineTo(w * 0.86f, w * 0.58f)
            quadraticBezierTo(w * 0.78f, w * 0.84f, w * 0.5f, w * 0.98f); quadraticBezierTo(w * 0.22f, w * 0.84f, w * 0.14f, w * 0.58f)
            lineTo(w * 0.1f, w * 0.17f); close()
        }
        BadgeFrame.SHARD -> { moveTo(w * 0.53f, w * 0.02f); lineTo(w * 0.94f, w * 0.38f); lineTo(w * 0.47f, w * 0.98f); lineTo(w * 0.06f, w * 0.44f); close() }
        BadgeFrame.SQUARE -> addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.06f, w * 0.06f, w * 0.94f, w * 0.94f, androidx.compose.ui.geometry.CornerRadius(w * 0.22f)))
    }
}

/** a badge: frame by family, weight by tier, symbol drawn by hand; [earned] false draws it faded and dashed */
@Composable
fun BadgeEmblem(def: BadgeDef, earned: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val alpha = if (earned) 1f else 0.26f
    val solid = earned && def.tier >= 3
    val number = numberFor(def.id)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension
            val path = framePath(def.frame, w)
            val fill: Color
            if (solid) {
                for (i in 0 until 16) {
                    val a = (2 * PI / 16 * i).toFloat()
                    val c = Offset(w / 2, w / 2)
                    drawLine(ink.copy(alpha = 0.35f), c + Offset(cos(a), sin(a)) * (w * 0.5f), c + Offset(cos(a), sin(a)) * (w * 0.56f), w * 0.02f, StrokeCap.Round)
                }
                drawPath(path, ink)
                fill = ink
            } else {
                drawPath(path, paper)
                if (def.tier >= 2 && earned) drawPath(path, ink.copy(alpha = 0.08f))
                drawPath(
                    path, ink.copy(alpha = alpha),
                    style = Stroke(w * 0.045f, pathEffect = if (earned) null else PathEffect.dashPathEffect(floatArrayOf(w * 0.06f, w * 0.05f))),
                )
                if (def.tier >= 2 && earned) {
                    val inner = framePath(def.frame, w * 0.8f)
                    val d = w * 0.1f
                    inner.translate(Offset(d, d))
                    drawPath(inner, ink.copy(alpha = 0.5f), style = Stroke(w * 0.016f))
                }
                fill = if (def.tier >= 2 && earned) lerpColor(paper, ink, 0.08f) else paper
            }
            val glyphInk = if (solid) paper else ink.copy(alpha = alpha)
            val cy = if (number != null) w * 0.44f else w * 0.5f
            val box = if (def.frame == BadgeFrame.DIAMOND || def.frame == BadgeFrame.SHARD) w * 0.34f else w * 0.4f
            badgeGlyph(def.id, Offset(w / 2, cy), box, glyphInk, fill)
        }
        number?.let { n ->
            Text(
                n, color = if (solid) paper else ink.copy(alpha = alpha), fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.12f).sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.15f),
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
)
