package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageGift
import dev.g000sha256.tdl.dto.MessageGiftedGrams
import dev.g000sha256.tdl.dto.MessageGiftedPremium
import dev.g000sha256.tdl.dto.MessageGiftedStars
import dev.g000sha256.tdl.dto.MessageGiveawayPrizeStars
import dev.g000sha256.tdl.dto.MessagePremiumGiftCode
import dev.g000sha256.tdl.dto.MessageRefundedUpgradedGift
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageUpgradedGift

/**
 * Co se darovalo. Telegram má na dárky sedm druhů zpráv a každý nese něco jiného; karta je
 * jedna a liší se titulkem a pár řádky pod ním.
 */
sealed interface GiftKind {
    /**
     * Běžný dárek za hvězdy. [sellStarCount] dostane příjemce, když dárek promění na hvězdy;
     * [wasConverted], [wasUpgraded] a [wasRefunded] říkají, co se s dárkem mezitím stalo.
     */
    data class Regular(
        val starCount: Long,
        val sellStarCount: Long,
        val wasConverted: Boolean,
        val wasUpgraded: Boolean,
        val wasRefunded: Boolean,
    ) : GiftKind

    /**
     * Vylepšený (sběratelský) dárek: jméno s pořadovým číslem („Plush Pepe #42") a tři rysy,
     * které ho dělají jedinečným. [centerColor] a [edgeColor] jsou barvy pozadí (RGB bez alfy);
     * kreslí se pod samolepkou jako u oficiálních klientů — bez nich by se dárek od běžného
     * nelišil ničím jiným než jménem.
     */
    data class Upgraded(
        val title: String,
        val number: Int,
        val modelName: String,
        val symbolName: String,
        val backdropName: String,
        val centerColor: Int,
        val edgeColor: Int,
        val wasTransferred: Boolean,
    ) : GiftKind

    /** Vylepšení dárku se nepovedlo a Telegram hvězdy vrátil — dárek zůstal běžný. */
    data object RefundedUpgrade : GiftKind

    /** Předplatné Telegram Premium; [months] nula u předplatného na dny ([days]). */
    data class Premium(val months: Int, val days: Int) : GiftKind

    /**
     * Dárkový kód na Premium (i výhra ze soutěže). Uplatnit ho Plume neumí — jde zkopírovat
     * a uplatnit v oficiální aplikaci.
     */
    data class PremiumCode(val months: Int, val days: Int, val code: String, val isUnclaimed: Boolean) : GiftKind

    /** Hvězdy — darované, nebo vyhrané v soutěži ([fromGiveaway]). */
    data class Stars(val count: Long, val fromGiveaway: Boolean = false) : GiftKind

    /** Toncoiny v nejmenších jednotkách (nanoTON, 10⁹ na TON). */
    data class Ton(val nanoAmount: Long) : GiftKind
}

/**
 * Částka TON jako celá část a desetiny bez koncových nul (nejvýš čtyři místa); oddělovač patří
 * jazyku, takže ho přidá až UI (`decimal_number`). Prázdné desetiny = celé číslo.
 */
fun tonParts(nanoAmount: Long): Pair<Long, String> {
    val whole = nanoAmount / NANO_PER_TON
    val fraction = (nanoAmount % NANO_PER_TON).toString().padStart(9, '0').take(4).trimEnd('0')
    return whole to fraction
}

private const val NANO_PER_TON = 1_000_000_000L

/** Id ve tvaru odesílatele zprávy: kladné uživatel, záporné chat. */
private fun MessageSender.key(): Long = when (this) {
    is MessageSenderUser -> userId
    is MessageSenderChat -> chatId
}

private suspend fun nameOf(id: Long, senderName: suspend (Long) -> String?): String? =
    id.takeIf { it != 0L }?.let { senderName(it) }?.takeIf { it.isNotBlank() }

internal suspend fun MessageGift.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.Regular(
        starCount = gift.starCount,
        sellStarCount = sellStarCount,
        wasConverted = wasConverted,
        wasUpgraded = wasUpgraded,
        wasRefunded = wasRefunded,
    ),
    sticker = gift.sticker?.toMedia(),
    text = text.text,
    senderName = senderId?.let { nameOf(it.key(), senderName) },
    receiverName = receiverId?.let { nameOf(it.key(), senderName) },
)

internal suspend fun MessageUpgradedGift.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.Upgraded(
        title = gift.title,
        number = gift.number,
        modelName = gift.model.name,
        symbolName = gift.symbol.name,
        backdropName = gift.backdrop.name,
        centerColor = gift.backdrop.colors.centerColor,
        edgeColor = gift.backdrop.colors.edgeColor,
        wasTransferred = wasTransferred,
    ),
    // Model je to, jak dárek vypadá; symbol je jen vzor na pozadí.
    sticker = gift.model.sticker.toMedia(),
    text = text.text,
    senderName = senderId?.let { nameOf(it.key(), senderName) },
    receiverName = receiverId?.let { nameOf(it.key(), senderName) },
)

internal suspend fun MessageRefundedUpgradedGift.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.RefundedUpgrade,
    sticker = gift.sticker?.toMedia(),
    text = "",
    senderName = senderId?.let { nameOf(it.key(), senderName) },
    receiverName = receiverId?.let { nameOf(it.key(), senderName) },
)

internal suspend fun MessageGiftedPremium.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.Premium(months = monthCount, days = dayCount),
    sticker = sticker?.toMedia(),
    text = text.text,
    senderName = nameOf(gifterUserId, senderName),
    receiverName = nameOf(receiverUserId, senderName),
)

internal suspend fun MessagePremiumGiftCode.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.PremiumCode(months = monthCount, days = dayCount, code = code, isUnclaimed = isUnclaimed),
    sticker = sticker?.toMedia(),
    text = text.text,
    senderName = creatorId?.let { nameOf(it.key(), senderName) },
    receiverName = null,
)

internal suspend fun MessageGiftedStars.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.Stars(count = starCount),
    sticker = sticker?.toMedia(),
    text = "",
    senderName = nameOf(gifterUserId, senderName),
    receiverName = nameOf(receiverUserId, senderName),
)

/** Hvězdy vyhrané v soutěži; „od" je pořádající kanál. */
internal suspend fun MessageGiveawayPrizeStars.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.Stars(count = starCount, fromGiveaway = true),
    sticker = sticker?.toMedia(),
    text = "",
    senderName = nameOf(boostedChatId, senderName),
    receiverName = null,
)

internal suspend fun MessageGiftedGrams.toMedia(senderName: suspend (Long) -> String?) = MessageMedia.Gift(
    kind = GiftKind.Ton(nanoAmount = gramAmount),
    sticker = sticker?.toMedia(),
    text = "",
    senderName = nameOf(gifterUserId, senderName),
    receiverName = nameOf(receiverUserId, senderName),
)
