package com.monolith.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Lightweight translation layer. Italian is the source language: every UI string is written in
 * Italian and looked up in assets/i18n.tsv (columns: it, en, fr, de, ru, pl, cs).
 * [lang] is Compose state, so changing it recomposes the whole UI instantly.
 */
object I18n {
    val LANGS = listOf(
        "it" to "Italiano",
        "en" to "English",
        "fr" to "Français",
        "de" to "Deutsch",
        "ru" to "Русский",
        "pl" to "Polski",
        "cs" to "Čeština",
    )

    var lang by mutableStateOf("it")
        private set

    private var table: Map<String, Array<String>> = emptyMap()
    private const val PREF = "monolith_lang"

    val locale: Locale get() = Locale.forLanguageTag(lang)

    fun init(ctx: Context) {
        runCatching {
            val map = HashMap<String, Array<String>>(1024)
            ctx.assets.open("i18n.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    if (raw.isBlank() || raw.startsWith("#")) return@forEach
                    val cols = raw.split('\t').map { it.replace("\\n", "\n") }
                    if (cols.size >= 2) map[cols[0]] = cols.drop(1).toTypedArray()
                }
            }
            table = map
        }
        val saved = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("lang", null)
        val system = Locale.getDefault().language
        lang = saved ?: LANGS.firstOrNull { it.first == system }?.first ?: "en"
        Locale.setDefault(locale)
    }

    fun set(ctx: Context, code: String) {
        if (LANGS.none { it.first == code }) return
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("lang", code).apply()
        lang = code
        Locale.setDefault(locale)
    }

    fun t(key: String, vararg args: Any?): String {
        val l = lang
        var s = key
        if (l != "it") {
            val idx = LANGS.indexOfFirst { it.first == l } - 1
            val row = table[key]
            if (row != null && idx >= 0 && idx < row.size && row[idx].isNotEmpty()) s = row[idx]
        }
        if (args.isEmpty()) return s
        var out = s
        args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
        return out
    }
}

fun t(key: String, vararg args: Any?): String = I18n.t(key, *args)
