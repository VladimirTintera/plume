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
 * Kolik zpráv PŘED odpovídaným blokem jde modelu jako kontext.
 *
 * Málo schválně: s deseti zprávami se poslední otázka ZTRATÍ a model odpovídá na rozhovor jako
 * celek. Ověřeno na zařízení — do chatu plného zpráv o výletu přišlo „Dáme v sobotu kafe?"
 * a model navrhl „Jsem připraven." a „Dobré, pak se."
 */
const val REPLY_EARLIER_MESSAGES = 5

/**
 * Kolik posledních zpráv od téhož autora se odpovídá jako jedné (viz [replyPrompt]).
 *
 * Strop je tu kvůli tomu, kdo píše po slovech: deset zpráv za sebou by vytlačilo kontext
 * a z bloku by se stal zase celý rozhovor.
 */
const val MAX_REPLY_BLOCK_MESSAGES = 4

/** Kolik zpráv se z konverzace pro návrh vybere; [replyPrompt] z nich složí blok a kontext. */
const val REPLY_CONTEXT_MESSAGES = REPLY_EARLIER_MESSAGES + MAX_REPLY_BLOCK_MESSAGES

/** Přepis kontextu pro návrh odpovědi je kratší než u shrnutí — odpovídá se na konec hovoru. */
const val MAX_REPLY_TRANSCRIPT_CHARS = 1_500

/**
 * Kolik smí zabrat odpovídaný blok. Poslední zpráva se do něj dostane vždycky (oříznutá jako
 * každý řádek přepisu na [MAX_LINE_CHARS]), starší z bloku jen dokud se vejdou.
 */
const val MAX_REPLY_BLOCK_CHARS = 1_000

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
 * **Odpovídá se na celý blok posledních zpráv od téhož autora**, ne jen na úplně poslední.
 * Lidé píšou po kouscích („Ahoj" / „mám dotaz" / „půjdeš v sobotu?") a otázka tak často
 * není v poslední zprávě — poslední bývá „?" nebo smajlík. S jedinou zprávou zůstávají pokyny
 * tytéž jako dřív, s víc zprávami dostane model blok jako celek a pokyn odpovědět na něj najednou.
 * Autor se pozná podle jména v [TranscriptLine]; dva stejně pojmenovaní lidé ve skupině by se
 * slili do jednoho bloku, což u návrhu odpovědi nevadí.
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
    val blockSize = replyBlockSize(lines)
    val block = fitTranscript(lines.takeLast(blockSize), MAX_REPLY_BLOCK_CHARS)
    val earlier = fitTranscript(lines.dropLast(blockSize).takeLast(REPLY_EARLIER_MESSAGES), maxChars)
    val several = blockSize > 1
    val english = looksEnglish(lines.takeLast(blockSize).joinToString(" ") { it.text })
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
        // Víc zpráv za sebou je JEDNA promluva: model na ni má odpovědět celou, jinak by
        // odpověděl na „Ahoj" na začátku nebo na smajlík na konci, a otázku mezi nimi minul.
        appendLine(if (several) "Reply to THESE messages, sent one after another:" else "Reply to THIS message:")
        appendLine(block)
        appendLine()
        appendLine("Suggest $MAX_REPLY_SUGGESTIONS different replies the user could send now:")
        if (!structured) appendLine("- one per line, each starting with \"- \"")
        if (several) {
            appendLine("- each must be a COMPLETE sentence that answers the messages above as a whole,")
            appendLine("  above all any question or request in them")
        } else {
            appendLine("- each must be a COMPLETE sentence that answers the message above")
        }
        appendLine("- at most 8 words each, no explanation")
        // Bez „where that fits" model odmítal i tam, kde to nedává smysl: na „Babička je
        // v nemocnici" nabídl „Je nebezpečné, nebudu."
        appendLine("- make them genuinely different: e.g. agree, decline, ask something back,")
        appendLine("  where that fits; to sad news reply with sympathy, never decline")
        appendLine("- write them as the user, in first person")
        // „Jsem v sobotu, rád přijdeš." — model si spletl, kdo koho zve, a odpověď psal za
        // protějšek. Ukázka obrácení perspektivy to říká líp než samotné „first person".
        appendLine("- the user RECEIVED the message: answer from the user's side")
        appendLine("  (asked \"Will you come?\" → \"Yes, I'll come.\", never \"You'll come.\")")
        // Malý model píše česky s chybami a s doslovnými překlady z angličtiny; pokyn je
        // nevyřeší (viz „Sloh návrhů" v poznatcích), ale ubírá jich.
        appendLine("- use simple everyday words, the way people really text; no literal translations")
        appendLine("- ${replyLanguageInstruction(languageCode, english)}")
        appendLine("- ${formalityInstruction(languageCode)}")
        if (!english) styleInstructions(languageCode).forEach { appendLine("- $it") }
        if (!structured) {
            appendLine()
            append("Replies:")
        }
    }.trimEnd()
}

/**
 * Kolik posledních řádků tvoří blok, na který se odpovídá: souvislá řada od autora poslední
 * zprávy, nejvýš [MAX_REPLY_BLOCK_MESSAGES]. Prázdný přepis = 0.
 */
internal fun replyBlockSize(lines: List<TranscriptLine>): Int {
    val author = lines.lastOrNull()?.author ?: return 0
    return lines.asReversed().takeWhile { it.author == author }.size.coerceAtMost(MAX_REPLY_BLOCK_MESSAGES)
}

/**
 * Jazyk konverzace je hlavní, jazyk aplikace jen nápověda.
 *
 * Samotné „same language as the conversation" u krátkého hovoru nestačí — model sklouzne
 * k jinému slovanskému jazyku. Uvedení pravděpodobného jazyka ho ukotví, aniž by přebilo
 * konverzaci vedenou jinak.
 */
private fun replyLanguageInstruction(languageCode: String, english: Boolean): String = when {
    english -> "the message is in English, so write the replies in English"
    languageCode == "cs" -> "write in the same language as the conversation, most likely Czech"
    languageCode == "en" -> "write in the same language as the conversation, most likely English"
    else -> "write in the same language as the conversation"
}

/**
 * Je zpráva anglicky? Hrubý odhad: žádná česká diakritika a aspoň dvě běžná anglická slova.
 *
 * Nápověda „most likely Czech" podle jazyka aplikace přebila anglickou zprávu: na „Hi, are you
 * coming to the meeting tomorrow?" model nabídl české odpovědi. Říct to modelu obecně („piš
 * jazykem zprávy, anglická zpráva → anglické odpovědi") nešlo — samotná zmínka angličtiny
 * stáhla do angličtiny i českou zprávu o babičce v nemocnici. Jazyk proto pozná kód a model
 * dostane jen výsledek. Chybný odhad vrátí původní chování, nic horšího.
 */
internal fun looksEnglish(text: String): Boolean {
    if (text.any { it.lowercaseChar() in CZECH_LETTERS }) return false
    val words = Regex("[a-z']+").findAll(text.lowercase()).map { it.value }.toSet()
    return words.count { it in ENGLISH_WORDS } >= 2
}

private const val CZECH_LETTERS = "áčďéěíňóřšťúůýž"

private val ENGLISH_WORDS = setOf(
    "the", "you", "are", "is", "to", "and", "i", "what", "how", "do", "can", "will", "have",
    "it", "for", "of", "in", "on", "with", "your", "we", "this", "that", "be", "not", "my",
)

/**
 * Pokyny ke slohu pro konkrétní jazyk; prázdné, kde žádné nemáme.
 *
 * **Rod uživatele neznáme** (Telegram ho nenese), a čeština ho prozradí v každém „rád/ráda"
 * a „šel/šla". Návrh v cizím rodě je horší než žádný — uživatel ho musí přepsat —, proto se
 * model vede k bezrodým tvarům („Jasně, přijdu." místo „Rád přijdu.").
 *
 * Pokyn rod nevynutí — „Jsem rád, že jsem pomohl" model napíše dál —, jen ho ubere.
 *
 * **Ukázky jsou TŘI na různá témata.** S jedinou model opisoval její obsah: „Nemůžu zítra, co
 * v sobotu?" vyšlo u pozvání na pivo i u poděkování za stěhování. Se třemi bere spíš tvar
 * (souhlas / odmítnutí s protinávrhem / doplňující otázka) a obsah prosákne jen tam, kde sedí.
 *
 * **Ukázky jdou až PODMÍNĚNĚ („if you write in Czech")** a u zprávy, kterou kód pozná jako
 * anglickou ([looksEnglish]), vůbec: jazyk aplikace je jen nápověda a české ukázky by táhly do
 * češtiny i konverzaci vedenou anglicky.
 */
private fun styleInstructions(languageCode: String): List<String> = when (languageCode) {
    "cs" -> listOf(
        "if you write in Czech: the user's gender is unknown, so avoid forms that show it " +
            "(rád/ráda, byl/byla, šel/šla) — write \"Jasně, přijdu.\", not \"Rád přijdu.\"",
        "if you write in Czech, replies should sound like these (other chats, do not copy them): " +
            "\"Nepůjdeme zítra do kina?\" → \"Jasně, půjdu.\" / \"Zítra nemůžu, co v pátek?\" / \"Na jaký film?\"; " +
            "\"Pošleš mi číslo na instalatéra?\" → \"Jasně, hned ti ho pošlu.\" / \"Teď ho nemám u sebe.\" / \"Na kterého?\"; " +
            "\"Ztratila se nám kočka.\" → \"To je mi líto, už se našla?\" / \"Můžu pomoct hledat?\" / \"Držím palce, ať se najde.\"",
    )
    else -> emptyList()
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
