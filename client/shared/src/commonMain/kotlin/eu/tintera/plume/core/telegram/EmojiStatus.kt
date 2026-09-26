package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.EmojiStatus
import dev.g000sha256.tdl.dto.EmojiStatusTypeCustomEmoji
import dev.g000sha256.tdl.dto.EmojiStatusTypeUpgradedGift
import kotlin.time.Clock

/**
 * Emoji status u jména: vlastní emoji, které si uživatel s Premium (nebo kanál) nastavil za
 * název. Kreslí se tam, kde je jméno chatu — v seznamu, v hlavičce konverzace a v profilu.
 *
 * Nese ho přímo `Chat.emojiStatus` (u soukromého chatu status protějšku) a mění ho
 * `UpdateChatEmojiStatus`, takže se na uživatele ani supergroupu dotazovat nemusí — na rozdíl
 * od ověření ([Verification]).
 *
 * Vylepšený dárek (`EmojiStatusTypeUpgradedGift`) se kreslí svým modelem; pozadí a symbol dárku
 * oficiální klienti do řádku se jménem stejně nedávají.
 */
internal fun EmojiStatus?.statusEmojiId(now: Long = Clock.System.now().epochSeconds): Long? {
    this ?: return null
    if (!emojiStatusActive(expirationDate, now)) return null
    // Bez `else`: nový druh statusu v novější TDLib má rozbít překlad, ne tiše zmizet.
    return when (val type = this.type) {
        is EmojiStatusTypeCustomEmoji -> type.customEmojiId
        is EmojiStatusTypeUpgradedGift -> type.modelCustomEmojiId
    }
}

/**
 * Jestli status ještě platí. Nula je „bez konce".
 *
 * Status s koncem TDLib po vypršení sám zruší updatem, jenže mezi tím by se ukázal starý —
 * a chat přečtený z databáze po delší pauze ho nese i dávno prošlý.
 */
internal fun emojiStatusActive(expirationDate: Int, now: Long): Boolean =
    expirationDate == 0 || expirationDate > now
