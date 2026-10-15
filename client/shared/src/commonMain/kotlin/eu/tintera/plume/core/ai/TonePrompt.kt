package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/**
 * Jak může rozepsaná zpráva vyznít u příjemce, když ne v pořádku.
 *
 * **Pasivní agrese tu vědomě není.** Gemma 4 E2B jí označila i věcné „Pošli mi to do pátku,
 * jinak to nestihnu." — a to i s výčtem, co je v pořádku (ověřeno na emulátoru). Je to
 * nejjemnější z tónů a malý model ji nerozezná; zbytečné varování je horší než žádné.
 */
enum class ToneIssue {
    /** Podrážděně, naštvaně. */
    Irritated,

    /** Příkře, neuctivě. */
    Rude,
}

/** Verdikt kontroly tónu; `null` u [parseTone] znamená „nevím", tedy mlčet. */
sealed interface ToneVerdict {
    data object Fine : ToneVerdict

    data class Issue(val issue: ToneIssue) : ToneVerdict
}

/**
 * Prompt pro kontrolu tónu VLASTNÍ rozepsané zprávy („nezní to podrážděně?"). Klasifikace,
 * nic se nepřepisuje; přepis je vedle na vyžádání (`RewriteStyle.Polite`).
 *
 * - **Odpověď je jedno slovo** (`FINE`, `IRRITATED`, `RUDE`, `UNSURE`),
 *   jako u podvodu ([scamPrompt]).
 * - **Radši mlčet než varovat zbytečně** (roadmapa): přímost a stručnost nejsou hrubost
 *   a `UNSURE` je výslovná možnost. Zbytečné varování u věcné zprávy otravuje víc, než pomůže.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt]; zpráva může být v jakémkoli
 *   jazyce.
 * - [structured] = odpověď přijde jako [ToneAnswer] (`generateAnswer`); prompt pak o formátu
 *   mlčí a povolené hodnoty dá modelu schéma.
 */
fun tonePrompt(text: String, structured: Boolean = false): String = buildString {
    appendLine("The user is about to send the chat message below. Judge how it may come across to the person who reads it.")
    appendLine()
    appendLine("Message:")
    appendLine("\"\"\"")
    appendLine(text.take(MAX_REWRITE_CHARS))
    appendLine("\"\"\"")
    appendLine()
    appendLine(if (structured) "Choose the verdict:" else "Answer with exactly one word:")
    appendLine("FINE - it reads as friendly or neutral")
    appendLine("IRRITATED - it sounds annoyed or angry")
    appendLine("RUDE - it sounds disrespectful, insulting or bossy")
    appendLine("UNSURE - anything you are not sure about")
    // Bez výčtu toho, co JE v pořádku, Gemma 4 E2B označila „Pošli mi to do pátku, jinak to
    // nestihnu." jako pasivně agresivní (ověřeno na emulátoru).
    appendLine("These are FINE: asking for something with a deadline, explaining why, saying no, being short or direct.")
    appendLine("Only answer IRRITATED or RUDE when the wording is clearly hostile or insulting.")
    if (!structured) append("Answer:")
}.trimEnd()

/**
 * Verdikt z výstupu modelu, nebo `null` — mlčet. Tolerantní k obalu (tečka, malá písmena),
 * ale jen JEDNOZNAČNÁ odpověď: víc štítků naráz nebo štítek s `UNSURE` je pochybnost.
 */
fun parseTone(output: String): ToneVerdict? {
    val words = TONE_WORD.findAll(output.uppercase()).map { it.value }.toSet()
    if ("UNSURE" in words) return null
    val found = buildList {
        if ("FINE" in words) add(ToneVerdict.Fine)
        if ("IRRITATED" in words) add(ToneVerdict.Issue(ToneIssue.Irritated))
        if ("RUDE" in words) add(ToneVerdict.Issue(ToneIssue.Rude))
    }
    return found.singleOrNull()
}

private val TONE_WORD = Regex("[A-Z]+")

/** Verdikt ze strukturované odpovědi — týmiž pravidly jako z textu ([parseTone]). */
fun toneVerdictOf(answer: ToneAnswer): ToneVerdict? = parseTone(answer.verdict.orEmpty())
