package com.monolith.app.ui

import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monolith.app.Graph
import com.monolith.app.core.AppLock
import com.monolith.app.core.t
import com.monolith.app.data.FriendStatus
import com.monolith.app.rtc.CallPhase
import com.monolith.app.ui.components.LocalNavScope
import com.monolith.app.ui.components.LocalSharedScope
import com.monolith.app.ui.components.MonolithMark
import com.monolith.app.ui.screens.AgendaScreen
import com.monolith.app.ui.screens.CallScreen
import com.monolith.app.ui.screens.ChatListScreen
import com.monolith.app.ui.screens.ChatScreen
import com.monolith.app.ui.screens.CreditsScreen
import com.monolith.app.ui.screens.EventEditorScreen
import com.monolith.app.ui.screens.FriendsScreen
import com.monolith.app.ui.screens.JournalScreen
import com.monolith.app.ui.screens.SoundsScreen
import com.monolith.app.ui.screens.SearchScreen
import com.monolith.app.ui.screens.ShardScreen
import com.monolith.app.ui.screens.ListDetailScreen
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
import com.monolith.app.ui.components.tapSounds

/** navigation requests coming from notifications / intents */
data class NavRequest(
    val conv: String? = null,
    val tab: Int? = null,
    val event: String? = null,
    val call: Boolean = false,
    val acceptCall: Boolean = false,
    val quickShare: Boolean = false,
    val route: String? = null,
)

object NavBus {
    val requests = MutableStateFlow<NavRequest?>(null)
    val openShareSheet = MutableStateFlow(false)
    /** a common place to show on the map (from a chat bubble) */
    val focusPlace = MutableStateFlow<String?>(null)
    /** the screen on top right now, and whether the app is in a small floating window */
    val route = MutableStateFlow<String?>(null)
    val pip = MutableStateFlow(false)
}

object Routes {
    fun shard(id: String) = "shard/$id"
    fun bill(id: String) = "bill/$id"
    fun profile(pk: String) = "profile/$pk"
    fun stories(pk: String) = "stories/$pk"
    fun album(id: String) = "album/" + Uri.encode(id)
    fun chat(conv: String, focus: String = "") = "chat/" + Uri.encode(conv) + (if (focus.isNotEmpty()) "?focus=" + Uri.encode(focus) else "")
    fun event(id: String, date: Long = 0L) = "event/" + Uri.encode(id) + "?date=$date"
    fun photo(path: String, key: String) = "photo?path=" + Uri.encode(path) + "&key=" + Uri.encode(key)
    fun list(id: String) = "list/" + Uri.encode(id)
}

// ====================================================================== device lock

object Unlock {
    private fun authenticators() = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun available(ctx: Context): Boolean {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        return km?.isDeviceSecure == true
    }

    fun prompt(ctx: Context, title: String, onOk: () -> Unit) {
        val activity = ctx as? FragmentActivity ?: return
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        val b = BiometricPrompt.PromptInfo.Builder().setTitle(title)
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(authenticators())
        } else {
            @Suppress("DEPRECATION")
            b.setDeviceCredentialAllowed(true)
        }
        runCatching { prompt.authenticate(b.build()) }
    }
}

@Composable
private fun LockScreen() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        delay(350)
        Unlock.prompt(ctx, t("Sblocca Monolith")) { AppLock.unlock() }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MonolithMark(Modifier.height(120.dp))
            Spacer(Modifier.height(28.dp))
            Text(t("Monolith è bloccato"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { Unlock.prompt(ctx, t("Sblocca Monolith")) { AppLock.unlock() } }, shape = CircleShape) {
                Icon(Icons.Rounded.Fingerprint, null)
                Spacer(Modifier.padding(4.dp))
                Text(t("Sblocca"))
            }
        }
    }
}

// ====================================================================== root

@Composable
fun AppRoot() {
    val profile by Graph.identity.profile.collectAsState()
    val kidsRole by com.monolith.app.kids.Kids.role.collectAsState()
    val kidParent by com.monolith.app.kids.Kids.parent.collectAsState()
    var intro by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1450)
        intro = false
    }
    Box(Modifier.fillMaxSize().tapSounds()) {
        AnimatedContent(
            targetState = when {
                intro -> 0
                profile == null -> 1
                // Monolith Kids: a child's phone waits for the parent before opening
                com.monolith.app.kids.Kids.edition && kidsRole == com.monolith.app.kids.KidRole.KID && kidParent == null -> 3
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
                1 -> if (com.monolith.app.kids.Kids.edition) com.monolith.app.kids.KidsOnboarding() else OnboardingFlow()
                3 -> com.monolith.app.kids.KidPairScreen()
                else -> MainNav()
            }
        }
        if (profile != null && !intro) com.monolith.app.kids.KidQuietOverlay()
        if (profile != null && !intro) com.monolith.app.ui.screens.DevOverlays()
        AnimatedVisibility(AppLock.locked && profile != null && !intro, enter = fadeIn(), exit = fadeOut(tween(350))) {
            LockScreen()
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
    val ctx = LocalContext.current
    val nav = rememberNavController()
    LaunchedEffect(nav) { nav.currentBackStackEntryFlow.collect { NavBus.route.value = it.destination.route } }
    val homeTab = rememberSaveable { mutableIntStateOf(if (com.monolith.app.kids.Kids.isParent) 5 else 0) }
    val req by NavBus.requests.collectAsState()
    val call by Graph.calls.state.collectAsState()

    LaunchedEffect(Unit) {
        if (!Graph.prefs.permissionsDone) nav.navigate("permissions?first=true")
    }

    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        NavBus.requests.value = null
        if (r.call) {
            if (r.acceptCall) Graph.calls.accept()
            if (nav.currentDestination?.route != "call") nav.navigate("call") { launchSingleTop = true }
            return@LaunchedEffect
        }
        if (r.quickShare) {
            homeTab.intValue = 2
            nav.popBackStack("home", inclusive = false)
            val last = Graph.prefs.lastShareFriends
            val friends = Graph.db.friends().friendsNow().map { it.pubkey }.toSet()
            val targets = last.filter { it in friends }
            if (targets.isNotEmpty() && Graph.location.hasPermission()) {
                Graph.location.startSharing(targets, Graph.prefs.lastShareMinutes)
                Toast.makeText(ctx, t("Condivisione della posizione avviata"), Toast.LENGTH_SHORT).show()
            } else {
                NavBus.openShareSheet.value = true
            }
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
            r.route != null -> {
                nav.popBackStack("home", inclusive = false)
                nav.navigate(r.route) { launchSingleTop = true }
            }
            else -> nav.popBackStack("home", inclusive = false)
        }
    }

    LaunchedEffect(call.phase) {
        if ((call.phase == CallPhase.INCOMING || call.phase == CallPhase.OUTGOING) && nav.currentDestination?.route != "call") {
            nav.navigate("call") { launchSingleTop = true }
        }
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
                    "chat/{conv}?focus={focus}",
                    arguments = listOf(
                        navArgument("conv") { type = NavType.StringType },
                        navArgument("focus") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        ChatScreen(nav, Uri.decode(entry.arguments?.getString("conv") ?: ""), Uri.decode(entry.arguments?.getString("focus") ?: ""))
                    }
                }
                composable("search") { SearchScreen(nav) }
                composable("shard/{id}") { entry -> ShardScreen(nav, entry.arguments?.getString("id") ?: "") }
                composable("profile/{pk}") { entry -> com.monolith.app.ui.screens.ProfileScreen(nav, entry.arguments?.getString("pk") ?: "") }
                composable("editprofile") { com.monolith.app.ui.screens.EditProfileScreen(nav) }
                composable("stories/{pk}") { entry -> com.monolith.app.ui.screens.StoryViewer(nav, entry.arguments?.getString("pk") ?: "") }
                composable("film", enterTransition = { fadeIn(tween(400)) }, exitTransition = { fadeOut(tween(300)) }) { com.monolith.app.ui.screens.FilmScreen { nav.popBackStack() } }
                composable("compass/{pk}") { entry -> com.monolith.app.ui.screens.CompassScreen(nav, entry.arguments?.getString("pk") ?: "") }
                composable("highfive") { com.monolith.app.ui.screens.HighFiveScreen(nav) }
                composable("photoedit") { com.monolith.app.ui.screens.PhotoEditScreen(nav) }
                composable("diary") { com.monolith.app.ui.screens.DiaryScreen(nav) }
                composable("once/{id}") { entry -> com.monolith.app.ui.screens.OnceViewer(nav, entry.arguments?.getString("id") ?: "") }
                composable("board/{conv}") { entry -> com.monolith.app.ui.screens.BoardScreen(nav, entry.arguments?.getString("conv") ?: "") }
                composable("wrapped", enterTransition = { fadeIn(tween(400)) }, exitTransition = { fadeOut(tween(300)) }) { com.monolith.app.ui.screens.WrappedScreen(nav) }
                composable("bill/{id}") { entry -> com.monolith.app.ui.screens.BillScreen(nav, entry.arguments?.getString("id") ?: "") }
                composable("album/{id}") { entry -> com.monolith.app.ui.screens.AlbumScreen(nav, Uri.decode(entry.arguments?.getString("id") ?: "")) }
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
                        EventEditorScreen(nav, Uri.decode(entry.arguments?.getString("id") ?: "new"), entry.arguments?.getLong("date") ?: 0L)
                    }
                }
                composable("list/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        ListDetailScreen(nav, Uri.decode(entry.arguments?.getString("id") ?: "new"))
                    }
                }
                composable("group/new") {
                    CompositionLocalProvider(LocalNavScope provides this) { NewGroupScreen(nav) }
                }
                composable("kid/{pk}") { entry -> com.monolith.app.kids.ChildScreen(nav, entry.arguments?.getString("pk") ?: "") }
                composable("settings") {
                    CompositionLocalProvider(LocalNavScope provides this) { SettingsScreen(nav) }
                }
                composable("journal") { JournalScreen(nav) }
                composable("credits") { CreditsScreen(nav) }
                composable("sounds") { SoundsScreen(nav) }
                composable(
                    "permissions?first={first}",
                    arguments = listOf(navArgument("first") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    val first = entry.arguments?.getBoolean("first") ?: false
                    PermissionsScreen(first = first, onDone = {
                        Graph.prefs.permissionsDone = true
                        nav.popBackStack()
                    })
                }
                composable(
                    "call",
                    enterTransition = { fadeIn(tween(350)) + scaleIn(tween(450, easing = Motion.Emphasized), initialScale = 0.94f) },
                    popExitTransition = { fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.96f) },
                ) { CallScreen(nav) }
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

private val TABS = mapOf(
    0 to TabSpec("Agenda", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    1 to TabSpec("Chat", Icons.Rounded.Forum, Icons.Outlined.Forum),
    2 to TabSpec("Mappa", Icons.Rounded.Map, Icons.Outlined.Map),
    3 to TabSpec("Amici", Icons.Rounded.People, Icons.Outlined.People),
    4 to TabSpec("Shard", com.monolith.app.ui.components.ShardIcons.Filled, com.monolith.app.ui.components.ShardIcons.Outline),
    5 to TabSpec("Bambini", Icons.Rounded.FamilyRestroom, Icons.Outlined.FamilyRestroom),
)

/** the tabs of the home screen: Monolith Kids shows fewer, and the parent's own */
private fun tabIds(): List<Int> = when {
    com.monolith.app.kids.Kids.isParent -> listOf(5, 1, 2)
    com.monolith.app.kids.Kids.isKid -> listOf(0, 1, 2, 3)
    else -> listOf(0, 1, 2, 3, 4)
}

@Composable
internal fun HomeScreen(nav: NavHostController, tab: Int, onTab: (Int) -> Unit) {
    val unread by remember { Graph.db.messages().unread() }.collectAsState(initial = emptyList())
    val friends by remember { Graph.db.friends().all() }.collectAsState(initial = emptyList())
    val unreadTotal = unread.filter { !com.monolith.app.net.Shard.isQuietConv(it.convId) && !it.convId.startsWith("g:shard.") }.sumOf { it.n }
    val unreadShards = unread.filter { it.convId.startsWith("g:shard.") }.sumOf { it.n }
    val requests = friends.count { it.status == FriendStatus.INCOMING }
    val kidsAsks = com.monolith.app.kids.Kids.children.collectAsState().value.sumOf { it.pending.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                tabIds().forEach { i ->
                    val tb = TABS.getValue(i)
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
                                4 -> unreadShards
                                5 -> kidsAsks
                                else -> 0
                            }
                            val badgeScale by animateFloatAsState(if (badge > 0) 1f else 0f, Motion.bouncy(), label = "badge")
                            BadgedBox(badge = {
                                if (badge > 0 || badgeScale > 0.01f) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.graphicsLayer { scaleX = badgeScale; scaleY = badgeScale },
                                    ) { Text(if (badge > 99) "99+" else badge.coerceAtLeast(1).toString()) }
                                }
                            }) {
                                Icon(
                                    if (selected) tb.on else tb.off,
                                    contentDescription = t(tb.label),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale; scaleY = scale
                                        translationY = lift.toPx()
                                    },
                                )
                            }
                        },
                        label = { Text(t(tb.label), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) },
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
        ) { tb ->
            when (tb) {
                0 -> AgendaScreen(nav)
                1 -> ChatListScreen(nav)
                2 -> MapScreen(nav)
                4 -> com.monolith.app.ui.screens.ShardsHome(nav)
                5 -> com.monolith.app.kids.ParentHome(nav)
                else -> FriendsScreen(nav, onOpenChat = { pk -> nav.navigate(Routes.chat(pk)) })
            }
        }
    }
}
