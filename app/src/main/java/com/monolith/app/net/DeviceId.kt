package com.monolith.app.net

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import com.monolith.app.crypto.Hex
import java.security.MessageDigest
import java.util.UUID

/**
 * Anonymous codes of this phone, so that the developer can block a phone and not only an account.
 * They are hashes: the phone's real identifiers never leave it. Two codes, because one of them
 * (the Android ID) changes when the app is signed with a different key, the other (the DRM one) does not.
 * They travel only to the developer, encrypted like any message.
 */
object DeviceId {
    private val WIDEVINE = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    @Volatile private var cached: List<String>? = null

    fun codes(ctx: Context): List<String> = cached ?: compute(ctx).also { cached = it }

    /** what the developer sees next to the codes */
    val model: String get() = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()

    private fun compute(ctx: Context): List<String> {
        val out = ArrayList<String>()
        runCatching {
            val drm = MediaDrm(WIDEVINE)
            try {
                out += hash("wv:" + Hex.encode(drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)))
            } finally {
                if (Build.VERSION.SDK_INT >= 28) drm.close() else @Suppress("DEPRECATION") drm.release()
            }
        }
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() }?.let { out += hash("aid:$it") }
        }
        return out.distinct()
    }

    private fun hash(s: String): String =
        Hex.encode(MessageDigest.getInstance("SHA-256").digest(("monolith-device|$s").toByteArray())).take(32)
}
