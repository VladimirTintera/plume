package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
