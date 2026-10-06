package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.GiveawayInfoCompleted
import dev.g000sha256.tdl.dto.GiveawayInfoOngoing
import dev.g000sha256.tdl.dto.GiveawayParameters
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusAlreadyWasMember
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusDisallowedCountry
import dev.g000sha256.tdl.dto.GiveawayParticipantStatusParticipating
import dev.g000sha256.tdl.dto.GiveawayPrizePremium
import dev.g000sha256.tdl.dto.GiveawayPrizeStars
import dev.g000sha256.tdl.dto.MessageGiveaway
import dev.g000sha256.tdl.dto.MessageGiveawayWinners
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GiveawayTest {

    private val names = mapOf(-100L to "Kanál", -200L to "Druhý kanál", 7L to "Jana")
    private val senderName: suspend (Long) -> String? = { names[it] }

    private fun giveaway(additional: LongArray = longArrayOf(-200L), countries: Array<String> = emptyArray()) =
        MessageGiveaway(
            parameters = GiveawayParameters(
                boostedChatId = -100L,
                additionalChatIds = additional,
                winnersSelectionDate = 1_800_000_000,
                onlyNewMembers = true,
                hasPublicWinners = true,
                countryCodes = countries,
                prizeDescription = "tričko",
            ),
            winnerCount = 3,
            prize = GiveawayPrizePremium(monthCount = 6),
            sticker = null,
        )

    @Test
    fun vyhlaseniNeseKanalyPodminkyAVyhru() = runTest {
        val media = giveaway(countries = arrayOf("CZ", "")).toMedia(senderName)
        assertEquals(GiveawayPrize.Premium(months = 6), media.prize)
        assertEquals(3, media.winnerCount)
        assertEquals("tričko", media.prizeDescription)
        assertEquals(listOf(GiveawayChat(-100L, "Kanál"), GiveawayChat(-200L, "Druhý kanál")), media.chats)
        assertEquals(listOf("CZ"), media.countryCodes)
        assertEquals(true, media.onlyNewMembers)
        assertEquals(1_800_000_000L, media.selectionDate)
        assertNull(media.results)
    }

    @Test
    fun neznamyKanalVypadneAOpakovanySeNezdvoji() = runTest {
        val media = giveaway(additional = longArrayOf(-100L, -300L)).toMedia(senderName)
        assertEquals(listOf(GiveawayChat(-100L, "Kanál")), media.chats)
    }

    @Test
    fun vysledkyNesouVyhercePodleJmen() = runTest {
        val media = MessageGiveawayWinners(
            boostedChatId = -100L,
            giveawayMessageId = 42L,
            additionalChatCount = 2,
            actualWinnersSelectionDate = 1_800_000_100,
            onlyNewMembers = false,
            wasRefunded = false,
            prize = GiveawayPrizeStars(starCount = 1_500),
            prizeDescription = "",
            winnerCount = 2,
            winnerUserIds = longArrayOf(7L, 8L),
            unclaimedPrizeCount = 1,
        ).toMedia(senderName)
        assertEquals(GiveawayPrize.Stars(count = 1_500), media.prize)
        assertEquals(listOf(GiveawayChat(-100L, "Kanál")), media.chats)
        assertEquals(
            GiveawayResults(
                // Výherce, kterého TDLib nezná, zůstane — jen bez jména; karta ho nekreslí.
                winners = listOf(GiveawayWinner(7L, "Jana"), GiveawayWinner(8L, "")),
                unclaimedCount = 1,
                wasRefunded = false,
                additionalChatCount = 2,
                giveawayMessageId = 42L,
            ),
            media.results,
        )
    }

    @Test
    fun hotovaSoutezRikaJestliUcetVyhral() {
        fun completed(refunded: Boolean = false, code: String = "", stars: Long = 0) = GiveawayInfoCompleted(
            creationDate = 0,
            actualWinnersSelectionDate = 0,
            wasRefunded = refunded,
            isWinner = code.isNotBlank() || stars > 0,
            winnerCount = 3,
            activationCount = 0,
            giftCode = code,
            wonStarCount = stars,
        ).toStatus()

        assertEquals(GiveawayStatus.NotWon, completed())
        assertEquals(GiveawayStatus.Won(stars = 0, giftCode = "ABC"), completed(code = "ABC"))
        assertEquals(GiveawayStatus.Won(stars = 500, giftCode = ""), completed(stars = 500))
        // Zrušená soutěž má přednost — výhra z ní nikomu nepřišla.
        assertEquals(GiveawayStatus.Refunded, completed(refunded = true, code = "ABC"))
    }

    @Test
    fun bezociSoutezRikaStavUcastnika() {
        fun ongoing(status: dev.g000sha256.tdl.dto.GiveawayParticipantStatus) =
            GiveawayInfoOngoing(creationDate = 0, status = status, isEnded = false).toStatus()

        assertEquals(GiveawayStatus.Participating, ongoing(GiveawayParticipantStatusParticipating()))
        assertEquals(GiveawayStatus.AlreadyMember(1_700_000_000), ongoing(GiveawayParticipantStatusAlreadyWasMember(1_700_000_000)))
        assertEquals(GiveawayStatus.DisallowedCountry("RU"), ongoing(GiveawayParticipantStatusDisallowedCountry("RU")))
    }
}
