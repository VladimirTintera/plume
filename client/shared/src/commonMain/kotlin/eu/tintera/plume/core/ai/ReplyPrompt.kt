package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Kolik návrhů odpovědi se nabídne. Tři se vejdou nad pole i na úzký telefon. */
const val MAX_REPLY_SUGGESTIONS = 3

/** Delší návrh se nenabízí — do čipu se nevejde a stejně by se přepisoval. */
const val MAX_REPLY_CHARS = 60

/**
 * Kolik posledních zpráv jde modelu jako kontext.
 *
 * Málo schválně: s deseti zprávami se poslední otázka ZTRATÍ a model odpovídá na rozhovor jako
 * celek. Ověřeno na zařízení — do chatu plného zpráv o výletu přišlo „Dáme v sobotu kafe?"
 * a model navrhl „Jsem připraven." a „Dobré, pak se."
 */
const val REPLY_CONTEXT_MESSAGES = 6

/** Přepis kontextu pro návrh odpovědi je kratší než u shrnutí — odpovídá se na konec hovoru. */
const val MAX_REPLY_TRANSCRIPT_CHARS = 1_500

/**
 * Prompt pro návrhy odpovědi.
 *
 * Instrukce jsou anglicky ze stejného důvodu jako u [summaryPrompt] — Gemini Nano je na
 * angličtinu laděný a pokyny v ní plní spolehlivěji.
 *
 * **Jazyk se ale volí JINAK než u shrnutí.** Shrnutí čte uživatel, takže jde v jazyce aplikace;
 * návrh odpovědi se ODEŠLE PROTĚJŠKU, takže musí být v jazyce konverzace — kdo si píše anglicky,
 * nechce nabídnout českou větu. Jazyk aplikace se proto předává jen jako nápověda pro případ,
 * že z konverzace jazyk poznat nejde: model jinak sklouzne jinam (ověřeno — na nejednoznačný
 * vstup odpověděl polsky).
 *
 * [lines] jsou chronologicky, nejstarší první.
 *
 * [structured] = odpověď přijde jako [ReplySuggestionsOutput] ([OnDeviceAi.generateStructured]),
 * takže prompt o FORMÁTU mlčí: řádky s odrážkou a závěrečné „Replies:" by se pralo se schématem,
 * které ML Kit do promptu přidá sám. Obsahové pokyny (celá věta, jazyk, tykání) zůstávají —
 * schéma říká, jak odpověď vypadá, ne co v ní má být.
 */
fun replyPrompt(
    chatTitle: String,
    lines: List<TranscriptLine>,
    languageCode: String,
    maxChars: Int = MAX_REPLY_TRANSCRIPT_CHARS,
    structured: Boolean = false,
): String {
    val last = lines.lastOrNull()
    val earlier = fitTranscript(lines.dropLast(1), maxChars)
    return buildString {
        appendLine("You help the user reply in the messaging app Telegram.")
        appendLine("Chat name: $chatTitle")
        appendLine()
        if (earlier.isNotBlank()) {
            appendLine("Earlier messages, for context only:")
            appendLine(earlier)
            appendLine()
        }
        // Poslední zpráva stojí ZVLÁŠŤ a až za kontextem. V jednom slepeném přepisu se ztratí
        // a model pak odpovídá na rozhovor jako celek místo na otázku, která právě přišla.
        appendLine("Reply to THIS message:")
        appendLine("${last?.author ?: ""}: ${last?.text ?: ""}")
        appendLine()
        appendLine("Suggest $MAX_REPLY_SUGGESTIONS different replies the user could send now:")
        if (!structured) appendLine("- one per line, each starting with \"- \"")
        appendLine("- each must be a COMPLETE sentence that answers the message above")
        appendLine("- at most 8 words each, no explanation")
        appendLine("- make them genuinely different: e.g. agree, decline, ask something back")
        appendLine("- write them as the user, in first person")
        appendLine("- ${replyLanguageInstruction(languageCode)}")
        appendLine("- ${formalityInstruction(languageCode)}")
        if (!structured) {
            appendLine()
            append("Replies:")
        }
    }.trimEnd()
}

/**
 * Jazyk konverzace je hlavní, jazyk aplikace jen nápověda.
 *
 * Samotné „same language as the conversation" u krátkého hovoru nestačí — model sklouzne
 * k jinému slovanskému jazyku. Uvedení pravděpodobného jazyka ho ukotví, aniž by přebilo
 * konverzaci vedenou jinak.
 */
private fun replyLanguageInstruction(languageCode: String): String = when (languageCode) {
    "cs" -> "write in the same language as the conversation, most likely Czech"
    "en" -> "write in the same language as the conversation, most likely English"
    else -> "write in the same language as the conversation"
}

/**
 * Jak oslovovat protějšek.
 *
 * V češtině to není kosmetika: nabídnout „Co byste předpokládali?" kamarádovi, se kterým si
 * uživatel tyká, je horší než nenabídnout nic — a přesně to model dělal, než tahle instrukce
 * přibyla. Rozhoduje se podle konverzace; **když z ní poznat nejde, tyká se**, protože v Telegramu
 * si lidé tykají mnohem častěji, a tykání cizímu člověku je menší prohřešek než vykání kamarádovi.
 *
 * Instrukce jmenuje „tykání/vykání" i anglický popis: samotné „be informal" model u češtiny
 * spolehlivě nepřeloží do správného tvaru slovesa.
 */
private fun formalityInstruction(languageCode: String): String = when (languageCode) {
    "cs" -> "address the other person the same way they address the user — informal \"tykání\" " +
        "(ty/tvůj) or formal \"vykání\" (vy/váš); if it is not clear from the conversation, use tykání"
    else -> "match how formal or informal the other person is"
}

/**
 * Vytáhne návrhy z odpovědi modelu.
 *
 * Model má psát řádky s „- ", ale nedrží to spolehlivě: očísluje je, obalí uvozovkami, přidá
 * úvodní větu („Here are three replies:") nebo vrátí jedinou větu bez odrážky. Parsování je proto
 * tolerantní a **nikdy nevrátí prázdno kvůli formátu** — když se řádky rozpoznat nedají, vezme
 * se celý text jako jeden návrh.
 *
 * Čistá funkce s testy: je to jediné místo, kde se výstup modelu překládá na to, co uživatel
 * uvidí, a hraniční případy (prázdno, samé odrážky, příliš dlouhý text) se v UI neověří.
 */
fun parseReplySuggestions(text: String): List<String> {
    val lines = text.lines()
        .map { cleanSuggestion(it) }
        .filter { it.isNotBlank() }
        // Úvodní věta modelu končí dvojtečkou a není to návrh.
        .filterNot { it.endsWith(":") }
        .filter { it.length <= MAX_REPLY_CHARS }
        .distinct()

    if (lines.isNotEmpty()) return lines.take(MAX_REPLY_SUGGESTIONS)

    // Žádný použitelný řádek: zkusit celý text jako jediný návrh.
    val whole = cleanSuggestion(text.replace('\n', ' '))
    return if (whole.isNotBlank() && whole.length <= MAX_REPLY_CHARS) listOf(whole) else emptyList()
}

/**
 * Návrhy odpovědi jako typ: tvar strukturovaného výstupu ([OnDeviceAi.generateStructured]).
 *
 * **Jméno pole je součást schématu**, které vidí model, a Gson ML Kitu podle něj plní objekt
 * (reflexí). Popis tvaru je na Androidu v `ReplySuggestionsOutputProvider` a pole drží keep
 * pravidlo před přejmenováním R8; přejmenovat znamená změnit obojí.
 *
 * Nullable schválně: Gson objekt staví bez konstruktoru, takže pole, které model vynechá,
 * zůstane `null` navzdory typu — a nenullable seznam by spadl až u čtenáře.
 */
data class ReplySuggestionsOutput(val replies: List<String>? = null)

/**
 * Návrhy ze strukturovaného výstupu. Tvar drží model, obsah ne: návrh může být pořád dlouhý,
 * prázdný, zdvojený nebo v uvozovkách — platí tatáž pravidla jako u textu
 * ([parseReplySuggestions]), jen bez hledání řádků.
 */
fun cleanReplySuggestions(output: ReplySuggestionsOutput): List<String> =
    output.replies.orEmpty()
        .map { cleanSuggestion(it) }
        .filter { it.isNotBlank() && it.length <= MAX_REPLY_CHARS }
        .distinct()
        .take(MAX_REPLY_SUGGESTIONS)

/** Sundá odrážku, číslování a uvozovky, které model kolem návrhu občas nechá. */
private fun cleanSuggestion(raw: String): String {
    var s = raw.trim()
    s = s.removePrefix("- ").removePrefix("* ").removePrefix("• ")
    // Číslování „1. ", „2) " — jen na začátku a jen jedna číslice, ať se neusekne „5 minut".
    if (s.length > 2 && s[0].isDigit() && (s[1] == '.' || s[1] == ')') && s[2] == ' ') {
        s = s.substring(3)
    }
    s = s.trim()
    if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1)
    if (s.length >= 2 && s.first() == '„' && s.last() == '“') s = s.substring(1, s.length - 1)
    return s.trim()
}
