package com.monolith.app.net

import android.content.Context
import android.net.Uri
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.ConvSetting
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Group
import com.monolith.app.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * 24-hour stories. Each person's stories live in a hidden group "story.<id>" with all their friends;
 * messages there expire after a day like disappearing messages.
 */
object Stories {
    const val DAY = 86_400L

    fun groupIdOf(pk: String) = Shard.STORY + pk.take(20)
    fun convOf(pk: String) = "g:" + groupIdOf(pk)

    /** the stories of [pk] still visible, oldest first */
    fun visible(msgs: List<Message>): List<Message> {
        val cutoff = now() - DAY * 1000
        return msgs.filter { (it.kind == "photo" || it.kind == "text" || it.kind == "voice") && it.ts > cutoff && (it.expiresAt == 0L || it.expiresAt > now()) }
            .sortedBy { it.ts }
    }

    /** my story group, created or brought up to date with my current friends */
    suspend fun ensureMine(): String? {
        val k = Graph.identity.keys?.pub ?: return null
        val gid = groupIdOf(k)
        val friends = Graph.db.friends().friendsNow().filter { it.status == FriendStatus.FRIEND }.map { it.pubkey }
        val members = (listOf(k) + friends).distinct()
        val g = Graph.db.groups().get(gid)
        if (g == null || g.memberList().toSet() != members.toSet()) {
            val ng = Group(gid, t("Storie"), "✨", members.joinToString(","), k, g?.createdAt ?: now())
            Graph.db.groups().upsert(ng)
            Graph.messenger.broadcastGroup(ng)
        }
        val conv = "g:$gid"
        val s = Graph.db.social().settingNow(conv) ?: ConvSetting(conv)
        if (s.ttlSec != DAY) Graph.db.social().saveSetting(s.copy(ttlSec = DAY))
        return gid
    }

    suspend fun postPhoto(uri: Uri, caption: String = "", music: org.json.JSONObject? = null) {
        val gid = ensureMine() ?: return
        Graph.messenger.sendPhoto("g:$gid", uri, caption, music = music)
    }

    suspend fun postText(text: String, music: org.json.JSONObject? = null) {
        val gid = ensureMine() ?: return
        Graph.messenger.sendText("g:$gid", text, music = music)
    }

    suspend fun postAudio(file: java.io.File, durationMs: Long, music: org.json.JSONObject, caption: String) {
        val gid = ensureMine() ?: return
        Graph.messenger.sendAudioStory("g:$gid", file, durationMs, music, caption)
    }

    // ---- seen

    private lateinit var ctx: Context
    private val sp by lazy { ctx.getSharedPreferences("stories_seen", Context.MODE_PRIVATE) }
    private val _seen = MutableStateFlow<Map<String, Long>>(emptyMap())
    val seen: StateFlow<Map<String, Long>> = _seen.asStateFlow()

    fun init(c: Context) {
        ctx = c.applicationContext
        _seen.value = sp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
    }

    fun markSeen(pk: String, upTo: Long) {
        if ((_seen.value[pk] ?: 0L) >= upTo) return
        sp.edit().putLong(pk, upTo).apply()
        _seen.value = _seen.value + (pk to upTo)
    }
}

/** how strong the bond with a friend is, from the days you talked */
data class Bond(val level: Int, val streak: Int, val days30: Int) {
    val name: String get() = when (level) {
        1 -> t("Sasso")
        2 -> t("Pietra")
        3 -> t("Stele")
        4 -> t("Menhir")
        else -> t("Monolite")
    }
}

object Bonds {
    suspend fun with(pk: String): Bond {
        val since = now() - 60L * DAY_MS
        val days = Graph.db.messages().timestamps(pk, since).map { dayCode(minOf(it, now())) }.toSet() +
            (Graph.bondDays.days.value[pk] ?: emptySet())
        val streak = com.monolith.app.data.streakOf(days)
        val cut = dayCode(now() - 30L * DAY_MS)
        val days30 = days.count { it > cut }
        val level = when {
            days30 >= 21 -> 5
            days30 >= 13 -> 4
            days30 >= 7 -> 3
            days30 >= 3 -> 2
            else -> 1
        }
        return Bond(level, streak, days30)
    }

    private const val DAY_MS = 86_400_000L
}
