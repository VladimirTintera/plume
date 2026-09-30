package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Rozpoznání dotazu na inline bota v poli a skládání stránek výsledků. */
class InlineQueryTest {

    @Test
    fun jmenoAMezeraJeDotaz() {
        assertEquals(InlineQueryDraft("gif", "kočka"), inlineQueryOf("@gif kočka"))
        // Samotné „@bot " už je dotaz — prázdný, bot na něj vrací populární.
        assertEquals(InlineQueryDraft("gif", ""), inlineQueryOf("@gif "))
        // Mezera navíc je součást dotazu, bot ji dostane tak, jak je.
        assertEquals(InlineQueryDraft("wiki", " praha  "), inlineQueryOf("@wiki  praha  "))
        assertEquals(InlineQueryDraft("My_Bot2", "a"), inlineQueryOf("@My_Bot2 a"))
    }

    @Test
    fun bezMezeryJesteDotazNeni() {
        // Do mezery může jít o rozepsanou zmínku.
        assertNull(inlineQueryOf("@gif"))
        assertNull(inlineQueryOf("@"))
        assertNull(inlineQueryOf("@ kočka"))
    }

    @Test
    fun zavinacJenNaZacatku() {
        assertNull(inlineQueryOf("ahoj @gif kočka"))
        assertNull(inlineQueryOf(" @gif kočka"))
        assertNull(inlineQueryOf(""))
    }

    @Test
    fun jmenoMusiBytPlatne() {
        assertNull(inlineQueryOf("@1gif kočka"))
        assertNull(inlineQueryOf("@_gif kočka"))
        assertNull(inlineQueryOf("@gíf kočka"))
        assertNull(inlineQueryOf("@gif-bot kočka"))
        // Zalomení za jménem: to už se píše zpráva, ne dotaz.
        assertNull(inlineQueryOf("@gif\nkočka"))
        assertNull(inlineQueryOf("@" + "a".repeat(33) + " x"))
    }

    private val bot = InlineBotInfo(userId = 1, username = "gif", placeholder = "Search GIFs", needLocation = false)

    private fun page(vararg ids: String, next: String = "", button: InlineResultsButton? = null) = InlineResultsPage(
        queryId = ids.size.toLong(),
        results = ids.map { InlineResult.Row(it, InlineRowKind.Article, it, "", null) },
        nextOffset = next,
        button = button,
    )

    @Test
    fun stranka() {
        val state = InlineQueryState(bot, "ko").withPage("ko", page("a", "b", next = "2"), append = false)
        assertEquals(listOf("a", "b"), state.results.map { it.id })
        assertFalse(state.loading)
        assertTrue(state.canLoadMore)
        assertEquals(2L, state.queryId)
    }

    @Test
    fun dalsiStrankaSePripojiBezDuplicit() {
        val button = InlineResultsButton.StartBot("Nastavit", "setup")
        val first = InlineQueryState(bot, "ko").withPage("ko", page("a", "b", next = "2", button = button), append = false)
        val next = first.copy(loading = true).withPage("ko", page("b", "c"), append = true)
        assertEquals(listOf("a", "b", "c"), next.results.map { it.id })
        assertFalse(next.canLoadMore)
        // Tlačítko nese jen první stránka, další ho nesmí smazat.
        assertEquals(button, next.button)
    }

    @Test
    fun strankaProStaryDotazSeZahodi() {
        val state = InlineQueryState(bot, "ko").withPage("ko", page("a"), append = false).withQuery("kočka")
        assertSame(state, state.withPage("ko", page("x"), append = false))
        // Během načítání zůstávají předchozí výsledky vidět.
        assertEquals(listOf("a"), state.results.map { it.id })
        assertTrue(state.loading)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun nicNenalezenoJenKAktualnimuDotazu() {
        val empty = InlineQueryState(bot, "zzz").withPage("zzz", page(), append = false)
        assertTrue(empty.nothingFound)
        assertFalse(empty.withQuery("zzzz").nothingFound)
        // S tlačítkem od bota to „nic" není — je co nabídnout.
        val withButton = InlineQueryState(bot, "zzz")
            .withPage("zzz", page(button = InlineResultsButton.WebApp("Otevřít", "https://example.com/")), append = false)
        assertFalse(withButton.nothingFound)
    }

    @Test
    fun selhaniJenKAktualnimuDotazu() {
        val state = InlineQueryState(bot, "ko")
        assertTrue(state.withFailure("ko").failed)
        assertSame(state, state.withFailure("k"))
    }
}
