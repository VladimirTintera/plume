package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

import eu.tintera.plume.core.ui.foldDiacritics
import kotlinx.datetime.LocalDateTime

/** Delší zpráva se modelu nedává; schůzka se pozná z okolí termínu a prompt by se natahoval. */
const val MAX_EVENT_MESSAGE_CHARS = 1_000

/** Nejdelší název události, který se od modelu vezme; delší je spíš věta než název. */
const val MAX_EVENT_TITLE_CHARS = 60

/**
 * Posudek modelu k termínu ve zprávě. [isEvent] = jde o budoucí schůzku nebo událost; [title]
 * a [place] jsou `null`, když je model nedal (nebo místo ve zprávě není).
 */
data class EventDetails(val isEvent: Boolean, val title: String?, val place: String?)

/**
 * Prompt pro posouzení termínu, který ve zprávě našla pravidla (`findMessageContext`), a pro
 * název a místo do kalendáře.
 *
 * - **Termín počítají pravidla, model ho nepočítá.** Data model drží, ale kalendář nezná; tady
 *   jen říká, jestli ten termín je schůzka — „v 15:00 jsem byl u doktora" není.
 * - **Odpověď jsou čtyři řádky s klíčem** (`WHEN`, `EVENT`, `TITLE`, `PLACE`), ne věta. Parser
 *   [parseEventDetails] je tolerantní k obalu, ne k obsahu.
 * - **Napřed čas (`WHEN`), pak schůzka (`EVENT`).** Gemma 4 E2B na samotnou otázku „je to
 *   schůzka?" odpověděla YES i u „Volal jsem ti v 9:30, ale nebral jsi to" — i s téměř stejným
 *   příkladem v promptu. Minulost nebo budoucnost je jednodušší klasifikace a rozhodne první.
 * - **`NO` je výchozí**: zbytečná ikonka u každé druhé zprávy by byla otrava (roadmapa), takže
 *   model dostane cestu odmítnout všechno, co není jasně domluvený termín.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt]; název v jazyce zprávy, protože
 *   ho uživatel uvidí ve vlastním kalendáři vedle zpráv, ze kterých vznikl.
 * - [structured] = odpověď přijde jako [EventAnswer] (`generateAnswer`); prompt pak o formátu
 *   mlčí, tvar dá modelu schéma.
 */
fun eventPrompt(text: String, sentAt: LocalDateTime, start: LocalDateTime, structured: Boolean = false): String = buildString {
    val sentDay = sentAt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val startDay = start.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    appendLine("You help a chat app user put plans from messages into their calendar.")
    appendLine("The message was sent on $sentDay ${sentAt.date} at ${twoDigits(sentAt.hour)}:${twoDigits(sentAt.minute)}.")
    // „It mentions <datum>" jako fakt model přebíral: u „včera v 15:00 jsem byl…" pak odpověděl
    // YES, protože mu prompt tvrdil budoucí termín (Gemma 4 E2B, ověřeno). Proto jen jako výklad.
    appendLine("If it plans something, the time would be $startDay ${start.date} at ${twoDigits(start.hour)}:${twoDigits(start.minute)}.")
    appendLine()
    appendLine("Message:")
    appendLine("\"\"\"")
    appendLine(text.take(MAX_EVENT_MESSAGE_CHARS))
    appendLine("\"\"\"")
    appendLine()
    appendLine("First decide WHEN: is that time before the message was sent (PAST) or after it (FUTURE)?")
    appendLine("Then EVENT: does the message plan or announce a meeting, appointment, deadline or event at that time?")
    appendLine("Answer EVENT: NO when it only mentions a time without a plan, or when you are not sure.")
    appendLine()
    appendLine("Examples:")
    appendLine("\"Včera v 15:00 jsem byl u doktora\" -> WHEN: PAST / EVENT: NO")
    appendLine("\"I called you at 9:30 but you didn't answer\" -> WHEN: PAST / EVENT: NO")
    appendLine("\"Sraz v 18:00 u kina Aero\" -> WHEN: FUTURE / EVENT: YES / TITLE: Sraz u kina / PLACE: kino Aero")
    appendLine()
    appendLine(if (structured) "Fill in these four fields:" else "Answer in exactly four lines:")
    appendLine("WHEN: PAST or FUTURE")
    appendLine("EVENT: YES or NO")
    appendLine("TITLE: a short name for the calendar event in the language of the message, at most 5 words")
    appendLine("PLACE: the place or address exactly as written in the message, or NONE")
    if (!structured) append("WHEN:")
}.trimEnd()

/**
 * Posudek z výstupu modelu, nebo `null`, když se z něj nedá nic vyčíst (pak platí pravidla).
 *
 * - **Prompt končí `WHEN:`**, takže výstup často začíná rovnou „FUTURE" bez klíče — první řádek
 *   bez klíče se proto čte jako odpověď na `WHEN`. `PAST` znamená „není to termín" bez ohledu
 *   na `EVENT`; potvrzení chce `FUTURE` i `EVENT: YES`.
 * - **`YES` i `NO` naráz** (nebo nic z toho) = model si jistý není → `null`.
 * - **Místo jen takové, které ve zprávě opravdu stojí** ([text]): aspoň jedno jeho delší slovo
 *   musí být ve zprávě (bez diakritiky, začátek slova — „Černém Mostě" i „Černý Most").
 *   Fakta model drží, ale místo, které ve zprávě není, by si vymyslel.
 */
fun parseEventDetails(output: String, text: String): EventDetails? {
    // Model občas napodobí příklady a dá všechno na jeden řádek oddělený lomítky.
    val lines = output.lines().flatMap { it.split(" / ") }.map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    fun value(key: String): String? = lines.firstNotNullOfOrNull { line ->
        KEY_LINE.find(line)?.takeIf { it.groupValues[1].equals(key, ignoreCase = true) }?.groupValues?.get(2)?.trim()
    }

    // Prompt končí „WHEN:", takže první řádek bez klíče je odpověď na WHEN.
    val whenAnswer = value("WHEN") ?: lines.first().takeIf { KEY_LINE.find(it) == null }
    return eventDetailsFrom(whenAnswer, value("EVENT"), value("TITLE"), value("PLACE"), text)
}

/** Posudek ze strukturované odpovědi — týmiž pravidly jako z textu ([parseEventDetails]). */
fun eventDetailsOf(answer: EventAnswer, text: String): EventDetails? =
    eventDetailsFrom(answer.`when`, answer.event, answer.title, answer.place, text)

/**
 * Společné jádro obou cest: z odpovědí na WHEN, EVENT, TITLE a PLACE (jakkoli získaných)
 * udělá posudek, nebo `null`, když odpověď není jednoznačná.
 */
private fun eventDetailsFrom(
    whenAnswer: String?,
    eventAnswer: String?,
    titleAnswer: String?,
    placeAnswer: String?,
    text: String,
): EventDetails? {
    val tense = whenAnswer?.uppercase()?.let { answer -> ANSWER_WORD.findAll(answer).map { it.value }.toSet() }.orEmpty()
    // Minulost rozhoduje sama: do kalendáře nepatří, ať model o schůzce tvrdí cokoli.
    if ("PAST" in tense && "FUTURE" !in tense) return EventDetails(isEvent = false, title = null, place = null)

    val answer = eventAnswer?.uppercase() ?: return null
    val words = ANSWER_WORD.findAll(answer).map { it.value }.toSet()
    val yes = "YES" in words
    val no = "NO" in words
    if (yes == no) return null
    if (no) return EventDetails(isEvent = false, title = null, place = null)
    // Budoucnost musí zaznít výslovně; „EVENT: YES" bez ní je půlka odpovědi.
    if ("FUTURE" !in tense) return null

    val title = titleAnswer?.let(::cleanValue)?.takeIf { it.length <= MAX_EVENT_TITLE_CHARS }
    val place = placeAnswer?.let(::cleanValue)?.takeIf { placeInText(it, text) }
    return EventDetails(isEvent = true, title = title, place = place)
}

/** Hodnota bez obalu, který model rád přidá (uvozovky, tečka na konci); `NONE` = nic. */
private fun cleanValue(value: String): String? =
    value.trim().trim('"', '\'', '„', '“', '*').trimEnd('.').trim()
        .takeUnless { it.isEmpty() || it.equals("NONE", ignoreCase = true) || it.equals("N/A", ignoreCase = true) }

private fun placeInText(place: String, text: String): Boolean {
    val words = WORD.findAll(foldDiacritics(text).lowercase()).map { it.value }.toList()
    return WORD.findAll(foldDiacritics(place).lowercase()).map { it.value }
        .filter { it.length >= PLACE_WORD_MIN }
        // Začátek slova bez koncovky kvůli skloňování: „Černý Most" i „na Černém Mostě" mají
        // společné „cer" a „mos", „kino" a „u kina" „kin".
        .any { placeWord -> words.any { it.startsWith(placeWord.take(maxOf(PLACE_STEM_MIN, placeWord.length - 2))) } }
}

private const val PLACE_WORD_MIN = 4
private const val PLACE_STEM_MIN = 3

private val KEY_LINE = Regex("^\\**\\s*(WHEN|EVENT|TITLE|PLACE)\\s*\\**\\s*[:=-]\\s*(.*)$", RegexOption.IGNORE_CASE)
private val ANSWER_WORD = Regex("[A-Z]+")
private val WORD = Regex("[\\p{L}\\p{N}]+")

private fun twoDigits(value: Int) = value.toString().padStart(2, '0')
