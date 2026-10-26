package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryPromptTest {

    @Test
    fun prepisJeChronologickyVeTvaruJmenoText() {
        val transcript = fitTranscript(
            listOf(
                TranscriptLine("Jana", "Ahoj"),
                TranscriptLine("Já", "Čau\njak je?"),
            ),
        )
        assertEquals("Jana: Ahoj\nJá: Čau jak je?", transcript)
    }

    @Test
    fun prazdneZpravySeVynechaji() {
        val transcript = fitTranscript(listOf(TranscriptLine("Jana", " "), TranscriptLine("Petr", "Ok")))
        assertEquals("Petr: Ok", transcript)
    }

    @Test
    fun presahZahodiNejstarsiZpravy() {
        val lines = (1..10).map { TranscriptLine("A", "zprava$it") }
        val transcript = fitTranscript(lines, maxChars = 40)
        assertTrue(transcript.endsWith("A: zprava10"), transcript)
        assertTrue("zprava1\n" !in transcript + "\n", transcript)
        assertTrue(transcript.length <= 40, transcript)
    }

    @Test
    fun nejnovejsiZpravaZustaneIKdyzJeDelsiNezLimit() {
        val transcript = fitTranscript(listOf(TranscriptLine("A", "x".repeat(100))), maxChars = 10)
        assertTrue(transcript.startsWith("A: "), transcript)
    }

    @Test
    fun dlouhaZpravaSeOrizne() {
        val transcript = fitTranscript(listOf(TranscriptLine("A", "x".repeat(MAX_LINE_CHARS + 50))))
        assertEquals("A: " + "x".repeat(MAX_LINE_CHARS) + "…", transcript)
    }

    @Test
    fun jazykOdpovediPodleAplikace() {
        val cs = summaryPrompt("Chat", listOf(TranscriptLine("A", "b")), "cs")
        val de = summaryPrompt("Chat", listOf(TranscriptLine("A", "b")), "de")
        assertTrue("in Czech" in cs)
        assertTrue("language of the conversation" in de)
    }

    @Test
    fun delkaPrepisuPodleLimitu() {
        assertEquals(MAX_TRANSCRIPT_CHARS, transcriptCharsFor(null))
        assertEquals(MAX_TRANSCRIPT_CHARS, transcriptCharsFor(0))
        assertEquals(9_600, transcriptCharsFor(4_000))
        assertEquals(MAX_TRANSCRIPT_CHARS_CAP, transcriptCharsFor(100_000))
    }

    @Test
    fun presahZmensiPrepisUmerne() {
        // 12 000 tokenů do limitu 4 000: třetina, a ještě o desetinu míň.
        assertEquals(2_700, shrunkTranscriptChars(9_000, tokens = 12_000, tokenLimit = 4_000))
    }

    @Test
    fun zmenseniVzdyAsponOZnak() {
        // Těsný přesah by úměrně vyšel na stejnou délku a smyčka by se točila naprázdno.
        assertTrue(shrunkTranscriptChars(1_000, tokens = 4_001, tokenLimit = 4_000) < 1_000)
    }

    @Test
    fun pocetZpravVePrepisu() {
        val lines = (1..10).map { TranscriptLine("A", "zprava$it") }
        assertEquals(10, keptLineCount(lines, maxChars = 10_000))
        assertEquals(3, keptLineCount(lines, maxChars = 40))
        assertEquals(0, keptLineCount(emptyList(), maxChars = 40))
    }

    @Test
    fun castiJdouOdNejnovejsichAJsouChronologicky() {
        // „A: zpravaN" má 10–11 znaků + konec řádku; do 25 znaků se vejdou dvě.
        val lines = (1..6).map { TranscriptLine("A", "zprava$it") }
        val parts = summaryParts(lines, maxChars = 25, maxParts = 3)
        assertEquals(listOf(listOf("zprava1", "zprava2"), listOf("zprava3", "zprava4"), listOf("zprava5", "zprava6")), parts.map { p -> p.map { it.text } })
    }

    @Test
    fun nadPocetCastiOdpadnouNejstarsi() {
        val lines = (1..10).map { TranscriptLine("A", "zprava$it") }
        val parts = summaryParts(lines, maxChars = 25, maxParts = 2)
        assertEquals(listOf("zprava7", "zprava8", "zprava9", "zprava10"), parts.flatten().map { it.text })
        assertEquals(2, parts.size)
    }

    @Test
    fun kratkyPrepisJeJednaCast() {
        val lines = listOf(TranscriptLine("A", "ahoj"), TranscriptLine("B", " "), TranscriptLine("B", "čau"))
        assertEquals(listOf(listOf("ahoj", "čau")), summaryParts(lines, maxChars = 1_000).map { p -> p.map { it.text } })
    }

    @Test
    fun dlouhaZpravaMaVlastniCast() {
        val lines = listOf(TranscriptLine("A", "x".repeat(50)), TranscriptLine("A", "y"))
        assertEquals(2, summaryParts(lines, maxChars = 20).size)
    }

    @Test
    fun slozenyPromptNesePoznamkyPoCastech() {
        val prompt = combinedSummaryPrompt("Chat", listOf("- Jana přijde", "- Petr ne"), "cs")
        assertTrue("Notes on part 1:\n- Jana přijde" in prompt, prompt)
        assertTrue("Notes on part 2:\n- Petr ne" in prompt, prompt)
        assertTrue("in Czech" in prompt)
        assertTrue(prompt.endsWith("Summary:"))
    }

    @Test
    fun promptCastiRikaKteraToJe() {
        val prompt = partSummaryPrompt("Chat", listOf(TranscriptLine("Jana", "Ahoj")), 2, 3, "en")
        assertTrue("part 2 of 3" in prompt, prompt)
        assertTrue("Jana: Ahoj" in prompt, prompt)
    }

    @Test
    fun useknutyTextKonciPosledniCelouOdrazkou() {
        assertEquals("- Jana přijde\n- Petr ne", completeLines("- Jana přijde\n- Petr ne\n- Karel ř"))
        // Bez celého řádku zůstane, co je.
        assertEquals("- Jana při", completeLines("- Jana při"))
    }

    /**
     * Uživatel podepsaný „Já" je čtenář shrnutí: píše se o něm ve druhé osobě, česky tykáním.
     * Všechny tři prompty shrnutí (celek, část, složení) to říkají stejně, a bez úvodu.
     */
    @Test
    fun ctenarVeDruheOsobeABezUvodu() {
        val lines = listOf(TranscriptLine("Martin", "Máš dopravu zdarma?"), TranscriptLine("Já", "Asi jo"))
        val prompts = listOf(
            summaryPrompt("Martin", lines, "cs", me = "Já"),
            partSummaryPrompt("Martin", lines, 1, 2, "cs", me = "Já"),
            combinedSummaryPrompt("Martin", listOf("- Martin se ptal"), "cs", me = "Já"),
        )
        for (prompt in prompts) {
            assertTrue("\"Já\" in the transcript is the user who reads this summary" in prompt, prompt)
            assertTrue("informal (ty)" in prompt, prompt)
            assertTrue("never write \"Já\" as a name" in prompt, prompt)
            assertTrue("start directly with the first bullet point, no introduction" in prompt, prompt)
        }
        assertTrue("the second person (you)" in summaryPrompt("Martin", lines, "en", me = "Me"))
        // Bez podpisu (starší volající) se o čtenáři nic neříká, úvod se zakazuje i tak.
        val anonymous = summaryPrompt("Martin", lines, "cs")
        assertFalse("reads this summary" in anonymous)
        assertTrue("no introduction" in anonymous)
    }

    @Test
    fun uvodniRadekSeZahodi() {
        assertEquals("- Martin se ptal\n- Ty jsi odpověděl", withoutIntro("Zde je shrnutí konverzace:\n\n- Martin se ptal\n- Ty jsi odpověděl"))
        assertEquals("- A", withoutIntro("  \nHere is the summary:\n- A"))
    }

    /** Text bez úvodu, úvod bez odrážek a řádek bez dvojtečky zůstanou, jak jsou. */
    @Test
    fun bezUvoduSeNicNemeni() {
        assertEquals("- A\n- B", withoutIntro("- A\n- B"))
        assertEquals("Zde je shrnutí:", withoutIntro("Zde je shrnutí:"))
        assertEquals("Martin se ptal na dopravu.\n- A", withoutIntro("Martin se ptal na dopravu.\n- A"))
        assertEquals("Zde je shrnutí:\nMartin se ptal.", withoutIntro("Zde je shrnutí:\nMartin se ptal."))
    }
}
