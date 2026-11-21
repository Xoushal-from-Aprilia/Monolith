package com.monolith.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.monolith.app.Graph
import com.monolith.app.MainActivity
import com.monolith.app.core.Fmt
import com.monolith.app.core.t
import com.monolith.app.service.Notifier
import com.monolith.app.ui.screens.Countdowns

private data class Cd(val id: String, val emoji: String, val title: String, val days: Int, val occ: Long)

/** Home screen widget: the days left to the next event with a countdown */
class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val next = runCatching {
            Countdowns.upcoming().firstOrNull()?.let { (e, occ) -> Cd(e.id, e.emoji, e.title, Countdowns.daysTo(occ), occ) }
        }.getOrNull()
        provideContent { Content(next) }
    }
}

private val BG = ColorProvider(Color(0xFF0A0A0A))
private val WHITE = ColorProvider(Color(0xFFF2F2F2))
private val GREY = ColorProvider(Color(0xFF8A8A8A))

@Composable
private fun Content(c: Cd?) {
    val ctx = LocalContext.current
    val open = Intent(ctx, MainActivity::class.java)
        .setData(Uri.parse("monolith://countdown/" + (c?.id ?: "")))
        .putExtra(Notifier.EXTRA_TAB, 0)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (c != null) open.putExtra(Notifier.EXTRA_EVENT, c.id)
    Column(
        GlanceModifier.fillMaxSize().background(BG).cornerRadius(24.dp).padding(16.dp).clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c == null) {
            Text("MONOLITH", style = TextStyle(color = GREY, fontSize = 10.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(6.dp))
            Text(t("Nessun conto alla rovescia"), style = TextStyle(color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(t("Attivalo su un evento in Agenda"), style = TextStyle(color = GREY, fontSize = 11.sp))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, style = TextStyle(fontSize = 16.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(c.title, maxLines = 1, style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(4.dp))
        if (c.days >= 2) {
            Text(c.days.toString(), style = TextStyle(color = WHITE, fontSize = 44.sp, fontWeight = FontWeight.Bold))
            Text(t("giorni") + " · " + Fmt.dayShort(c.occ), style = TextStyle(color = GREY, fontSize = 11.sp))
        } else {
            Text(Countdowns.label(c.days), style = TextStyle(color = WHITE, fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text(Fmt.dayShort(c.occ), style = TextStyle(color = GREY, fontSize = 11.sp))
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
