package com.monolith.app.net

import android.content.Context
import android.util.Base64
import com.monolith.app.Graph
import com.monolith.app.core.now
import com.monolith.app.core.nowSec
import com.monolith.app.core.strings
import com.monolith.app.crypto.Hex
import com.monolith.app.data.Group
import com.monolith.app.nostr.Envelope
import com.monolith.app.nostr.NostrEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** what an invite link shows before you go in: [kind] "g" = group, "s" = shard */
data class InviteCard(
    val kind: String,
    val id: String,
    val name: String,
    val emoji: String,
    val desc: String,
    val count: Int,
    /** who decides: the link is answered by them */
    val admins: List<String>,
    val approve: Boolean,
    val cover: String,
    val color: String,
)

/**
 * Invite links for groups and shards: https://monolith.support/#entra/<secret>.
 * The administrators leave a small card on the relays, readable only by whoever has the link
 * (it is sealed with a key made from the secret). Whoever opens the link reads the card and asks the
 * administrators to come in; one of their phones lets them in (at once, or after a yes).
 */
object Invites {
    const val SITE_PREFIX = "https://monolith.support/#entra/"
    private const val D_PREFIX = "monolith-invite-"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    private val prefs get() = Graph.app.getSharedPreferences("invites", Context.MODE_PRIVATE)

    fun newSecret(): String {
        val r = SecureRandom()
        return (1..16).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun linkFor(secret: String) = SITE_PREFIX + secret

    /** the secret inside a link (from the site or the app), or null */
    fun parse(text: String?): String? {
        text ?: return null
        val m = Regex("(?:monolith\\.support/?#entra/|monolith://entra/)([A-Za-z0-9]{12,32})").find(text) ?: return null
        return m.groupValues[1]
    }

    /** a link to a group or a shard inside a chat message */
    fun findIn(text: String): String? = parse(text)

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    private fun tagOf(secret: String) = Hex.encode(sha("monolith-invite|$secret")).take(32)
    private fun keyOf(secret: String) = sha("monolith-invite-key|$secret")

    // ------------------------------------------------------------------ the card

    private suspend fun publish(secret: String, content: String) {
        val k = Graph.identity.keys ?: return
        val tag = tagOf(secret)
        val ev = withContext(Dispatchers.Default) {
            NostrEvent("", nowSec(), Envelope.KIND_PROFILE, listOf(listOf("d", D_PREFIX + tag), listOf("c", tag)), content).sign(k.priv)
        }
        Graph.relays.publish(ev)
    }

    private fun seal(secret: String, o: JSONObject): String? = runCatching {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyOf(secret), "AES"), GCMParameterSpec(128, iv))
        Base64.encodeToString(iv + c.doFinal(o.toString().toByteArray()), Base64.NO_WRAP)
    }.getOrNull()

    private fun open(secret: String, b64: String): JSONObject? = runCatching {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyOf(secret), "AES"), GCMParameterSpec(128, all, 0, 12))
        JSONObject(String(c.doFinal(all, 12, all.size - 12)))
    }.getOrNull()

    /** an administrator of the group keeps the card of its link up to date */
    suspend fun publishGroup(g: Group) {
        val m = Graph.groupMeta.get(g.id)
        if (m.link.isBlank()) return
        val o = JSONObject().put("k", "g").put("id", g.id).put("n", g.name).put("e", g.emoji).put("ds", m.desc)
            .put("c", g.memberList().size).put("a", (listOf(g.admin) + m.admins).distinct().let { JSONArray(it) })
            .put("ap", m.approve).put("cv", m.cover).put("cl", m.color)
        seal(m.link, o)?.let { publish(m.link, it) }
    }

    /** the founder or a moderator keeps the card of the shard's link up to date */
    suspend fun publishShard(s: Shard) {
        if (s.link.isBlank() || s.open) return
        val o = JSONObject().put("k", "s").put("id", s.id).put("n", s.name).put("e", s.emoji).put("ds", s.desc)
            .put("c", s.members.size).put("a", JSONArray((listOf(s.owner) + s.mods).distinct())).put("ap", s.approve).put("cv", "").put("cl", "")
        seal(s.link, o)?.let { publish(s.link, it) }
    }

    /** an old link stops working */
    suspend fun revoke(secret: String) {
        if (secret.isBlank()) return
        publish(secret, "")
    }

    /** read the card behind a link; null if the link is not valid (any more) */
    suspend fun resolve(secret: String): InviteCard? {
        val tag = tagOf(secret)
        val f = JSONObject().put("kinds", JSONArray().put(Envelope.KIND_PROFILE)).put("#c", JSONArray().put(tag)).put("limit", 20)
        val list = runCatching { Graph.relays.query(listOf(f), 7000) }.getOrDefault(emptyList())
            .filter { it.firstTag("d") == D_PREFIX + tag }.sortedByDescending { it.createdAt }
        // the newest card of each administrator: an empty one means the link was turned off
        val newest = list.groupBy { it.pubkey }.mapValues { it.value.first() }.values.sortedByDescending { it.createdAt }
        val ev = newest.firstOrNull() ?: return null
        if (!withContext(Dispatchers.Default) { ev.verify() }) return null
        if (ev.content.isBlank()) return null
        val o = open(secret, ev.content) ?: return null
        val admins = (o.optJSONArray("a") ?: JSONArray()).strings().filter { Hex.isHex64(it) }
        if (ev.pubkey !in admins) return null
        return InviteCard(
            o.optString("k"), o.optString("id").take(64), o.optString("n").take(60), o.optString("e").take(8).ifBlank { "💬" }, o.optString("ds").take(500),
            o.optInt("c"), admins, o.optBoolean("ap"), o.optString("cv").take(16), o.optString("cl").take(16),
        )
    }

    /** am I already inside what the card leads to? */
    suspend fun alreadyIn(card: InviteCard): Boolean = when (card.kind) {
        "s" -> Graph.shards.get(card.id) != null
        else -> Graph.db.groups().get(card.id) != null
    }

    /** ask the administrators to let me in */
    suspend fun join(card: InviteCard, secret: String) {
        val k = Graph.identity.keys ?: return
        prefs.edit().putLong("p_" + card.id, now()).apply()
        val prof = Graph.identity.profile.value
        val p = JSONObject().put("t", if (card.kind == "s") "sjoin" else "gjoin").put(if (card.kind == "s") "id" else "g", card.id).put("s", secret)
        if (prof != null) p.put("pf", prof.toJson())
        card.admins.filter { it != k.pub }.take(8).forEach { Graph.messenger.sendStored(it, JSONObject(p.toString())) }
    }

    /** I asked to come in here with a link, not long ago */
    fun isPending(id: String): Boolean = now() - prefs.getLong("p_$id", 0L) < 14L * 86_400_000L

    fun done(id: String) {
        prefs.edit().remove("p_$id").apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
