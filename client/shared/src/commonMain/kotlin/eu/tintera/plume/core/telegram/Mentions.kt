package eu.tintera.plume.core.telegram

/**
 * Rozepsaná zmínka u kurzoru: `ahoj @ja|` → zavináč na pozici [start], dotaz „ja".
 *
 * [start] je index zavináče; napovězená zmínka nahradí text od něj po kurzor.
 */
data class MentionQuery(val start: Int, val query: String)

/**
 * [MentionQuery] u kurzoru, nebo `null`, když se u něj zmínka nepíše.
 *
 * Zmínka začíná zavináčem na začátku textu nebo po mezeře / zalomení — „jana@firma.cz" zmínka
 * není. Mezi zavináčem a kurzorem smí být jen písmena, číslice a podtržítko: po mezeře už
 * uživatel píše větu dál. Písmena i s diakritikou, protože se hledá i podle jména („@Jiří").
 * Samotný zavináč (prázdný dotaz) je zmínka taky — nabídnou se členové, se kterými se píše nejvíc.
 */
fun mentionQueryAt(text: String, cursor: Int): MentionQuery? {
    if (cursor < 1 || cursor > text.length) return null
    var at = cursor - 1
    while (at >= 0 && text[at].isMentionChar()) at--
    if (at < 0 || text[at] != '@') return null
    if (at > 0 && !text[at - 1].isWhitespace()) return null
    val query = text.substring(at + 1, cursor)
    if (query.length > MAX_MENTION_QUERY) return null
    return MentionQuery(at, query)
}

private fun Char.isMentionChar(): Boolean = isLetterOrDigit() || this == '_'

/** Delší „jméno" už zmínka není; uživatelské jméno má nejvýš 32 znaků. */
private const val MAX_MENTION_QUERY = 32

/**
 * Člen, kterého jde zmínit.
 *
 * S [username] se vloží `@jmeno` a odkaz z něj vyrobí server sám. Bez něj se vloží [firstName]
 * a zmínku nese entita s id uživatele ([NameMention]) — tak to dělají i oficiální klienti.
 */
data class MentionCandidate(
    val userId: Long,
    val name: String,
    val firstName: String,
    val username: String?,
    val avatarFileId: Int?,
)

/**
 * Zmínka jménem vložená z napovídání: text [name] v poli odkazuje na uživatele [userId].
 *
 * Pole je holý text, takže si entitu pamatuje ViewModel a do zprávy ji doplní až odeslání
 * ([nameMentionEntities]). Když uživatel jméno z pole smaže nebo přepíše, prostě se nenajde.
 */
data class NameMention(val name: String, val userId: Long)

/** Entita zmínky jménem v odesílaném textu. */
data class NameMentionEntity(val offset: Int, val length: Int, val userId: Long)

/**
 * Kde v odesílaném textu stojí zmínky jménem.
 *
 * Každá zmínka se hledá od konce té předchozí, v pořadí vkládání — tentýž člověk zmíněný dvakrát
 * dostane dvě entity. Jméno musí stát jako samostatné slovo (ne kus delšího) a nesmí zasahovat
 * do entity, kterou už text má ([occupied] — odkaz, `@jmeno`). Co se nenajde, se vynechá: jméno
 * mezitím z pole zmizelo.
 */
fun nameMentionEntities(
    text: String,
    mentions: List<NameMention>,
    occupied: List<IntRange> = emptyList(),
): List<NameMentionEntity> {
    val taken = occupied.toMutableList()
    val found = mutableListOf<NameMentionEntity>()
    val searchFrom = mutableMapOf<String, Int>()
    for (mention in mentions) {
        if (mention.name.isEmpty()) continue
        var from = searchFrom[mention.name] ?: 0
        while (true) {
            val index = text.indexOf(mention.name, from)
            if (index < 0) break
            val range = index until index + mention.name.length
            val wholeWord = (index == 0 || !text[index - 1].isMentionChar()) &&
                (range.last + 1 >= text.length || !text[range.last + 1].isMentionChar())
            if (wholeWord && taken.none { it.first <= range.last && range.first <= it.last }) {
                found += NameMentionEntity(index, mention.name.length, mention.userId)
                taken += range
                searchFrom[mention.name] = range.last + 1
                break
            }
            from = index + 1
        }
    }
    return found.sortedBy { it.offset }
}

/**
 * Text pole po výběru [candidate] z napovídání a kam patří kurzor.
 *
 * Nahradí se úsek od zavináče po kurzor ([query]); za zmínku přijde mezera, ať se dá psát dál.
 * Mezera, která za kurzorem už je, se nezdvojuje.
 */
fun mentionInsertion(text: String, cursor: Int, query: MentionQuery, candidate: MentionCandidate): Pair<String, Int> {
    val inserted = candidate.username?.let(::usernameMention) ?: candidate.firstName
    val rest = text.substring(cursor)
    val separator = if (rest.startsWith(" ")) "" else " "
    val result = text.substring(0, query.start) + inserted + separator + rest
    return result to query.start + inserted.length + 1
}

/** `@jmeno` — zmínka uživatelským jménem, jak se vkládá do pole i ukazuje v napovídání. */
fun usernameMention(username: String): String = "@$username"
