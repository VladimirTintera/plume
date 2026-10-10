package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Delší zpráva se modelu nedává; podvod se pozná ze začátku a prompt by se natahoval. */
const val MAX_SCAM_MESSAGE_CHARS = 1_000

/**
 * Druh podvodu, před kterým se varuje. Uživatel dostane NAŠI větu podle druhu, ne text od
 * modelu: věta od modelu by mohla být v jiném jazyce, nesmyslná, nebo by zprávu „vysvětlovala"
 * tónem, za který aplikace nemůže ručit.
 */
enum class ScamKind {
    /** Chce přihlášení, ověření účtu, kód nebo heslo. */
    Phishing,

    /** Výhra, dárek, soutěž, které se musí „vyzvednout". */
    Prize,

    /** Peníze: investice, kryptoměny, doplatek za balík, naléhavá platba. */
    Money,
}

/**
 * Prompt pro klasifikaci jedné příchozí zprávy.
 *
 * - **Odpověď je jedno slovo** (`SAFE`, `PHISHING`, `PRIZE`, `MONEY`, `UNSURE`). Z věty by se
 *   verdikt dolovat musel a model by ho rád obalil vysvětlením.
 * - **`UNSURE` je výslovná možnost**, ne zbytková: varování u legitimní zprávy je horší než
 *   žádné (roadmapa), takže model dostane cestu, jak se vyjádřit, aniž by varoval.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt]: malé modely je drží líp,
 *   a zpráva sama může být v jakémkoli jazyce.
 */
fun scamPrompt(text: String): String = buildString {
    appendLine("You protect a user of a chat app from scams. Classify the incoming message below.")
    appendLine()
    appendLine("Message:")
    appendLine("\"\"\"")
    appendLine(text.take(MAX_SCAM_MESSAGE_CHARS))
    appendLine("\"\"\"")
    appendLine()
    appendLine("Answer with exactly one word:")
    appendLine("PHISHING - it asks the user to log in, verify or unlock an account, scan a QR code, or send a code or password")
    appendLine("PRIZE - it claims the user won something or gets a free gift they must claim, pay for, or log in for")
    appendLine("MONEY - it pushes an investment, crypto, a fee for a parcel or delivery, or an urgent payment to a stranger")
    appendLine("SAFE - an ordinary message, including real notices, friends talking about money, codes or links")
    appendLine("UNSURE - anything you are not sure about")
    appendLine("Only answer PHISHING, PRIZE or MONEY when the message is clearly a scam. When in doubt, answer UNSURE.")
    append("Answer:")
}

/**
 * Verdikt z výstupu modelu, nebo `null` — tedy mlčet. Tolerantní k obalu (model přidá tečku,
 * uvozovky, malá písmena), ale **varuje jen při jednoznačné odpovědi**: když výstup jmenuje víc
 * druhů nebo zároveň `SAFE` / `UNSURE`, model si jistý není a varování by bylo hádání.
 */
fun parseScamVerdict(output: String): ScamKind? {
    val words = WORD.findAll(output.uppercase()).map { it.value }.toSet()
    if ("SAFE" in words || "UNSURE" in words) return null
    val kinds = ScamKind.entries.filter { it.name.uppercase() in words }
    return kinds.singleOrNull()
}

private val WORD = Regex("[A-Z]+")
