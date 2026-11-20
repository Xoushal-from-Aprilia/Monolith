package com.monolith.app.net

import android.content.Context
import android.icu.text.BreakIterator
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.Recur
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.InviteState
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.Calendar

data class WeekPerson(val pk: String, val name: String, val emoji: String, val n: Int)

/** Everything the Sunday recap shows: the last seven days, counted on this phone only. */
data class WeekRecap(
    val from: Long,
    val to: Long,
    val sent: Int,
    val received: Int,
    val people: List<WeekPerson>,
    val emojis: List<Pair<String, Int>>,
    val calls: Int,
    val callMs: Long,
    val photos: Int,
    val voices: Int,
    /** start of each of the seven days, with the messages of that day */
    val days: List<Pair<Long, Int>>,
    /** hour of the day with the most messages, -1 if none */
    val hour: Int,
    val arrivals: Int,
    val places: List<Pair<String, Int>>,
    val events: Int,
    val eventsDone: Int,
    val habitDays: Int,
    val bestHabit: Triple<String, String, Int>?,
    val newFriends: List<WeekPerson>,
    /** the strongest bond right now */
    val bond: Pair<WeekPerson, Bond>?,
) {
    val messages get() = sent + received
    val callMinutes get() = ((callMs + 59_999) / 60_000).toInt()
    val quiet get() = messages == 0 && calls == 0 && photos == 0 && arrivals == 0 && events == 0 && habitDays == 0

    /** a title for the week, picked from what stood out the most */
    fun persona(): Triple<String, String, String> {
        val all = listOf(
            Triple("💬", "Anima della chat" /*i18n*/, "Hai tenuto vive le conversazioni" /*i18n*/) to sent / 60f,
            Triple("📞", "Voce calda" /*i18n*/, "Le tue chiamate hanno accorciato le distanze" /*i18n*/) to callMinutes / 30f,
            Triple("📷", "Occhio fotografico" /*i18n*/, "Hai raccontato la settimana per immagini" /*i18n*/) to photos / 6f,
            Triple("🧭", "Spirito esploratore" /*i18n*/, "Sempre in giro, sempre in arrivo" /*i18n*/) to arrivals / 3f,
            Triple("🪨", "Costanza di pietra" /*i18n*/, "Giorno dopo giorno, senza mollare" /*i18n*/) to habitDays / 5f,
            Triple("📅", "Agenda d'acciaio" /*i18n*/, "Una settimana piena e ben organizzata" /*i18n*/) to events / 4f,
        )
        val best = all.maxBy { it.second }
        val p = if (best.second < 0.5f) Triple("🗿", "Quiete del monolite" /*i18n*/, "Una settimana calma: anche il silenzio ha il suo peso" /*i18n*/) else best.first
        return Triple(p.first, t(p.second), t(p.third))
    }

    fun shareText(): String {
        val p = persona()
        return buildString {
            append(t("La mia settimana su Monolith")).append(" ✦\n")
            append(p.first).append(' ').append(p.second).append('\n')
            if (messages > 0) append("💬 ").append(t("{0} messaggi", messages)).append('\n')
            if (calls > 0) append("📞 ").append(t("{0} chiamate · {1} min", calls, callMinutes)).append('\n')
            if (photos > 0) append("📷 ").append(t("{0} foto", photos)).append('\n')
            if (arrivals > 0) append("📍 ").append(t("{0} arrivi nei luoghi comuni", arrivals)).append('\n')
            emojis.firstOrNull()?.let { append(t("Emoji della settimana: {0}", it.first)).append('\n') }
        }.trim()
    }
}

/** The weekly recap: computed from local data, announced on Sunday evening. */
object Weekly {
    private const val DAY = 86_400_000L
    private fun sp(ctx: Context) = ctx.getSharedPreferences("weekly", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean("on", true)
    fun setEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("on", on).apply()

    /** the recap was announced and not opened yet (shown as a card in Clubhouse for three days) */
    fun unseen(ctx: Context): Boolean {
        val s = sp(ctx)
        val at = s.getLong("notified_at", 0L)
        return at > 0 && now() - at < 3 * DAY && s.getLong("seen_at", 0L) < at
    }

    /** true while the Clubhouse card "your week is ready" should show */
    val ready = MutableStateFlow(false)

    fun refresh(ctx: Context) { ready.value = unseen(ctx) }

    fun markSeen(ctx: Context) {
        sp(ctx).edit().putLong("seen_at", now()).apply()
        ready.value = false
    }

    /** this Sunday, as a day code: one announcement per week */
    private fun weekKey(): String {
        val c = Calendar.getInstance()
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) c.add(Calendar.DAY_OF_YEAR, -1)
        return dayCode(c.timeInMillis)
    }

    fun alreadyAnnounced(ctx: Context) = sp(ctx).getString("week", "") == weekKey()

    suspend fun announce(ctx: Context) {
        if (!enabled(ctx) || alreadyAnnounced(ctx) || Graph.identity.keys == null) return
        sp(ctx).edit().putString("week", weekKey()).apply()
        val r = compute()
        if (r.quiet) return
        sp(ctx).edit().putLong("notified_at", now()).apply()
        ready.value = true
        val p = r.persona()
        Graph.notifier.weekly(t("La tua settimana è pronta"), p.first + " " + p.second + " · " + t("{0} messaggi", r.messages))
    }

    suspend fun compute(): WeekRecap {
        val to = now()
        val firstDay = Fmt.startOfDay(to) - 6 * DAY
        val me = Graph.identity.keys?.pub ?: ""
        val db = Graph.db
        val friends = db.friends().allNow().associateBy { it.pubkey }
        val all = db.messages().since(firstDay)

        val chat = all.filter { it.kind != "call" }
        val visible = chat.filter { !it.convId.startsWith("g:" + Shard.STORY) }
        val sent = visible.count { it.outgoing }
        val received = visible.count { !it.outgoing }

        // people: messages in your one-to-one chat, plus what they wrote in groups and shards
        val score = HashMap<String, Int>()
        visible.forEach { m ->
            val pk = if (!m.convId.startsWith("g:")) m.convId else if (!m.outgoing) m.sender else null
            if (pk != null && pk != me && friends[pk]?.status == FriendStatus.FRIEND) score[pk] = (score[pk] ?: 0) + 1
        }
        val people = score.entries.sortedByDescending { it.value }.take(3).mapNotNull { (pk, n) ->
            friends[pk]?.let { WeekPerson(pk, it.displayName, it.emoji, n) }
        }

        // emoji you used, in your messages and reactions
        val emo = HashMap<String, Int>()
        fun count(s: String) = emojisIn(s).forEach { emo[it] = (emo[it] ?: 0) + 1 }
        visible.filter { it.outgoing && it.kind != "place" }.forEach { count(it.text) }
        runCatching { db.social().allReactions() }.getOrDefault(emptyList())
            .filter { it.sender == me && it.ts >= firstDay }.forEach { count(it.emoji) }
        val emojis = emo.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }

        // calls: the log line ends with the length, m:ss
        val callLogs = all.filter { it.kind == "call" }
        var callMs = 0L
        var calls = 0
        val dur = Regex("(\\d+):(\\d{2})\\s*$")
        callLogs.forEach { m ->
            val d = dur.find(m.text) ?: return@forEach
            calls++
            callMs += (d.groupValues[1].toLong() * 60 + d.groupValues[2].toLong()) * 1000
        }

        val photos = visible.count { it.kind == "photo" }
        val voices = visible.count { it.kind == "voice" }

        val days = (0 until 7).map { i ->
            val start = firstDay + i * DAY
            val end = Fmt.startOfDay(start + DAY + 3_600_000L)
            start to visible.count { it.ts in start until end }
        }
        val hours = IntArray(24)
        visible.forEach { hours[Calendar.getInstance().apply { timeInMillis = it.ts }.get(Calendar.HOUR_OF_DAY)]++ }
        val hour = if (hours.all { it == 0 }) -1 else hours.indices.maxBy { hours[it] }

        // arrivals in common places: the same arrival reaches every friend, so count it once
        val arrivalsSeen = HashSet<String>()
        val placeCount = HashMap<String, Int>()
        all.filter { it.kind == "place" }.forEach { m ->
            val key = m.sender + "|" + m.text + "|" + (m.ts / 120_000L)
            if (arrivalsSeen.add(key)) {
                val e = runCatching { JSONObject(m.extra ?: "{}").optString("e", "📍") }.getOrDefault("📍")
                val label = e + " " + m.text
                placeCount[label] = (placeCount[label] ?: 0) + 1
            }
        }
        val places = placeCount.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

        // agenda: every occurrence in the last seven days
        var events = 0
        var eventsDone = 0
        val agenda = db.agenda().allNow().filter { it.inviteState != InviteState.DECLINED && it.inviteState != InviteState.PENDING }
        for (i in 0 until 7) {
            val day = firstDay + i * DAY + 3_600_000L
            agenda.forEach { e ->
                val occ = Recur.occurrenceOn(e, day) ?: return@forEach
                if (occ > to) return@forEach
                events++
                if (Graph.done.isDone(e.id, occ)) eventsDone++
            }
        }

        // habits
        val dayCodes = (0 until 7).map { dayCode(firstDay + it * DAY + 3_600_000L) }.toSet()
        val habits = Graph.together.habits.value.filter { !it.deleted && me in it.members }
        val checks = Graph.together.checks.value
        val habitDays = habits.sumOf { h -> checks[h.id]?.get(me)?.count { it in dayCodes } ?: 0 }
        val bestHabit = habits.map { Triple(it.emoji, it.name, Graph.together.streak(it.id, me)) }
            .filter { it.third > 0 }.maxByOrNull { it.third }

        val newFriends = friends.values.filter { it.status == FriendStatus.FRIEND && it.createdAt >= firstDay }
            .sortedBy { it.createdAt }.map { WeekPerson(it.pubkey, it.displayName, it.emoji, 0) }

        val bond = friends.values.filter { it.status == FriendStatus.FRIEND }
            .map { f -> f to Bonds.with(f.pubkey) }
            .filter { it.second.days30 > 0 }
            .maxByOrNull { it.second.days30 * 1000 + it.second.streak }
            ?.let { (f, b) -> WeekPerson(f.pubkey, f.displayName, f.emoji, b.days30) to b }

        return WeekRecap(
            firstDay, to, sent, received, people, emojis, calls, callMs, photos, voices, days, hour,
            arrivalsSeen.size, places, events, eventsDone, habitDays, bestHabit, newFriends, bond,
        )
    }

    /** emoji in a piece of text, one entry per visible symbol (flags and families included) */
    fun emojisIn(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            val g = s.substring(start, end)
            if (isEmoji(g.codePointAt(0))) out += g
            start = end
            end = it.next()
        }
        return out
    }

    private fun isEmoji(cp: Int) =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp == 0x2B50 || cp == 0x2B55 || cp == 0x2B1B || cp == 0x2B1C
}
