package com.monolith.app.core

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.monolith.app.crypto.Bech32
import com.monolith.app.crypto.Hex
import com.monolith.app.crypto.Secp256k1
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun now(): Long = System.currentTimeMillis()
fun nowSec(): Long = System.currentTimeMillis() / 1000
fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(24)

val IT: Locale = Locale.ITALIAN

/** short discriminator shown after usernames, e.g. mario#3bf0 */
fun tagOf(pubkey: String): String = pubkey.take(4)

fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun List<String>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

// ------------------------------------------------------------------ time formatting

object Fmt {
    private fun f(p: String) = SimpleDateFormat(p, IT)
    fun time(ms: Long): String = f("HH:mm").format(Date(ms))
    fun dayLong(ms: Long): String = f("EEEE d MMMM").format(Date(ms)).replaceFirstChar { it.titlecase(IT) }
    fun dayShort(ms: Long): String = f("EEE d MMM").format(Date(ms)).replaceFirstChar { it.titlecase(IT) }
    fun monthYear(ms: Long): String = f("MMMM yyyy").format(Date(ms)).replaceFirstChar { it.titlecase(IT) }
    fun weekdayLetter(ms: Long): String = f("EEE").format(Date(ms)).take(3).replaceFirstChar { it.titlecase(IT) }
    fun dayNum(ms: Long): String = f("d").format(Date(ms))

    fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun chatStamp(ms: Long): String {
        val n = now()
        return when {
            sameDay(ms, n) -> time(ms)
            sameDay(ms, n - 86_400_000L) -> "Ieri"
            n - ms < 6 * 86_400_000L -> f("EEEE").format(Date(ms)).replaceFirstChar { it.titlecase(IT) }
            else -> f("dd/MM/yy").format(Date(ms))
        }
    }

    fun ago(ms: Long): String {
        if (ms <= 0) return "mai"
        val d = (now() - ms) / 1000
        return when {
            d < 10 -> "adesso"
            d < 60 -> "${d}s fa"
            d < 3600 -> "${d / 60} min fa"
            d < 86400 -> "${d / 3600} h fa"
            else -> dayShort(ms)
        }
    }

    fun remaining(untilMs: Long): String {
        val m = ((untilMs - now()) / 60000).coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }
}

// ------------------------------------------------------------------ emoji rendering

object EmojiArt {
    /** renders an emoji into a square bitmap; used for notification icons and map markers */
    fun bitmap(emoji: String, sizePx: Int, background: Int? = null, ring: Int? = null): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        if (background != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
            c.drawCircle(r, r, r - (if (ring != null) sizePx * 0.04f else 0f), p)
        }
        if (ring != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ring; style = Paint.Style.STROKE; strokeWidth = sizePx * 0.07f
            }
            c.drawOval(RectF(sizePx * 0.05f, sizePx * 0.05f, sizePx * 0.95f, sizePx * 0.95f), p)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * (if (background != null) 0.56f else 0.82f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = Color.BLACK
        }
        val fm = tp.fontMetrics
        val y = r - (fm.ascent + fm.descent) / 2f
        c.drawText(emoji.ifBlank { "◼" }, r, y, tp)
        return bmp
    }
}

// ------------------------------------------------------------------ preferences

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("monolith_prefs", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
            "wss://offchain.pub",
        )
        val DEFAULT_STUN = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun.cloudflare.com:3478",
        )
    }

    private val _theme = MutableStateFlow(sp.getInt("theme", 0))
    /** 0 = system, 1 = light, 2 = dark */
    val theme: StateFlow<Int> = _theme.asStateFlow()
    fun setTheme(v: Int) { sp.edit().putInt("theme", v).apply(); _theme.value = v }

    private val _relays = MutableStateFlow(
        sp.getString("relays", null)?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_RELAYS
    )
    val relays: StateFlow<List<String>> = _relays.asStateFlow()
    fun setRelays(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.startsWith("wss://") || it.startsWith("ws://") }.distinct()
        sp.edit().putString("relays", clean.joinToString("\n")).apply(); _relays.value = clean
    }

    var turnUrl: String
        get() = sp.getString("turn_url", "") ?: ""
        set(v) = sp.edit().putString("turn_url", v).apply()
    var turnUser: String
        get() = sp.getString("turn_user", "") ?: ""
        set(v) = sp.edit().putString("turn_user", v).apply()
    var turnPass: String
        get() = sp.getString("turn_pass", "") ?: ""
        set(v) = sp.edit().putString("turn_pass", v).apply()

    var lastWrapSec: Long
        get() = sp.getLong("last_wrap", 0L)
        set(v) = sp.edit().putLong("last_wrap", v).apply()

    var permissionsDone: Boolean
        get() = sp.getBoolean("perm_done", false)
        set(v) = sp.edit().putBoolean("perm_done", v).apply()

    var alwaysOnline: Boolean
        get() = sp.getBoolean("always_online", true)
        set(v) = sp.edit().putBoolean("always_online", v).apply()
}

// ------------------------------------------------------------------ identity

class Keys(val priv: ByteArray, val pub: String)

data class Profile(
    val username: String,
    val displayName: String,
    val emoji: String,
    val bio: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("u", username).put("n", displayName).put("e", emoji).put("b", bio)

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            username = o.optString("u"),
            displayName = o.optString("n").ifBlank { o.optString("u") },
            emoji = o.optString("e").ifBlank { "🙂" },
            bio = o.optString("b"),
        )

        private val USERNAME = Regex("^[a-z0-9_.]{3,20}$")
        fun normalizeUsername(s: String): String = s.trim().lowercase(Locale.ROOT).removePrefix("@")
        fun isValidUsername(s: String) = USERNAME.matches(s)
    }
}

class IdentityStore(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("monolith_identity", Context.MODE_PRIVATE)
    private val alias = "monolith_identity_key"

    @Volatile
    var keys: Keys? = null
        private set

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    init {
        runCatching {
            val enc = sp.getString("sk", null)
            if (enc != null) {
                val sk = decrypt(enc)
                keys = Keys(sk, Hex.encode(Secp256k1.publicKey(sk)))
                sp.getString("profile", null)?.let { _profile.value = Profile.fromJson(JSONObject(it)) }
            }
        }
    }

    fun hasIdentity() = keys != null && _profile.value != null

    fun create(profile: Profile, importedSecret: ByteArray? = null): Keys {
        val sk = importedSecret ?: Secp256k1.generatePrivateKey()
        sp.edit().putString("sk", encrypt(sk)).putString("profile", profile.toJson().toString()).apply()
        val k = Keys(sk, Hex.encode(Secp256k1.publicKey(sk)))
        keys = k
        _profile.value = profile
        return k
    }

    fun updateProfile(p: Profile) {
        sp.edit().putString("profile", p.toJson().toString()).apply()
        _profile.value = p
    }

    fun npub(): String = keys?.let { Bech32.encode("npub", Hex.decode(it.pub)) } ?: ""
    fun nsec(): String = keys?.let { Bech32.encode("nsec", it.priv) } ?: ""

    fun reset() {
        sp.edit().clear().apply()
        keys = null
        _profile.value = null
    }

    companion object {
        /** accepts nsec1... or 64 hex chars */
        fun parseSecret(input: String): ByteArray? {
            return try {
                val s = input.trim()
                val sk: ByteArray? = if (s.startsWith("nsec1")) {
                    val d = Bech32.decode(s)
                    if (d[0] == "nsec") d[1] as ByteArray else null
                } else Hex.decode(s)
                if (sk != null && Secp256k1.isValidPrivateKey(sk)) sk else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = c.doFinal(data)
        return Base64.getEncoder().encodeToString(c.iv) + ":" + Base64.getEncoder().encodeToString(ct)
    }

    private fun decrypt(s: String): ByteArray {
        val (iv, ct) = s.split(":").let { Base64.getDecoder().decode(it[0]) to Base64.getDecoder().decode(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return c.doFinal(ct)
    }
}

/** tiny holder so non-UI code can reach the Application */
object AppCtx {
    lateinit var app: Application
}
