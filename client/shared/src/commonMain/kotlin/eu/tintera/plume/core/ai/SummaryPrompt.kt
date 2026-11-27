package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Jedna zpráva pro shrnutí: kdo ji napsal a co v ní je. */
data class TranscriptLine(val author: String, val text: String)

/**
 * Kolik znaků přepisu jde do modelu, když jeho limit neznáme.
 *
 * Gemini Nano bere na vstupu zhruba 4 000 tokenů. Čeština má na token méně znaků než angličtina,
 * takže 6 000 znaků je s rezervou na instrukce i odpověď. Když se přepis nevejde, zahazují se
 * NEJSTARŠÍ zprávy — shrnutí má říct, co se dělo naposledy.
 */
const val MAX_TRANSCRIPT_CHARS = 6_000

/** Horní mez přepisu i u modelu s velkým limitem — delší přepis jen prodlužuje čekání. */
const val MAX_TRANSCRIPT_CHARS_CAP = 24_000

/**
 * Odhad znaků přepisu pro limit [tokenLimit]: zhruba tři znaky na token (čeština je na tokeny
 * dražší než angličtina) a pětina limitu na instrukce. Odhad se pak ověřuje počítáním tokenů.
 */
fun transcriptCharsFor(tokenLimit: Int?): Int =
    tokenLimit?.takeIf { it > 0 }
        ?.let { (it * 4 / 5 * 3).coerceIn(1_000, MAX_TRANSCRIPT_CHARS_CAP) }
        ?: MAX_TRANSCRIPT_CHARS

/** Jedna zpráva se ořízne na tolik znaků; dlouhý příspěvek by jinak vytlačil všechny ostatní. */
const val MAX_LINE_CHARS = 600

/**
 * Prompt pro shrnutí konverzace.
 *
 * Instrukce jsou anglicky — Gemini Nano je na angličtinu laděný a pokyny v ní plní spolehlivěji.
 * Jazyk ODPOVĚDI se volí výslovně podle jazyka aplikace ([languageCode]); bez toho model odpovídá
 * jazykem instrukcí, ne konverzace.
 *
 * [lines] jsou chronologicky, nejstarší první. [me] je podpis uživatelových zpráv v přepisu
 * („Já"), viz [readerInstruction].
 */
fun summaryPrompt(
    chatTitle: String,
    lines: List<TranscriptLine>,
    languageCode: String,
    maxChars: Int = MAX_TRANSCRIPT_CHARS,
    me: String? = null,
): String {
    val transcript = fitTranscript(lines, maxChars)
    return buildString {
        appendLine("You summarize a chat conversation from the messaging app Telegram.")
        appendLine("Chat name: $chatTitle")
        appendLine()
        appendLine("Write a short summary of the conversation below:")
        appendLine("- 3 to 6 bullet points, each starting with \"- \"")
        appendLine("- $BULLET_LENGTH_INSTRUCTION")
        appendLine("- mention who said or decided what, using the names from the transcript")
        me?.let { appendLine("- ${readerInstruction(it, languageCode)}") }
        appendLine("- only facts from the transcript, do not invent anything")
        appendLine("- ${languageInstruction(languageCode)}")
        appendLine("- $NO_INTRO_INSTRUCTION")
        appendLine()
        appendLine("Transcript:")
        appendLine(transcript)
        appendLine()
        append("Summary:")
    }
}

/** Kolik zpráv z [lines] se vejde do přepisu dlouhého [maxChars]. */
fun keptLineCount(lines: List<TranscriptLine>, maxChars: Int): Int =
    fitTranscript(lines, maxChars).let { if (it.isEmpty()) 0 else it.count { c -> c == '\n' } + 1 }

/** Přepis ve tvaru „Jméno: text", oříznutý zezačátku na [MAX_TRANSCRIPT_CHARS]. */
internal fun fitTranscript(lines: List<TranscriptLine>, maxChars: Int = MAX_TRANSCRIPT_CHARS): String {
    val rendered = lines
        .filter { it.text.isNotBlank() }
        .map { it.rendered() }
    // Od nejnovější dozadu, dokud se vejde; pak zase chronologicky.
    val kept = ArrayDeque<String>()
    var length = 0
    for (line in rendered.asReversed()) {
        val added = line.length + 1
        if (length + added > maxChars && kept.isNotEmpty()) break
        kept.addFirst(line)
        length += added
    }
    return kept.joinToString("\n")
}

/** Řádek přepisu: „Jméno: text" na jednom řádku, dlouhá zpráva oříznutá na [MAX_LINE_CHARS]. */
private fun TranscriptLine.rendered(): String {
    val oneLine = text.replace('\n', ' ').trim()
    val clipped = if (oneLine.length > MAX_LINE_CHARS) oneLine.take(MAX_LINE_CHARS) + "…" else oneLine
    return "$author: $clipped"
}

/**
 * Délka odrážky. Bez ní model u delšího přepisu psal odrážku skoro ke každé zprávě a narazil na
 * strop výstupu uprostřed věty (na Foldu shrnutí dvanácti zpráv skončilo u šesté).
 */
private const val BULLET_LENGTH_INSTRUCTION = "keep each bullet point short, at most 20 words"

/**
 * Kolik částí se u dlouhé konverzace shrnuje zvlášť. Každá je jedno generování navíc (na Foldu
 * 12–35 s, Gemma na CPU emulátoru přes 40 s), takže víc částí by čekání jen natáhlo. Co se
 * nevejde ani do nich, odpadne jako dřív — nejstarší zprávy.
 */
const val MAX_SUMMARY_PARTS = 3

/**
 * Přepis rozdělený na části, z nichž se každá vejde do [maxChars] — pro konverzaci, která se
 * do jednoho promptu nevejde. Plní se od NEJNOVĚJŠÍCH zpráv, nejvýš [maxParts] částí; zbytek
 * (nejstarší) odpadne. Výsledek je chronologicky: první část je nejstarší a uvnitř části jde
 * nejstarší zpráva první. Prázdné zprávy se vynechají, každá část má aspoň jednu zprávu.
 */
fun summaryParts(
    lines: List<TranscriptLine>,
    maxChars: Int,
    maxParts: Int = MAX_SUMMARY_PARTS,
): List<List<TranscriptLine>> {
    val parts = ArrayDeque<List<TranscriptLine>>()
    var current = ArrayDeque<TranscriptLine>()
    var length = 0
    for (line in lines.filter { it.text.isNotBlank() }.asReversed()) {
        val added = line.rendered().length + 1
        if (length + added > maxChars && current.isNotEmpty()) {
            parts.addFirst(current.toList())
            if (parts.size == maxParts) return parts.toList()
            current = ArrayDeque()
            length = 0
        }
        current.addFirst(line)
        length += added
    }
    if (current.isNotEmpty()) parts.addFirst(current.toList())
    return parts.toList()
}

/**
 * Prompt pro jednu část dlouhé konverzace: krátké poznámky, ze kterých [combinedSummaryPrompt]
 * složí shrnutí celku. Poznámky jsou už v jazyce odpovědi — jména a fakta se tak nepřekládají
 * dvakrát.
 */
fun partSummaryPrompt(
    chatTitle: String,
    part: List<TranscriptLine>,
    partNumber: Int,
    partCount: Int,
    languageCode: String,
    me: String? = null,
): String = buildString {
    appendLine("You take notes on a long chat conversation from the messaging app Telegram.")
    appendLine("Chat name: $chatTitle")
    appendLine("This is part $partNumber of $partCount of the conversation, oldest part first.")
    appendLine()
    appendLine("Write brief notes on this part:")
    appendLine("- at most 4 bullet points, each starting with \"- \"")
    appendLine("- $BULLET_LENGTH_INSTRUCTION")
    appendLine("- mention who said or decided what, using the names from the transcript")
    me?.let { appendLine("- ${readerInstruction(it, languageCode)}") }
    appendLine("- only facts from the transcript, do not invent anything")
    appendLine("- ${languageInstruction(languageCode)}")
    appendLine("- $NO_INTRO_INSTRUCTION")
    appendLine()
    appendLine("Transcript:")
    appendLine(fitTranscript(part, maxChars = Int.MAX_VALUE))
    appendLine()
    append("Notes:")
}

/**
 * Prompt, který z poznámek k částem ([notes], nejstarší první) složí jedno shrnutí. Pokyny jsou
 * tytéž jako u [summaryPrompt], jen místo přepisu dostane model poznámky — a novější části
 * mají přednost, jako když se přepis ořezává zezačátku.
 */
fun combinedSummaryPrompt(chatTitle: String, notes: List<String>, languageCode: String, me: String? = null): String = buildString {
    appendLine("You summarize a long chat conversation from the messaging app Telegram.")
    appendLine("Chat name: $chatTitle")
    appendLine("Below are notes on consecutive parts of the conversation, oldest part first.")
    appendLine()
    appendLine("Write a short summary of the whole conversation:")
    appendLine("- 3 to 6 bullet points, each starting with \"- \"")
    appendLine("- $BULLET_LENGTH_INSTRUCTION")
    appendLine("- recent events matter more than old ones")
    appendLine("- mention who said or decided what, using the names from the notes")
    me?.let { appendLine("- ${readerInstruction(it, languageCode)}") }
    appendLine("- only facts from the notes, do not invent anything")
    appendLine("- ${languageInstruction(languageCode)}")
    appendLine("- $NO_INTRO_INSTRUCTION")
    appendLine()
    notes.forEachIndexed { index, note ->
        appendLine("Notes on part ${index + 1}:")
        appendLine(note.trim())
        appendLine()
    }
    append("Summary:")
}

/**
 * Text useknutý stropem výstupu, zkrácený na poslední CELÝ řádek (odrážku). Useknutá věta
 * uprostřed vypadala jako chyba modelu; celé odrážky se čtou jako kratší shrnutí, a že není
 * úplné, říká sheet zvlášť. Bez jediného celého řádku zůstane text, jak je — něco je lepší
 * než nic.
 */
fun completeLines(text: String): String {
    val end = text.trimEnd().lastIndexOf('\n')
    return if (end <= 0) text else text.substring(0, end).trimEnd()
}

/**
 * Shrnutí bez úvodní věty. Bez pokynu psaly Gemini Nano i Gemma 4 E4B (na Foldu) před odrážky
 * „Zde je shrnutí konverzace:" — vysvětlení a shrnutí hlasovky ten pokyn měly odjakživa.
 */
private const val NO_INTRO_INSTRUCTION = "start directly with the first bullet point, no introduction"

/**
 * Kdo je [me]: uživatel, který shrnutí čte. Bez toho modely podpis opisovaly jako jméno třetí
 * osoby v uvozovkách („Martin Glasnák a "Já" se…", obě na Foldu). Ve druhé osobě se shrnutí
 * čte přirozeně („Martin se ptal, jestli máš dopravu zdarma"); česky tykáním, jako celá
 * aplikace — bez výslovného „ty" by model mohl vykat.
 */
internal fun readerInstruction(me: String, languageCode: String): String {
    val person = if (languageCode == "cs") "the second person singular, informal (ty)" else "the second person (you)"
    return "\"$me\" in the transcript is the user who reads this summary: write about them in $person, never write \"$me\" as a name"
}

/**
 * Úvodní řádek, který model napsal i přes [NO_INTRO_INSTRUCTION] („Zde je shrnutí konverzace:"),
 * se zahodí: první neprázdný řádek, který není odrážka a končí dvojtečkou, když po něm odrážky
 * jsou. Jinak se text nemění — shrnutí bez odrážek je pořád lepší než prázdno.
 */
fun withoutIntro(text: String): String {
    val lines = text.lines()
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first < 0) return text
    val line = lines[first].trim()
    if (line.startsWith("-") || line.startsWith("*") || line.startsWith("•") || !line.endsWith(":")) return text
    val rest = lines.drop(first + 1).dropWhile { it.isBlank() }
    if (rest.firstOrNull()?.trimStart()?.startsWith("-") != true) return text
    return rest.joinToString("\n")
}

internal fun languageInstruction(languageCode: String): String = when (languageCode) {
    "cs" -> "write the summary in Czech"
    "en" -> "write the summary in English"
    else -> "write the summary in the language of the conversation"
}

/**
 * Menší délka přepisu, když se prompt o [tokens] tokenech nevešel do [tokenLimit].
 *
 * Zmenšuje úměrně přesahu a ještě o desetinu navíc: tokeny na znak nejsou v přepisu rovnoměrné
 * (emoji, odkazy), takže přesně úměrné zmenšení by se často minulo o kousek.
 */
fun shrunkTranscriptChars(currentChars: Int, tokens: Int, tokenLimit: Int): Int =
    (currentChars.toLong() * tokenLimit / tokens * 9 / 10).toInt()
        .coerceAtMost(currentChars - 1)
        .coerceAtLeast(1)
