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
import com.monolith.app.audio.SoundKind
import com.monolith.app.audio.Sounds
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
import com.monolith.app.core.t
import com.monolith.app.data.AgendaEvent
import java.util.concurrent.ConcurrentHashMap

class Notifier(private val ctx: Context) {

    companion object {
        const val CH_SOCIAL = "social"
        const val CH_CALLS = "calls"
        const val CH_SERVICE = "service"
        const val CH_SOS = "sos"
        const val CH_LIVE = "live"
        const val CH_DEV = "dev"

        const val ID_SERVICE = 1
        const val ID_CALL = 2

        const val EXTRA_CONV = "open_conv"
        const val EXTRA_TAB = "open_tab"
        const val EXTRA_EVENT = "open_event"
        const val EXTRA_CALL = "open_call"
        const val EXTRA_ACCEPT_CALL = "accept_call"
        const val EXTRA_QUICK_SHARE = "quick_share"
        const val EXTRA_ROUTE = "open_route"

        private const val INK = 0xFF0A0A0A.toInt()
    }

    private val nm = NotificationManagerCompat.from(ctx)
    private val history = ConcurrentHashMap<String, MutableList<Triple<Pair<String, String>, String, Long>>>()

    fun createChannels() {
        val m = ctx.getSystemService(NotificationManager::class.java)
        fun withSound(kind: SoundKind, name: String, importance: Int) =
            NotificationChannel(Sounds.channelId(kind), name, importance).apply {
                enableVibration(true)
                val choice = Sounds.soundFor(kind)
                val attrs = AudioAttributes.Builder()
                    .setUsage(if (kind == SoundKind.REMINDER) AudioAttributes.USAGE_NOTIFICATION_EVENT else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                when (choice) {
                    Sounds.NONE -> setSound(null, attrs)
                    Sounds.SYSTEM -> {}
                    else -> Sounds.uri(choice)?.let { setSound(it, attrs) }
                }
            }
        val list = listOf(
            withSound(SoundKind.MESSAGE, t("Messaggi"), NotificationManager.IMPORTANCE_HIGH),
            withSound(SoundKind.MEETING, t("Incontri e arrivi"), NotificationManager.IMPORTANCE_HIGH),
            withSound(SoundKind.REMINDER, t("Promemoria agenda"), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_SOCIAL, t("Amici e inviti"), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_LIVE, t("Arrivi in diretta"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            NotificationChannel(CH_CALLS, t("Chiamate"), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            },
            NotificationChannel(CH_SERVICE, t("Connessione"), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
            // high importance: the developer's messages pop up on the screen
            NotificationChannel(CH_DEV, t("Messaggi dello sviluppatore"), NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
            NotificationChannel(CH_SOS, "SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
                setBypassDnd(true)
                Sounds.uri("sos")?.let {
                    setSound(it, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                }
            },
        )
        m.createNotificationChannels(list)
        // channels of sounds no longer chosen, and the ones from before the sound kit
        val keep = list.map { it.id }.toSet()
        runCatching {
            m.notificationChannels.forEach { ch ->
                val stale = ch.id !in keep && (ch.id == "messages" || ch.id == "reminders" || SoundKind.entries.any { ch.id.startsWith(Sounds.prefix(it)) })
                if (stale) m.deleteNotificationChannel(ch.id)
            }
        }
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
        meeting: Boolean = false,
        sos: Boolean = false,
    ) {
        if (nudge) vibrateNudge()
        val lines = history.getOrPut(conv) { mutableListOf() }
        synchronized(lines) {
            lines.add(Triple(senderName to senderEmoji, text, now()))
            while (lines.size > 7) lines.removeAt(0)
        }
        val me = Person.Builder().setName(t("Tu")).build()
        val style = NotificationCompat.MessagingStyle(me).setGroupConversation(isGroup)
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
        val remote = RemoteInput.Builder(ActionReceiver.KEY_REPLY).setLabel(t("Rispondi…")).build()
        val replyAction = NotificationCompat.Action.Builder(
            0, t("Rispondi"),
            action(code, ActionReceiver.ACTION_REPLY, mutable = true) { putExtra(ActionReceiver.EXTRA_CONV, conv) }
        ).addRemoteInput(remote).setAllowGeneratedReplies(true).build()
        val readAction = NotificationCompat.Action.Builder(
            0, t("Segna come letto"),
            action(code + 1, ActionReceiver.ACTION_READ) { putExtra(ActionReceiver.EXTRA_CONV, conv) }
        ).build()
        val n = base(if (sos) CH_SOS else Sounds.channelId(if (meeting) SoundKind.MEETING else SoundKind.MESSAGE))
            .setStyle(style)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(emojiIcon(if (isGroup) groupEmoji ?: "💬" else senderEmoji))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(code) { putExtra(EXTRA_CONV, conv) })
            .addAction(replyAction)
            .addAction(readAction)
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

    private fun simple(id: Int, title: String, text: String, emoji: String, intent: PendingIntent? = null, channel: String = CH_SOCIAL) {
        val b = base(channel).setContentTitle(title).setContentText(text).setLargeIcon(emojiIcon(emoji))
        intent?.let { b.setContentIntent(it) }
        post(id, b.build())
    }

    fun friendRequest(pk: String, name: String, username: String, emoji: String) {
        val code = ("fr$pk").hashCode()
        val n = base(CH_SOCIAL)
            .setContentTitle(t("Nuova richiesta di amicizia"))
            .setContentText(t("{0} (@{1}) vuole aggiungerti", name, username))
            .setLargeIcon(emojiIcon(emoji))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(openApp(code) { putExtra(EXTRA_TAB, 3) })
            .addAction(0, t("Accetta"), action(code, ActionReceiver.ACTION_ACCEPT_FRIEND) { putExtra(ActionReceiver.EXTRA_PK, pk) })
            .addAction(0, t("Rifiuta"), action(code + 1, ActionReceiver.ACTION_DECLINE_FRIEND) { putExtra(ActionReceiver.EXTRA_PK, pk) })
            .build()
        post(code, n)
    }

    fun cancelFriendRequest(pk: String) = nm.cancel(("fr$pk").hashCode())

    // ------------------------------------------------------------------ the developer

    private fun dev(id: Int, title: String, text: String, intent: PendingIntent) {
        val n = base(CH_DEV)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(intent)
            .build()
        post(id, n)
    }

    /** an announcement to everybody */
    fun devNews(title: String, body: String) =
        dev("devnews".hashCode(), title.ifBlank { t("Novità da Monolith") }, body, openApp(15))

    /** a message to me only */
    fun devMessage(id: String, title: String, body: String) =
        dev(("devmsg$id").hashCode(), title, body, openApp(16))

    fun devUpdate(version: String, notes: String) =
        dev("devupdate".hashCode(), t("È uscita la versione {0}", version), notes.ifBlank { t("Aggiorna Monolith dal sito") }, openApp(17))

    /** Monolith Kids: the child's phone said no to the request to connect */
    fun kidPairNo(child: String) =
        simple(("kpn$child").hashCode(), t("Collegamento rifiutato"), t("Sul telefono di {0} hanno risposto che non sei il genitore.", child), "🔒", openApp(18) { putExtra(EXTRA_TAB, 5) })

    /** Monolith Kids: a child would like to add someone */
    fun kidAsk(child: String, who: String, emoji: String) =
        simple(("ka$child$who").hashCode(), t("{0} vuole aggiungere {1}", child, who), t("Apri Monolith Kids per approvare o rifiutare"), emoji, openApp(14) { putExtra(EXTRA_TAB, 5) })

    fun friendAccepted(name: String, emoji: String) =
        simple(("fa$name").hashCode(), t("{0} ha accettato la tua richiesta", name), t("Ora potete chattare, condividere eventi e posizione"), emoji, openApp(11) { putExtra(EXTRA_TAB, 3) })

    fun locationShared(name: String, emoji: String, until: Long, onTheWay: Boolean) =
        simple(
            ("loc$name").hashCode(),
            if (onTheWay) t("{0} sta arrivando", name) else t("{0} condivide la posizione con te", name),
            t("In tempo reale fino alle {0}", Fmt.time(until)), emoji, openApp(12) { putExtra(EXTRA_TAB, 2) },
        )

    fun shardEvent(shard: String, text: String) =
        simple(("shev$shard$text").hashCode(), t("Nuovo evento in {0}", shard), text, "📅", openApp(22) { putExtra(EXTRA_TAB, 4) })

    fun anniversary(pk: String, name: String, emoji: String, years: Int) =
        simple(
            ("anniv$pk").hashCode(),
            if (years == 1) t("Un anno di amicizia con {0}", name) else t("{0} anni di amicizia con {1}", years, name),
            t("Siete amici su Monolith da oggi, {0} anni fa. Scrivigli!", years), emoji,
            openApp(("anniv$pk").hashCode()) { putExtra(EXTRA_CONV, pk) },
        )

    /** a friend with "friend nearby" turned on is less than 500 m away */
    fun nearby(pk: String, name: String, emoji: String, dist: Int) =
        simple(
            ("near$pk").hashCode(),
            t("{0} è nei paraggi", name),
            t("A circa {0} da te. Vi vedete?", Fmt.distance(dist.toFloat())), emoji,
            openApp(("near$pk").hashCode()) { putExtra(EXTRA_CONV, pk) }, Sounds.channelId(SoundKind.MEETING),
        )

    fun badge(name: String, desc: String) =
        simple(("badge$name").hashCode(), t("Nuovo distintivo: {0}", name), desc, "🏅", openApp(23) { putExtra(EXTRA_TAB, 3) })

    fun screenshot(conv: String, name: String, emoji: String) =
        simple(("shot$conv").hashCode(), t("{0} ha fatto uno screenshot", name), t("della vostra chat"), emoji, openApp(("s$conv").hashCode()) { putExtra(EXTRA_CONV, conv) })

    private val otwStart = HashMap<String, Int>()

    /** a friend on the way: a notification with a bar that fills as they get closer */
    fun arrivingLive(pk: String, name: String, emoji: String, dist: Int, etaSec: Int) {
        val start = synchronized(otwStart) {
            val s0 = otwStart[pk]
            if (s0 == null || dist > s0) { otwStart[pk] = dist.coerceAtLeast(1); dist.coerceAtLeast(1) } else s0
        }
        val progress = ((1f - dist.toFloat() / start) * 100).toInt().coerceIn(0, 100)
        val eta = if (etaSec < 60) t("meno di 1 min") else if (etaSec < 3600) "${etaSec / 60} min" else "${etaSec / 3600} h ${(etaSec % 3600) / 60} min"
        val n = base(CH_LIVE)
            .setContentTitle(t("{0} sta arrivando", name))
            .setContentText(Fmt.distance(dist.toFloat()) + " · " + t("circa {0}", eta))
            .setLargeIcon(emojiIcon(emoji))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openApp(("otw$pk").hashCode()) { putExtra(EXTRA_TAB, 2) })
            .build()
        post(("otw$pk").hashCode(), n)
    }

    fun cancelArriving(pk: String) {
        synchronized(otwStart) { otwStart.remove(pk) }
        nm.cancel(("otw$pk").hashCode())
    }

    /** a move in a game played in a chat */
    fun game(conv: String, title: String, text: String) =
        simple(("game$conv").hashCode(), title, text, "🎮", openApp(("g$conv").hashCode()) { putExtra(EXTRA_CONV, conv) })

    /** a friend sharing their position is about to run out of battery */
    fun batteryLow(pk: String, name: String, emoji: String, level: Int) =
        simple(
            ("bat$pk").hashCode(),
            t("Il telefono di {0} sta per spegnersi", name),
            t("Batteria al {0}% mentre condivide la posizione", level), emoji, openApp(21) { putExtra(EXTRA_TAB, 2) },
        )

    fun arrival(name: String, emoji: String, place: String) =
        simple(("arr$name$place").hashCode(), t("{0} è arrivato", name), place, emoji, openApp(13) { putExtra(EXTRA_TAB, 2) }, Sounds.channelId(SoundKind.MEETING))

    /** I walked into a common place where friends already are */
    fun together(names: String, place: String, emoji: String) =
        simple(("tog$place").hashCode(), t("Sei con {0}", names), place, emoji, openApp(16) { putExtra(EXTRA_TAB, 2) }, Sounds.channelId(SoundKind.MEETING))

    fun arrivedSelf(name: String, place: String) =
        simple(("me-arr$place").hashCode(), t("Sei arrivato"), t("Ho avvisato {0} e fermato la condivisione", name), "📍", null, Sounds.channelId(SoundKind.MEETING))

    /** kind: 0 = expenses, 1 = habit */
    fun togetherShared(from: String, what: String, kind: Int) =
        simple(("tog$kind$what").hashCode(), if (kind == 0) t("{0} ti ha aggiunto a delle spese condivise", from) else t("{0} ti ha invitato a un'abitudine", from), what, if (kind == 0) "💶" else "✅", openApp(19) { putExtra(EXTRA_TAB, 0) })

    /** I'm inside a shard for everyone (once) */
    fun hubJoined(shard: String) =
        simple(("hub$shard").hashCode(), t("Sei dentro {0}", shard), t("Lo Shard di tutti gli utenti di Monolith: annunci e chiacchiere con tutta la community"), "🌐", openApp(23) { putExtra(EXTRA_TAB, 4) })

    /** someone used the invite link and waits for a yes */
    fun joinAsk(target: String, who: String, emoji: String, where: String) =
        simple(("ja$target$who").hashCode(), t("{0} vuole entrare in {1}", who, where), t("Apri Monolith per accettare o rifiutare"), emoji,
            openApp(("ja$target").hashCode()) { if (target.startsWith("g:")) putExtra(EXTRA_ROUTE, "groupinfo/" + android.net.Uri.encode(target.removePrefix("g:"))) else putExtra(EXTRA_TAB, 4) })

    fun shardInvite(from: String, shard: String) =
        simple(("shard$shard").hashCode(), t("{0} ti ha invitato in uno shard", from), shard, "🪨", openApp(18) { putExtra(EXTRA_TAB, 4) })

    fun placeShared(from: String, place: String) =
        simple(("place$place").hashCode(), t("{0} ha aggiunto un luogo comune", from), place, "📍", openApp(15) { putExtra(EXTRA_TAB, 2) })

    fun listShared(from: String, title: String) =
        simple(("list$title").hashCode(), t("{0} ha condiviso una lista con te", from), title, "📝", openApp(14) { putExtra(EXTRA_TAB, 0) })

    fun missedCall(conv: String, name: String, emoji: String) =
        simple(("mc$conv").hashCode(), t("Chiamata persa"), t("{0} ti ha chiamato", name), emoji, openApp(conv.hashCode()) { putExtra(EXTRA_CONV, conv) })

    // ------------------------------------------------------------------ agenda

    fun eventInvite(ev: AgendaEvent, from: String) {
        val code = ("inv" + ev.id).hashCode()
        val n = base(CH_SOCIAL)
            .setContentTitle(t("{0} ti invita: {1}", from, ev.emoji + " " + ev.title))
            .setContentText(Fmt.dayLong(ev.start) + if (ev.allDay) "" else " · " + Fmt.time(ev.start))
            .setLargeIcon(emojiIcon(ev.emoji))
            .setContentIntent(openApp(code) { putExtra(EXTRA_TAB, 0); putExtra(EXTRA_EVENT, ev.id) })
            .addAction(0, t("Partecipo"), action(code, ActionReceiver.ACTION_EVENT_YES) { putExtra(ActionReceiver.EXTRA_EVENT, ev.id) })
            .addAction(0, t("Non posso"), action(code + 1, ActionReceiver.ACTION_EVENT_NO) { putExtra(ActionReceiver.EXTRA_EVENT, ev.id) })
            .build()
        post(code, n)
    }

    fun cancelInvite(id: String) = nm.cancel(("inv$id").hashCode())

    fun rsvp(ev: AgendaEvent, name: String, yes: Boolean) =
        simple(
            ("rsvp" + ev.id + name).hashCode(),
            if (yes) t("{0} partecipa a {1}", name, ev.title) else t("{0} non può venire a {1}", name, ev.title),
            Fmt.dayLong(ev.start), ev.emoji, openApp(15) { putExtra(EXTRA_TAB, 0); putExtra(EXTRA_EVENT, ev.id) },
        )

    fun eventCancelled(ev: AgendaEvent) =
        simple(("del" + ev.id).hashCode(), t("Evento annullato"), "${ev.emoji} ${ev.title} · ${Fmt.dayShort(ev.start)}", ev.emoji)

    /** Agenda reminder: the chosen emoji is the notification icon */
    fun reminder(ev: AgendaEvent, occurrence: Long) {
        if (!allowed()) return
        val small = Icon.createWithBitmap(EmojiArt.bitmap(ev.emoji, 96))
        val large = EmojiArt.bitmap(ev.emoji, 192, background = 0xFFEDEDED.toInt())
        val whenText = when {
            ev.allDay -> t("Oggi, tutto il giorno")
            occurrence <= now() + 60_000 -> t("Adesso · {0}", Fmt.time(occurrence))
            occurrence - now() > 20 * 3_600_000L -> Fmt.dayLong(occurrence) + " · " + Fmt.time(occurrence)
            else -> t("Alle {0} · tra {1}", Fmt.time(occurrence), Fmt.remaining(occurrence))
        }
        val body = listOf(ev.description, ev.notes).filter { it.isNotBlank() }.joinToString("\n")
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TAB, 0)
            putExtra(EXTRA_EVENT, ev.id)
        }
        val pi = PendingIntent.getActivity(ctx, ev.id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, Sounds.channelId(SoundKind.REMINDER))
            .setSmallIcon(small)
            .setLargeIcon(large)
            .setContentTitle("${ev.emoji}  ${ev.title}")
            .setContentText(whenText)
            .setStyle(Notification.BigTextStyle().bigText(if (body.isBlank()) whenText else "$whenText\n$body"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(INK)
            .addAction(reminderAction(ev.id, occurrence, 10, t("+10 min")))
            .addAction(reminderAction(ev.id, occurrence, 60, t("+1 ora")))
            .addAction(reminderAction(ev.id, occurrence, 0, t("Fatto")))
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(reminderId(ev.id), n) }
    }

    fun reminderId(eventId: String) = ("rem$eventId").hashCode()

    /** snooze ([minutes] > 0) or done ([minutes] = 0) */
    private fun reminderAction(id: String, occ: Long, minutes: Int, label: String): Notification.Action {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(if (minutes > 0) ReminderReceiver.ACTION_SNOOZE + minutes else ReminderReceiver.ACTION_DONE)
            .putExtra("id", id).putExtra("occ", occ).putExtra("snooze", minutes)
        val pi = PendingIntent.getBroadcast(ctx, ("$id|$minutes").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null as android.graphics.drawable.Icon?, label, pi).build()
    }

    fun cancelReminder(eventId: String) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(reminderId(eventId)) }
    }

    /** "when I get there, remind me to…" */
    fun placeNotes(place: String, notes: List<String>) {
        val text = notes.joinToString("\n") { "• $it" }
        val n = base(Sounds.channelId(SoundKind.REMINDER))
            .setContentTitle(t("Sei a {0}", place))
            .setContentText(notes.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setLargeIcon(emojiIcon("📍"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(17) { putExtra(EXTRA_TAB, 0) })
            .build()
        post(("notes$place").hashCode(), n)
    }

    /** Sunday evening: the recap of the week is ready */
    fun weekly(title: String, text: String) {
        simple(7070, title, text, "✨", openApp(20) { putExtra(EXTRA_ROUTE, "wrapped") })
    }

    fun birthday(pk: String, name: String, emoji: String) {
        val n = base(Sounds.channelId(SoundKind.REMINDER))
            .setContentTitle(t("Oggi è il compleanno di {0}", name))
            .setContentText(t("Manda i tuoi auguri 🎂"))
            .setLargeIcon(emojiIcon(emoji))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(("bday$pk").hashCode()) { putExtra(EXTRA_CONV, pk) })
            .build()
        post(("bday$pk").hashCode(), n)
    }

    // ------------------------------------------------------------------ calls

    fun incomingCall(pk: String, name: String, emoji: String, video: Boolean, groupName: String?) {
        val full = openApp(70) { putExtra(EXTRA_CALL, true) }
        val accept = openApp(71) { putExtra(EXTRA_CALL, true); putExtra(EXTRA_ACCEPT_CALL, true) }
        val decline = action(72, ActionReceiver.ACTION_DECLINE_CALL)
        val what = if (video) t("Videochiamata in arrivo…") else t("Chiamata vocale in arrivo…")
        val n = base(CH_CALLS)
            .setContentTitle(if (groupName != null) "$name · $groupName" else name)
            .setContentText(what)
            .setLargeIcon(emojiIcon(emoji))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, t("Rifiuta"), decline)
            .addAction(0, t("Rispondi"), accept)
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
            inCall -> b.setContentTitle(t("Chiamata in corso")).setContentText(t("Tocca per tornare alla chiamata"))
                .setContentIntent(openApp(81) { putExtra(EXTRA_CALL, true) })
            sharing -> b.setContentTitle(t("Stai condividendo la posizione"))
                .setContentText(t("In tempo reale con i tuoi amici"))
                .addAction(0, t("Interrompi"), action(82, ActionReceiver.ACTION_STOP_SHARING))
            else -> b.setContentTitle(t("Monolith è attivo")).setContentText(t("Pronto a ricevere messaggi e chiamate"))
        }
        return b.build()
    }
}
