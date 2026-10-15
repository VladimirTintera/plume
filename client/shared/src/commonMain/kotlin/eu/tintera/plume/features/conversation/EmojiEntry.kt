package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.CustomEmojiItem

/**
 * Položka mřížky výběru emoji, ve které se potkají obyčejná a vlastní emoji: nedávná (v pořadí,
 * jak je uživatel vybíral) a výsledky hledání.
 */
sealed interface EmojiEntry {
    /** Obyčejné emoji z katalogu. */
    data class Plain(val emoji: String) : EmojiEntry

    /** Vlastní emoji (samolepka se svým id a náhradním znakem). */
    data class Custom(val item: CustomEmojiItem) : EmojiEntry
}

/**
 * Klíč vlastního emoji v nedávných. Nedávná jsou v nastavení JEDEN seznam řetězců (tak se
 * obyčejná a vlastní emoji řadí podle toho, kdy je uživatel použil, ne po skupinách), a proto
 * vlastní emoji nese značku, id a náhradní znak — bez něj by se nedalo vložit do textu ani
 * nakreslit, než dorazí obrázek. Mezera v klíči není (dělí položky seznamu), dvojtečka v emoji
 * nevadí: id se bere do PRVNÍ dvojtečky za značkou.
 */
internal fun CustomEmojiItem.recentKey(): String = "$CUSTOM_EMOJI_KEY$id:$emoji"

/**
 * Uložená položka nedávných zpět na [EmojiEntry]. Obyčejné emoji jen z katalogu (uložené mohlo
 * zůstat z jiné verze aplikace), vlastní jen s platným id a náhradním znakem.
 */
internal fun parseRecentEmoji(stored: String): EmojiEntry? {
    if (!stored.startsWith(CUSTOM_EMOJI_KEY)) return catalogEmoji(stored)?.let(EmojiEntry::Plain)
    val rest = stored.removePrefix(CUSTOM_EMOJI_KEY)
    val colon = rest.indexOf(':')
    if (colon <= 0) return null
    val id = rest.substring(0, colon).toLongOrNull() ?: return null
    val emoji = rest.substring(colon + 1)
    if (emoji.isEmpty()) return null
    return EmojiEntry.Custom(CustomEmojiItem(id = id, emoji = emoji))
}

/** Značka vlastního emoji v nedávných; žádné emoji z katalogu takhle nezačíná. */
private const val CUSTOM_EMOJI_KEY = "custom:"
