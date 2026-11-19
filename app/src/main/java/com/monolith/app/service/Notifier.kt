package com.monolith.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.monolith.app.MainActivity
import com.monolith.app.R
import com.monolith.app.core.EmojiArt
import com.monolith.app.core.Fmt
import com.monolith.app.core.now
import com.monolith.app.data.AgendaEvent
import java.util.concurrent.ConcurrentHashMap

class Notifier(private val ctx: Context) {

    companion object {
        const val CH_MESSAGES = "messages"
        const val CH_SOCIAL = "social"
        const val CH_REMINDERS = "reminders"
        const val CH_CALLS = "calls"
        const val CH_SERVICE = "service"

        const val ID_SERVICE = 1
        const val ID_CALL = 2

        const val EXTRA_CONV = "open_conv"
        const val EXTRA_TAB = "open_tab"
        const val EXTRA_EVENT = "open_event"
        const val EXTRA_CALL = "open_call"
        const val EXTRA_ACCEPT_CALL = "accept_call"

        private const val INK = 0xFF0A0A0A.toInt()
    }

    private val nm = NotificationManagerCompat.from(ctx)
    private val history = ConcurrentHashMap<String, MutableList<Triple<Pair<String, String>, String, Long>>>()

    fun createChannels() {
        val m = ctx.getSystemService(NotificationManager::class.java)
        val list = listOf(
            NotificationChannel(CH_MESSAGES, "Messaggi", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nuovi messaggi in Clubhouse"
                enableVibration(true)
            },
            NotificationChannel(CH_SOCIAL, "Amici e inviti", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Richieste di amicizia, inviti, posizione"
            },
            NotificationChannel(CH_REMINDERS, "Promemoria agenda", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Promemoria degli eventi"
                enableVibration(true)
            },
            NotificationChannel(CH_CALLS, "Chiamate", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chiamate vocali in arrivo"
                setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            },
            NotificationChannel(CH_SERVICE, "Connessione", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Mantiene Monolith sempre online"
                setShowBadge(false)
            },
        )
        m.createNotificationChannels(list)
    }

    private fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return nm.areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, n: Notification) {
        if (!allowed()) return
        runCatching { nm.notify(id, n) }
    }

    private fun openApp(code: Int, fill: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            fill()
        }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(code: Int, act: String, mutable: Boolean = false, fill: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            action = act
            fill()
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        return PendingIntent.getBroadcast(ctx, code, i, flags)
    }

    private fun emojiIcon(emoji: String, size: Int = 128) =
        EmojiArt.bitmap(emoji, size, background = 0xFFEDEDED.toInt())

    private fun base(channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_monolith)
        .setColor(INK)
        .setAutoCancel(true)

    // ------------------------------------------------------------------ messages

    fun message(
        conv: String,
        title: String,
        senderName: String,
        senderEmoji: String,
        text: String,
        isGroup: Boolean,
        groupEmoji: String?,
        nudge: Boolean,
    ) {
        if (nudge) vibrateNudge()
        val lines = history.getOrPut(conv) { mutableListOf() }
        synchronized(lines) {
            lines.add(Triple(senderName to senderEmoji, text, now()))
            while (lines.size > 7) lines.removeAt(0)
        }
        val me = Person.Builder().setName("Tu").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setGroupConversation(isGroup)
        if (isGroup) style.setConversationTitle(title)
        synchronized(lines) {
            lines.forEach { (who, txt, ts) ->
                val person = Person.Builder()
                    .setName(who.first)
                    .setKey(who.first)
                    .setIcon(IconCompat.createWithBitmap(emojiIcon(who.second, 96)))
                    .build()
                style.addMessage(txt, ts, person)
            }
        }
        val code = conv.hashCode()
        val remote = RemoteInput.Builder(ActionReceiver.KEY_REPLY).setLabel("Rispondi…").build()
        val replyAction = NotificationCompat.Action.Builder(
            0, "Rispondi",
            action(code, ActionReceiver.ACTION_REPLY, mutable = true) { putExtra(ActionReceiver.EXTRA_CONV, conv) }
        ).addRemoteInput(remote).setAllowGeneratedReplies(true).build()
        val readAction = NotificationCompat.Action.Builder(
            0, "Segna come letto",
            action(code + 1, ActionReceiver.ACTION_READ) { putExtra(ActionReceiver.EXTRA_CONV, conv) }
        ).build()
        val n = base(CH_MESSAGES)
            .setStyle(style)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(emojiIcon(if (isGroup) groupEmoji ?: "💬" else senderEmoji))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(code) { putExtra(EXTRA_CONV, conv) })
            .addAction(replyAction)
            .addAction(readAction)
            .setOnlyAlertOnce(false)
            .build()
        post(code, n)
    }

    fun cancelConversation(conv: String) {
        history.remove(conv)
        nm.cancel(conv.hashCode())
    }

    fun vibrateNudge() {
        runCatching {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 90, 70, 90, 70, 300), -1)
            if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
            }
        }
    }

    // ------------------------------------------------------------------ social

    fun friendRequest(pk: String, name: String, username: String, emoji: String) {
        val code = ("fr$pk").hashCode()
        val n = base(CH_SOCIAL)
            .setContentTitle("Nuova richiesta di amicizia")
            .setContentText("$name (@$username) vuole aggiungerti")
            .setLargeIcon(emojiIcon(emoji))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(openApp(code) { putExtra(EXTRA_TAB, 3) })
            .addAction(0, "Accetta", action(code, ActionReceiver.ACTION_ACCEPT_FRIEND) { putExtra(ActionReceiver.EXTRA_PK, pk) })
            .addAction(0, "Rifiuta", action(code + 1, ActionReceiver.ACTION_DECLINE_FRIEND) { putExtra(ActionReceiver.EXTRA_PK, pk) })
            .build()
        post(code, n)
    }

    fun cancelFriendRequest(pk: String) = nm.cancel(("fr$pk").hashCode())

    fun friendAccepted(name: String, emoji: String) {
        val n = base(CH_SOCIAL)
            .setContentTitle("$name ha accettato la tua richiesta")
            .setContentText("Ora potete chattare, condividere eventi e posizione")
            .setLargeIcon(emojiIcon(emoji))
            .setContentIntent(openApp(11) { putExtra(EXTRA_TAB, 3) })
            .build()
        post(("fa$name").hashCode(), n)
    }

    fun locationShared(name: String, emoji: String, until: Long) {
        val n = base(CH_SOCIAL)
            .setContentTitle("$name condivide la posizione con te")
            .setContentText("In tempo reale fino alle ${Fmt.time(until)}")
            .setLargeIcon(emojiIcon(emoji))
            .setContentIntent(openApp(12) { putExtra(EXTRA_TAB, 2) })
            .build()
        post(("loc$name").hashCode(), n)
    }

    fun missedCall(pk: String, name: String, emoji: String) {
        val n = base(CH_SOCIAL)
            .setContentTitle("Chiamata persa")
            .setContentText("$name ti ha chiamato")
            .setLargeIcon(emojiIcon(emoji))
            .setContentIntent(openApp(pk.hashCode()) { putExtra(EXTRA_CONV, pk) })
            .build()
        post(("mc$pk").hashCode(), n)
    }

    // ------------------------------------------------------------------ agenda

    fun eventInvite(ev: AgendaEvent, from: String) {
        val code = ("inv" + ev.id).hashCode()
        val n = base(CH_SOCIAL)
            .setContentTitle("$from ti invita: ${ev.emoji} ${ev.title}")
            .setContentText(Fmt.dayLong(ev.start) + if (ev.allDay) "" else " · " + Fmt.time(ev.start))
            .setLargeIcon(emojiIcon(ev.emoji))
            .setContentIntent(openApp(code) { putExtra(EXTRA_TAB, 0); putExtra(EXTRA_EVENT, ev.id) })
            .addAction(0, "Partecipo", action(code, ActionReceiver.ACTION_EVENT_YES) { putExtra(ActionReceiver.EXTRA_EVENT, ev.id) })
            .addAction(0, "Non posso", action(code + 1, ActionReceiver.ACTION_EVENT_NO) { putExtra(ActionReceiver.EXTRA_EVENT, ev.id) })
            .build()
        post(code, n)
    }

    fun cancelInvite(id: String) = nm.cancel(("inv$id").hashCode())

    fun rsvp(ev: AgendaEvent, name: String, yes: Boolean) {
        val n = base(CH_SOCIAL)
            .setContentTitle(if (yes) "$name partecipa a ${ev.title}" else "$name non può venire a ${ev.title}")
            .setContentText(Fmt.dayLong(ev.start))
            .setLargeIcon(emojiIcon(ev.emoji))
            .setContentIntent(openApp(13) { putExtra(EXTRA_TAB, 0); putExtra(EXTRA_EVENT, ev.id) })
            .build()
        post(("rsvp" + ev.id + name).hashCode(), n)
    }

    fun eventCancelled(ev: AgendaEvent) {
        val n = base(CH_SOCIAL)
            .setContentTitle("Evento annullato")
            .setContentText("${ev.emoji} ${ev.title} · ${Fmt.dayShort(ev.start)}")
            .setLargeIcon(emojiIcon(ev.emoji))
            .build()
        post(("del" + ev.id).hashCode(), n)
    }

    /** Agenda reminder: the chosen emoji is the notification icon */
    fun reminder(ev: AgendaEvent) {
        if (!allowed()) return
        val small = Icon.createWithBitmap(EmojiArt.bitmap(ev.emoji, 96))
        val large = EmojiArt.bitmap(ev.emoji, 192, background = 0xFFEDEDED.toInt())
        val whenText = when {
            ev.allDay -> "Oggi, tutto il giorno"
            ev.start <= now() + 60_000 -> "Adesso · ${Fmt.time(ev.start)}"
            else -> "Alle ${Fmt.time(ev.start)} · tra ${Fmt.remaining(ev.start)}"
        }
        val body = listOf(ev.description, ev.notes).filter { it.isNotBlank() }.joinToString("\n")
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TAB, 0)
            putExtra(EXTRA_EVENT, ev.id)
        }
        val pi = PendingIntent.getActivity(ctx, ev.id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(small)
            .setLargeIcon(large)
            .setContentTitle("${ev.emoji}  ${ev.title}")
            .setContentText(whenText)
            .setStyle(Notification.BigTextStyle().bigText(if (body.isBlank()) whenText else "$whenText\n$body"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(INK)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(("rem" + ev.id).hashCode(), n) }
    }

    // ------------------------------------------------------------------ calls

    fun incomingCall(pk: String, name: String, emoji: String) {
        val full = openApp(70) { putExtra(EXTRA_CALL, true) }
        val accept = openApp(71) { putExtra(EXTRA_CALL, true); putExtra(EXTRA_ACCEPT_CALL, true) }
        val decline = action(72, ActionReceiver.ACTION_DECLINE_CALL)
        val n = base(CH_CALLS)
            .setContentTitle(name)
            .setContentText("Chiamata vocale in arrivo…")
            .setLargeIcon(emojiIcon(emoji))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Rifiuta", decline)
            .addAction(0, "Rispondi", accept)
            .build()
        post(ID_CALL, n)
    }

    fun cancelCall() = nm.cancel(ID_CALL)

    // ------------------------------------------------------------------ service

    fun serviceNotification(sharing: Boolean, inCall: Boolean): Notification {
        val b = base(CH_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(80) { if (sharing) putExtra(EXTRA_TAB, 2) })
        when {
            inCall -> b.setContentTitle("Chiamata in corso").setContentText("Tocca per tornare alla chiamata")
                .setContentIntent(openApp(81) { putExtra(EXTRA_CALL, true) })
            sharing -> b.setContentTitle("Stai condividendo la posizione")
                .setContentText("In tempo reale con i tuoi amici")
                .addAction(0, "Interrompi", action(82, ActionReceiver.ACTION_STOP_SHARING))
            else -> b.setContentTitle("Monolith è online").setContentText("Pronto a ricevere messaggi e chiamate")
        }
        return b.build()
    }
}
