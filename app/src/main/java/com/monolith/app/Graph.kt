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
        dev = com.monolith.app.net.DevChannel(application, scope)
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
        }
        MonolithService.start(app)
    }
}
