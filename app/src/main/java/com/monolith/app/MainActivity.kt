package com.monolith.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.monolith.app.rtc.CallPhase
import com.monolith.app.service.Notifier
import com.monolith.app.ui.AppRoot
import com.monolith.app.ui.NavBus
import com.monolith.app.ui.NavRequest
import com.monolith.app.ui.theme.LocalDark
import com.monolith.app.ui.theme.MonolithTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        lifecycleScope.launch {
            Graph.calls.state.collect { st ->
                val ringing = st.phase == CallPhase.INCOMING || st.phase == CallPhase.ACTIVE || st.phase == CallPhase.CONNECTING || st.phase == CallPhase.OUTGOING
                showOverLock(ringing)
            }
        }

        setContent {
            val mode by Graph.prefs.theme.collectAsState()
            MonolithTheme(mode) {
                val dark = LocalDark.current
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun showOverLock(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        } else {
            @Suppress("DEPRECATION")
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun handle(i: Intent?) {
        i ?: return
        val conv = i.getStringExtra(Notifier.EXTRA_CONV)
        val tab = if (i.hasExtra(Notifier.EXTRA_TAB)) i.getIntExtra(Notifier.EXTRA_TAB, 0) else null
        val event = i.getStringExtra(Notifier.EXTRA_EVENT)
        val call = i.getBooleanExtra(Notifier.EXTRA_CALL, false)
        val accept = i.getBooleanExtra(Notifier.EXTRA_ACCEPT_CALL, false)
        val quick = i.getBooleanExtra(Notifier.EXTRA_QUICK_SHARE, false)
        val route = i.getStringExtra(Notifier.EXTRA_ROUTE)
        if (call) showOverLock(true)
        if (conv != null || tab != null || event != null || call || quick || route != null) {
            NavBus.requests.value = NavRequest(conv, tab, event, call, accept, quick, route)
        }
        // consume the extras so a configuration change doesn't replay them
        i.removeExtra(Notifier.EXTRA_CONV)
        i.removeExtra(Notifier.EXTRA_TAB)
        i.removeExtra(Notifier.EXTRA_EVENT)
        i.removeExtra(Notifier.EXTRA_CALL)
        i.removeExtra(Notifier.EXTRA_ACCEPT_CALL)
        i.removeExtra(Notifier.EXTRA_QUICK_SHARE)
        i.removeExtra(Notifier.EXTRA_ROUTE)
    }

    override fun onResume() {
        super.onResume()
        if (Graph.calls.state.value.phase != CallPhase.INCOMING) Graph.notifier.cancelCall()
    }
}
