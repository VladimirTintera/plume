package eu.tintera.plume.core.ai

import eu.tintera.ai.core.estimatedTokenCount
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Odhad tokenů pro LiteRT-LM ([estimatedTokenCount]) musí být HORNÍ mez. Čísla jsou naměřená
 * `Conversation.getTokenCount` na desktopu nad týmž promptem (emoji vypuštěná jako před
 * odesláním na desktopu): přestřelení jen dřív rozdělí chat, podstřelení přeplní keš.
 */
class TokenEstimateTest {

    private val samples = listOf(
        "Ahoj, tak co, platí ta sobota? Já bych byl pro sraz v deset u nádraží 😊",
        "Jo platí, jen nevím, jestli stihnu vlak v 9:42, uvidíme. Kdyžtak napíšu 👍",
        "Koukni sem https://www.idos.cz/vlakyautobusymhdvse/spojeni/vysledky/?f=Praha&t=Brno&date=8.11.2026&time=9:42 jede to každou hodinu",
        "Vezmi s sebou deštník, předpověď hlásí přeháňky 🌧️🌧️ a možná i bouřky odpoledne!!!",
        "Rezervoval jsem chatu na dvě noci, záloha 1 500 Kč, číslo účtu 123456789/0100, VS 2026118",
    )

    private fun prompt(maxChars: Int): String {
        val lines = (0 until 400).map { i -> TranscriptLine(if (i % 2 == 0) "Martin" else "Já", samples[i % samples.size]) }
        val text = summaryPrompt("Výlet", lines, "cs", maxChars = maxChars, me = "Já")
        // Znaky mimo základní rovinu (emoji) desktop před odesláním vypouští.
        return text.filterIndexed { _, c -> !c.isSurrogate() }
    }

    @Test
    fun gemmaNaCeskemChatu() {
        // Gemma 4 E2B: 16 330 znaků = 7 547 tokenů.
        val text = prompt(16_000)
        assertTrue(estimatedTokenCount(text) >= 7_547, "odhad ${estimatedTokenCount(text)} pro ${text.length} znaků")
    }

    @Test
    fun qwenNaCeskemChatu() {
        // Qwen3 0,6B: 4 526 znaků = 2 224 tokenů.
        val text = prompt(4_000)
        assertTrue(estimatedTokenCount(text) >= 2_224, "odhad ${estimatedTokenCount(text)} pro ${text.length} znaků")
    }

    @Test
    fun anglictinaSeNepodstreli() {
        // Angličtina má na token víc znaků než čeština; odhad z bajtů ji tedy nadhodnotí, ne podhodnotí.
        val text = "The quick brown fox jumps over the lazy dog. ".repeat(100)
        assertTrue(estimatedTokenCount(text) >= text.length / 4)
    }
}
