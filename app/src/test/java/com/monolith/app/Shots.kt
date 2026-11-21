package com.monolith.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
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
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens with sample data, rendered on the JVM (no phone needed).
 * Run by CI when the commit message contains [shots]; the pictures are published as a release.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class Shots {
    @get:Rule val compose = createComposeRule()
    @get:Rule val timeout: Timeout = Timeout.seconds(120)

    private val me = "a1".repeat(32)
    private val ada = "b2".repeat(32)
    private val leo = "c3".repeat(32)
    private val mia = "d4".repeat(32)
    private val tom = "e5".repeat(32)

    @Before
    fun seed() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Graph.identity.previewAs(Keys(ByteArray(32) { 7 }, me), Profile("christian", "Christian", "🗿", "Costruisco cose. Monolith ogni giorno."))
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
        Thread.sleep(300)
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            MonolithTheme(if (dark) 2 else 1) {
                val nav = rememberNavController()
                NavHost(nav, "s") {
                    composable("s") { content(nav) }
                    composable("x") { }
                }
            }
        }
        // room loads on its own threads: give it time, then let the animations end
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun home_agenda() = shot("01_home_agenda") { com.monolith.app.ui.HomeScreen(it, 0) {} }
    @Test fun home_chat() = shot("02_home_chat") { com.monolith.app.ui.HomeScreen(it, 1) {} }
    @Test fun home_friends() = shot("03_home_friends") { com.monolith.app.ui.HomeScreen(it, 3) {} }
    @Test fun home_shards() = shot("04_home_shards") { com.monolith.app.ui.HomeScreen(it, 4) {} }
    @Test fun profile_mine() = shot("05_profile_mine") { com.monolith.app.ui.screens.ProfileScreen(it, me) }
    @Test fun profile_friend() = shot("06_profile_friend") { com.monolith.app.ui.screens.ProfileScreen(it, ada) }
    @Test fun profile_friend_dark() = shot("07_profile_friend_dark", dark = true) { com.monolith.app.ui.screens.ProfileScreen(it, ada) }
    @Test fun settings() = shot("08_settings") { com.monolith.app.ui.screens.SettingsScreen(it) }
    @Test fun chat() = shot("09_chat") { com.monolith.app.ui.screens.ChatScreen(it, ada) }
}
