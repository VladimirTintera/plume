package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.features.conversation.items.MessageA11y
import eu.tintera.plume.features.conversation.items.messageA11y
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bublina pro čtečku obrazovky: uzel s `contextClickable` (jako rám bubliny) a [messageA11y] nad
 * textem zprávy. Ověřuje, na čem popis stojí — že se jméno sloučí PŘED text zprávy (sloučení
 * `text` jde seznamem, rodič první), stav a akce jsou na tomtéž uzlu a akce opravdu volají.
 */
@OptIn(ExperimentalTestApi::class)
class MessageA11ySemanticsTest {

    @Test
    fun bublinaJeJedenUzelSeJmenemStavemAAkcemi() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .contextClickable(onLongClickLabel = "Možnosti zprávy", onLongClick = { calls += "menu" }, onClick = {})
                        .semantics {
                            messageA11y(
                                MessageA11y(
                                    sender = "Jana",
                                    state = "14:32, Přečteno",
                                    replyLabel = "Odpovědět",
                                    reactLabel = "Reagovat ❤️",
                                    onReply = { calls += "reply" },
                                    onReact = { calls += "react" },
                                ),
                            )
                        },
                ) { Text("Ahoj") }
            }
        }

        val node = onNodeWithText("Ahoj", substring = true).fetchSemanticsNode()
        val config = node.config
        assertEquals(listOf("Jana", "Ahoj"), config[SemanticsProperties.Text].map { it.text })
        assertEquals("14:32, Přečteno", config[SemanticsProperties.StateDescription])
        assertEquals("Možnosti zprávy", config.getOrNull(SemanticsActions.OnLongClick)?.label)

        val actions = config[SemanticsActions.CustomActions]
        assertEquals(listOf("Odpovědět", "Reagovat ❤️"), actions.map { it.label })
        actions.forEach { it.action() }
        assertEquals(listOf("reply", "react"), calls)
    }
}
