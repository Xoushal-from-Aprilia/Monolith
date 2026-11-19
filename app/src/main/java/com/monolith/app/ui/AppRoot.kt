package com.monolith.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monolith.app.Graph
import com.monolith.app.data.FriendStatus
import com.monolith.app.rtc.CallPhase
import com.monolith.app.ui.components.LocalNavScope
import com.monolith.app.ui.components.LocalSharedScope
import com.monolith.app.ui.components.MonolithMark
import com.monolith.app.ui.screens.AgendaScreen
import com.monolith.app.ui.screens.CallScreen
import com.monolith.app.ui.screens.ChatListScreen
import com.monolith.app.ui.screens.ChatScreen
import com.monolith.app.ui.screens.EventEditorScreen
import com.monolith.app.ui.screens.FriendsScreen
import com.monolith.app.ui.screens.MapScreen
import com.monolith.app.ui.screens.NewGroupScreen
import com.monolith.app.ui.screens.OnboardingFlow
import com.monolith.app.ui.screens.PermissionsScreen
import com.monolith.app.ui.screens.PhotoViewer
import com.monolith.app.ui.screens.SettingsScreen
import com.monolith.app.ui.theme.Grotesk
import com.monolith.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** navigation requests coming from notifications / intents */
data class NavRequest(
    val conv: String? = null,
    val tab: Int? = null,
    val event: String? = null,
    val call: Boolean = false,
    val acceptCall: Boolean = false,
)

object NavBus {
    val requests = MutableStateFlow<NavRequest?>(null)
}

object Routes {
    fun chat(conv: String) = "chat/" + Uri.encode(conv)
    fun event(id: String, date: Long = 0L) = "event/" + Uri.encode(id) + "?date=$date"
    fun photo(path: String, key: String) = "photo?path=" + Uri.encode(path) + "&key=" + Uri.encode(key)
}

@Composable
fun AppRoot() {
    val profile by Graph.identity.profile.collectAsState()
    var intro by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1450)
        intro = false
    }
    AnimatedContent(
        targetState = when {
            intro -> 0
            profile == null -> 1
            else -> 2
        },
        transitionSpec = {
            (fadeIn(tween(500, delayMillis = 120)) + scaleIn(tween(600, easing = Motion.Emphasized), initialScale = 1.04f)) togetherWith
                fadeOut(tween(260))
        },
        label = "root",
    ) { stage ->
        when (stage) {
            0 -> IntroSplash()
            1 -> OnboardingFlow()
            else -> MainNav()
        }
    }
}

@Composable
private fun IntroSplash() {
    val rise = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.3f) }
    val horizon = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { horizon.animateTo(1f, tween(500, easing = Motion.Emphasized)) }
        delay(150)
        launch { rise.animateTo(1f, tween(750, easing = Motion.EmphasizedDecel)) }
        delay(500)
        launch { word.animateTo(1f, tween(500, easing = Motion.Emphasized)) }
        sweep.animateTo(1.3f, tween(900))
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MonolithMark(Modifier.height(150.dp), rise = rise.value, sweep = sweep.value, horizon = horizon.value)
            Spacer(Modifier.height(28.dp))
            Text(
                "MONOLITH",
                fontFamily = Grotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (4 + 8 * word.value).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 18f
                },
            )
        }
    }
}

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val homeTab = rememberSaveable { mutableIntStateOf(0) }
    val req by NavBus.requests.collectAsState()
    val call by Graph.calls.state.collectAsState()

    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        NavBus.requests.value = null
        if (r.call) {
            if (r.acceptCall) Graph.calls.accept()
            if (nav.currentDestination?.route != "call") nav.navigate("call") { launchSingleTop = true }
            return@LaunchedEffect
        }
        r.tab?.let { homeTab.intValue = it }
        when {
            r.conv != null -> {
                nav.popBackStack("home", inclusive = false)
                nav.navigate(Routes.chat(r.conv)) { launchSingleTop = true }
            }
            r.event != null -> {
                nav.popBackStack("home", inclusive = false)
                nav.navigate(Routes.event(r.event)) { launchSingleTop = true }
            }
            else -> nav.popBackStack("home", inclusive = false)
        }
    }

    LaunchedEffect(call.phase) {
        if ((call.phase == CallPhase.INCOMING || call.phase == CallPhase.OUTGOING) && nav.currentDestination?.route != "call") {
            nav.navigate("call") { launchSingleTop = true }
        }
    }

    LaunchedEffect(Unit) {
        if (!Graph.prefs.permissionsDone) nav.navigate("permissions?first=true")
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = "home",
                enterTransition = { slideInHorizontally(tween(420, easing = Motion.Emphasized)) { it / 3 } + fadeIn(tween(320)) },
                exitTransition = { slideOutHorizontally(tween(420, easing = Motion.Emphasized)) { -it / 6 } + fadeOut(tween(220)) },
                popEnterTransition = { slideInHorizontally(tween(420, easing = Motion.Emphasized)) { -it / 6 } + fadeIn(tween(320)) },
                popExitTransition = { slideOutHorizontally(tween(380, easing = Motion.Emphasized)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                composable("home") {
                    CompositionLocalProvider(LocalNavScope provides this) {
                        HomeScreen(nav, homeTab.intValue) { homeTab.intValue = it }
                    }
                }
                composable(
                    "chat/{conv}",
                    arguments = listOf(navArgument("conv") { type = NavType.StringType }),
                ) { entry ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        ChatScreen(nav, Uri.decode(entry.arguments?.getString("conv") ?: ""))
                    }
                }
                composable(
                    "event/{id}?date={date}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("date") { type = NavType.LongType; defaultValue = 0L },
                    ),
                    enterTransition = { slideInVertically(tween(480, easing = Motion.Emphasized)) { it / 5 } + fadeIn(tween(300)) },
                    popExitTransition = { slideOutVertically(tween(380, easing = Motion.Emphasized)) { it / 5 } + fadeOut(tween(220)) },
                ) { entry ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        EventEditorScreen(
                            nav,
                            Uri.decode(entry.arguments?.getString("id") ?: "new"),
                            entry.arguments?.getLong("date") ?: 0L,
                        )
                    }
                }
                composable("group/new") {
                    CompositionLocalProvider(LocalNavScope provides this) { NewGroupScreen(nav) }
                }
                composable("settings") {
                    CompositionLocalProvider(LocalNavScope provides this) { SettingsScreen(nav) }
                }
                composable(
                    "permissions?first={first}",
                    arguments = listOf(navArgument("first") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    val first = entry.arguments?.getBoolean("first") ?: false
                    PermissionsScreen(
                        first = first,
                        onDone = {
                            Graph.prefs.permissionsDone = true
                            nav.popBackStack()
                        },
                    )
                }
                composable(
                    "call",
                    enterTransition = { fadeIn(tween(350)) + scaleIn(tween(450, easing = Motion.Emphasized), initialScale = 0.94f) },
                    popExitTransition = { fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.96f) },
                ) {
                    CallScreen(nav)
                }
                composable(
                    "photo?path={path}&key={key}",
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType; defaultValue = "" },
                        navArgument("key") { type = NavType.StringType; defaultValue = "" },
                    ),
                    enterTransition = { fadeIn(tween(250)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(250)) },
                    popExitTransition = { fadeOut(tween(250)) },
                ) { entry ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        PhotoViewer(nav, Uri.decode(entry.arguments?.getString("path") ?: ""), Uri.decode(entry.arguments?.getString("key") ?: ""))
                    }
                }
            }
        }
    }
}

private data class TabSpec(val label: String, val on: ImageVector, val off: ImageVector)

private val TABS = listOf(
    TabSpec("Agenda", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    TabSpec("Clubhouse", Icons.Rounded.Forum, Icons.Outlined.Forum),
    TabSpec("Mappa", Icons.Rounded.Map, Icons.Outlined.Map),
    TabSpec("Amici", Icons.Rounded.People, Icons.Outlined.People),
)

@Composable
private fun HomeScreen(nav: NavHostController, tab: Int, onTab: (Int) -> Unit) {
    val unread by remember { Graph.db.messages().unread() }.collectAsState(initial = emptyList())
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val unreadTotal = unread.sumOf { it.n }
    val requests = friends.count { it.status == FriendStatus.INCOMING }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    val selected = tab == i
                    val scale by animateFloatAsState(if (selected) 1.12f else 1f, Motion.bouncy(), label = "tabScale")
                    val lift by animateDpAsState(if (selected) (-1).dp else 0.dp, Motion.bouncy(), label = "tabLift")
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onTab(i) },
                        icon = {
                            val badge = when (i) {
                                1 -> unreadTotal
                                3 -> requests
                                else -> 0
                            }
                            val badgeScale by animateFloatAsState(if (badge > 0) 1f else 0f, Motion.bouncy(), label = "badge")
                            BadgedBox(badge = {
                                if (badge > 0 || badgeScale > 0.01f) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.graphicsLayer { scaleX = badgeScale; scaleY = badgeScale },
                                    ) {
                                        Text(if (badge > 99) "99+" else badge.coerceAtLeast(1).toString())
                                    }
                                }
                            }) {
                                Icon(
                                    if (selected) t.on else t.off,
                                    contentDescription = t.label,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale; scaleY = scale
                                        translationY = lift.toPx()
                                    },
                                )
                            }
                        },
                        label = { Text(t.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(260, delayMillis = 60)) + slideInHorizontally(tween(380, easing = Motion.Emphasized)) { dir * it / 12 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { -dir * it / 16 })
            },
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            label = "tabs",
        ) { t ->
            when (t) {
                0 -> AgendaScreen(nav)
                1 -> ChatListScreen(nav)
                2 -> MapScreen(nav)
                else -> FriendsScreen(nav, onOpenChat = { pk -> nav.navigate(Routes.chat(pk)) })
            }
        }
    }
}
