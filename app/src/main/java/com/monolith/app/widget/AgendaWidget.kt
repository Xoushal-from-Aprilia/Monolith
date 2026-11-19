package com.monolith.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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
import com.monolith.app.core.Recur
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.InviteState

private data class WidgetItem(val emoji: String, val title: String, val whenText: String)

/** Home screen widget: today's date and the next events, black and white. */
class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = runCatching {
            val events = Graph.db.agenda().allNow().filter { it.inviteState != InviteState.DECLINED && it.inviteState != InviteState.PENDING }
            Recur.upcoming(events, Fmt.startOfDay(now()), 5).map { (e, occ) ->
                val day = when {
                    Fmt.sameDay(occ, now()) -> t("Oggi")
                    Fmt.sameDay(occ, now() + 86_400_000L) -> t("Domani")
                    else -> Fmt.dayShort(occ)
                }
                WidgetItem(e.emoji, e.title, if (e.allDay) day else day + " · " + Fmt.time(occ))
            }
        }.getOrDefault(emptyList())
        val today = Fmt.dayLong(now())
        provideContent { Content(today, items) }
    }
}

private val INK = ColorProvider(Color(0xFF0A0A0A))
private val WHITE = ColorProvider(Color(0xFFF2F2F2))
private val GREY = ColorProvider(Color(0xFF8A8A8A))

@Composable
private fun Content(today: String, items: List<WidgetItem>) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(INK).cornerRadius(24.dp).padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text("MONOLITH", style = TextStyle(color = GREY, fontSize = 10.sp, fontWeight = FontWeight.Medium))
        Text(today, style = TextStyle(color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(10.dp))
        if (items.isEmpty()) {
            Text(t("Nessun evento in programma"), style = TextStyle(color = GREY, fontSize = 13.sp))
        }
        items.forEach { item ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.emoji, style = TextStyle(fontSize = 18.sp))
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(item.title, maxLines = 1, style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text(item.whenText, maxLines = 1, style = TextStyle(color = GREY, fontSize = 11.sp))
                }
            }
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
