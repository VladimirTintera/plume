package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.KeyboardButtonSourceMessage
import dev.g000sha256.tdl.dto.KeyboardButtonSourceWebApp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tlačítko „vyber lidi / chat" ze zprávy i od mini aplikace jde do TDLib se správným zdrojem. */
class BotShareSourceTest {

    @Test
    fun zpravaBota() {
        assertEquals(KeyboardButtonSourceMessage(chatId = 5, messageId = 7), BotShareSource.Message(5, 7).toTdl())
    }

    @Test
    fun pripraveneMiniAplikaci() {
        assertEquals(
            KeyboardButtonSourceWebApp(botUserId = 42, preparedButtonId = "r1"),
            BotShareSource.WebApp(botUserId = 42, preparedButtonId = "r1").toTdl(),
        )
    }
}
