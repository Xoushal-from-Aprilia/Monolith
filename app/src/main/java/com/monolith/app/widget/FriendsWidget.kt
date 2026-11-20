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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.monolith.app.Graph
import com.monolith.app.MainActivity
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.service.Notifier

private data class FriendItem(val pk: String, val emoji: String, val name: String, val line: String, val unread: Int)

/** Home screen widget: the friends you talk to the most, with their status or where they are. */
class FriendsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = runCatching { load() }.getOrDefault(emptyList())
        provideContent { Content(items) }
    }

    private suspend fun load(): List<FriendItem> {
        if (Graph.identity.keys == null) return emptyList()
        val friends = Graph.db.friends().friendsNow()
        val last = Graph.db.messages().lastActivity().associate { it.convId to it.ts }
        val unread = Graph.db.messages().unreadNow().associate { it.convId to it.n }
        val statuses = Graph.statuses.friends.value
        val places = Graph.places.all.value
        val at = now()
        return friends
            .sortedWith(compareByDescending<com.monolith.app.data.Friend> { last[it.pubkey] ?: 0L }.thenBy { it.displayName.lowercase() })
            .take(6)
            .map { f ->
                val st = statuses[f.pubkey]?.takeIf { it.active }
                val here = places.firstOrNull { p -> p.here[f.pubkey]?.let { it.inside && at - it.at < 12 * 3_600_000L } == true }
                val line = when {
                    st != null -> (st.emoji + " " + st.text).trim()
                    here != null -> here.emoji + " " + t("È a {0}", here.name)
                    f.sharingUntil > at && f.distM >= 0 -> "📡 " + t("A {0} da te", Fmt.distance(f.distM.toFloat()))
                    last[f.pubkey] != null -> t("Ultimo messaggio {0}", Fmt.ago(last[f.pubkey] ?: 0L))
                    else -> "@" + f.username
                }
                FriendItem(f.pubkey, f.emoji, f.displayName, line, unread[f.pubkey] ?: 0)
            }
    }
}

private val INK = ColorProvider(Color(0xFF0A0A0A))
private val CHIP = ColorProvider(Color(0xFF1C1C1C))
private val WHITE = ColorProvider(Color(0xFFF2F2F2))
private val GREY = ColorProvider(Color(0xFF8A8A8A))

@Composable
private fun Content(items: List<FriendItem>) {
    val ctx = LocalContext.current
    val height = LocalSize.current.height.value
    val fit = ((height - 54f) / 48f).toInt().coerceIn(1, 6)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(INK).cornerRadius(24.dp).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(Intent(ctx, MainActivity::class.java).putExtra(Notifier.EXTRA_TAB, 3))), verticalAlignment = Alignment.CenterVertically) {
            Text("MONOLITH", style = TextStyle(color = GREY, fontSize = 10.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(8.dp))
            Text(t("Amici"), style = TextStyle(color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (items.isEmpty()) {
            Text(t("Aggiungi amici per vederli qui"), style = TextStyle(color = GREY, fontSize = 13.sp))
        }
        items.take(fit).forEach { f ->
            val open = Intent(ctx, MainActivity::class.java)
                .setData(Uri.parse("monolith://chat/" + f.pk))
                .putExtra(Notifier.EXTRA_CONV, f.pk)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(open)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.size(38.dp).background(CHIP).cornerRadius(19.dp), contentAlignment = Alignment.Center) {
                    Text(f.emoji, style = TextStyle(fontSize = 18.sp))
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(f.name, maxLines = 1, style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text(f.line, maxLines = 1, style = TextStyle(color = GREY, fontSize = 11.sp))
                }
                if (f.unread > 0) {
                    Spacer(GlanceModifier.width(6.dp))
                    Box(GlanceModifier.size(22.dp).background(WHITE).cornerRadius(11.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (f.unread > 9) "9+" else f.unread.toString(),
                            style = TextStyle(color = INK, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}

class FriendsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FriendsWidget()
}
