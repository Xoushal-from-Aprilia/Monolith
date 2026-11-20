package com.monolith.app.net

import android.content.Context
import com.monolith.app.Graph
import com.monolith.app.core.I18n
import com.monolith.app.core.newId
import com.monolith.app.core.now
import com.monolith.app.core.strings
import com.monolith.app.core.t
import com.monolith.app.core.toJsonArray
import com.monolith.app.crypto.Hex
import com.monolith.app.data.FriendStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.abs

// ====================================================================== shared expenses

data class Bill(val id: String, val name: String, val emoji: String, val members: List<String>, val createdBy: String, val updatedAt: Long)

data class Expense(
    val id: String,
    val billId: String,
    val payer: String,
    val cents: Long,
    val desc: String,
    val split: List<String>,
    val at: Long,
    val by: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** who gives how much to whom to settle everything */
data class Transfer(val from: String, val to: String, val cents: Long)

fun money(cents: Long): String {
    val neg = cents < 0
    val a = abs(cents)
    val s = "${a / 100}" + (if (I18n.lang == "en") "." else ",") + "%02d".format(a % 100)
    return (if (neg) "-" else "") + if (I18n.lang == "en") "€$s" else "$s €"
}

/** "12,5" / "12.50" -> cents */
fun parseMoney(s: String): Long? {
    val clean = s.trim().replace("€", "").replace(" ", "").replace(',', '.')
    val v = clean.toBigDecimalOrNull() ?: return null
    if (v.signum() <= 0) return null
    return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

// ====================================================================== habits

data class Habit(val id: String, val name: String, val emoji: String, val members: List<String>, val createdBy: String, val updatedAt: Long, val deleted: Boolean = false)

fun dayCode(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/**
 * Spese condivise, album and habits done together. Expenses and habits sync with their own small
 * messages; albums are hidden groups, so photos travel like any chat photo.
 */
class Together(ctx: Context) {
    private val file = File(ctx.filesDir, "together.json")
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills.asStateFlow()
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()
    /** habitId -> pubkey -> days done */
    private val _checks = MutableStateFlow<Map<String, Map<String, Set<String>>>>(emptyMap())
    val checks: StateFlow<Map<String, Map<String, Set<String>>>> = _checks.asStateFlow()

    private fun me() = Graph.identity.keys?.pub

    init {
        runCatching {
            if (!file.exists()) return@runCatching
            val o = JSONObject(file.readText())
            _bills.value = arr(o, "bills").mapNotNull(::billFrom)
            _expenses.value = arr(o, "exp").mapNotNull(::expFrom)
            _habits.value = arr(o, "habits").mapNotNull(::habitFrom)
            val c = o.optJSONObject("checks") ?: JSONObject()
            val out = HashMap<String, Map<String, Set<String>>>()
            c.keys().forEach { hid ->
                val per = c.optJSONObject(hid) ?: return@forEach
                val m = HashMap<String, Set<String>>()
                per.keys().forEach { pk -> m[pk] = (per.optJSONArray(pk) ?: JSONArray()).strings().toSet() }
                out[hid] = m
            }
            _checks.value = out
        }
    }

    private fun arr(o: JSONObject, k: String): List<JSONObject> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    @Synchronized
    private fun save() {
        runCatching {
            val c = JSONObject()
            _checks.value.forEach { (hid, per) ->
                val o = JSONObject()
                per.forEach { (pk, days) -> o.put(pk, days.sorted().takeLast(400).toJsonArray()) }
                c.put(hid, o)
            }
            val root = JSONObject()
                .put("bills", JSONArray().also { a -> _bills.value.forEach { a.put(billJson(it)) } })
                .put("exp", JSONArray().also { a -> _expenses.value.forEach { a.put(expJson(it)) } })
                .put("habits", JSONArray().also { a -> _habits.value.forEach { a.put(habitJson(it)) } })
                .put("checks", c)
            file.writeText(root.toString())
        }
    }

    fun clear() {
        _bills.value = emptyList(); _expenses.value = emptyList(); _habits.value = emptyList(); _checks.value = emptyMap()
        runCatching { file.delete() }
    }

    // ------------------------------------------------------------------ json

    private fun members(o: JSONObject) = (o.optJSONArray("m") ?: JSONArray()).strings().filter { Hex.isHex64(it) }

    private fun billJson(b: Bill) = JSONObject().put("id", b.id).put("n", b.name).put("e", b.emoji).put("m", b.members.toJsonArray()).put("by", b.createdBy).put("u", b.updatedAt)
    private fun billFrom(o: JSONObject) = runCatching { Bill(o.getString("id"), o.optString("n").take(60), o.optString("e", "💶"), members(o), o.optString("by"), o.optLong("u")) }.getOrNull()

    private fun expJson(e: Expense) = JSONObject().put("id", e.id).put("b", e.billId).put("p", e.payer).put("c", e.cents).put("d", e.desc)
        .put("s", e.split.toJsonArray()).put("at", e.at).put("by", e.by).put("u", e.updatedAt).put("del", e.deleted)
    private fun expFrom(o: JSONObject) = runCatching {
        Expense(
            o.getString("id"), o.getString("b"), o.optString("p"), o.optLong("c").coerceIn(0, 100_000_000_00L), o.optString("d").take(80),
            (o.optJSONArray("s") ?: JSONArray()).strings().filter { Hex.isHex64(it) }, o.optLong("at"), o.optString("by"), o.optLong("u"), o.optBoolean("del"),
        )
    }.getOrNull()

    private fun habitJson(h: Habit) = JSONObject().put("id", h.id).put("n", h.name).put("e", h.emoji).put("m", h.members.toJsonArray()).put("by", h.createdBy).put("u", h.updatedAt).put("del", h.deleted)
    private fun habitFrom(o: JSONObject) = runCatching { Habit(o.getString("id"), o.optString("n").take(60), o.optString("e", "✅"), members(o), o.optString("by"), o.optLong("u"), o.optBoolean("del")) }.getOrNull()

    private suspend fun sendTo(members: List<String>, p: JSONObject) {
        val k = me() ?: return
        members.filter { it != k }.forEach { Graph.messenger.sendStored(it, p) }
    }

    private suspend fun isFriend(pk: String) = Graph.db.friends().get(pk)?.status == FriendStatus.FRIEND

    // ------------------------------------------------------------------ bills

    fun bill(id: String) = _bills.value.firstOrNull { it.id == id }
    fun expensesOf(id: String) = _expenses.value.filter { it.billId == id && !it.deleted }.sortedByDescending { it.at }

    /** net balance per member (positive = should receive) */
    fun balances(id: String): Map<String, Long> {
        val b = bill(id) ?: return emptyMap()
        val out = HashMap<String, Long>()
        b.members.forEach { out[it] = 0L }
        expensesOf(id).forEach { e ->
            val split = e.split.ifEmpty { b.members }
            out[e.payer] = (out[e.payer] ?: 0L) + e.cents
            val share = e.cents / split.size
            var rest = e.cents - share * split.size
            split.sorted().forEach { pk ->
                val extra = if (rest > 0) 1L else 0L
                rest -= extra
                out[pk] = (out[pk] ?: 0L) - share - extra
            }
        }
        return out
    }

    /** few transfers that settle everything: the biggest debtor pays the biggest creditor */
    fun settle(id: String): List<Transfer> {
        val bal = balances(id).filterValues { it != 0L }.toMutableMap()
        val out = ArrayList<Transfer>()
        while (true) {
            val cred = bal.maxByOrNull { it.value } ?: break
            val debt = bal.minByOrNull { it.value } ?: break
            if (cred.value <= 0 || debt.value >= 0) break
            val amount = minOf(cred.value, -debt.value)
            out += Transfer(debt.key, cred.key, amount)
            bal[cred.key] = cred.value - amount
            bal[debt.key] = debt.value + amount
            bal.entries.removeAll { it.value == 0L }
        }
        return out
    }

    suspend fun saveBill(id: String?, name: String, emoji: String, friends: List<String>): String? {
        val k = me() ?: return null
        val old = id?.let { bill(it) }
        val b = Bill(old?.id ?: newId(), name.trim().ifBlank { t("Spese") }, emoji, (friends + k + (old?.members ?: emptyList())).distinct(), old?.createdBy ?: k, now())
        _bills.value = _bills.value.filter { it.id != b.id } + b
        save()
        sendTo(b.members, billJson(b).put("t", "bill"))
        // a newcomer also needs what was spent so far
        if (old != null) {
            val newcomers = b.members - old.members.toSet()
            expensesOf(b.id).forEach { e -> newcomers.forEach { pk -> Graph.messenger.sendStored(pk, expJson(e).put("t", "bexp")) } }
        }
        return b.id
    }

    suspend fun onBill(from: String, p: JSONObject) {
        val k = me() ?: return
        val b = billFrom(p) ?: return
        val old = bill(b.id)
        if (old == null && (!isFriend(from) || k !in b.members || from !in b.members)) return
        if (old != null && from !in old.members) return
        if (old != null && old.updatedAt >= b.updatedAt) return
        if (k !in b.members) {
            _bills.value = _bills.value.filter { it.id != b.id }
            _expenses.value = _expenses.value.filter { it.billId != b.id }
            save()
            return
        }
        _bills.value = _bills.value.filter { it.id != b.id } + b
        save()
        if (old == null) Graph.notifier.togetherShared(Graph.db.friends().get(from)?.displayName ?: t("Qualcuno"), "${b.emoji} ${b.name}", 0)
    }

    suspend fun saveExpense(billId: String, id: String?, payer: String, cents: Long, desc: String, split: List<String>, deleted: Boolean = false) {
        val k = me() ?: return
        val b = bill(billId) ?: return
        val old = id?.let { i -> _expenses.value.firstOrNull { it.id == i } }
        val e = Expense(old?.id ?: newId(), billId, payer, cents, desc.trim().ifBlank { t("Spesa") }, split.ifEmpty { b.members }, old?.at ?: now(), old?.by ?: k, now(), deleted)
        _expenses.value = _expenses.value.filter { it.id != e.id } + e
        save()
        sendTo(b.members, expJson(e).put("t", "bexp"))
    }

    fun onExpense(from: String, p: JSONObject) {
        val e = expFrom(p) ?: return
        val b = bill(e.billId) ?: return
        if (from !in b.members) return
        val old = _expenses.value.firstOrNull { it.id == e.id }
        if (old != null && old.updatedAt >= e.updatedAt) return
        _expenses.value = _expenses.value.filter { it.id != e.id } + e
        save()
    }

    suspend fun leaveBill(id: String) {
        val k = me() ?: return
        val b = bill(id) ?: return
        _bills.value = _bills.value.filter { it.id != id }
        _expenses.value = _expenses.value.filter { it.billId != id }
        save()
        sendTo(b.members, billJson(b.copy(members = b.members - k, updatedAt = now())).put("t", "bill"))
    }

    // ------------------------------------------------------------------ habits

    fun habit(id: String) = _habits.value.firstOrNull { it.id == id && !it.deleted }

    fun done(habitId: String, pk: String, day: String) = _checks.value[habitId]?.get(pk)?.contains(day) == true

    /** days in a row, ending today (or yesterday, if today isn't done yet) */
    fun streak(habitId: String, pk: String): Int {
        val days = _checks.value[habitId]?.get(pk) ?: return 0
        val c = Calendar.getInstance()
        if (dayCode(c.timeInMillis) !in days) c.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (dayCode(c.timeInMillis) in days) { n++; c.add(Calendar.DAY_OF_YEAR, -1) }
        return n
    }

    suspend fun saveHabit(id: String?, name: String, emoji: String, friends: List<String>, deleted: Boolean = false): String? {
        val k = me() ?: return null
        val old = id?.let { i -> _habits.value.firstOrNull { it.id == i } }
        val h = Habit(old?.id ?: newId(), name.trim().ifBlank { t("Abitudine") }, emoji, (friends + k + (old?.members ?: emptyList())).distinct(), old?.createdBy ?: k, now(), deleted)
        _habits.value = _habits.value.filter { it.id != h.id } + h
        save()
        sendTo(h.members, habitJson(h).put("t", "habit"))
        return h.id
    }

    suspend fun onHabit(from: String, p: JSONObject) {
        val k = me() ?: return
        val h = habitFrom(p) ?: return
        val old = _habits.value.firstOrNull { it.id == h.id }
        if (old == null && (!isFriend(from) || k !in h.members || from !in h.members)) return
        if (old != null && from !in old.members) return
        if (old != null && old.updatedAt >= h.updatedAt) return
        _habits.value = _habits.value.filter { it.id != h.id } + if (k in h.members) listOf(h) else emptyList()
        save()
        if (old == null && !h.deleted) Graph.notifier.togetherShared(Graph.db.friends().get(from)?.displayName ?: t("Qualcuno"), "${h.emoji} ${h.name}", 1)
    }

    suspend fun check(habitId: String, on: Boolean, day: String = dayCode(now())) {
        val k = me() ?: return
        val h = habit(habitId) ?: return
        setCheck(habitId, k, day, on)
        sendTo(h.members, JSONObject().put("t", "hcheck").put("id", habitId).put("d", day).put("on", on))
    }

    fun onCheck(from: String, p: JSONObject) {
        val h = habit(p.optString("id")) ?: return
        if (from !in h.members) return
        val day = p.optString("d")
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(day)) return
        setCheck(h.id, from, day, p.optBoolean("on"))
    }

    private fun setCheck(habitId: String, pk: String, day: String, on: Boolean) {
        val per = (_checks.value[habitId] ?: emptyMap()).toMutableMap()
        val days = (per[pk] ?: emptySet()).toMutableSet()
        if (on) days += day else days -= day
        per[pk] = days
        _checks.value = _checks.value + (habitId to per)
        save()
    }

    suspend fun leaveHabit(id: String) {
        val k = me() ?: return
        val h = _habits.value.firstOrNull { it.id == id } ?: return
        _habits.value = _habits.value.filter { it.id != id }
        save()
        sendTo(h.members, habitJson(h.copy(members = h.members - k, updatedAt = now())).put("t", "habit"))
    }
}
