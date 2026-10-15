package eu.tintera.plume.core.telegram

/**
 * Rozepsaný hashtag u kurzoru: `ahoj #pl|` → mřížka na pozici [start], dotaz „pl".
 *
 * [start] je index `#`; napovězený hashtag nahradí text od něj po kurzor.
 */
data class HashtagQuery(val start: Int, val query: String)

/**
 * [HashtagQuery] u kurzoru, nebo `null`, když se u něj hashtag nepíše.
 *
 * Stejná pravidla jako u zmínky ([mentionQueryAt]): `#` na začátku textu nebo po mezeře
 * („C#" hashtag není), mezi ním a kurzorem jen písmena, číslice a podtržítko. Samotná `#`
 * je dotaz taky — nabídnou se naposledy použité hashtagy.
 */
fun hashtagQueryAt(text: String, cursor: Int): HashtagQuery? {
    if (cursor < 1 || cursor > text.length) return null
    var hash = cursor - 1
    while (hash >= 0 && text[hash].isHashtagChar()) hash--
    if (hash < 0 || text[hash] != '#') return null
    if (hash > 0 && !text[hash - 1].isWhitespace()) return null
    val query = text.substring(hash + 1, cursor)
    if (query.length > MAX_HASHTAG_QUERY) return null
    return HashtagQuery(hash, query)
}

private fun Char.isHashtagChar(): Boolean = isLetterOrDigit() || this == '_'

/** Delší „hashtag" se už nenapovídá; tak dlouhý nikdo nepoužívá opakovaně. */
private const val MAX_HASHTAG_QUERY = 64

/**
 * Text pole po výběru [hashtag] (bez `#`) z napovídání a kam patří kurzor.
 *
 * Nahradí se úsek od `#` po kurzor ([query]); za hashtag přijde mezera, ať se dá psát dál.
 * Mezera, která za kurzorem už je, se nezdvojuje — stejně jako u zmínky ([mentionInsertion]).
 */
fun hashtagInsertion(text: String, cursor: Int, query: HashtagQuery, hashtag: String): Pair<String, Int> {
    val inserted = hashtagText(hashtag)
    val rest = text.substring(cursor)
    val separator = if (rest.startsWith(" ")) "" else " "
    val result = text.substring(0, query.start) + inserted + separator + rest
    return result to query.start + inserted.length + 1
}

/** `#hashtag` — jak se hashtag vkládá do pole i ukazuje v napovídání. */
fun hashtagText(hashtag: String): String = "#$hashtag"
