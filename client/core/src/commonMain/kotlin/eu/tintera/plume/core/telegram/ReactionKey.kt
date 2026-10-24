package eu.tintera.plume.core.telegram

/**
 * Klíč reakce, jak ho nosí aplikace: obyčejné emoji je samo sebou, vlastní emoji
 * (`ReactionTypeCustomEmoji`) má tvar [customEmojiReaction].
 *
 * **Řetězec, ne vlastní typ.** Reakce jdou jako `String` přes celou cestu — nabídka nad zprávou,
 * optimistické přepnutí ve ViewModelu, prohlížeč médií, klíče v mřížce výběru — a všude jsou
 * jen porovnávaným klíčem. Vlastní emoji potřebují vědět jen dvě místa: hranice TDLib (převod
 * na `ReactionType`) a kreslení. Kdyby byl klíč typ, měnila by se celá cesta kvůli dvěma koncům.
 *
 * Předpona se s emoji splést nemůže: emoji nikdy nezačíná písmenem ASCII.
 */
fun customEmojiReaction(customEmojiId: Long): String = "$CUSTOM_EMOJI_REACTION$customEmojiId"

/** Id vlastního emoji v klíči reakce, nebo `null` u obyčejného emoji. */
fun customEmojiIdOf(reaction: String): Long? =
    if (reaction.startsWith(CUSTOM_EMOJI_REACTION)) {
        reaction.substring(CUSTOM_EMOJI_REACTION.length).toLongOrNull()
    } else {
        null
    }

private const val CUSTOM_EMOJI_REACTION = "custom-emoji:"
