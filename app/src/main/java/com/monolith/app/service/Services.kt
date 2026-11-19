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
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.monolith.app.Graph
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.data.AgendaEvent
import com.monolith.app.data.InviteState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Foreground service that keeps the relay connections (and live location / calls) alive. */
class MonolithService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        promote()
        Graph.startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        if (!Graph.prefs.alwaysOnline && !Graph.location.sharing.value && !Graph.calls.inCall) {
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
            if (!Graph.prefs.alwaysOnline && !Graph.location.sharing.value && !Graph.calls.inCall) return
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

object ReminderScheduler {
    private fun pending(ctx: Context, id: String, create: Boolean): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).setAction("monolith.reminder.$id")
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, flags)
    }

    fun triggerAt(e: AgendaEvent): Long? {
        if (e.reminderMin < 0) return null
        val base = if (e.allDay) Fmt.startOfDay(e.start) + 9 * 3_600_000L else e.start
        return base - e.reminderMin * 60_000L
    }

    fun schedule(ctx: Context, e: AgendaEvent) {
        val t = triggerAt(e)
        if (t == null || t < now() - 30_000 || e.inviteState == InviteState.DECLINED || e.inviteState == InviteState.PENDING) {
            cancel(ctx, e.id)
            return
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, e.id, true) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    fun cancel(ctx: Context, id: String) {
        val pi = pending(ctx, id, false) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    suspend fun rescheduleAll(ctx: Context) {
        Graph.db.agenda().upcoming(now() - 86_400_000L).forEach { schedule(ctx, it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        Graph.scope.launch {
            try {
                val ev = Graph.db.agenda().get(id)
                if (ev != null && ev.inviteState != InviteState.DECLINED && ev.inviteState != InviteState.PENDING) {
                    Graph.notifier.reminder(ev)
                }
            } finally {
                pending.finish()
            }
        }
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
                    ACTION_DECLINE_CALL -> Graph.calls.decline()
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
