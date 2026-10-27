package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.testMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ohlášení příchozí zprávy ([NewMessageAnnouncer]): živá oblast dostane text jen u zprávy, která
 * opravdu přišla, a po chvíli zase zůstane prázdná.
 */
@OptIn(ExperimentalTestApi::class)
class NewMessageAnnouncerTest {

    private val liveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun announcerTest(block: AnnouncerScope.() -> Unit) = runComposeUiTest {
        var messages by mutableStateOf(listOf(testMessage(1, "ahoj")))
        var live by mutableStateOf(true)
        setContent {
            MaterialTheme {
                Box { NewMessageAnnouncer(messages = messages, live = live, chatTitle = "Jana") }
            }
        }
        AnnouncerScope(
            receive = { message, isLive ->
                live = isLive
                waitForIdle()
                messages = listOf(message) + messages
                waitForIdle()
            },
            announcement = {
                onNode(liveRegion).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            },
            advance = { mainClock.advanceTimeBy(it) },
        ).block()
    }

    private class AnnouncerScope(
        val receive: (ChatMessage, Boolean) -> Unit,
        val announcement: () -> String?,
        val advance: (Long) -> Unit,
    )

    @Test
    fun historieSeNeohlasuje() = announcerTest {
        assertNull(announcement())
    }

    @Test
    fun prichoziZpravaSeOhlasiAPakZmizi() = announcerTest {
        receive(testMessage(2, "jak se máš"), true)
        val text = announcement()
        assertTrue(text != null && "Jana" in text && "jak se máš" in text, "ohlášení: $text")

        advance(6_000)
        assertNull(announcement())
    }

    @Test
    fun vlastniZpravaSeNeohlasi() = announcerTest {
        receive(testMessage(2, "odpověď", outgoing = true), true)
        assertNull(announcement())
    }

    @Test
    fun dotazenaStrankaSeNeohlasi() = announcerTest {
        // Okno v historii: seznam nekončil u přítomnosti, novější zprávy jen dorazily stránkou.
        receive(testMessage(2, "stará"), false)
        assertEquals(null, announcement())
    }
}
