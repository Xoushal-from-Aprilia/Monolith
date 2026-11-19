package com.monolith.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.monolith.app.Graph
import com.monolith.app.crypto.Hex
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.ArrivalAlert
import com.monolith.app.data.ConvSetting
import com.monolith.app.data.Friend
import com.monolith.app.data.Group
import com.monolith.app.data.ListItem
import com.monolith.app.data.Message
import com.monolith.app.data.Reaction
import com.monolith.app.data.SharedList
import com.monolith.app.data.Vote
import com.monolith.app.net.Media
import com.monolith.app.service.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Biometric / device credential lock, re-armed after 30 s in background */
object AppLock {
    var locked by mutableStateOf(false)
    private var enabled = false
    private var backgroundAt = 0L

    fun init(prefs: Prefs) {
        enabled = prefs.appLock
        locked = enabled
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        Graph.prefs.appLock = on
        if (!on) locked = false
    }

    fun onBackground() {
        backgroundAt = now()
    }

    fun onForeground() {
        if (enabled && backgroundAt > 0 && now() - backgroundAt > 30_000) locked = true
    }

    fun unlock() {
        locked = false
    }
}

/** Friend QR codes: monolith:<pubkey hex>:<username> */
object FriendQr {
    fun content(pub: String, username: String) = "monolith:$pub:$username"

    fun parse(text: String?): Pair<String, String>? {
        val s = text?.trim() ?: return null
        if (!s.startsWith("monolith:")) return null
        val parts = s.removePrefix("monolith:").split(":")
        val pk = parts.getOrNull(0)?.lowercase() ?: return null
        if (!Hex.isHex64(pk)) return null
        return pk to (parts.getOrNull(1) ?: "")
    }

    fun bitmap(text: String, size: Int, dark: Int = Color.BLACK, light: Int = Color.WHITE): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (m.get(x, y)) dark else light
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Encrypted backup: a zip (data.json + media) encrypted with AES-256-GCM,
 * key derived from the user's password with PBKDF2-HMAC-SHA256.
 */
object Backup {
    private val MAGIC = "MONOBK1".toByteArray()
    private const val ITER = 150_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password, salt, ITER, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    // ---------------------------------------------------------------- json mapping

    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null

    private fun friend(f: Friend) = JSONObject().put("pk", f.pubkey).put("u", f.username).put("n", f.displayName).put("e", f.emoji)
        .put("b", f.bio).put("st", f.status).put("c", f.createdAt).put("ls", f.lastSeen).put("v", f.verified)

    private fun friend(o: JSONObject) = Friend(
        o.getString("pk"), o.optString("u"), o.optString("n"), o.optString("e"), o.optString("b"), o.optInt("st"),
        o.optLong("c"), o.optLong("ls"), verified = o.optBoolean("v"),
    )

    private fun group(g: Group) = JSONObject().put("id", g.id).put("n", g.name).put("e", g.emoji).put("m", g.members).put("a", g.admin).put("c", g.createdAt)
    private fun group(o: JSONObject) = Group(o.getString("id"), o.optString("n"), o.optString("e"), o.optString("m"), o.optString("a"), o.optLong("c"))

    private fun msg(m: Message) = JSONObject().put("id", m.id).put("cv", m.convId).put("s", m.sender).put("o", m.outgoing).put("ts", m.ts)
        .put("k", m.kind).put("x", m.text).put("mp", m.mediaPath?.let { File(it).name } ?: JSONObject.NULL).put("ex", m.extra ?: JSONObject.NULL)
        .put("st", m.status).put("exp", m.expiresAt).put("ed", m.edited)

    private fun msg(o: JSONObject, mediaDir: File) = Message(
        o.getString("id"), o.optString("cv"), o.optString("s"), o.optBoolean("o"), o.optLong("ts"), o.optString("k"),
        text = o.optString("x"),
        mediaPath = if (o.isNull("mp")) null else File(mediaDir, o.optString("mp")).absolutePath,
        extra = if (o.isNull("ex")) null else o.optString("ex"),
        status = o.optInt("st"), progress = 1f, expiresAt = o.optLong("exp"), edited = o.optBoolean("ed"),
    )

    private fun event(e: AgendaEvent) = JSONObject().put("id", e.id).put("t", e.title).put("d", e.description).put("n", e.notes)
        .put("s", e.start).put("en", e.end ?: JSONObject.NULL).put("ad", e.allDay).put("e", e.emoji).put("r", e.reminders)
        .put("rp", e.repeat).put("ru", e.repeatUntil ?: JSONObject.NULL).put("o", e.owner).put("i", e.invitees).put("rs", e.rsvp)
        .put("is", e.inviteState).put("c", e.createdAt).put("u", e.updatedAt)

    private fun event(o: JSONObject) = AgendaEvent(
        o.getString("id"), o.optString("t"), o.optString("d"), o.optString("n"), o.optLong("s"), o.optLongOrNull("en"),
        o.optBoolean("ad"), o.optString("e"), o.optString("r"), o.optInt("rp"), o.optLongOrNull("ru"), o.optString("o"),
        o.optString("i"), o.optString("rs", "{}"), o.optInt("is"), o.optLong("c"), o.optLong("u"),
    )

    private fun list(l: SharedList) = JSONObject().put("id", l.id).put("t", l.title).put("e", l.emoji).put("m", l.members).put("o", l.owner).put("c", l.createdAt).put("u", l.updatedAt)
    private fun list(o: JSONObject) = SharedList(o.getString("id"), o.optString("t"), o.optString("e"), o.optString("m"), o.optString("o"), o.optLong("c"), o.optLong("u"))

    private fun item(i: ListItem) = JSONObject().put("id", i.id).put("l", i.listId).put("x", i.text).put("d", i.done).put("b", i.by).put("u", i.updatedAt).put("del", i.deleted)
    private fun item(o: JSONObject) = ListItem(o.getString("id"), o.optString("l"), o.optString("x"), o.optBoolean("d"), o.optString("b"), o.optLong("u"), o.optBoolean("del"))

    private fun alert(a: ArrivalAlert) = JSONObject().put("id", a.id).put("f", a.friend).put("la", a.lat).put("lo", a.lon).put("r", a.radius).put("l", a.label).put("c", a.createdAt)
    private fun alert(o: JSONObject) = ArrivalAlert(o.getString("id"), o.optString("f"), o.optDoubleOrNull("la") ?: 0.0, o.optDoubleOrNull("lo") ?: 0.0, o.optInt("r"), o.optString("l"), o.optLong("c"))

    // ---------------------------------------------------------------- export / import

    suspend fun export(ctx: Context, out: OutputStream, password: CharArray) {
        val db = Graph.db
        val keys = Graph.identity.keys ?: throw IllegalStateException("no identity")
        val data = JSONObject()
            .put("v", 1)
            .put("sk", Hex.encode(keys.priv))
            .put("profile", Graph.identity.profile.value?.toJson() ?: JSONObject())
            .put("friends", JSONArray().also { a -> db.friends().allNow().forEach { a.put(friend(it)) } })
            .put("groups", JSONArray().also { a -> db.groups().allNow().forEach { a.put(group(it)) } })
            .put("messages", JSONArray().also { a -> db.messages().allNow().forEach { a.put(msg(it)) } })
            .put("agenda", JSONArray().also { a -> db.agenda().allNow().forEach { a.put(event(it)) } })
            .put("lists", JSONArray().also { a -> db.lists().listsNow().forEach { a.put(list(it)) } })
            .put("items", JSONArray().also { a -> db.lists().allItems().forEach { a.put(item(it)) } })
            .put("alerts", JSONArray().also { a -> db.social().allAlerts().forEach { a.put(alert(it)) } })
            .put("reactions", JSONArray().also { a ->
                db.social().allReactions().forEach { r -> a.put(JSONObject().put("m", r.msgId).put("s", r.sender).put("c", r.convId).put("e", r.emoji).put("t", r.ts)) }
            })
            .put("votes", JSONArray().also { a ->
                db.social().allVotes().forEach { v -> a.put(JSONObject().put("m", v.msgId).put("s", v.voter).put("c", v.convId).put("o", v.options).put("t", v.ts)) }
            })
            .put("conv", JSONArray().also { a ->
                db.social().allSettings().forEach { c -> a.put(JSONObject().put("c", c.convId).put("t", c.ttlSec)) }
            })

        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(data.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            Media.dir(ctx).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("media/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(zipBytes.toByteArray())
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(ct)
        out.flush()
    }

    /** restores everything; returns false on wrong password / corrupted file */
    suspend fun import(ctx: Context, input: InputStream, password: CharArray): Boolean {
        val all = input.readBytes()
        if (all.size < MAGIC.size + 28 || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return false
        val salt = all.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = all.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val zipBytes = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            c.doFinal(all, MAGIC.size + 28, all.size - MAGIC.size - 28)
        } catch (e: Exception) {
            return false
        }
        val mediaDir = Media.dir(ctx)
        var data: JSONObject? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == "data.json") {
                    data = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                } else if (e.name.startsWith("media/") && !e.name.contains("..")) {
                    File(mediaDir, e.name.removePrefix("media/")).outputStream().use { zip.copyTo(it) }
                }
                e = zip.nextEntry
            }
        }
        val d = data ?: return false
        val sk = Hex.decode(d.getString("sk"))
        val profile = Profile.fromJson(d.optJSONObject("profile") ?: JSONObject())
        val db = Graph.db
        fun arr(k: String, f: (JSONObject) -> Unit) {
            val a = d.optJSONArray(k) ?: return
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { runCatching { f(it) } }
        }
        val friends = ArrayList<Friend>(); arr("friends") { friends += friend(it) }
        val groups = ArrayList<Group>(); arr("groups") { groups += group(it) }
        val msgs = ArrayList<Message>(); arr("messages") { msgs += msg(it, mediaDir) }
        val events = ArrayList<AgendaEvent>(); arr("agenda") { events += event(it) }
        val lists = ArrayList<SharedList>(); arr("lists") { lists += list(it) }
        val items = ArrayList<ListItem>(); arr("items") { items += item(it) }
        val alerts = ArrayList<ArrivalAlert>(); arr("alerts") { alerts += alert(it) }
        val reactions = ArrayList<Reaction>(); arr("reactions") { reactions += Reaction(it.getString("m"), it.getString("s"), it.optString("c"), it.optString("e"), it.optLong("t")) }
        val votes = ArrayList<Vote>(); arr("votes") { votes += Vote(it.getString("m"), it.getString("s"), it.optString("c"), it.optString("o"), it.optLong("t")) }
        val conv = ArrayList<ConvSetting>(); arr("conv") { conv += ConvSetting(it.getString("c"), it.optLong("t")) }

        friends.forEach { db.friends().upsert(it) }
        groups.forEach { db.groups().upsert(it) }
        msgs.forEach { db.messages().upsert(it) }
        events.forEach { db.agenda().upsert(it) }
        lists.forEach { db.lists().upsert(it) }
        items.forEach { db.lists().upsertItem(it) }
        alerts.forEach { db.social().saveAlert(it) }
        reactions.forEach { db.social().react(it) }
        votes.forEach { db.social().vote(it) }
        conv.forEach { db.social().saveSetting(it) }
        Graph.identity.create(profile, sk)
        events.forEach { ReminderScheduler.schedule(ctx, it) }
        return true
    }
}
