package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class TelegramErrorsTest {

    private val fallback = "Akci se nepodařilo provést."

    @Test
    fun `neznama hlaska od TDLib se uzivateli neukaze`() {
        assertEquals(fallback, IllegalStateException("CHANNEL_PRIVATE").userMessage(fallback))
    }

    @Test
    fun `vyjimka bez hlasky spadne na zalozni vetu`() {
        assertEquals(fallback, IllegalStateException().userMessage(fallback))
    }

    @Test
    fun `konstanta od serveru dostane vlastni vetu`() {
        assertEquals(
            "Kód nesedí.",
            IllegalStateException("PHONE_CODE_INVALID").userMessage(fallback),
        )
    }

    @Test
    fun `veta od TDLib dostane vlastni vetu`() {
        assertEquals(
            "Víc chatů připnout nejde. Vyšší limit má Telegram Premium.",
            IllegalStateException("The maximum number of pinned chats exceeded").userMessage(fallback),
        )
    }

    @Test
    fun `tyz limit prichazi i jako konstanta od serveru`() {
        assertEquals(
            IllegalStateException("The maximum number of pinned chats exceeded").userMessage(fallback),
            IllegalStateException("PINNED_DIALOGS_TOO_MUCH").userMessage(fallback),
        )
    }

    @Test
    fun `flood wait nese cas cekani`() {
        assertEquals(
            "Moc rychle po sobě. Zkus to za 23 s.",
            IllegalStateException("FLOOD_WAIT_23").userMessage(fallback),
        )
    }

    @Test
    fun `flood wait z omezovace TDLib se cte taky`() {
        assertEquals(
            "Moc rychle po sobě. Zkus to za 5 s.",
            IllegalStateException("Too Many Requests: retry after 5").userMessage(fallback),
        )
    }

    @Test
    fun `dlouhe cekani se rika v minutach a zaokrouhluje nahoru`() {
        assertEquals(
            "Moc rychle po sobě. Zkus to za 4 min.",
            IllegalStateException("FLOOD_WAIT_181").userMessage(fallback),
        )
    }

    @Test
    fun `flood wait bez cisla nesmi spadnout`() {
        assertEquals(fallback, IllegalStateException("FLOOD_WAIT_").userMessage(fallback))
    }
}
