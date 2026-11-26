package com.monolith.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monolith.app.core.Keys
import com.monolith.app.crypto.Hex
import com.monolith.app.crypto.Secp256k1
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the developer dashboard with sample data. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "it-rIT-w400dp-h860dp-xxhdpi")
class DevShots {
    @get:Rule val compose = createComposeRule()

    private fun key(): Keys { val sk = Secp256k1.generatePrivateKey(); return Keys(sk, Hex.encode(Secp256k1.publicKey(sk))) }

    private fun seed() {
        val now = System.currentTimeMillis() / 1000
        val names = listOf("christian" to "Christian", "ada.rossi" to "Ada Rossi", "leo" to "Leo Bianchi", "mia" to "Mia Verdi", "tom" to "Tommaso", "giulia" to "Giulia", "sara.b" to "Sara", "marco" to "Marco")
        val emojis = listOf("🗿", "🦊", "🐻", "🌙", "🎸", "🌸", "☕", "🚲")
        val users = names.mapIndexed { i, (u, n) ->
            User(key().pub, u, n, emojis[i], if (i == 1) "Fotografa di tramonti" else "", if (i % 3 == 0) "2.3.1" else "2.4.0",
                if (i % 2 == 0) now - i * 3 * 86400L else 0, now - i * i * 9000L)
        }
        val reporter = users[2].pk
        Dev.preview(
            key(), users,
            Doc(newsId = "n1", newsTitle = "Benvenuti nella 2.4", newsBody = "Temi di colore e chiamate nuove.", latest = "2.4.0", latestUrl = "https://github.com/x/y", blocked = setOf(users[7].pk),
                verified = mapOf(users[0].pk to Ver("Sviluppatore", "mono", "ink"), users[1].pk to Ver("Creator", "seal", "blue")), at = now - 3600),
            listOf(
                Msg("m0", users[5].pk, now - 300, "verify", users[5].pk, "Giulia (@giulia)", "", "Sono Giulia, la sorella di Christian. Mi trovi anche su Instagram come @giulia.", "2.4.0", false),
                Msg("m1", reporter, now - 600, "report", users[7].pk, "Marco", "Spam", "Manda link strani nei gruppi", "2.4.0", false),
                Msg("m2", users[1].pk, now - 7200, "feedback", "", "", "", "Le cuffie funzionano benissimo! Si potrebbe avere un tema viola scuro?", "2.4.0", false),
            ),
        )
    }

    private fun shot(name: String, act: (() -> Unit)? = null, content: @Composable () -> Unit) {
        compose.setContent { DevTheme { content() } }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        if (act != null) { act(); compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/shots/$name.png")
    }

    @Test fun setup() = shot("d1_setup") { SetupScreen {} }
    @Test fun keyIntro() = shot("d2_key") { seed(); KeyIntro {} }
    @Config(qualifiers = "+h1500dp") @Test fun overview() = shot("d3_overview") { seed(); MainScreen(0) }
    @Test fun users() = shot("d4_users") { seed(); MainScreen(1) }
    @Test fun inbox() = shot("d5_inbox") { seed(); MainScreen(2) }
    @Config(qualifiers = "+h1700dp") @Test fun publish() = shot("d6_publish") { seed(); MainScreen(3) }
    @Test fun verifyDialog() = shot("d8_verify") { seed(); VerifyDialog(Dev.users.value[5].pk, "Giulia") {} }
    @Test fun userDialog() = shot("d7_user", act = { compose.onAllNodesWithText("Ada Rossi").onFirst().performClick() }) { seed(); MainScreen(1) }
}
