package com.monolith.app.net

import android.content.Context
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.FriendStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Calendar

/** frame of a badge: each family of achievements has its own shape */
enum class BadgeFrame { HEX, CIRCLE, DIAMOND, SHIELD, SHARD, SQUARE }

/** [tier]: 1 outline, 2 double ring, 3 solid with rays */
data class BadgeDef(val id: String, val name: String, val desc: String, val frame: BadgeFrame, val tier: Int)

/**
 * Badges: earned by using the app, computed on this phone, shown on the profile and sent to friends
 * together with the profile so they can see them too.
 */
object Badges {
    val ALL = listOf(
        BadgeDef("first_word", "Prima parola" /*i18n*/, "Il primo messaggio inviato" /*i18n*/, BadgeFrame.HEX, 1),
        BadgeDef("chatter", "Chiacchierone" /*i18n*/, "1.000 messaggi inviati" /*i18n*/, BadgeFrame.HEX, 2),
        BadgeDef("novelist", "Romanziere" /*i18n*/, "10.000 messaggi inviati" /*i18n*/, BadgeFrame.HEX, 3),
        BadgeDef("night_owl", "Nottambulo" /*i18n*/, "Un messaggio tra le 2 e le 5 di notte" /*i18n*/, BadgeFrame.CIRCLE, 1),
        BadgeDef("early_bird", "Mattiniero" /*i18n*/, "Un messaggio prima delle 6 del mattino" /*i18n*/, BadgeFrame.CIRCLE, 1),
        BadgeDef("photographer", "Fotografo" /*i18n*/, "100 foto inviate" /*i18n*/, BadgeFrame.SQUARE, 2),
        BadgeDef("first_call", "Pronto?" /*i18n*/, "La prima chiamata" /*i18n*/, BadgeFrame.CIRCLE, 1),
        BadgeDef("marathon", "Maratona" /*i18n*/, "Una chiamata di oltre un'ora" /*i18n*/, BadgeFrame.CIRCLE, 2),
        BadgeDef("friends5", "Cerchia" /*i18n*/, "5 amici" /*i18n*/, BadgeFrame.DIAMOND, 1),
        BadgeDef("friends20", "Tribù" /*i18n*/, "20 amici" /*i18n*/, BadgeFrame.DIAMOND, 2),
        BadgeDef("streak7", "Settimana piena" /*i18n*/, "7 giorni di fila con un amico" /*i18n*/, BadgeFrame.DIAMOND, 1),
        BadgeDef("streak30", "Un mese insieme" /*i18n*/, "30 giorni di fila con un amico" /*i18n*/, BadgeFrame.DIAMOND, 2),
        BadgeDef("streak100", "Cento" /*i18n*/, "100 giorni di fila con un amico" /*i18n*/, BadgeFrame.DIAMOND, 3),
        BadgeDef("streak365", "Un anno di fila" /*i18n*/, "365 giorni di fila con un amico" /*i18n*/, BadgeFrame.SHARD, 3),
        BadgeDef("monolith", "Monolite" /*i18n*/, "Il livello Monolite con un amico" /*i18n*/, BadgeFrame.SHARD, 2),
        BadgeDef("anniversary", "Un anno insieme" /*i18n*/, "Un amico da almeno un anno" /*i18n*/, BadgeFrame.DIAMOND, 2),
        BadgeDef("highfive", "Batti cinque" /*i18n*/, "Un amico aggiunto battendo il cinque" /*i18n*/, BadgeFrame.CIRCLE, 1),
        BadgeDef("first_place", "Punto d'incontro" /*i18n*/, "Il primo luogo comune" /*i18n*/, BadgeFrame.SHIELD, 1),
        BadgeDef("explorer", "Esploratore" /*i18n*/, "20 arrivi nei luoghi comuni" /*i18n*/, BadgeFrame.SHIELD, 2),
        BadgeDef("cartographer", "Cartografo" /*i18n*/, "5 luoghi comuni" /*i18n*/, BadgeFrame.SHIELD, 2),
        BadgeDef("founder", "Fondatore" /*i18n*/, "Hai creato uno Shard" /*i18n*/, BadgeFrame.SHARD, 1),
        BadgeDef("keeper", "Custode" /*i18n*/, "Moderatore di uno Shard" /*i18n*/, BadgeFrame.SHARD, 1),
        BadgeDef("community", "Comunità" /*i18n*/, "Uno Shard con 10 membri" /*i18n*/, BadgeFrame.SHARD, 2),
        BadgeDef("architect", "Architetto" /*i18n*/, "Uno Shard con 6 stanze" /*i18n*/, BadgeFrame.SHARD, 2),
        BadgeDef("storyteller", "Cantastorie" /*i18n*/, "La prima storia" /*i18n*/, BadgeFrame.SQUARE, 1),
        BadgeDef("narrator", "Narratore" /*i18n*/, "30 storie pubblicate" /*i18n*/, BadgeFrame.SQUARE, 2),
        BadgeDef("artist", "Artista" /*i18n*/, "Un disegno inviato dalla lavagna" /*i18n*/, BadgeFrame.SQUARE, 1),
        BadgeDef("collector", "Collezionista" /*i18n*/, "5 sticker creati" /*i18n*/, BadgeFrame.SQUARE, 2),
        BadgeDef("winner", "Prima vittoria" /*i18n*/, "Una partita vinta in chat" /*i18n*/, BadgeFrame.HEX, 1),
        BadgeDef("champion", "Campione" /*i18n*/, "10 partite vinte" /*i18n*/, BadgeFrame.HEX, 3),
        BadgeDef("organizer", "Organizzatore" /*i18n*/, "50 eventi in Agenda" /*i18n*/, BadgeFrame.SQUARE, 2),
        BadgeDef("done", "Fatto!" /*i18n*/, "20 eventi segnati come fatti" /*i18n*/, BadgeFrame.SQUARE, 1),
        BadgeDef("habit7", "Costanza" /*i18n*/, "7 giorni di fila in un'abitudine" /*i18n*/, BadgeFrame.HEX, 1),
        BadgeDef("habit30", "Disciplina" /*i18n*/, "30 giorni di fila in un'abitudine" /*i18n*/, BadgeFrame.HEX, 2),
        BadgeDef("accountant", "Conti chiari" /*i18n*/, "La prima spesa condivisa" /*i18n*/, BadgeFrame.CIRCLE, 1),
        BadgeDef("weekly", "Fedele alla domenica" /*i18n*/, "5 riepiloghi della domenica guardati" /*i18n*/, BadgeFrame.CIRCLE, 2),
        BadgeDef("pioneer", "Pioniere" /*i18n*/, "Tra i primi a usare Monolith" /*i18n*/, BadgeFrame.SHARD, 3),
    )
    private val byId = ALL.associateBy { it.id }
    fun def(id: String) = byId[id]

    private fun sp(ctx: Context) = ctx.getSharedPreferences("badges", Context.MODE_PRIVATE)
    private val _earned = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** my badges: id -> when it was earned */
    val earned: StateFlow<Map<String, Long>> = _earned.asStateFlow()

    fun init(ctx: Context) {
        _earned.value = runCatching {
            val o = JSONObject(sp(ctx).getString("earned", "{}") ?: "{}")
            o.keys().asSequence().filter { it in byId }.associateWith { o.optLong(it) }
        }.getOrDefault(emptyMap())
    }

    fun ids(): String = _earned.value.keys.joinToString(",")

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
        _earned.value = emptyMap()
    }

    /** looks at what you have done and hands out the new badges */
    suspend fun refresh(ctx: Context) {
        val me = Graph.identity.keys?.pub ?: return
        val db = Graph.db
        val x = Graph.profileExtras
        val got = HashSet<String>()

        val sent = x.atLeast("sent", db.messages().countSent())
        if (sent >= 1) got += "first_word"
        if (sent >= 1000) got += "chatter"
        if (sent >= 10_000) got += "novelist"
        if (x.atLeast("photos", db.messages().countSentKind("photo")) >= 100) got += "photographer"
        val cal = Calendar.getInstance()
        db.messages().recentSentTs().forEach { ts ->
            cal.timeInMillis = ts
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h in 2..4) got += "night_owl"
            if (h == 5) got += "early_bird"
        }
        if (x.count("night_owl") > 0) got += "night_owl"
        if (x.count("early_bird") > 0) got += "early_bird"

        val dur = Regex("(\\d+):(\\d{2})\\s*$")
        val calls = db.messages().byKind("call").mapNotNull { m -> dur.find(m.text)?.let { it.groupValues[1].toLong() * 60 + it.groupValues[2].toLong() } }
        if (x.atLeast("calls", calls.size) >= 1) got += "first_call"
        if (x.atLeast("longest_call", (calls.maxOrNull() ?: 0L).toInt()) >= 3600) got += "marathon"

        val friends = db.friends().friendsNow().filter { it.status == FriendStatus.FRIEND }
        if (friends.size >= 5) got += "friends5"
        if (friends.size >= 20) got += "friends20"
        val streak = x.atLeast("best_streak", friends.maxOfOrNull { Graph.bondDays.streak(it.pubkey) } ?: 0)
        if (streak >= 7) got += "streak7"
        if (streak >= 30) got += "streak30"
        if (streak >= 100) got += "streak100"
        if (streak >= 365) got += "streak365"
        if (friends.any { now() - it.createdAt >= 365L * 86_400_000L }) got += "anniversary"
        val last30 = (0 until 30).map { dayCode(now() - it * 86_400_000L) }.toSet()
        val monolithBond = friends.any { f -> (Graph.bondDays.days.value[f.pubkey] ?: emptySet()).count { it in last30 } >= 21 }
        if (monolithBond || x.count("monolith") > 0) { got += "monolith"; x.atLeast("monolith", 1) }
        if (x.count("highfive") > 0) got += "highfive"

        val places = Graph.places.all.value
        if (x.atLeast("places", places.size) >= 1) got += "first_place"
        if (x.count("places") >= 5) got += "cartographer"
        val arrivals = db.messages().byKind("place").filter { it.outgoing }.map { it.text + (it.ts / 120_000L) }.toSet().size
        if (x.atLeast("arrivals", arrivals) >= 20) got += "explorer"

        val shards = Graph.shards.all.value
        if (shards.any { it.owner == me } || x.count("founded") > 0) { got += "founder"; x.atLeast("founded", 1) }
        if (shards.any { me in it.mods }) got += "keeper"
        if (shards.any { it.members.size >= 10 }) got += "community"
        if (shards.any { it.rooms.size >= 6 }) got += "architect"

        val stories = x.count("stories")
        if (stories >= 1) got += "storyteller"
        if (stories >= 30) got += "narrator"
        if (x.count("drawings") >= 1) got += "artist"
        if (x.atLeast("stickers", com.monolith.app.ui.screens.Stickers.count()) >= 5) got += "collector"

        val wins = db.messages().byKind("game").count { m ->
            val st = Games.parse(m)
            val r = Games.result(st)
            when {
                st.type == Games.HANG -> r == 2 && st.creator != me && st.moves.any { it.by == me }
                r == 1 -> st.creator == me
                r == 2 -> st.second == me
                else -> false
            }
        }
        val w = x.atLeast("wins", wins)
        if (w >= 1) got += "winner"
        if (w >= 10) got += "champion"

        if (x.atLeast("events", db.agenda().allNow().size) >= 50) got += "organizer"
        if (x.atLeast("done", Graph.done.count()) >= 20) got += "done"
        val habit = x.atLeast("habit_streak", Graph.together.habits.value.filter { me in it.members }.maxOfOrNull { Graph.together.streak(it.id, me) } ?: 0)
        if (habit >= 7) got += "habit7"
        if (habit >= 30) got += "habit30"
        if (Graph.together.bills.value.isNotEmpty() || x.count("bills") > 0) { got += "accountant"; x.atLeast("bills", 1) }
        if (x.count("wrapped") >= 5) got += "weekly"

        val installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime }.getOrDefault(now())
        val cutoff = Calendar.getInstance().apply { set(2027, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
        if (installed < cutoff) got += "pioneer"

        // badges are never taken back
        val fresh = got.filter { it !in _earned.value }
        if (fresh.isEmpty()) return
        val next = _earned.value + fresh.associateWith { now() }
        _earned.value = next
        val o = JSONObject()
        next.forEach { (k, v) -> o.put(k, v) }
        sp(ctx).edit().putString("earned", o.toString()).apply()
        // the first time everything is counted at once: no notifications for that
        if (sp(ctx).getBoolean("primed", false)) {
            fresh.take(3).forEach { id -> def(id)?.let { Graph.notifier.badge(t(it.name), t(it.desc)) } }
        } else sp(ctx).edit().putBoolean("primed", true).apply()
        Graph.messenger.sendProfileToFriends()
    }
}
