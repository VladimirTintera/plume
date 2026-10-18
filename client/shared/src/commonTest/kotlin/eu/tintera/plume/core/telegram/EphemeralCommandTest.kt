package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.BotCommand
import dev.g000sha256.tdl.dto.BotCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Komu jde příkaz jako mizející zpráva (`BotCommand.isEphemeral`) a kdy zůstane běžnou zprávou. */
class EphemeralCommandTest {

    private val quiz = BotCommands(
        botUserId = 1,
        commands = arrayOf(BotCommand("tajne", "Jen pro tebe", isEphemeral = true), BotCommand("start", "Začít", isEphemeral = false)),
    )
    private val other = BotCommands(
        botUserId = 2,
        commands = arrayOf(BotCommand("tajne", "Totéž jinde", isEphemeral = false)),
    )
    private val names = mapOf(1L to "QuizBot", 2L to "OtherBot")

    private fun target(text: String, bots: List<BotCommands> = listOf(quiz)) =
        ephemeralCommandBot(text, bots, names::get)

    @Test
    fun mizejiciPrikazJdeSvemuBotovi() {
        assertEquals(1L, target("/tajne"))
        assertEquals(1L, target("/TAJNE argument"))
    }

    @Test
    fun beznyPrikazZustaneBeznouZpravou() {
        assertNull(target("/start"))
    }

    @Test
    fun textBezPrikazuZustaneBeznouZpravou() {
        assertNull(target("tajne"))
        assertNull(target("/"))
        assertNull(target("/neznamy"))
    }

    @Test
    fun jmenoBotaRozhodne() {
        assertEquals(1L, target("/tajne@QuizBot", listOf(quiz, other)))
        assertNull(target("/tajne@OtherBot", listOf(quiz, other)))
    }

    @Test
    fun dvaBotiBezJmenaZnamenajiBeznouZpravu() {
        assertNull(target("/tajne", listOf(quiz, other)))
    }
}
