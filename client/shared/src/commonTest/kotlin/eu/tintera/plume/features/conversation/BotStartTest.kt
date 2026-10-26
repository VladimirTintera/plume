package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.core.telegram.BotIntro
import eu.tintera.plume.core.telegram.BusinessIntro
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
    fun profilBotaJenVPrazdnemNactenemChatu() {
        assertTrue(showsBotIntro(ConversationState(botIntro = bot, reachedStart = true)))
        // Parametr z odkazu Start nabídne hned, profil až nad načtenou prázdnou historií.
        assertFalse(showsBotIntro(ConversationState(botIntro = bot, botStartParameter = "x")))
        assertFalse(showsBotIntro(ConversationState(botIntro = null, reachedStart = true)))
    }

    private val business = BusinessIntro(title = "Vítejte", message = "Odpovídáme do hodiny.")

    /** Úvodní stránka firmy jen nad načtenou prázdnou historií, jako profil bota. */
    @Test
    fun uvodniStrankaFirmyJenVPrazdnemNactenemChatu() {
        assertTrue(showsBusinessIntro(ConversationState(businessIntro = business, reachedStart = true)))
        assertFalse(showsBusinessIntro(ConversationState(businessIntro = business)))
        assertFalse(showsBusinessIntro(ConversationState(businessIntro = business, loading = true, reachedStart = true)))
        assertFalse(showsBusinessIntro(ConversationState(businessIntro = null, reachedStart = true)))
    }

    /** Firma bez bota Start nenabízí; bot s úvodní stránkou firmy má přednost svou stránkou. */
    @Test
    fun firmaStartNenabiziABotMaPrednost() {
        val firm = ConversationState(businessIntro = business, reachedStart = true)
        assertFalse(showsBotStart(firm))
        assertTrue(showsIntroPage(firm))

        val botFirm = ConversationState(botIntro = bot, businessIntro = business, reachedStart = true)
        assertTrue(showsBotIntro(botFirm))
        assertFalse(showsBusinessIntro(botFirm))
    }

    private val person = testChatInfo().copy(isWithPerson = true)
    private fun empty(info: ChatInfo = person) = ConversationState(info = info, reachedStart = true)

    /** Samolepka na pozdrav: prázdný načtený chat s člověkem, kam se smí psát i posílat samolepky. */
    @Test
    fun pozdravJenVPrazdnemChatuSClovekem() {
        assertTrue(showsGreetingSticker(empty()))
        assertFalse(showsGreetingSticker(ConversationState(info = person)), "před načtením historie ne")
        assertFalse(showsGreetingSticker(empty().copy(messages = listOf(testMessage(1)))), "chat se zprávami ne")
        assertFalse(showsGreetingSticker(empty(testChatInfo())), "bot, Uložené zprávy, skupina ne")
    }

    @Test
    fun pozdravUstoupiStranceBotaFirmyAZakazum() {
        assertFalse(showsGreetingSticker(empty().copy(botIntro = bot)))
        assertFalse(showsGreetingSticker(empty().copy(businessIntro = business)), "firma má vlastní samolepku")
        assertFalse(showsGreetingSticker(empty(person.copy(content = ContentAccess.All.copy(stickers = false)))))
        assertFalse(showsGreetingSticker(empty(person.copy(writeAccess = WriteAccess.entries.first { it != WriteAccess.Allowed }))))
        assertFalse(showsGreetingSticker(empty().copy(error = UiText.Raw("chyba"))), "chybu hlásí prázdný stav")
    }
}
