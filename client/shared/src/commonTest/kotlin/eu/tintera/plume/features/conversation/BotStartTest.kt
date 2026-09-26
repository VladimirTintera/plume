package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.BotIntro
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotStartTest {

    private val bot = BotIntro(description = "Umím hody kostkou.")

    @Test
    fun prazdnyChatSBotemNabidneStart() {
        assertTrue(showsBotStart(ConversationState(botIntro = bot, loading = false, reachedStart = true)))
    }

    @Test
    fun predPrvnimNactenimSeStartNenabizi() {
        // Výchozí stav je prázdný a nenačítá — historie ale ještě nebyla ani vyžádaná.
        assertFalse(showsBotStart(ConversationState(botIntro = bot)))
    }

    @Test
    fun behemNacitaniSeStartNenabizi() {
        // Prázdná historie, která se ještě načítá, by se tvářila jako nespuštěný bot.
        assertFalse(showsBotStart(ConversationState(botIntro = bot, loading = true, reachedStart = true)))
    }

    @Test
    fun parametrZOdkazuNabidneStartIVRozepsanemChatu() {
        val state = ConversationState(botIntro = bot, loading = false, botStartParameter = "hello")
        assertTrue(showsBotStart(state))
    }

    @Test
    fun bezBotaStartNeni() {
        assertFalse(showsBotStart(ConversationState(botIntro = null, loading = false)))
        assertFalse(showsBotStart(ConversationState(botIntro = null, loading = false, botStartParameter = "x")))
    }

    @Test
    fun kartaJenSObsahem() {
        assertFalse(BotIntro(description = " ").hasContent)
        assertTrue(bot.hasContent)
    }
}
