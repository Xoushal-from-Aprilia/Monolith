package com.monolith.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.monolith.app.core.AppCtx
import com.monolith.app.core.IdentityStore
import com.monolith.app.core.Prefs
import com.monolith.app.data.AppDatabase
import com.monolith.app.location.LocationShare
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

    @Volatile
    var foreground = false
        private set

    private var engineStarted = false

    fun init(application: Application) {
        app = application
        AppCtx.app = application
        prefs = Prefs(application)
        identity = IdentityStore(application)
        db = AppDatabase.build(application)
        notifier = Notifier(application).also { it.createChannels() }
        relays = RelayPool(scope) { identity.keys }
        messenger = Messenger(application, scope, db)
        rtc = RtcManager(application, scope)
        calls = CallManager(application, scope)
        location = LocationShare(application, scope)
        rtc.keepAlive = { pk ->
            messenger.activeConv.value == pk || calls.state.value.peer == pk
        }

        runCatching {
            Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
            Configuration.getInstance().osmdroidBasePath = application.filesDir.resolve("osmdroid")
            Configuration.getInstance().osmdroidTileCache = application.cacheDir.resolve("tiles")
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                messenger.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
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

    @Synchronized
    fun startEngine() {
        if (!identity.hasIdentity()) return
        if (!engineStarted) {
            engineStarted = true
            messenger.start()
        }
        MonolithService.start(app)
    }
}
