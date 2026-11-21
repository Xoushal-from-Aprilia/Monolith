package com.monolith.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
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

private fun iconFor(id: String): ImageVector = when (id) {
    "first_word" -> Icons.Rounded.ChatBubble
    "chatter" -> Icons.Rounded.Forum
    "novelist" -> Icons.Rounded.MenuBook
    "night_owl" -> Icons.Rounded.Bedtime
    "early_bird" -> Icons.Rounded.WbTwilight
    "photographer" -> Icons.Rounded.PhotoCamera
    "first_call" -> Icons.Rounded.Call
    "marathon" -> Icons.Rounded.Timer
    "friends5" -> Icons.Rounded.Group
    "friends20" -> Icons.Rounded.Groups
    "streak7", "streak30", "streak100", "streak365" -> Icons.Rounded.LocalFireDepartment
    "monolith", "pioneer" -> if (id == "pioneer") Icons.Rounded.RocketLaunch else ShardIcons.Filled
    "anniversary" -> Icons.Rounded.Cake
    "highfive" -> Icons.Rounded.PanTool
    "first_place" -> Icons.Rounded.Place
    "explorer" -> Icons.Rounded.Explore
    "cartographer" -> Icons.Rounded.Map
    "founder" -> Icons.Rounded.Flag
    "keeper" -> Icons.Rounded.Shield
    "community" -> Icons.Rounded.Diversity3
    "architect" -> Icons.Rounded.Architecture
    "storyteller" -> Icons.Rounded.AutoStories
    "narrator" -> Icons.Rounded.HistoryEdu
    "artist" -> Icons.Rounded.Brush
    "collector" -> Icons.Rounded.EmojiEmotions
    "winner" -> Icons.Rounded.EmojiEvents
    "champion" -> Icons.Rounded.MilitaryTech
    "organizer" -> Icons.Rounded.Event
    "done" -> Icons.Rounded.TaskAlt
    "habit7" -> Icons.Rounded.Repeat
    "habit30" -> Icons.Rounded.SelfImprovement
    "accountant" -> Icons.Rounded.Payments
    "weekly" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.EmojiEvents
}

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

/** a badge: frame by family, weight by tier; [earned] false draws it faded and dashed */
@Composable
fun BadgeEmblem(def: BadgeDef, earned: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val alpha = if (earned) 1f else 0.22f
    val solid = earned && def.tier >= 3
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension
            val path = framePath(def.frame, w)
            if (solid) {
                for (i in 0 until 16) {
                    val a = (2 * PI / 16 * i).toFloat()
                    val c = Offset(w / 2, w / 2)
                    drawLine(ink.copy(alpha = 0.35f), c + Offset(cos(a), sin(a)) * (w * 0.5f), c + Offset(cos(a), sin(a)) * (w * 0.56f), w * 0.02f, StrokeCap.Round)
                }
                drawPath(path, ink)
            } else {
                if (def.tier >= 2 && earned) drawPath(path, ink.copy(alpha = 0.1f))
                drawPath(
                    path, ink.copy(alpha = alpha),
                    style = Stroke(w * 0.045f, pathEffect = if (earned) null else PathEffect.dashPathEffect(floatArrayOf(w * 0.06f, w * 0.05f))),
                )
                if (def.tier >= 2 && earned) {
                    val inner = framePath(def.frame, w * 0.78f)
                    val d = w * 0.11f
                    inner.translate(Offset(d, d))
                    drawPath(inner, ink.copy(alpha = 0.6f), style = Stroke(w * 0.02f))
                }
            }
        }
        Icon(
            iconFor(def.id), null, Modifier.size(size * 0.4f).padding(bottom = if (numberFor(def.id) != null) size * 0.06f else size * 0f),
            tint = if (solid) paper else ink.copy(alpha = alpha),
        )
        numberFor(def.id)?.let { n ->
            Text(
                n, color = if (solid) paper else ink.copy(alpha = alpha), fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.14f).sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.16f),
            )
        }
    }
}
