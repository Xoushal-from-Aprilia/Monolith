package com.monolith.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.glance.appwidget.updateAll
import com.monolith.app.core.AppCtx
import com.monolith.app.core.AppLock
import com.monolith.app.core.I18n
import com.monolith.app.widget.AgendaWidget
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
    lateinit var birthdays: BirthdayStore
    lateinit var done: DoneStore
    lateinit var scheduled: ScheduledStore
    lateinit var pins: PinStore
    lateinit var statuses: StatusStore
    lateinit var placeWatch: PlaceWatcher

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
        statuses = StatusStore(application)
        Sounds.init(application)
        notifier = Notifier(application).also { it.createChannels() }
        relays = RelayPool(scope) { identity.keys }
        messenger = Messenger(application, scope, db)
        rtc = RtcManager(application, scope)
        calls = CallManager(application, scope)
        Sounds.watchCalls()
        places = PlacesStore(application)
        location = LocationShare(application, scope)
        placeWatch = PlaceWatcher(application, scope)
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

    @Synchronized
    fun startEngine() {
        if (!identity.hasIdentity()) return
        if (!engineStarted) {
            engineStarted = true
            messenger.start()
            placeWatch.start()
        }
        MonolithService.start(app)
    }
}
