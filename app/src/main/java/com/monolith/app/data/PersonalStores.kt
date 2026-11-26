package com.monolith.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** chats where screenshots are blocked on both phones */
class ChatGuard(ctx: Context) {
    private val sp = ctx.getSharedPreferences("chat_guard", Context.MODE_PRIVATE)
    private val _blocked = MutableStateFlow(sp.getStringSet("blocked", emptySet())?.toSet() ?: emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun set(conv: String, on: Boolean) {
        val next = if (on) _blocked.value + conv else _blocked.value - conv
        sp.edit().putStringSet("blocked", next).apply()
        _blocked.value = next
    }

    fun clear() {
        sp.edit().clear().apply()
        _blocked.value = emptySet()
    }
}

/** what friends see together with your SOS; it never leaves the phone otherwise */
data class Medical(
    val blood: String = "",
    val allergies: String = "",
    val medicines: String = "",
    val conditions: String = "",
    val contact: String = "",
    val notes: String = "",
) {
    val isEmpty get() = listOf(blood, allergies, medicines, conditions, contact, notes).all { it.isBlank() }

    fun toJson(): JSONObject = JSONObject().put("b", blood).put("a", allergies).put("m", medicines).put("c", conditions).put("p", contact).put("n", notes)

    companion object {
        fun fromJson(o: JSONObject?): Medical? = o?.let {
            Medical(it.optString("b"), it.optString("a"), it.optString("m"), it.optString("c"), it.optString("p"), it.optString("n")).takeIf { m -> !m.isEmpty }
        }
    }
}

class MedicalStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("medical", Context.MODE_PRIVATE)
    private val _card = MutableStateFlow(runCatching { Medical.fromJson(JSONObject(sp.getString("card", "{}") ?: "{}")) }.getOrNull() ?: Medical())
    val card: StateFlow<Medical> = _card.asStateFlow()

    fun save(m: Medical) {
        sp.edit().putString("card", m.toJson().toString()).apply()
        _card.value = m
    }

    fun clear() = save(Medical())
}

/** the app icon on the home screen: one launcher entry per look, only one enabled */
object AppIcons {
    data class Look(val key: String, val label: String, val bg: Long, val fg: String)

    val LOOKS = listOf(
        Look("IconClassic", "Classica" /*i18n*/, 0xFFF4F4F2, "ic_monolith_fg"),
        Look("IconNight", "Notte" /*i18n*/, 0xFF0A0A0A, "ic_monolith_fg_night"),
        Look("IconStone", "Pietra" /*i18n*/, 0xFF9A9A98, "ic_fg_stone"),
        Look("IconOutline", "Contorno" /*i18n*/, 0xFFF4F4F2, "ic_fg_outline"),
        Look("IconStars", "Stelle" /*i18n*/, 0xFF0A0A0A, "ic_fg_stars"),
        Look("IconSlab", "Lastra" /*i18n*/, 0xFFFFFFFF, "ic_fg_slab"),
    )

    fun current(ctx: Context): String = ctx.getSharedPreferences("app_icon", Context.MODE_PRIVATE).getString("key", "IconClassic") ?: "IconClassic"

    private fun comp(ctx: Context, key: String) = ComponentName(ctx.packageName, "com.monolith.app.$key")

    private fun isOn(pm: PackageManager, ctx: Context, key: String): Boolean = when (pm.getComponentEnabledSetting(comp(ctx, key))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> key == "IconClassic"   // the manifest turns on only the classic one
        else -> false
    }

    /**
     * Switches the home screen entry. The launcher only notices for sure when the app is closed, so the entry
     * in use is turned off last and without DONT_KILL_APP: the system closes Monolith and the launcher
     * redraws the icon. The choice is saved first, because the process ends right after.
     */
    fun apply(ctx: Context, key: String) {
        val pm = ctx.packageManager
        ctx.getSharedPreferences("app_icon", Context.MODE_PRIVATE).edit().putString("key", key).commit()
        val sync = if (android.os.Build.VERSION.SDK_INT >= 33) PackageManager.SYNCHRONOUS else 0
        pm.setComponentEnabledSetting(comp(ctx, key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP or sync)
        val inUse = LOOKS.filter { it.key != key && isOn(pm, ctx, it.key) }.map { it.key }
        LOOKS.filter { it.key != key && it.key !in inUse }.forEach {
            pm.setComponentEnabledSetting(comp(ctx, it.key), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP or sync)
        }
        inUse.forEachIndexed { i, k ->
            val last = i == inUse.lastIndex
            pm.setComponentEnabledSetting(comp(ctx, k), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, if (last) sync else PackageManager.DONT_KILL_APP or sync)
        }
    }

    /** the entry that is really on (the saved choice can be wrong if the app was closed halfway) */
    fun actual(ctx: Context): String = LOOKS.firstOrNull { isOn(ctx.packageManager, ctx, it.key) }?.key ?: current(ctx)
}
