package com.monolith.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.MainActivity
import com.monolith.app.core.Fmt
import com.monolith.app.core.Recur
import com.monolith.app.core.now
import com.monolith.app.core.t
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.InviteState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Foreground service that keeps Monolith reachable (messages, calls, live location). */
class MonolithService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        promote()
        Graph.startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        if (!Graph.prefs.alwaysOnline && !Graph.location.sharing.value && !Graph.calls.inCall && !Graph.placeWatch.active) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun baseType(): Int = when {
        Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> 0
    }

    private fun promote() {
        val sharing = Graph.location.sharing.value
        val inCall = Graph.calls.inCall
        var types = baseType()
        if (Build.VERSION.SDK_INT >= 29 && sharing && Graph.location.hasPermission()) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 30 && inCall && granted(Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (Build.VERSION.SDK_INT >= 30 && Graph.calls.videoCall && granted(Manifest.permission.CAMERA)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        val n = Graph.notifier.serviceNotification(sharing, inCall)
        try {
            ServiceCompat.startForeground(this, Notifier.ID_SERVICE, n, types)
        } catch (e: Exception) {
            try {
                ServiceCompat.startForeground(this, Notifier.ID_SERVICE, n, baseType())
            } catch (e2: Exception) {
                stopSelf()
            }
        }
    }

    companion object {
        fun start(ctx: Context) {
            if (!Graph.identity.hasIdentity()) return
            if (!Graph.prefs.alwaysOnline && !Graph.location.sharing.value && !Graph.calls.inCall && !Graph.placeWatch.active) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MonolithService::class.java))
            } catch (_: Exception) {
            }
        }

        fun refresh(ctx: Context) = start(ctx)

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonolithService::class.java))
        }
    }
}

/**
 * Agenda reminders: one alarm per reminder offset, always for the next occurrence.
 *
 * A reminder is only ever scheduled strictly in the future, and every (event, occurrence, offset)
 * is shown at most once: rescheduling right after an alarm fires used to re-arm the same instant
 * and Android delivered it again and again (the "same reminder three times" bug).
 */
object ReminderScheduler {
    private val ALL_OFFSETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

    private fun pending(ctx: Context, id: String, minutes: Int, create: Boolean, occurrence: Long = 0L, trigger: Long = 0L): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("id", id).putExtra("occ", occurrence).putExtra("min", minutes).putExtra("trig", trigger)
            .setAction("monolith.reminder.$id.$minutes")
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, (id + minutes).hashCode(), i, flags)
    }

    private fun baseOf(e: AgendaEvent, occ: Long) = if (e.allDay) Fmt.startOfDay(occ) + 9 * 3_600_000L else occ

    fun schedule(ctx: Context, e: AgendaEvent) {
        cancel(ctx, e.id)
        if (e.inviteState == InviteState.DECLINED || e.inviteState == InviteState.PENDING) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val tNow = now()
        e.reminderList().forEach { m ->
            // first occurrence whose reminder time is still strictly ahead
            var occ = Recur.nextAfter(e, tNow - m * 60_000L - 60_000L) ?: return@forEach
            var guard = 0
            while ((baseOf(e, occ) - m * 60_000L <= tNow + 1_000L || wasShown(ctx, e.id, occ, m)) && guard < 400) {
                occ = Recur.nextAfter(e, occ + 60_000) ?: return@forEach
                guard++
            }
            if (guard >= 400) return@forEach
            val trigger = baseOf(e, occ) - m * 60_000L
            val pi = pending(ctx, e.id, m, true, occ, trigger) ?: return@forEach
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    fun cancel(ctx: Context, id: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        ALL_OFFSETS.forEach { m ->
            pending(ctx, id, m, false)?.let { am.cancel(it); it.cancel() }
        }
    }

    suspend fun rescheduleAll(ctx: Context) {
        Graph.db.agenda().allNow().forEach { schedule(ctx, it) }
        BirthdayAlarm.schedule(ctx)
        ScheduledSender.rearmAll(ctx)
        // a birthday today that nobody announced yet (phone was off at nine)
        if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 9) BirthdayAlarm.fire(ctx)
    }

    fun snooze(ctx: Context, id: String, occ: Long, minutes: Int) {
        val at = now() + minutes.coerceIn(1, 24 * 60) * 60_000L
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZED + "." + id)
            .putExtra("id", id).putExtra("occ", occ)
        val pi = PendingIntent.getBroadcast(ctx, ("snz$id").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ---- "already shown" memory, so the same reminder never appears twice

    private fun shownPrefs(ctx: Context) = ctx.getSharedPreferences("reminders_shown", Context.MODE_PRIVATE)
    private fun key(id: String, occ: Long, m: Int) = "$id|$occ|$m"

    private fun wasShown(ctx: Context, id: String, occ: Long, m: Int) = shownPrefs(ctx).contains(key(id, occ, m))

    /** returns false if this exact reminder was already shown */
    @Synchronized
    fun markShown(ctx: Context, id: String, occ: Long, m: Int): Boolean {
        val sp = shownPrefs(ctx)
        val k = key(id, occ, m)
        if (sp.contains(k)) return false
        val ed = sp.edit().putLong(k, now())
        // forget entries older than 60 days
        val old = now() - 60L * 86_400_000
        sp.all.forEach { (kk, v) -> if (v is Long && v < old) ed.remove(kk) }
        ed.commit()
        return true
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "monolith.reminder.SNOOZE_"
        const val ACTION_DONE = "monolith.reminder.DONE"
        const val ACTION_SNOOZED = "monolith.reminder.SNOOZED"
        const val ACTION_BIRTHDAYS = "monolith.reminder.BIRTHDAYS"
        const val ACTION_SEND = "monolith.reminder.SEND"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        val id = intent.getStringExtra("id")
        val occ = intent.getLongExtra("occ", 0L)
        val pending = goAsync()
        Graph.scope.launch {
            try {
                when {
                    action == ACTION_BIRTHDAYS -> BirthdayAlarm.fire(context)
                    action.startsWith(ACTION_SEND) && id != null -> ScheduledSender.fire(id)
                    action.startsWith(ACTION_SNOOZE) && id != null -> {
                        Graph.notifier.cancelReminder(id)
                        ReminderScheduler.snooze(context, id, occ, intent.getIntExtra("snooze", 10))
                    }
                    action == ACTION_DONE && id != null -> {
                        Graph.notifier.cancelReminder(id)
                        Graph.done.set(id, occ, true)
                    }
                    action.startsWith(ACTION_SNOOZED) && id != null -> {
                        val ev = Graph.db.agenda().get(id)
                        if (ev != null && !Graph.done.isDone(id, occ)) Graph.notifier.reminder(ev, if (occ > 0) occ else ev.start)
                    }
                    id != null -> {
                        val minutes = intent.getIntExtra("min", -1)
                        val trigger = intent.getLongExtra("trig", 0L)
                        val ev = Graph.db.agenda().get(id)
                        if (ev != null && ev.inviteState != InviteState.DECLINED && ev.inviteState != InviteState.PENDING) {
                            // skip duplicates, done events, and reminders that come very late (phone was off for hours)
                            val fresh = trigger <= 0L || now() - trigger < 6 * 3_600_000L
                            val o = if (occ > 0) occ else ev.start
                            if (fresh && !Graph.done.isDone(id, o) && ReminderScheduler.markShown(context, id, occ, minutes)) {
                                Graph.notifier.reminder(ev, o)
                            }
                            ReminderScheduler.schedule(context, ev)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** messages written now that leave later */
object ScheduledSender {
    private fun pi(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SEND + "." + id).putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, ("sch$id").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun add(ctx: Context, conv: String, text: String, at: Long) {
        val s = com.monolith.app.data.Scheduled(java.util.UUID.randomUUID().toString(), conv, text.trim(), at)
        Graph.scheduled.add(s)
        arm(ctx, s)
    }

    fun cancel(ctx: Context, id: String) {
        Graph.scheduled.remove(id)
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id)) }
    }

    private fun arm(ctx: Context, s: com.monolith.app.data.Scheduled) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi(ctx, s.id))
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi(ctx, s.id))
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi(ctx, s.id))
        }
    }

    suspend fun fire(id: String) {
        val s = Graph.scheduled.get(id) ?: return
        Graph.scheduled.remove(id)
        Graph.startEngine()
        Graph.messenger.sendText(s.conv, s.text)
    }

    suspend fun rearmAll(ctx: Context) {
        Graph.scheduled.all.value.forEach { if (it.at <= now()) fire(it.id) else arm(ctx, it) }
    }
}

/** once a day, at nine: friends' birthdays */
object BirthdayAlarm {
    fun schedule(ctx: Context) {
        val c = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_BIRTHDAYS)
        val pi = PendingIntent.getBroadcast(ctx, 9090, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi) }
    }

    suspend fun fire(ctx: Context) {
        val today = com.monolith.app.core.Fmt.startOfDay(now())
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        Graph.birthdays.on(today).forEach { pk ->
            val f = Graph.db.friends().get(pk) ?: return@forEach
            if (f.status == com.monolith.app.data.FriendStatus.FRIEND && Graph.birthdays.markAnnounced(pk, year)) {
                Graph.notifier.birthday(pk, f.displayName, f.emoji)
            }
        }
        schedule(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.scope.launch {
            try {
                ReminderScheduler.rescheduleAll(context)
                MonolithService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REPLY = "monolith.REPLY"
        const val ACTION_READ = "monolith.READ"
        const val ACTION_ACCEPT_FRIEND = "monolith.ACCEPT_FRIEND"
        const val ACTION_DECLINE_FRIEND = "monolith.DECLINE_FRIEND"
        const val ACTION_DECLINE_CALL = "monolith.DECLINE_CALL"
        const val ACTION_STOP_SHARING = "monolith.STOP_SHARING"
        const val ACTION_EVENT_YES = "monolith.EVENT_YES"
        const val ACTION_EVENT_NO = "monolith.EVENT_NO"
        const val KEY_REPLY = "reply_text"
        const val EXTRA_CONV = "conv"
        const val EXTRA_PK = "pk"
        const val EXTRA_EVENT = "event"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.startEngine()
        MonolithService.start(context)
        Graph.scope.launch {
            try {
                if (intent.action == ACTION_DECLINE_CALL) {
                    Graph.calls.decline()
                    return@launch
                }
                withTimeoutOrNull(8000) { while (Graph.relays.connectedCount == 0) delay(200) }
                val m = Graph.messenger
                when (intent.action) {
                    ACTION_REPLY -> {
                        val conv = intent.getStringExtra(EXTRA_CONV) ?: return@launch
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
                        if (!text.isNullOrBlank()) {
                            m.sendText(conv, text)
                            m.markRead(conv)
                            delay(1500)
                        }
                        Graph.notifier.cancelConversation(conv)
                    }
                    ACTION_READ -> intent.getStringExtra(EXTRA_CONV)?.let { m.markRead(it); Graph.notifier.cancelConversation(it) }
                    ACTION_ACCEPT_FRIEND -> intent.getStringExtra(EXTRA_PK)?.let { m.acceptFriend(it); delay(1200) }
                    ACTION_DECLINE_FRIEND -> intent.getStringExtra(EXTRA_PK)?.let { m.declineFriend(it) }
                    ACTION_STOP_SHARING -> Graph.location.stopSharing()
                    ACTION_EVENT_YES, ACTION_EVENT_NO -> intent.getStringExtra(EXTRA_EVENT)?.let {
                        m.respondInvite(it, intent.action == ACTION_EVENT_YES)
                        Graph.notifier.cancelInvite(it)
                        delay(1200)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Quick settings tile: start/stop live location in one tap */
class ShareTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (Graph.location.sharing.value) {
            Graph.location.stopSharing()
            qsTile?.let { it.state = Tile.STATE_INACTIVE; it.updateTile() }
            return
        }
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Notifier.EXTRA_QUICK_SHARE, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 90, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Graph.location.sharing.value
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = t("Posizione live")
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) t("Attiva") else t("Tocca per condividere")
        tile.updateTile()
    }
}
