package com.monolith.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monolith.app.core.Keys
import com.monolith.app.core.Profile
import com.monolith.app.data.Birthday
import com.monolith.app.data.Friend
import com.monolith.app.data.FriendStatus
import com.monolith.app.data.Message
import com.monolith.app.data.MsgStatus
import com.monolith.app.data.Status
import com.monolith.app.ui.theme.MonolithTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens with sample data, rendered on the JVM (no phone needed).
 * Run by CI when the commit message contains [shots]; the pictures are published as a release.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "it-rIT-w400dp-h860dp-xxhdpi")
class Shots {
    @get:Rule val compose = createComposeRule()

    private val meSk = com.monolith.app.crypto.Secp256k1.generatePrivateKey()
    private fun pub(sk: ByteArray) = com.monolith.app.crypto.Hex.encode(com.monolith.app.crypto.Secp256k1.publicKey(sk))
    private fun newPub() = pub(com.monolith.app.crypto.Secp256k1.generatePrivateKey())
    private val me = pub(meSk)
    private val ada = newPub()
    private val leo = newPub()
    private val mia = newPub()
    private val tom = newPub()

    @Before
    fun seed() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Graph.identity.previewAs(Keys(meSk, me), Profile("christian", "Christian", "🗿", "Costruisco cose. Monolith ogni giorno."))
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val db = Graph.db
        db.friends().upsert(Friend(ada, "ada", "Ada Rossi", "🦊", "Fotografa, caffè e montagne", FriendStatus.FRIEND, now - 400 * day, lastSeen = now))
        db.friends().upsert(Friend(leo, "leo", "Leo Bianchi", "🐻", "", FriendStatus.FRIEND, now - 40 * day, lastSeen = now - 3_600_000))
        db.friends().upsert(Friend(mia, "mia", "Mia Verdi", "🌙", "Musica sempre", FriendStatus.FRIEND, now - 120 * day))
        db.friends().upsert(Friend(tom, "tom", "Tommaso", "🚲", "", FriendStatus.INCOMING, now - day))
        val texts = listOf(
            true to "Ci vediamo domani alle 9 al solito posto?",
            false to "Sì! Porto anche le foto della gita",
            true to "Perfetto 🙌",
            false to "Hai visto il tramonto ieri sera? Era incredibile, sembrava dipinto",
            true to "Sì, l'ho fotografato dal tetto",
        )
        texts.forEachIndexed { i, (out, tx) ->
            db.messages().upsert(
                Message(
                    "m$i", ada, if (out) me else ada, out, now - (texts.size - i) * 600_000L, "text", tx,
                    status = if (out) MsgStatus.READ else MsgStatus.DELIVERED,
                ),
            )
        }
        db.messages().upsert(Message("l1", leo, leo, false, now - 2 * 3_600_000L, "text", "Stasera partita?", status = MsgStatus.DELIVERED))
        db.messages().upsert(Message("n1", mia, me, true, now - day, "text", "Grazie per ieri!", status = MsgStatus.READ))
        // a long streak with Ada
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val days = (0 until 120).map { fmt.format(java.util.Date(now - it * day)) }.toSet()
        Graph.bondDays.add(ada, days)
        Graph.statuses.setMine(Status("☕", "In pausa caffè", now + 3_600_000))
        Graph.statuses.setFriend(ada, Status("📷", "In giro a fotografare", now + 3_600_000))
        Graph.birthdays.setMine(Birthday(3, 14))
        val earned = JSONObject()
        listOf("first_word", "night_owl", "first_call", "friends5", "streak7", "streak30", "streak100", "monolith", "founder", "storyteller", "winner", "pioneer")
            .forEachIndexed { i, id -> earned.put(id, now - i * day) }
        ctx.getSharedPreferences("badges", Context.MODE_PRIVATE).edit().putString("earned", earned.toString()).putBoolean("primed", true).apply()
        com.monolith.app.net.Badges.init(ctx)
        Graph.profileExtras.read(ada, JSONObject().put("cv", "mountains").put("bg", "first_word,photographer,streak100,explorer,artist,anniversary"))
        Graph.profileExtras.setCover("stars")
        Graph.profileExtras.setFeatured(listOf("streak100", "monolith", "pioneer"))
        Graph.profileExtras.read(ada, JSONObject().put("bf", "streak100,photographer"))
        val today = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        db.agenda().upsert(com.monolith.app.data.AgendaEvent("e1", "Caffè con Ada", start = today + 9 * 3_600_000L, end = today + 10 * 3_600_000L, emoji = "☕", createdAt = now, updatedAt = now))
        db.agenda().upsert(com.monolith.app.data.AgendaEvent("e2", "Palestra", start = today + 18 * 3_600_000L + 1_800_000L, emoji = "🏋️", createdAt = now, updatedAt = now))
        db.agenda().upsert(com.monolith.app.data.AgendaEvent("e3", "Compleanno di Leo", start = today + 2 * day, allDay = true, emoji = "🎂", createdAt = now, updatedAt = now))
        // a shard with rooms, messages and a board
        val sh = JSONObject().put("id", "s1").put("n", "Montagna").put("e", "🏔️").put("o", me).put("u", now)
            .put("m", org.json.JSONArray().put(me).put(ada).put(leo).put(mia)).put("md", org.json.JSONArray().put(ada))
            .put("r", org.json.JSONArray()
                .put(JSONObject().put("id", "gen").put("n", "generale").put("v", false))
                .put(JSONObject().put("id", "gite").put("n", "gite").put("v", false))
                .put(JSONObject().put("id", "staff").put("n", "organizzatori").put("v", false).put("pm", org.json.JSONArray().put(me).put(ada)))
                .put(JSONObject().put("id", "voce").put("n", "chiacchiere").put("v", true)))
            .put("wl", "Benvenuti! Qui organizziamo le uscite in montagna.")
            .put("tt", JSONObject().put(ada, "Guida"))
            .put("nb", org.json.JSONArray().put(JSONObject().put("id", "n1").put("x", "Sabato: ritrovo alle 7 al parcheggio").put("by", ada).put("at", now)))
        java.io.File(ctx.filesDir, "shards.json").writeText(org.json.JSONArray().put(sh).toString())
        Graph.shards = com.monolith.app.net.ShardManager(ctx, Graph.scope)
        val gid = "shard.s1.gen"
        db.groups().upsert(com.monolith.app.data.Group(gid, "Montagna · #generale", "🏔️", listOf(me, ada, leo, mia).joinToString(","), me, now))
        listOf(ada to "Chi viene sabato?", leo to "Io ci sono!", me to "Porto il thermos ☕", mia to "Ci sono anche io, ma arrivo alle 8").forEachIndexed { i, (who, tx) ->
            db.messages().upsert(Message("g$i", "g:$gid", who, who == me, now - (4 - i) * 300_000L, "text", tx, status = MsgStatus.READ))
        }
        Thread.sleep(300)
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun shot(name: String, dark: Boolean = false, palette: String = "mono", act: (() -> Unit)? = null, content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            MonolithTheme(if (dark) 2 else 1, palette) {
                // like MainActivity: a surface gives the right text colour in dark mode
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(nav, "s") {
                        composable("s") { content(nav) }
                        composable("x") { }
                    }
                }
            }
        }
        // room loads on its own threads: give it time, then let the animations end
        settle()
        if (act != null) {
            act()
            settle()
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/shots/$name.png")
    }

    private fun settle() {
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
    }

    private fun click(text: String) = compose.onAllNodesWithText(text, substring = false, useUnmergedTree = true).onFirst().performClick()

    @Test fun home_agenda() = shot("01_home_agenda") { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun home_chat() = shot("02_home_chat") { com.monolith.app.ui.HomeScreen(it, 1) {} }
    @Test fun home_friends() = shot("03_home_friends") { com.monolith.app.ui.HomeScreen(it, 3) {} }
    @Test fun home_shards() = shot("04_home_shards") { com.monolith.app.ui.HomeScreen(it, 4) {} }
    @Config(qualifiers = "+h1500dp") @Test fun profile_mine() = shot("05_profile_mine") { com.monolith.app.ui.screens.ProfileScreen(it, me) }
    @Config(qualifiers = "+h1500dp") @Test fun profile_friend() = shot("06_profile_friend") { com.monolith.app.ui.screens.ProfileScreen(it, ada) }
    @Test fun profile_friend_dark() = shot("07_profile_friend_dark", dark = true) { com.monolith.app.ui.screens.ProfileScreen(it, ada) }
    @Config(qualifiers = "+h2900dp") @Test fun settings() = shot("08_settings") { com.monolith.app.ui.screens.SettingsScreen(it) }
    @Config(qualifiers = "+h1900dp") @Test fun edit_profile() = shot("12_edit_profile") { com.monolith.app.ui.screens.EditProfileScreen(it) }
    @Test fun story_sheet() = shot("13_story_sheet", act = { click("Nuova storia") }) { com.monolith.app.ui.screens.ProfileScreen(it, me) }
    @Test fun badges_sheet() = shot("14_badges_sheet", act = { click("Vedi tutti") }) { com.monolith.app.ui.screens.ProfileScreen(it, me) }
    @Test fun agenda_lists() = shot("15_agenda_lists", act = { click("Liste") }) { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun agenda_expenses() = shot("16_agenda_expenses", act = { click("Spese") }) { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun agenda_albums() = shot("17_agenda_albums", act = { click("Album") }) { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun agenda_habits() = shot("18_agenda_habits", act = { click("Abitudini") }) { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun agenda_month() = shot("19_agenda_dark", dark = true) { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun chat_dark() = shot("20_chat_dark", dark = true) { com.monolith.app.ui.screens.ChatScreen(it, ada) }
    @Test fun chat_attach() = shot("21_chat_attach", act = { compose.onAllNodesWithContentDescription("Allega").onFirst().performClick() }) { com.monolith.app.ui.screens.ChatScreen(it, ada) }
    @Test fun event_editor() = shot("22_event_editor") { com.monolith.app.ui.screens.EventEditorScreen(it, "e1", 0L) }
    @Test fun shard() = shot("25_shard") { com.monolith.app.ui.HomeScreen(it, 4) {} }
    @Test fun onboarding() = shot("26_onboarding") { com.monolith.app.ui.screens.OnboardingFlow() }
    @Test fun film() = shot("27_film", act = { compose.mainClock.advanceTimeBy(4300L * 6) }) { com.monolith.app.ui.screens.FeatureFilm(onDone = {}) }
    @Test fun credits() = shot("28_credits") { com.monolith.app.ui.screens.CreditsScreen(it) }
    @Test fun list_new() = shot("29_list_new") { com.monolith.app.ui.screens.ListDetailScreen(it, "new") }
    @Test fun new_group() = shot("30_new_group") { com.monolith.app.ui.screens.NewGroupScreen(it) }
    @Test fun diary() = shot("31_diary") { com.monolith.app.ui.screens.DiaryScreen(it) }
    @Test fun permissions() = shot("32_permissions") { com.monolith.app.ui.screens.PermissionsScreen(first = true) {} }
    @Test fun chat_list_dark() = shot("33_chat_list_dark", dark = true) { com.monolith.app.ui.HomeScreen(it, 1) {} }
    @Test fun friends_dark() = shot("34_friends_dark", dark = true) { com.monolith.app.ui.HomeScreen(it, 3) {} }
    @Test fun group_chat() = shot("35_shard_room") { com.monolith.app.ui.screens.ChatScreen(it, "g:shard.s1.gen") }
    @Test fun journal() = shot("23_journal") { com.monolith.app.ui.screens.JournalScreen(it) }
    @Test fun sounds() = shot("24_sounds") { com.monolith.app.ui.screens.SoundsScreen(it) }
    @Test fun badges() = shot("10_badges") { BadgeBoard(false) }
    @Test fun badges_dark() = shot("11_badges_dark", dark = true) { BadgeBoard(true) }
    @Test fun chat() = shot("09_chat") { com.monolith.app.ui.screens.ChatScreen(it, ada) }

    // ---- 2.4.0: colours, calls, audio, developer channel
    @Config(qualifiers = "+h1300dp") @Test fun themes() = shot("36_themes") { ThemeBoard() }
    @Config(qualifiers = "+h1300dp") @Test fun themes_dark() = shot("37_themes_dark", dark = true) { ThemeBoard() }
    @Test fun home_ocean() = shot("38_home_ocean", palette = "ocean") { com.monolith.app.ui.HomeScreen(it, 1) {} }
    @Test fun chat_rose_dark() = shot("39_chat_rose_dark", dark = true, palette = "rose") { com.monolith.app.ui.screens.ChatScreen(it, ada) }
    @Test fun agenda_forest() = shot("40_agenda_forest", palette = "forest") { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Config(qualifiers = "+h2900dp") @Test fun settings_sunset() = shot("41_settings_sunset", palette = "sunset") { com.monolith.app.ui.screens.SettingsScreen(it) }
    @Test fun call_voice() = shot("42_call_voice") { voiceCall(); com.monolith.app.ui.screens.CallContent(Graph.calls.state.value) }
    @Test fun call_audio_sheet() = shot("43_call_audio", act = { click("AirPods Pro") }) { voiceCall(); com.monolith.app.ui.screens.CallContent(Graph.calls.state.value) }
    @Test fun call_more() = shot("44_call_more", act = { click("Altro") }) { voiceCall(); com.monolith.app.ui.screens.CallContent(Graph.calls.state.value) }
    @Test fun call_room() = shot("45_call_room") { roomCall(); com.monolith.app.ui.screens.CallContent(Graph.calls.state.value) }
    @Test fun call_incoming() = shot("46_call_incoming") {
        Graph.calls.preview(com.monolith.app.rtc.CallUi(phase = com.monolith.app.rtc.CallPhase.INCOMING, cid = "c1", caller = ada, members = listOf(me, ada), peer = ada, video = true))
        com.monolith.app.ui.screens.CallContent(Graph.calls.state.value)
    }
    @Test fun dev_news() = shot("47_dev_news") {
        Graph.dev.preview(com.monolith.app.net.DevState(active = true, news = com.monolith.app.net.DevNews("n1", "Benvenuti nella 2.4", "Temi di colore, chiamate nuove e cuffie Bluetooth. Grazie a tutti per le idee!", "")))
        com.monolith.app.ui.HomeScreen(it, 1) {}
        com.monolith.app.ui.screens.DevOverlays()
    }
    @Test fun dev_update() = shot("48_dev_update") {
        Graph.dev.preview(com.monolith.app.net.DevState(active = true, latest = "9.0.0", min = "9.0.0"))
        com.monolith.app.ui.screens.DevOverlays()
    }

    private fun voiceCall() {
        val bt = com.monolith.app.rtc.AudioRoute("BT:AirPods Pro", com.monolith.app.rtc.RouteKind.BLUETOOTH, "AirPods Pro")
        Graph.calls.routes.preview(
            listOf(bt, com.monolith.app.rtc.AudioRoute("EARPIECE", com.monolith.app.rtc.RouteKind.EARPIECE, "Telefono"), com.monolith.app.rtc.AudioRoute("SPEAKER", com.monolith.app.rtc.RouteKind.SPEAKER, "Vivavoce")),
            bt,
            listOf(com.monolith.app.rtc.AudioRoute("BT:AirPods Pro", com.monolith.app.rtc.RouteKind.BLUETOOTH, "Microfono di AirPods Pro"), com.monolith.app.rtc.AudioRoute("MIC", com.monolith.app.rtc.RouteKind.EARPIECE, "Microfono del telefono")),
        )
        Graph.calls.preview(com.monolith.app.rtc.CallUi(
            phase = com.monolith.app.rtc.CallPhase.ACTIVE, cid = "c1", caller = me, members = listOf(me, ada), peer = ada,
            participants = mapOf(ada to com.monolith.app.rtc.Participant(ada, connected = true)), since = System.currentTimeMillis() - 754_000,
        ))
    }

    private fun roomCall() {
        Graph.calls.routes.preview(listOf(com.monolith.app.rtc.AudioRoute("SPEAKER", com.monolith.app.rtc.RouteKind.SPEAKER, "Vivavoce")), com.monolith.app.rtc.AudioRoute("SPEAKER", com.monolith.app.rtc.RouteKind.SPEAKER, "Vivavoce"), emptyList())
        Graph.calls.preview(com.monolith.app.rtc.CallUi(
            phase = com.monolith.app.rtc.CallPhase.ACTIVE, cid = "room.s1.voice", caller = me, members = listOf(me, ada, leo, mia),
            participants = listOf(ada, leo, mia).associateWith { com.monolith.app.rtc.Participant(it, connected = true) }, since = System.currentTimeMillis() - 1_830_000,
        ))
    }

    @Composable
    private fun ThemeBoard() {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp)) {
            androidx.compose.material3.Text("Colori", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
            com.monolith.app.ui.screens.ThemeGrid()
        }
    }

    @Composable
    private fun BadgeBoard(dark: Boolean) {
        androidx.compose.foundation.layout.FlowRow(
            androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).padding(8.dp),
            maxItemsInEachRow = 6,
        ) {
            com.monolith.app.net.Badges.ALL.forEachIndexed { i, d ->
                com.monolith.app.ui.components.BadgeEmblem(d, i % 9 != 8, 62.dp, androidx.compose.ui.Modifier.padding(2.dp))
            }
        }
    }
}
