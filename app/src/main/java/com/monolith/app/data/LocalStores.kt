package com.monolith.app.data

import android.content.Context
import com.monolith.app.core.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/** day and month of a birthday */
data class Birthday(val month: Int, val day: Int) {
    fun code() = "%02d-%02d".format(month, day)
    fun isOn(dayStart: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        if (c.get(Calendar.MONTH) + 1 == month && c.get(Calendar.DAY_OF_MONTH) == day) return true
        // 29 February outside leap years: celebrated on the 28th
        if (month == 2 && day == 29 && !isLeap(c.get(Calendar.YEAR))) return c.get(Calendar.MONTH) == 1 && c.get(Calendar.DAY_OF_MONTH) == 28
        return false
    }

    companion object {
        fun parse(s: String?): Birthday? {
            val p = s?.split("-") ?: return null
            if (p.size != 2) return null
            val m = p[0].toIntOrNull() ?: return null
            val d = p[1].toIntOrNull() ?: return null
            return if (m in 1..12 && d in 1..31) Birthday(m, d) else null
        }

        private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    }
}

/** my birthday and the ones my friends shared with me (never public) */
class BirthdayStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("birthdays", Context.MODE_PRIVATE)
    private val _friends = MutableStateFlow(load())
    val friends: StateFlow<Map<String, Birthday>> = _friends.asStateFlow()
    private val _mine = MutableStateFlow(Birthday.parse(sp.getString("__me", null)))
    val mine: StateFlow<Birthday?> = _mine.asStateFlow()

    private fun load(): Map<String, Birthday> =
        sp.all.filterKeys { it != "__me" && !it.startsWith("__") }.mapNotNull { (k, v) -> Birthday.parse(v as? String)?.let { k to it } }.toMap()

    fun setFriend(pk: String, b: Birthday?) {
        if (b == null) sp.edit().remove(pk).apply() else sp.edit().putString(pk, b.code()).apply()
        _friends.value = load()
    }

    fun setMine(b: Birthday?) {
        if (b == null) sp.edit().remove("__me").apply() else sp.edit().putString("__me", b.code()).apply()
        _mine.value = b
    }

    fun on(dayStart: Long): List<String> = _friends.value.filter { it.value.isOn(dayStart) }.keys.toList()

    /** each birthday is announced once a year */
    fun markAnnounced(pk: String, year: Int): Boolean {
        val k = "__ann_${pk}_$year"
        if (sp.contains(k)) return false
        sp.edit().putBoolean(k, true).apply()
        return true
    }
}

/** occurrences of agenda events marked as done */
class DoneStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("agenda_done", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun key(id: String, occ: Long) = "$id|$occ"

    fun isDone(id: String, occ: Long) = sp.contains(key(id, occ))

    fun count() = sp.all.size

    fun set(id: String, occ: Long, done: Boolean) {
        val ed = sp.edit()
        if (done) ed.putLong(key(id, occ), now()) else ed.remove(key(id, occ))
        // forget very old entries
        val old = now() - 400L * 86_400_000
        sp.all.forEach { (k, v) -> if (v is Long && v < old) ed.remove(k) }
        ed.apply()
        _version.value++
    }
}
