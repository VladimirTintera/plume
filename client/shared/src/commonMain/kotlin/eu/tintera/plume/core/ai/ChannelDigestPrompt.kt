package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/**
 * Prompt pro řádek ranního přehledu kanálů: co je v kanálu nového, nejvýš dvě krátké věty.
 *
 * Shrnutí konverzace ([summaryPrompt]) chce odrážky a jména mluvčích; příspěvky kanálu píše
 * jeden autor a přehled má být čitelný na první pohled v seznamu, proto vlastní prompt.
 * [posts] jsou od nejstaršího, každý oříznutý na [MAX_LINE_CHARS]; když se nevejdou do
 * [maxChars], zahazují se NEJSTARŠÍ — přehled má říct, co je nového.
 */
fun channelDigestPrompt(channelTitle: String, posts: List<String>, languageCode: String, maxChars: Int = MAX_TRANSCRIPT_CHARS): String {
    val kept = ArrayDeque<String>()
    var used = 0
    for (post in posts.asReversed()) {
        val line = post.replace('\n', ' ').trim().take(MAX_LINE_CHARS)
        if (line.isEmpty()) continue
        if (used + line.length > maxChars && kept.isNotEmpty()) break
        kept.addFirst(line)
        used += line.length + 1
    }
    return buildString {
        appendLine("You write one entry of a morning digest of Telegram channels.")
        appendLine("Channel name: $channelTitle")
        appendLine()
        appendLine("Summarize what is new in the channel posts below:")
        appendLine("- at most two short sentences, 35 words in total")
        appendLine("- the most important news first; skip greetings, ads and links")
        appendLine("- no bullet points, no title, no introduction like \"The channel posted\"")
        appendLine("- ${languageInstruction(languageCode)}")
        appendLine()
        appendLine("Posts (oldest first):")
        kept.forEach { appendLine("- $it") }
    }
}

/** Odpověď modelu do řádku: bez úvodu a odrážek, na jednom odstavci. */
fun digestLine(text: String): String =
    withoutIntro(text).lines().joinToString(" ") { it.trim().removePrefix("- ").removePrefix("• ") }.replace(Regex(" {2,}"), " ").trim()
