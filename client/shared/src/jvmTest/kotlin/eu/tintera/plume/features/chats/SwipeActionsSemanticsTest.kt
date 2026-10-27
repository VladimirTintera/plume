package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_mark_read
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/**
 * Akce tahu pro čtečku obrazovky: [SwipeableChat] je dá na uzel s ťuknutím (jako `ChatRow`),
 * s popisky z nabídky nad chatem, a akce opravdu volá tah.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeActionsSemanticsTest {

    @Test
    fun akceTahuJsouNaUzluRadku() = runComposeUiTest {
        val swiped = mutableListOf<ChatSwipeEffect>()
        setContent {
            MaterialTheme {
                SwipeableChat(
                    right = ChatSwipeEffect.MarkRead,
                    left = ChatSwipeEffect.Archive,
                    shape = RoundedCornerShape(16.dp),
                    onSwipe = { swiped += it },
                ) { swipeActions ->
                    Box(swipeActions.contextClickable(onLongClick = {}, onClick = {})) { Text("Jana") }
                }
            }
        }

        val actions = onNodeWithText("Jana").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val expected = runBlocking {
            listOf(readString(Res.string.chats_action_mark_read), readString(Res.string.chats_action_archive))
        }
        assertEquals(expected, actions.map { it.label })
        actions.forEach { it.action() }
        assertEquals(listOf(ChatSwipeEffect.MarkRead, ChatSwipeEffect.Archive), swiped)
    }

    @Test
    fun bezTahuZadneAkce() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SwipeableChat(right = null, left = null, shape = RoundedCornerShape(16.dp), onSwipe = {}) { swipeActions ->
                    Box(swipeActions.contextClickable(onLongClick = {}, onClick = {})) { Text("Jana") }
                }
            }
        }

        assertNull(onNodeWithText("Jana").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions))
    }
}
