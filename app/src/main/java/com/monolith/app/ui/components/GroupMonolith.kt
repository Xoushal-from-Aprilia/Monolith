package com.monolith.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.Message
import java.util.TimeZone

/**
 * The monolith of a group: a stone that grows with the life of the group. It is worked out on each phone
 * from the last 60 days of the chat (messages, calls, places and plans, the days you talked, how many of
 * you talk), so it needs nothing new on the wire. It glows when the group is lively this week and it
 * cracks when the group goes quiet: one crack after a week of silence, two after two weeks, three after a month.
 */
object GroupStone {
    data class Stats(
        val msgs: Int = 0,
        val calls: Int = 0,
        val places: Int = 0,
        val days: Int = 0,
        val voices: Int = 0,
        val lastAt: Long = 0,
        /** messages in the last 7 days */
        val week: Int = 0,
    )

    /** score needed for each level */
    val LEVELS = listOf(0 to "Sasso" /*i18n*/, 40 to "Ciottolo" /*i18n*/, 120 to "Pietra" /*i18n*/, 300 to "Masso" /*i18n*/, 600 to "Menhir" /*i18n*/, 1000 to "Obelisco" /*i18n*/, 1600 to "Monolite" /*i18n*/)

    private const val DAY = 86_400_000L

    fun stats(list: List<Message>, at: Long = now()): Stats {
        val from = at - 60 * DAY
        val recent = list.filter { it.ts >= from && it.kind != "system" && it.kind != "deleted" }
        if (recent.isEmpty()) return Stats(lastAt = list.filter { it.kind != "system" }.maxOfOrNull { it.ts } ?: 0L)
        val tz = TimeZone.getDefault()
        return Stats(
            msgs = recent.count { it.kind != "call" },
            calls = recent.count { it.kind == "call" },
            places = recent.count { it.kind == "loc" || it.kind == "event" || it.kind == "place" },
            days = recent.map { (it.ts + tz.getOffset(it.ts)) / DAY }.distinct().size,
            voices = recent.map { it.sender }.distinct().size,
            lastAt = recent.maxOf { it.ts },
            week = recent.count { it.ts >= at - 7 * DAY && it.kind != "call" },
        )
    }

    fun score(s: Stats) = s.msgs.coerceAtMost(900) + s.calls * 8 + s.places * 5 + s.days * 6 + s.voices.coerceAtMost(30) * 4

    fun level(score: Int) = LEVELS.indexOfLast { score >= it.first }.coerceAtLeast(0)

    /** 0..1: how lively the group is this week */
    fun glow(s: Stats) = (s.week / 60f).coerceIn(0f, 1f)

    /** cracks from silence */
    fun cracks(s: Stats, at: Long = now()): Int {
        if (s.lastAt == 0L) return 0
        val quiet = (at - s.lastAt) / DAY
        return when { quiet >= 30 -> 3; quiet >= 14 -> 2; quiet >= 7 -> 1; else -> 0 }
    }
}

/** the stone itself, drawn: its shape changes with the level */
@Composable
fun MonolithFigure(level: Int, glow: Float, cracks: Int, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    val inf = rememberInfiniteTransition(label = "stone")
    val pulse by inf.animateFloat(0.75f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val stone = if (level >= 6) Color(0xFF101012) else lerpGray(level)
    val edge = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = Offset(w / 2, h * 0.9f)
        // the glow behind it
        if (glow > 0.02f && cracks == 0) {
            val r = h * (0.35f + 0.25f * glow) * pulse
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * glow), Color.Transparent), Offset(w / 2, h * 0.55f), r), r, Offset(w / 2, h * 0.55f))
        }
        // the shadow on the ground
        drawOval(edge.copy(alpha = 0.12f), Offset(w / 2 - w * 0.3f, base.y - h * 0.03f), Size(w * 0.6f, h * 0.07f))
        val shape = stonePath(level, base, w, h)
        drawPath(shape, stone)
        // light on one side
        drawPath(shape, Brush.horizontalGradient(listOf(Color.White.copy(alpha = if (level >= 6) 0.18f else 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.18f))))
        drawPath(shape, edge.copy(alpha = if (level >= 6) 0.55f else 0.25f), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        drawCracks(level, cracks, base, w, h)
    }
}

private fun lerpGray(level: Int): Color {
    val v = 0.62f - level * 0.07f
    return Color(v, v * 0.98f, v * 0.95f)
}

/** the outline of the stone for each level, standing on [base] */
private fun stonePath(level: Int, base: Offset, w: Float, h: Float): Path = Path().apply {
    val x = base.x
    val y = base.y
    when (level) {
        0 -> addOval(androidx.compose.ui.geometry.Rect(x - w * 0.17f, y - h * 0.2f, x + w * 0.17f, y))
        1 -> {
            moveTo(x - w * 0.2f, y)
            cubicTo(x - w * 0.24f, y - h * 0.2f, x - w * 0.08f, y - h * 0.32f, x + w * 0.04f, y - h * 0.3f)
            cubicTo(x + w * 0.2f, y - h * 0.28f, x + w * 0.24f, y - h * 0.1f, x + w * 0.2f, y)
            close()
        }
        2 -> {
            moveTo(x - w * 0.22f, y)
            lineTo(x - w * 0.24f, y - h * 0.22f)
            lineTo(x - w * 0.1f, y - h * 0.42f)
            lineTo(x + w * 0.12f, y - h * 0.4f)
            lineTo(x + w * 0.24f, y - h * 0.18f)
            lineTo(x + w * 0.21f, y)
            close()
        }
        3 -> {
            moveTo(x - w * 0.3f, y)
            lineTo(x - w * 0.32f, y - h * 0.3f)
            lineTo(x - w * 0.16f, y - h * 0.55f)
            lineTo(x + w * 0.1f, y - h * 0.58f)
            lineTo(x + w * 0.3f, y - h * 0.36f)
            lineTo(x + w * 0.3f, y)
            close()
        }
        4 -> {
            moveTo(x - w * 0.14f, y)
            lineTo(x - w * 0.16f, y - h * 0.55f)
            cubicTo(x - w * 0.15f, y - h * 0.78f, x + w * 0.12f, y - h * 0.8f, x + w * 0.14f, y - h * 0.6f)
            lineTo(x + w * 0.15f, y)
            close()
        }
        5 -> {
            moveTo(x - w * 0.13f, y)
            lineTo(x - w * 0.09f, y - h * 0.72f)
            lineTo(x, y - h * 0.84f)
            lineTo(x + w * 0.09f, y - h * 0.72f)
            lineTo(x + w * 0.13f, y)
            close()
        }
        else -> addRect(androidx.compose.ui.geometry.Rect(x - w * 0.12f, y - h * 0.84f, x + w * 0.12f, y))
    }
}

/** how tall the stone is, to place the cracks on it */
private fun stoneTop(level: Int) = listOf(0.2f, 0.3f, 0.42f, 0.56f, 0.75f, 0.8f, 0.84f)[level.coerceIn(0, 6)]

private fun DrawScope.drawCracks(level: Int, cracks: Int, base: Offset, w: Float, h: Float) {
    if (cracks <= 0) return
    val top = base.y - h * stoneTop(level)
    val half = w * (if (level >= 4) 0.1f else 0.14f)
    val ink = Color.Black.copy(alpha = 0.75f)
    val starts = listOf(Offset(base.x - half * 0.2f, top + h * 0.03f), Offset(base.x + half * 0.8f, top + (base.y - top) * 0.35f), Offset(base.x - half * 0.9f, top + (base.y - top) * 0.6f))
    for (k in 0 until cracks.coerceAtMost(3)) {
        val s = starts[k]
        val dir = if (k == 1) -1f else 1f
        val p = Path().apply {
            moveTo(s.x, s.y)
            val step = (base.y - top) * 0.09f
            var cx = s.x
            var cy = s.y
            for (i in 1..4) {
                cx += dir * half * (if (i % 2 == 0) 0.18f else -0.12f) + dir * half * 0.08f
                cy += step
                lineTo(cx, cy)
            }
        }
        drawPath(p, ink, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Miter))
    }
}

/** the card in the group's info */
@Composable
fun GroupMonolithCard(conv: String) {
    val list by remember(conv) { Graph.db.messages().conversation(conv, 5000) }.collectAsState(initial = emptyList())
    val s = remember(list) { GroupStone.stats(list) }
    val score = GroupStone.score(s)
    val level = GroupStone.level(score)
    val cracks = GroupStone.cracks(s)
    val glow = GroupStone.glow(s)
    val next = GroupStone.LEVELS.getOrNull(level + 1)
    val from = GroupStone.LEVELS[level].first
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonolithFigure(level, glow, cracks, Modifier.size(width = 92.dp, height = 110.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Il monolite del gruppo"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t(GroupStone.LEVELS[level].second) + " · " + t("livello {0} di 7", level + 1), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    cracks >= 3 -> t("Un mese di silenzio: si sta spaccando. Basta un messaggio per ripararlo.")
                    cracks == 2 -> t("Due settimane di silenzio: le crepe si allargano.")
                    cracks == 1 -> t("Una settimana di silenzio: è comparsa una crepa.")
                    glow > 0.5f -> t("Brilla: questa settimana siete vivissimi.")
                    next == null -> t("Più in alto di così non si va.")
                    else -> t("Cresce con i messaggi, le chiamate, i posti e i giorni passati insieme.")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (next != null) {
                val f = ((score - from).toFloat() / (next.first - from)).coerceIn(0f, 1f)
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                Text(t("Verso {0}", t(next.second)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                t("{0} messaggi · {1} chiamate · {2} giorni insieme", s.msgs, s.calls, s.days),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
