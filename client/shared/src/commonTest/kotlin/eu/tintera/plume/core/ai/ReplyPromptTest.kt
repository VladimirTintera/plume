package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Překlad výstupu modelu na návrhy, které uvidí uživatel. */
class ReplyPromptTest {

    @Test
    fun beznaOdrazkySeRozeberou() {
        val text = "- Jasně, rád\n- Bohužel nemůžu\n- Dej vědět později"
        assertEquals(listOf("Jasně, rád", "Bohužel nemůžu", "Dej vědět později"), parseReplySuggestions(text))
    }

    @Test
    fun uvodniVetaModeluNeniNavrh() {
        // Model rád začne větou zakončenou dvojtečkou; ta se do čipu dostat nesmí.
        val text = "Here are three replies:\n- Jasně\n- Nemůžu"
        assertEquals(listOf("Jasně", "Nemůžu"), parseReplySuggestions(text))
    }

    @Test
    fun cislovaniAUvozovkySeSundaji() {
        val text = "1. \"Jasně, rád\"\n2) „Nemůžu“\n3. Dej vědět"
        assertEquals(listOf("Jasně, rád", "Nemůžu", "Dej vědět"), parseReplySuggestions(text))
    }

    @Test
    fun cisloNaZacatkuVetySeNEUSEKNE() {
        // „5 minut" není číslování — usekávat po první číslici by zmrzačilo návrh.
        assertEquals(listOf("5 minut a jsem tam"), parseReplySuggestions("- 5 minut a jsem tam"))
    }

    @Test
    fun nejvysTriNavrhy() {
        val text = (1..6).joinToString("\n") { "- navrh $it" }
        assertEquals(MAX_REPLY_SUGGESTIONS, parseReplySuggestions(text).size)
    }

    @Test
    fun duplicitySeZahodi() {
        assertEquals(listOf("Jasně"), parseReplySuggestions("- Jasně\n- Jasně"))
    }

    @Test
    fun prilisDlouhyNavrhSeNenabizi() {
        val long = "a".repeat(MAX_REPLY_CHARS + 1)
        assertEquals(listOf("krátká"), parseReplySuggestions("- $long\n- krátká"))
    }

    @Test
    fun jedinaVetaBezOdrazkyProjdeJakoNavrh() {
        // Když model odrážky nepoužije vůbec, nesmí zbýt prázdno.
        assertEquals(listOf("Jasně, rád"), parseReplySuggestions("Jasně, rád"))
    }

    @Test
    fun prazdnyVystupDaPrazdnySeznam() {
        assertTrue(parseReplySuggestions("").isEmpty())
        assertTrue(parseReplySuggestions("   \n  \n").isEmpty())
    }

    @Test
    fun uplneDlouhyVystupBezOdrazekNeprojde() {
        // Model místo návrhů napsal odstavec — nabídnout se nedá nic.
        assertTrue(parseReplySuggestions("b".repeat(MAX_REPLY_CHARS + 50)).isEmpty())
    }

    @Test
    fun promptNeseJazykKonverzaceIKontext() {
        val lines = listOf(TranscriptLine("Jana", "Dáš si kafe?"))
        val prompt = replyPrompt("Jana", lines, "cs")
        assertTrue(prompt.contains("Dáš si kafe?"), "kontext chybí")
        // Jazyk konverzace je hlavní, čeština jen nápověda — viz replyPrompt.
        assertTrue(prompt.contains("same language as the conversation"), "chybí jazyk konverzace")
        assertTrue(prompt.contains("most likely Czech"), "chybí nápověda o češtině")
        // Bez toho model vykal kamarádovi (ověřeno na zařízení).
        assertTrue(prompt.contains("tykání"), "chybí instrukce o tykání")
    }

    @Test
    fun posledniZpravaStojiVPromptuZVLAST() {
        // Kontext nesmí poslední otázku pohltit — kvůli tomu model odpovídal na rozhovor
        // jako celek místo na to, co právě přišlo.
        val lines = listOf(
            TranscriptLine("Jana", "Zítra beru děti k doktorovi"),
            TranscriptLine("Jana", "Šlo by to přesunout na neděli?"),
        )
        val prompt = replyPrompt("Jana", lines, "cs")
        val contextAt = prompt.indexOf("Zítra beru děti")
        val lastAt = prompt.indexOf("Šlo by to přesunout")
        assertTrue(prompt.contains("Reply to THIS message"), "chybí zdůraznění poslední zprávy")
        assertTrue(contextAt in 1..<lastAt, "poslední zpráva musí stát AŽ ZA kontextem")
    }

    @Test
    fun strukturovanyPromptOFormatuMlci() {
        val lines = listOf(TranscriptLine("Jana", "Dáš si kafe?"))
        val prompt = replyPrompt("Jana", lines, "cs", structured = true)
        // Odrážky a „Replies:" by se praly se schématem, které do promptu přidá ML Kit.
        assertFalse(prompt.contains("starting with"), "strukturovaný prompt nesmí chtít odrážky")
        assertFalse(prompt.trimEnd().endsWith("Replies:"), "strukturovaný prompt nesmí končit výzvou k řádkům")
        // Obsahové pokyny zůstávají.
        assertTrue(prompt.contains("Dáš si kafe?"))
        assertTrue(prompt.contains("tykání"))
        assertTrue(prompt.contains("COMPLETE sentence"))
    }

    @Test
    fun textovyPromptKonciVyzvouKRadkum() {
        val prompt = replyPrompt("Jana", listOf(TranscriptLine("Jana", "Dáš si kafe?")), "cs")
        assertTrue(prompt.contains("starting with"))
        assertTrue(prompt.endsWith("Replies:"))
    }

    @Test
    fun strukturovaneNavrhyProjdouStejnymSitem() {
        val output = ReplySuggestionsOutput(
            listOf("\"Jasně, rád\"", "  ", "Jasně, rád", "b".repeat(MAX_REPLY_CHARS + 1), "- Nemůžu", "Dej vědět", "Navíc"),
        )
        assertEquals(listOf("Jasně, rád", "Nemůžu", "Dej vědět"), cleanReplySuggestions(output))
    }

    @Test
    fun chybejiciPoleJePrazdnySeznam() {
        // Gson staví objekt bez konstruktoru: pole, které model vynechá, je null.
        assertTrue(cleanReplySuggestions(ReplySuggestionsOutput(null)).isEmpty())
    }
}
