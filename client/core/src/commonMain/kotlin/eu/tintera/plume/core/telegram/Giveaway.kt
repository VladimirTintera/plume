package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.GiveawayInfo
import dev.g000sha256.tdl.dto.GiveawayInfoCompleted
import dev.g000sha256.tdl.dto.GiveawayInfoOngoing
import dev.g000sha256.tdl.dto.GiveawayParticipantStatus
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusAdministrator
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusAlreadyWasMember
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusDisallowedCountry
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusEligible
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusParticipating
import dev.g000sha256.tdl.dto.GiveawayPrizePremium
import dev.g000sha256.tdl.dto.GiveawayPrizeStars
import dev.g000sha256.tdl.dto.MessageGiveaway
import dev.g000sha256.tdl.dto.MessageGiveawayWinners
import dev.g000sha256.tdl.dto.GiveawayPrize as TdGiveawayPrize

/** Co se v soutěži vyhrává: předplatné Premium na [Premium.months], nebo [Stars.count] hvězd celkem. */
sealed interface GiveawayPrize {
    data class Premium(val months: Int) : GiveawayPrize

    /** Hvězdy CELKEM, rozdělené mezi výherce. */
    data class Stars(val count: Long) : GiveawayPrize
}

/** Chat, jehož odběratelé soutěží (pořádající kanál a kanály, které se přidaly). */
data class GiveawayChat(val chatId: Long, val title: String)

/** Výherce z oznámení výsledků; [name] je prázdné, když ho TDLib nezná. */
data class GiveawayWinner(val userId: Long, val name: String)

/** Výsledky soutěže (`MessageGiveawayWinners`): to, co oznámení navíc říká o vylosování. */
data class GiveawayResults(
    /** Veřejně oznámení výherci; u soutěže bez veřejných výherců prázdné. */
    val winners: List<GiveawayWinner>,
    /** Výhry, které si nikdo nevyzvedl (u Premium kódy, které nikdo neuplatnil). */
    val unclaimedCount: Int,
    /** Soutěž zrušil Telegram a hvězdy vrátil pořadateli. */
    val wasRefunded: Boolean,
    /** Kolik kanálů se k pořadateli přidalo; jejich jména oznámení nenese. */
    val additionalChatCount: Int,
    /** Zpráva s vyhlášením soutěže, v pořádajícím chatu. */
    val giveawayMessageId: Long,
)

/**
 * Stav účasti na soutěži z pohledu tohoto účtu (`getGiveawayInfo`). Telegram to neposílá ve
 * zprávě — je to dotaz na server, a proto se ptá až sheet podrobností, ne každá bublina.
 */
sealed interface GiveawayStatus {
    /** Soutěž běží a účet se jí účastní (odebírá některý z kanálů). */
    data object Participating : GiveawayStatus

    /** Soutěž běží a účet se může zapojit — stačí odebírat některý z kanálů. */
    data object Eligible : GiveawayStatus

    /** Soutěž je jen pro nové odběratele a účet kanál odebírá od [sinceEpochSeconds]. */
    data class AlreadyMember(val sinceEpochSeconds: Long) : GiveawayStatus

    /** Účet je správce jednoho z kanálů; správci nesoutěží. */
    data object Administrator : GiveawayStatus

    /** Soutěž je jen pro jiné země, než ze které účet je. */
    data class DisallowedCountry(val countryCode: String) : GiveawayStatus

    /** Vylosováno a účet nevyhrál. */
    data object NotWon : GiveawayStatus

    /**
     * Vylosováno a účet vyhrál: hvězdy ([stars]), nebo Premium s kódem ([giftCode]) — ten jde
     * uplatnit v oficiální aplikaci, Plume Premium aktivovat neumí.
     */
    data class Won(val stars: Long, val giftCode: String) : GiveawayStatus

    /** Telegram soutěž zrušil a výhry vrátil pořadateli. */
    data object Refunded : GiveawayStatus
}

internal fun TdGiveawayPrize.toDomain(): GiveawayPrize = when (this) {
    is GiveawayPrizePremium -> GiveawayPrize.Premium(months = monthCount)
    is GiveawayPrizeStars -> GiveawayPrize.Stars(count = starCount)
}

/**
 * Stav účasti. Hotová soutěž má přednost před stavem účastníka: `GiveawayInfoCompleted` nese,
 * jestli účet vyhrál (kód Premium, nebo hvězdy), a nic jiného už není podstatné.
 */
internal fun GiveawayInfo.toStatus(): GiveawayStatus = when (this) {
    is GiveawayInfoCompleted -> when {
        wasRefunded -> GiveawayStatus.Refunded
        giftCode.isNotBlank() || wonStarCount > 0 -> GiveawayStatus.Won(stars = wonStarCount, giftCode = giftCode)
        else -> GiveawayStatus.NotWon
    }
    is GiveawayInfoOngoing -> status.toStatus()
}

private fun GiveawayParticipantStatus.toStatus(): GiveawayStatus = when (this) {
    is GiveawayParticipantStatusParticipating -> GiveawayStatus.Participating
    is GiveawayParticipantStatusEligible -> GiveawayStatus.Eligible
    is GiveawayParticipantStatusAlreadyWasMember -> GiveawayStatus.AlreadyMember(joinedChatDate.toLong())
    is GiveawayParticipantStatusAdministrator -> GiveawayStatus.Administrator
    is GiveawayParticipantStatusDisallowedCountry -> GiveawayStatus.DisallowedCountry(userCountryCode)
}

/**
 * Vyhlášení soutěže jako karta. [senderName] dává jméno podle id ve tvaru odesílatele (záporné
 * = chat); kanál, který TDLib nezná, z karty vypadne — čip bez jména by nikam nevedl.
 */
internal suspend fun MessageGiveaway.toMedia(senderName: suspend (Long) -> String?): MessageMedia.Giveaway =
    MessageMedia.Giveaway(
        prize = prize.toDomain(),
        winnerCount = winnerCount,
        prizeDescription = parameters.prizeDescription,
        chats = (listOf(parameters.boostedChatId) + parameters.additionalChatIds.toList())
            .distinct()
            .mapNotNull { id -> senderName(id)?.let { GiveawayChat(chatId = id, title = it) } },
        selectionDate = parameters.winnersSelectionDate.toLong(),
        onlyNewMembers = parameters.onlyNewMembers,
        countryCodes = parameters.countryCodes.filter { it.isNotBlank() },
        sticker = sticker?.toMedia(),
    )

/**
 * Výsledky soutěže. Kanály, které se k pořadateli přidaly, oznámení nese jen počtem; výherci
 * jsou jen u soutěže s veřejnými výherci, jinak je seznam prázdný a karta řekne jen počet.
 */
internal suspend fun MessageGiveawayWinners.toMedia(senderName: suspend (Long) -> String?): MessageMedia.Giveaway =
    MessageMedia.Giveaway(
        prize = prize.toDomain(),
        winnerCount = winnerCount,
        prizeDescription = prizeDescription,
        chats = listOfNotNull(senderName(boostedChatId)?.let { GiveawayChat(chatId = boostedChatId, title = it) }),
        selectionDate = actualWinnersSelectionDate.toLong(),
        onlyNewMembers = onlyNewMembers,
        countryCodes = emptyList(),
        results = GiveawayResults(
            winners = winnerUserIds.toList().map { id -> GiveawayWinner(userId = id, name = senderName(id).orEmpty()) },
            unclaimedCount = unclaimedPrizeCount,
            wasRefunded = wasRefunded,
            additionalChatCount = additionalChatCount,
            giveawayMessageId = giveawayMessageId,
        ),
    )
