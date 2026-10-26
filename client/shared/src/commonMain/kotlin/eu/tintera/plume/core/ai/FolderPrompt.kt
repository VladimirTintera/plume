package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Kolik chatů model dostane — nejnovějších; víc by se do vstupu Nano nevešlo s rezervou. */
const val MAX_FOLDER_CHATS = 40

/** Náhled poslední zprávy se zkrátí — o chatu stačí naznačit, o čem v něm jde řeč. */
const val MAX_FOLDER_PREVIEW_CHARS = 80

/**
 * Do čeho model chaty třídí. **Pevná sada, ne volný název**: klasifikace je silná stránka
 * modelu, kdežto vymyšlené názvy složek by bylo psaní — a s pevnou sadou se dá hlídat délka
 * názvu (12 znaků, `CHAT_FOLDER_TITLE_MAX`) i duplicita s existující složkou.
 * [Other] = do žádné; ze složek se zahodí.
 */
enum class FolderCategory { Work, Family, Friends, School, Shopping, News, Hobbies, Other }

/** Jeden chat pro model: název, druh a o čem je poslední zpráva. */
data class FolderCandidate(val title: String, val kind: FolderChatKind, val preview: String?)

enum class FolderChatKind { Private, Group, Channel }

/**
 * Prompt pro rozřazení chatů do složek. Chaty jsou očíslované od 1 a model odpoví řádkem
 * „číslo: KATEGORIE" za každý — z věty by se přiřazení dolovat nedalo.
 *
 * - **`OTHER` je výslovná možnost** a výchozí, když si model není jistý: chat ve špatné
 *   složce je horší než chat bez složky, stejně jako u triáže a podvodu.
 * - **Druh chatu je v řádku** ([FolderChatKind]): kanál bývá zpravodajství, soukromý chat
 *   rodina nebo přátelé — bez druhu by model hádal jen z názvu.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt]; jazyk odpovědi tu nehraje
 *   roli, odpovídá se kódy.
 */
fun folderPrompt(chats: List<FolderCandidate>): String = buildString {
    appendLine("You help the user of a chat app sort their chats into folders.")
    appendLine("Below is a numbered list of chats: kind, chat name, and a short preview of the latest message.")
    appendLine()
    appendLine("Chats:")
    chats.take(MAX_FOLDER_CHATS).forEachIndexed { index, chat ->
        val kind = when (chat.kind) {
            FolderChatKind.Private -> "private chat"
            FolderChatKind.Group -> "group"
            FolderChatKind.Channel -> "channel"
        }
        val preview = chat.preview?.replace('\n', ' ')?.take(MAX_FOLDER_PREVIEW_CHARS)?.takeIf { it.isNotBlank() }
        appendLine("${index + 1}. [$kind] ${chat.title.replace('\n', ' ')}" + (preview?.let { " — $it" } ?: ""))
    }
    appendLine()
    appendLine("For every chat, choose one folder:")
    appendLine("WORK - colleagues, clients, job")
    appendLine("FAMILY - relatives")
    appendLine("FRIENDS - friends and personal chats that are not family")
    appendLine("SCHOOL - school, university, courses, classmates")
    appendLine("SHOPPING - shops, orders, deliveries, services")
    appendLine("NEWS - news, media, announcement channels")
    appendLine("HOBBIES - hobby, sport, games, interest groups")
    appendLine("OTHER - anything else, or when you are not sure")
    appendLine("When in doubt, answer OTHER.")
    appendLine("Answer with one line per chat in the form \"number: FOLDER\", for example \"1: WORK\", and nothing else.")
    append("Answer:")
}

/**
 * Přiřazení z odpovědi modelu: index chatu (od 0) → kategorie. Řádky, kterým nejde rozumět,
 * čísla mimo seznam a [FolderCategory.Other] se vynechají; chat, který model jmenuje dvakrát,
 * má první odpověď. Tolerantní k obalu („1. WORK", „1) work", odrážky).
 */
fun parseFolderCategories(output: String, count: Int): Map<Int, FolderCategory> {
    val result = mutableMapOf<Int, FolderCategory>()
    for (match in FOLDER_LINE.findAll(output)) {
        val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: continue
        if (index !in 0 until count || index in result) continue
        val category = FolderCategory.entries.firstOrNull { it.name.equals(match.groupValues[2], ignoreCase = true) } ?: continue
        if (category != FolderCategory.Other) result[index] = category
    }
    return result
}

private val FOLDER_LINE = Regex("""(\d+)\s*[:.)\-]\s*\**([A-Za-z]+)""")
