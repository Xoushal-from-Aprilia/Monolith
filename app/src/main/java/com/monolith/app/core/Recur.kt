package com.monolith.app.core

import com.monolith.app.data.AgendaEvent
import java.util.Calendar

/** Recurring events: occurrence math shared by the agenda, the widget and the reminders. */
object Recur {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3
    const val YEARLY = 4

    fun label(r: Int): String = when (r) {
        DAILY -> t("Ogni giorno")
        WEEKLY -> t("Ogni settimana")
        MONTHLY -> t("Ogni mese")
        YEARLY -> t("Ogni anno")
        else -> t("Mai")
    }

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    /** start of the occurrence on [day] (any time within that day), or null if the event doesn't happen that day */
    fun occurrenceOn(e: AgendaEvent, day: Long): Long? {
        val d = Fmt.startOfDay(day)
        val startDay = Fmt.startOfDay(e.start)
        if (d < startDay) return null
        if (e.repeat == NONE) {
            val endDay = e.end?.let { Fmt.startOfDay(it) } ?: startDay
            return if (d in startDay..maxOf(startDay, endDay)) (if (d == startDay) e.start else d) else null
        }
        e.repeatUntil?.let { if (d > Fmt.startOfDay(it)) return null }
        val s = cal(e.start)
        val c = cal(d)
        val ok = when (e.repeat) {
            DAILY -> true
            WEEKLY -> c.get(Calendar.DAY_OF_WEEK) == s.get(Calendar.DAY_OF_WEEK)
            MONTHLY -> c.get(Calendar.DAY_OF_MONTH) == s.get(Calendar.DAY_OF_MONTH)
            YEARLY -> c.get(Calendar.DAY_OF_MONTH) == s.get(Calendar.DAY_OF_MONTH) && c.get(Calendar.MONTH) == s.get(Calendar.MONTH)
            else -> false
        }
        if (!ok) return null
        c.set(Calendar.HOUR_OF_DAY, s.get(Calendar.HOUR_OF_DAY))
        c.set(Calendar.MINUTE, s.get(Calendar.MINUTE))
        return c.timeInMillis
    }

    fun occursOn(e: AgendaEvent, day: Long) = occurrenceOn(e, day) != null

    /** first occurrence whose start is >= [t] */
    fun nextAfter(e: AgendaEvent, t: Long): Long? {
        if (e.repeat == NONE) return if (e.start >= t) e.start else null
        var day = Fmt.startOfDay(maxOf(t, e.start))
        repeat(800) {
            val o = occurrenceOn(e, day)
            if (o != null && o >= t) return o
            day = cal(day).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            e.repeatUntil?.let { if (day > Fmt.startOfDay(it)) return null }
        }
        return null
    }

    /** upcoming occurrences (event, start) after [from], sorted, at most [limit] */
    fun upcoming(events: List<AgendaEvent>, from: Long, limit: Int): List<Pair<AgendaEvent, Long>> =
        events.mapNotNull { e -> nextAfter(e, from)?.let { e to it } }.sortedBy { it.second }.take(limit)
}
