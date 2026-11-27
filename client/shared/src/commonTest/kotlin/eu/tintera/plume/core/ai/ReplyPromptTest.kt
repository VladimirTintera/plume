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
    fun ayaSCislemOdrazkouAPrekladem() {
        // Skutečný výstup Aya Expanse 8B: úvodní věta, „1. - " a anglický překlad v závorce.
        // Se starým čištěním z něj nezbyl ani jeden návrh (řádky přetekly délku).
        val text = """
            Here are three different reply options for the user:

            1. - Samozřejmě, pošlu ti je. (Of course, I'll send them to you.)
            2. - Bohužel, nemám je po ruce teď. (Unfortunately, I don't have them with me right now.)
            3. - Rád, ale musím se podívat, kde jsou uložené. (Sure, but I need to check where they are saved.)
        """.trimIndent()
        assertEquals(
            listOf("Samozřejmě, pošlu ti je.", "Bohužel, nemám je po ruce teď.", "Rád, ale musím se podívat, kde jsou uložené."),
            parseReplySuggestions(text),
        )
    }

    @Test
    fun tucnyNavrhSKomentaremZaNim() {
        // Aya: tučný návrh, za ním překlad a hodnocení — návrh je jen to tučné.
        val text = """
            1. **Jasně, přijdu.** (Sure, I'll come.) - Direct and positive confirmation.
            2. **- Přežil jsem, ale byl to boj.** (I survived, but it was a struggle.)
            3. "- Páni, gratuluju! Jaké to je?"
        """.trimIndent()
        assertEquals(listOf("Jasně, přijdu.", "Přežil jsem, ale byl to boj.", "Páni, gratuluju! Jaké to je?"), parseReplySuggestions(text))
    }

    @Test
    fun prekladAPoznamkaNaVlastnimRadkuNejsouNavrh() {
        // EuroLLM 9B: překlad a poznámka kurzívou na samostatných řádcích se dostaly do čipů.
        val text = """
            - **„Budu, hned jedu.“**
              (I’ll come, I’m heading right now.)
            - **- To je mi líto.**
              *Short, direct, and open-ended.*
        """.trimIndent()
        assertEquals(listOf("Budu, hned jedu.", "To je mi líto."), parseReplySuggestions(text))
    }

    @Test
    fun zavorkaKteraNeniPrekladZustane() {
        // Krátká závorka patří k odpovědi, stejně jako závorka v anglické konverzaci.
        assertEquals(listOf("Přijdu (asi)"), parseReplySuggestions("- Přijdu (asi)"))
        assertEquals(
            listOf("Sure, see you there (in the lobby)"),
            parseReplySuggestions("- Sure, see you there (in the lobby)"),
        )
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
    fun ceskyPromptVedeKBezrodymTvarumAUkazce() {
        val prompt = replyPrompt("Jana", listOf(TranscriptLine("Jana", "Dáš si kafe?")), "cs")
        // Rod uživatele neznáme — „Rád přijdu." by ženě musel přepisovat.
        assertTrue(prompt.contains("gender is unknown"), "chybí pokyn k bezrodým tvarům")
        assertTrue(prompt.contains("Jasně, půjdu."), "chybí ukázka slohu")
        // Ukázková věta v pokynu k rodu („Jasně, přijdu.") se opisovala jako první návrh
        // ke všemu — mimo blok ukázek nesmí stát žádná hotová česká odpověď.
        val genderLine = prompt.lines().single { "gender is unknown" in it }
        assertFalse(genderLine.contains('"'), "pokyn k rodu nese ukázkovou odpověď")
        assertEquals(1, Regex("Jasně").findAll(prompt).count(), "víc ukázek začíná „Jasně“")
        // Ukázka je podmíněná, aby netáhla do češtiny anglickou konverzaci.
        assertTrue(prompt.contains("if you write in Czech"))
        assertTrue(prompt.contains("received the message", ignoreCase = true), "chybí perspektiva uživatele")
    }

    @Test
    fun anglickyPromptCeskouUkazkuNema() {
        val prompt = replyPrompt("Jane", listOf(TranscriptLine("Jane", "Coffee?")), "en")
        assertFalse(prompt.contains("Czech"))
        assertFalse(prompt.contains("Jasně"))
        // Perspektiva platí pro každý jazyk.
        assertTrue(prompt.contains("received the message", ignoreCase = true))
    }

    @Test
    fun anglickaZpravaVCeskeAplikaciDostaneAnglickeOdpovedi() {
        // Nápověda „most likely Czech" přebila anglickou zprávu (ověřeno s Gemmou).
        val lines = listOf(TranscriptLine("John", "Hi, are you coming to the meeting tomorrow?"))
        val prompt = replyPrompt("John", lines, "cs")
        assertTrue(prompt.contains("the message is in English"))
        assertFalse(prompt.contains("most likely Czech"))
        // České ukázky by ji táhly zpátky do češtiny.
        assertFalse(prompt.contains("Jasně, půjdu."))
    }

    @Test
    fun jazykZpravyPoznaKod() {
        assertTrue(looksEnglish("Hi, are you coming to the meeting tomorrow?"))
        assertTrue(looksEnglish("Can you send me the photos?"))
        // Diakritika rozhoduje hned, i s anglickým slovem uvnitř.
        assertFalse(looksEnglish("Babička je v nemocnici, is it ok?"))
        // Čeština bez háčků není angličtina.
        assertFalse(looksEnglish("Pujdes zitra na pivo?"))
        // Jedno slovo je málo — „ok", „to" i „do" jsou i v češtině.
        assertFalse(looksEnglish("Ok, to je dobry"))
        assertFalse(looksEnglish(""))
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
