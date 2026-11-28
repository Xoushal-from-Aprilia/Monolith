package com.monolith.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import com.monolith.app.core.AppCtx
import com.monolith.app.core.AppLock
import com.monolith.app.core.I18n
import com.monolith.app.widget.AgendaWidget
import com.monolith.app.widget.FriendsWidget
import com.monolith.app.core.IdentityStore
import com.monolith.app.core.Prefs
import com.monolith.app.data.AppDatabase
import com.monolith.app.audio.Sounds
import com.monolith.app.data.BirthdayStore
import com.monolith.app.data.DoneStore
import com.monolith.app.data.PinStore
import com.monolith.app.data.ScheduledStore
import com.monolith.app.data.StatusStore
import com.monolith.app.data.PlacesStore
import com.monolith.app.location.LocationShare
import com.monolith.app.location.PlaceWatcher
import com.monolith.app.net.Messenger
import com.monolith.app.nostr.RelayPool
import com.monolith.app.rtc.CallManager
import com.monolith.app.rtc.RtcManager
import com.monolith.app.service.MonolithService
import com.monolith.app.service.Notifier
import com.monolith.app.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

/** Tiny service locator: every long-lived component lives here, created once per process. */
object Graph {
    lateinit var app: Application
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var prefs: Prefs
    lateinit var identity: IdentityStore
    lateinit var db: AppDatabase
    lateinit var notifier: Notifier
    lateinit var relays: RelayPool
    lateinit var messenger: Messenger
    lateinit var rtc: RtcManager
    lateinit var calls: CallManager
    lateinit var location: LocationShare
    lateinit var places: PlacesStore
    lateinit var shards: com.monolith.app.net.ShardManager
    lateinit var together: com.monolith.app.net.Together
    lateinit var birthdays: BirthdayStore
    lateinit var done: DoneStore
    lateinit var scheduled: ScheduledStore
    lateinit var pins: PinStore
    lateinit var bondDays: com.monolith.app.data.BondDays
    lateinit var favorites: com.monolith.app.data.Favorites
    lateinit var saved: com.monolith.app.data.SavedStore
    lateinit var guard: com.monolith.app.data.ChatGuard
    lateinit var profileExtras: com.monolith.app.data.ProfileExtras
    lateinit var medical: com.monolith.app.data.MedicalStore
    lateinit var diary: com.monolith.app.location.PlaceDiary
    lateinit var nearby: com.monolith.app.location.Nearby
    val nearbyActive: Boolean get() = ::nearby.isInitialized && nearby.active
    lateinit var statuses: StatusStore
    lateinit var placeWatch: PlaceWatcher
    lateinit var dev: com.monolith.app.net.DevChannel
    lateinit var hubs: com.monolith.app.net.Hubs
    lateinit var groupMeta: com.monolith.app.data.GroupMetaStore
    lateinit var joinAsks: com.monolith.app.data.JoinAsks
    lateinit var mentions: com.monolith.app.data.Mentions
    lateinit var chatOrg: com.monolith.app.data.ChatOrg
    lateinit var drafts: com.monolith.app.data.Drafts
    lateinit var receipts: com.monolith.app.data.Receipts
    lateinit var roomNotify: com.monolith.app.data.RoomNotify
    lateinit var msgReminders: com.monolith.app.data.MsgReminders

    @Volatile
    var foreground = false
        private set

    private var engineStarted = false

    fun init(application: Application) {
        app = application
        AppCtx.app = application
        I18n.init(application)
        prefs = Prefs(application)
        AppLock.init(prefs)
        identity = IdentityStore(application)
        db = AppDatabase.build(application)
        birthdays = BirthdayStore(application)
        done = DoneStore(application)
        scheduled = ScheduledStore(application)
        pins = PinStore(application)
        bondDays = com.monolith.app.data.BondDays(application)
        favorites = com.monolith.app.data.Favorites(application)
        saved = com.monolith.app.data.SavedStore(application)
        guard = com.monolith.app.data.ChatGuard(application)
        profileExtras = com.monolith.app.data.ProfileExtras(application)
        com.monolith.app.net.Badges.init(application)
        medical = com.monolith.app.data.MedicalStore(application)
        com.monolith.app.core.AutoBackup.load(application)
        statuses = StatusStore(application)
        Sounds.init(application)
        com.monolith.app.net.Stories.init(application)
        com.monolith.app.net.Weekly.refresh(application)
        notifier = Notifier(application).also { it.createChannels() }
        relays = RelayPool(scope) { identity.keys }
        messenger = Messenger(application, scope, db)
        rtc = RtcManager(application, scope)
        calls = CallManager(application, scope)
        Sounds.watchCalls()
        shards = com.monolith.app.net.ShardManager(application, scope)
        hubs = com.monolith.app.net.Hubs(application, scope)
        groupMeta = com.monolith.app.data.GroupMetaStore(application)
        joinAsks = com.monolith.app.data.JoinAsks(application)
        mentions = com.monolith.app.data.Mentions(application)
        chatOrg = com.monolith.app.data.ChatOrg(application)
        drafts = com.monolith.app.data.Drafts(application)
        receipts = com.monolith.app.data.Receipts(application)
        roomNotify = com.monolith.app.data.RoomNotify(application)
        msgReminders = com.monolith.app.data.MsgReminders(application)
        dev = com.monolith.app.net.DevChannel(application, scope)
        com.monolith.app.kids.Kids.init(application)
        together = com.monolith.app.net.Together(application)
        places = PlacesStore(application)
        location = LocationShare(application, scope)
        placeWatch = PlaceWatcher(application, scope)
        diary = com.monolith.app.location.PlaceDiary(application, scope)
        nearby = com.monolith.app.location.Nearby(application, scope)
        nearby.refresh()
        rtc.keepAlive = { pk ->
            messenger.activeConv.value == pk || calls.state.value.peer == pk
        }
        // the developer's document is read even before an account exists: a blocked phone stays blocked
        dev.start()
        scope.launch(Dispatchers.IO) { com.monolith.app.net.DeviceId.codes(application) }

        runCatching {
            // tile servers ask for a stable, app-specific User-Agent
            Configuration.getInstance().userAgentValue = "Monolith/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"
            Configuration.getInstance().osmdroidBasePath = application.filesDir.resolve("osmdroid")
            Configuration.getInstance().osmdroidTileCache = application.cacheDir.resolve("tiles")
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                AppLock.onForeground()
                messenger.onForeground()
                dev.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                AppLock.onBackground()
            }
        })

        runCatching {
            application.getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (engineStarted) relays.reconnectNow()
                    }
                })
        }

        if (identity.hasIdentity()) {
            startEngine()
            scope.launch { ReminderScheduler.rescheduleAll(application) }
        }
    }

    /**
     * Deletes the account and everything on this phone, then restarts the app on the welcome screen.
     * A Kids parent first deletes the children's accounts too.
     */
    suspend fun wipeEverything(ctx: android.content.Context) {
        runCatching { com.monolith.app.kids.Kids.wipeAllChildren() }
        relays.stop()
        db.clearAllTables()
        places.clear()
        scheduled.clear(); pins.clear(); statuses.clear(); together.clear(); bondDays.clear(); favorites.clear(); saved.clear(); guard.clear(); medical.clear(); diary.setEnabled(false); diary.clear(); com.monolith.app.ui.screens.Countdowns.clear(); profileExtras.clear(); com.monolith.app.net.Badges.clear(ctx); nearby.clear(); com.monolith.app.core.AutoBackup.clear(ctx)
        com.monolith.app.kids.Kids.clear()
        hubs.clear(); groupMeta.clear(); joinAsks.clear(); mentions.clearAll(); com.monolith.app.net.Invites.clear()
        chatOrg.clear(); drafts.clear(); receipts.clear(); roomNotify.clear(); msgReminders.clear()
        identity.reset()
        prefs.lastWrapSec = 0
        prefs.permissionsDone = false
        AppLock.setEnabled(false)
        restart(ctx)
    }

    /** closes and reopens the app, so that everything is read again from the phone (after a wipe or a restore) */
    suspend fun restart(ctx: android.content.Context) {
        // settings saved in the background need a moment to reach the disk
        kotlinx.coroutines.delay(600)
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            MonolithService.stop(ctx)
            runCatching {
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                    it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    ctx.startActivity(it)
                }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** redraw the home screen agenda widget */
    fun refreshWidget() {
        scope.launch { runCatching { AgendaWidget().updateAll(app) } }
    }

    /** keep the friends widget in step with chats, statuses and common places */
    private fun watchFriendsWidget() {
        scope.launch {
            kotlinx.coroutines.flow.combine(
                db.messages().unread(), db.messages().lastMessages(), statuses.friends, places.all, db.friends().friends(),
            ) { _, _, _, _, _ -> Unit }.combine(favorites.all) { _, _ -> Unit }.combine(bondDays.days) { _, _ -> Unit }.collectLatest {
                kotlinx.coroutines.delay(2500)
                runCatching { FriendsWidget().updateAll(app) }
            }
        }
    }

    @Synchronized
    fun startEngine() {
        if (!identity.hasIdentity()) return
        if (!engineStarted) {
            engineStarted = true
            messenger.start()
            placeWatch.start()
            shards.start()
            hubs.start()
            watchFriendsWidget()
            scope.launch { runCatching { bondDays.backfill(db) } }
            scope.launch {
                kotlinx.coroutines.delay(20_000)
                while (true) {
                    runCatching { com.monolith.app.net.Badges.refresh(app) }
                    kotlinx.coroutines.delay(20 * 60_000L)
                }
            }
            diary.start()
            com.monolith.app.kids.Kids.start()
        }
        MonolithService.start(app)
    }
}
