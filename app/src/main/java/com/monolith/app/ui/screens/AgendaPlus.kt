package com.monolith.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.navigation.NavHostController
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.I18n
import com.monolith.app.core.Recur
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.InviteState
import com.monolith.app.ui.Routes
import com.monolith.app.ui.components.MonoDialog
import com.monolith.app.ui.components.bounceClick
import com.monolith.app.widget.CountdownWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

// ====================================================================== countdowns

/** events with a countdown: kept on this phone */
object Countdowns {
    private val sp by lazy { Graph.app.getSharedPreferences("countdowns", Context.MODE_PRIVATE) }
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    private var loaded = false

    fun flow(): StateFlow<Set<String>> {
        if (!loaded) { loaded = true; _ids.value = sp.getStringSet("ids", emptySet())?.toSet() ?: emptySet() }
        return _ids.asStateFlow()
    }

    fun toggle(id: String) {
        flow()
        val next = if (id in _ids.value) _ids.value - id else _ids.value + id
        sp.edit().putStringSet("ids", next).apply()
        _ids.value = next
        Graph.scope.launch { runCatching { CountdownWidget().updateAll(Graph.app) } }
    }

    fun clear() {
        sp.edit().clear().apply()
        _ids.value = emptySet()
    }

    /** the next occurrence of each countdown event, soonest first */
    suspend fun upcoming(): List<Pair<AgendaEvent, Long>> {
        val ids = flow().value
        if (ids.isEmpty()) return emptyList()
        val today = Fmt.startOfDay(now())
        return Graph.db.agenda().allNow().filter { it.id in ids && it.inviteState != InviteState.DECLINED }
            .mapNotNull { e -> Recur.upcoming(listOf(e), today, 1).firstOrNull()?.let { e to it.second } }
            .sortedBy { it.second }
    }

    /** whole days from today to the day of [ts] */
    fun daysTo(ts: Long): Int {
        val a = Calendar.getInstance().apply { timeInMillis = Fmt.startOfDay(now()) }
        val b = Calendar.getInstance().apply { timeInMillis = Fmt.startOfDay(ts) }
        var n = 0
        while (a.before(b) && n < 4000) { a.add(Calendar.DAY_OF_YEAR, 1); n++ }
        return n
    }

    fun label(days: Int): String = when (days) {
        0 -> t("Oggi")
        1 -> t("Domani")
        else -> t("tra {0} giorni", days)
    }
}

@Composable
fun CountdownStrip(nav: NavHostController) {
    val ids by remember { Countdowns.flow() }.collectAsState()
    val events by remember { Graph.db.agenda().all() }.collectAsState(initial = emptyList())
    val list by produceState(emptyList<Pair<AgendaEvent, Long>>(), ids, events) { value = Countdowns.upcoming() }
    if (list.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.first.id }) { (e, occ) ->
            val d = Countdowns.daysTo(occ)
            Column(
                Modifier.width(150.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary)
                    .bounceClick(0.95f) { nav.navigate(Routes.event(e.id, occ)) }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(e.title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                if (d >= 2) {
                    Text(d.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp)
                    Text(t("giorni"), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(Countdowns.label(d), color = MaterialTheme.colorScheme.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(Fmt.dayShort(occ), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ====================================================================== weather (free and keyless)

data class Forecast(val code: Int, val max: Double, val min: Double, val rain: Int, val hourTemp: Double?, val hourCode: Int?)
data class GeoPlace(val name: String, val lat: Double, val lon: Double)

object Weather {
    private val cache = HashMap<String, Pair<Long, JSONObject>>()
    private val sp by lazy { Graph.app.getSharedPreferences("event_places", Context.MODE_PRIVATE) }

    fun emoji(code: Int) = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "🌨️"
        in 80..82 -> "🌦️"
        85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun label(code: Int) = when (code) {
        0 -> t("Sereno")
        1, 2 -> t("Poco nuvoloso")
        3 -> t("Nuvoloso")
        45, 48 -> t("Nebbia")
        in 51..57 -> t("Pioggerella")
        in 61..67 -> t("Pioggia")
        in 71..77 -> t("Neve")
        in 80..82 -> t("Rovesci")
        85, 86 -> t("Rovesci di neve")
        in 95..99 -> t("Temporale")
        else -> ""
    }

    private fun get(url: String): JSONObject? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Monolith (Android)")
        c.inputStream.bufferedReader().use { JSONObject(it.readText()) }.also { c.disconnect() }
    }.getOrNull()

    fun placeFor(eventId: String): GeoPlace? = sp.getString(eventId, null)?.let { s ->
        runCatching { JSONObject(s).let { GeoPlace(it.getString("n"), it.getDouble("a"), it.getDouble("o")) } }.getOrNull()
    }

    fun setPlace(eventId: String, p: GeoPlace?) {
        if (p == null) sp.edit().remove(eventId).apply()
        else sp.edit().putString(eventId, JSONObject().put("n", p.name).put("a", p.lat).put("o", p.lon).toString()).apply()
    }

    suspend fun search(q: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        val lang = I18n.locale.language
        val o = get("https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&language=$lang&name=" + URLEncoder.encode(q.trim(), "UTF-8"))
            ?: return@withContext emptyList()
        val a = o.optJSONArray("results") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { r ->
                val extra = listOf(r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                GeoPlace(r.optString("name") + if (extra.isNotBlank()) " · $extra" else "", r.optDouble("latitude"), r.optDouble("longitude"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun here(ctx: Context): GeoPlace? {
        Graph.location.me.value?.let { return GeoPlace(t("Qui"), it.latitude, it.longitude) }
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val l = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return null
        return GeoPlace(t("Qui"), l.latitude, l.longitude)
    }

    /** forecast for the day of [ts] (and its hour, if [withHour]); null when too far or offline */
    suspend fun forecast(p: GeoPlace, ts: Long, withHour: Boolean): Forecast? = withContext(Dispatchers.IO) {
        val days = Countdowns.daysTo(ts)
        if (ts < Fmt.startOfDay(now()) || days > 15) return@withContext null
        val key = "%.2f,%.2f".format(java.util.Locale.US, p.lat, p.lon)
        val cached = cache[key]?.takeIf { now() - it.first < 3 * 3_600_000L }?.second
        val o = cached ?: get(
            "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(java.util.Locale.US, p.lat)}&longitude=${"%.4f".format(java.util.Locale.US, p.lon)}" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&hourly=temperature_2m,weather_code&timezone=auto&forecast_days=16",
        )?.also { cache[key] = now() to it } ?: return@withContext null
        val daily = o.optJSONObject("daily") ?: return@withContext null
        val dayStr = Calendar.getInstance().apply { timeInMillis = ts }.let { "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
        val times = daily.optJSONArray("time") ?: return@withContext null
        val i = (0 until times.length()).firstOrNull { times.optString(it) == dayStr } ?: return@withContext null
        var hourTemp: Double? = null
        var hourCode: Int? = null
        if (withHour) {
            val hourly = o.optJSONObject("hourly")
            val ht = hourly?.optJSONArray("time")
            val h = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
            val target = dayStr + "T%02d:00".format(h)
            val j = ht?.let { a -> (0 until a.length()).firstOrNull { a.optString(it) == target } }
            if (j != null) {
                hourTemp = hourly.optJSONArray("temperature_2m")?.optDouble(j)
                hourCode = hourly.optJSONArray("weather_code")?.optInt(j)
            }
        }
        Forecast(
            daily.optJSONArray("weather_code")?.optInt(i) ?: 0,
            daily.optJSONArray("temperature_2m_max")?.optDouble(i) ?: 0.0,
            daily.optJSONArray("temperature_2m_min")?.optDouble(i) ?: 0.0,
            daily.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0,
            hourTemp, hourCode,
        )
    }
}

/** extra rows of the event editor: countdown switch and the weather of that day */
@Composable
fun EventExtras(eventId: String?, start: Long, allDay: Boolean) {
    val ctx = LocalContext.current
    val ids by remember { Countdowns.flow() }.collectAsState()
    var place by remember(eventId) { mutableStateOf(eventId?.let { Weather.placeFor(it) }) }
    var pickPlace by remember { mutableStateOf(false) }
    if (eventId != null) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HourglassTop, null, Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Conto alla rovescia"), style = MaterialTheme.typography.titleSmall)
                Text(t("In cima all'agenda e nel widget"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = eventId in ids, onCheckedChange = { Countdowns.toggle(eventId) })
        }
    }
    val where = place ?: remember { Weather.here(ctx) }
    val fc by produceState<Forecast?>(null, where, start, allDay) {
        value = null
        if (where != null) value = Weather.forecast(where, start, !allDay)
    }
    val days = Countdowns.daysTo(start)
    if (start >= Fmt.startOfDay(now()) && days <= 15) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).bounceClick(0.98f) { pickPlace = true }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbSunny, null, Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                val f = fc
                when {
                    where == null -> Text(t("Meteo: scegli un luogo"), style = MaterialTheme.typography.titleSmall)
                    f == null -> Text(t("Meteo in arrivo…"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text(
                            Weather.emoji(f.code) + "  " + Weather.label(f.code) + " · " + "%.0f° / %.0f°".format(f.max, f.min),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val hour = if (f.hourTemp != null) t("alle {0}: {1}°", Fmt.time(start), "%.0f".format(f.hourTemp)) + " " + Weather.emoji(f.hourCode ?: f.code) + " · " else ""
                        Text(hour + t("pioggia {0}%", f.rain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (where != null) Text(where.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(t("Cambia"), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (pickPlace) {
        var q by remember { mutableStateOf("") }
        val results by produceState(emptyList<GeoPlace>(), q) {
            delay(350)
            value = Weather.search(q)
        }
        MonoDialog(
            onDismissRequest = { pickPlace = false },
            title = { Text(t("Luogo per il meteo")) },
            text = {
                Column {
                    OutlinedTextField(q, { q = it.take(60) }, label = { Text(t("Città o paese")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 260.dp).padding(top = 8.dp)) {
                        item {
                            Text(
                                "📍  " + t("Dove sono adesso"),
                                Modifier.fillMaxWidth().bounceClick(0.98f) {
                                    eventId?.let { Weather.setPlace(it, null) }
                                    place = null; pickPlace = false
                                }.padding(vertical = 10.dp),
                            )
                        }
                        items(results) { r ->
                            Text(
                                r.name,
                                Modifier.fillMaxWidth().bounceClick(0.98f) {
                                    eventId?.let { Weather.setPlace(it, r) }
                                    place = r; pickPlace = false
                                }.padding(vertical = 10.dp),
                            )
                        }
                    }
                    Text(t("Previsioni da Open-Meteo"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { pickPlace = false }) { Text(t("Chiudi")) } },
        )
    }
}
