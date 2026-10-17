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
            TranscriptLine("Já", "Zítra beru děti k doktorovi"),
            TranscriptLine("Jana", "Šlo by to přesunout na neděli?"),
        )
        val prompt = replyPrompt("Jana", lines, "cs")
        val contextAt = prompt.indexOf("Zítra beru děti")
        val lastAt = prompt.indexOf("Šlo by to přesunout")
        assertTrue(prompt.contains("Reply to THIS message"), "chybí zdůraznění poslední zprávy")
        assertTrue(contextAt in 1..<lastAt, "poslední zpráva musí stát AŽ ZA kontextem")
    }

    @Test
    fun blokZpravOdTehozAutoraSeOdpovidaCely() {
        // Otázka je v předposlední zprávě, poslední je jen „?" — model ji nesmí minout.
        val lines = listOf(
            TranscriptLine("Já", "Dorazím kolem šesté"),
            TranscriptLine("Jana", "Ahoj"),
            TranscriptLine("Jana", "Půjdeš v sobotu na výlet?"),
            TranscriptLine("Jana", "?"),
        )
        val prompt = replyPrompt("Jana", lines, "cs")
        val blockAt = prompt.indexOf("Reply to THESE messages")
        assertTrue(blockAt > 0, "víc zpráv za sebou musí jít jako blok")
        assertFalse(prompt.contains("Reply to THIS message"))
        // Celý blok stojí ZA nadpisem, vlastní zpráva před ním zůstává kontextem.
        assertTrue(prompt.indexOf("Jana: Ahoj") > blockAt)
        assertTrue(prompt.indexOf("Jana: Půjdeš v sobotu") > blockAt)
        assertTrue(prompt.indexOf("Dorazím kolem šesté") in 1..<blockAt)
        assertTrue(prompt.contains("as a whole"), "chybí pokyn odpovědět na blok najednou")
    }

    @Test
    fun jedinaZpravaMaPuvodniPokyny() {
        val lines = listOf(TranscriptLine("Já", "Ahoj"), TranscriptLine("Jana", "Dáš si kafe?"))
        val prompt = replyPrompt("Jana", lines, "cs")
        assertTrue(prompt.contains("Reply to THIS message:\nJana: Dáš si kafe?"))
        assertTrue(prompt.contains("answers the message above"))
        assertFalse(prompt.contains("as a whole"))
    }

    @Test
    fun blokKonciUJinehoAutoraAMaStrop() {
        assertEquals(0, replyBlockSize(emptyList()))
        val group = listOf(TranscriptLine("Petr", "a"), TranscriptLine("Jana", "b"), TranscriptLine("Jana", "c"))
        assertEquals(2, replyBlockSize(group))
        val chatty = (1..10).map { TranscriptLine("Jana", "slovo$it") }
        assertEquals(MAX_REPLY_BLOCK_MESSAGES, replyBlockSize(chatty))
    }

    @Test
    fun kontextPredBlokemMaStalePetZprav() {
        // Blok nesmí kontext prodloužit — s deseti zprávami se otázka ztrácela.
        val earlier = (1..8).map { TranscriptLine(if (it % 2 == 0) "Já" else "Jana", "kontext$it") }
        val lines = earlier + TranscriptLine("Petr", "otázka")
        val prompt = replyPrompt("Skupina", lines, "cs")
        assertFalse(prompt.contains("kontext3"))
        assertTrue(prompt.contains("kontext4"))
        assertTrue(prompt.contains("kontext8"))
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
