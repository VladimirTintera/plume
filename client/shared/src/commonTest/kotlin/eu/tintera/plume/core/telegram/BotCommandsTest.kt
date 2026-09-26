package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Napovídání příkazů bota podle rozepsaného textu a co se za příkaz pošle. */
class BotCommandsTest {

    private val commands = listOf(
        BotCommandItem("start", "Začít", "/start"),
        BotCommandItem("stats", "Statistiky", "/stats"),
        BotCommandItem("help", "Nápověda", "/help"),
    )

    @Test
    fun samotneLomitkoNabidneVse() {
        assertEquals(commands, commandSuggestions("/", commands))
    }

    @Test
    fun filtrujePodleZacatku() {
        assertEquals(listOf("start", "stats"), commandSuggestions("/st", commands)?.map { it.command })
        assertEquals(listOf("start", "stats"), commandSuggestions("/ST", commands)?.map { it.command })
    }

    @Test
    fun zadnyNesediJePrazdnySeznam() {
        assertEquals(emptyList(), commandSuggestions("/xyz", commands))
    }

    @Test
    fun textBezLomitkaNeboSMezerouPrikazNeni() {
        assertNull(commandSuggestions("ahoj", commands))
        assertNull(commandSuggestions("/start něco", commands))
        assertNull(commandSuggestions("/start\n", commands))
        assertNull(commandSuggestions("", commands))
    }

    @Test
    fun bezPrikazuNicNenapovida() {
        assertNull(commandSuggestions("/", emptyList()))
    }

    @Test
    fun jmenoBotaZaZavinacemSeNeresi() {
        assertEquals(listOf("help"), commandSuggestions("/help@muj_bot", commands)?.map { it.command })
    }

    @Test
    fun veSkupineSPriponouJmena() {
        assertEquals("/start@muj_bot", botCommandText("start", "muj_bot", inGroup = true))
        assertEquals("/start", botCommandText("start", "muj_bot", inGroup = false))
        // Bot bez jména (nemá ho vůbec) — holý příkaz je jediné, co jde poslat.
        assertEquals("/start", botCommandText("start", null, inGroup = true))
    }
}
