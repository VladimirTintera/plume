package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.Gift
import dev.g000sha256.tdl.dto.GiftBackground
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.MessageGift
import dev.g000sha256.tdl.dto.MessageGiftedPremium
import dev.g000sha256.tdl.dto.MessageGiveawayPrizeStars
import dev.g000sha256.tdl.dto.MessagePremiumGiftCode
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageUpgradedGift
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFullTypeRegular
import dev.g000sha256.tdl.dto.UpgradedGift
import dev.g000sha256.tdl.dto.UpgradedGiftAttributeRarityRare
import dev.g000sha256.tdl.dto.UpgradedGiftBackdrop
import dev.g000sha256.tdl.dto.UpgradedGiftBackdropColors
import dev.g000sha256.tdl.dto.UpgradedGiftModel
import dev.g000sha256.tdl.dto.UpgradedGiftOriginUpgrade
import dev.g000sha256.tdl.dto.UpgradedGiftSymbol
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GiftTest {

    private val names = mapOf(7L to "Jana", 8L to "Petr", -100L to "Kanál")
    private val senderName: suspend (Long) -> String? = { names[it] }

    private fun sticker(fileId: Int) = Sticker(
        id = fileId.toLong(),
        setId = 0,
        width = 512,
        height = 512,
        emoji = "🎁",
        format = StickerFormatTgs(),
        fullType = StickerFullTypeRegular(premiumAnimation = null),
        thumbnail = null,
        sticker = File(
            id = fileId,
            size = 0,
            expectedSize = 0,
            local = LocalFile("", false, false, false, false, 0, 0, 0),
            remote = RemoteFile("", "", false, false, 0),
        ),
    )

    private fun gift() = Gift(
        id = 1,
        publisherChatId = 0,
        sticker = sticker(11),
        starCount = 50,
        defaultSellStarCount = 40,
        upgradeStarCount = 0,
        upgradeVariantCount = 0,
        hasColors = false,
        isForBirthday = false,
        isPremium = false,
        auctionInfo = null,
        nextSendDate = 0,
        userLimits = null,
        overallLimits = null,
        background = GiftBackground(0, 0, 0),
        firstSendDate = 0,
        lastSendDate = 0,
    )

    private fun text(value: String) = FormattedText(value, emptyArray())

    @Test
    fun bezniDarekNeseHodnotuStavAJmena() = runTest {
        val media = MessageGift(
            gift = gift(),
            senderId = MessageSenderUser(7),
            receiverId = MessageSenderUser(8),
            receivedGiftId = "",
            text = text("Všechno nejlepší"),
            uniqueGiftNumber = 0,
            sellStarCount = 40,
            prepaidUpgradeStarCount = 0,
            isUpgradeSeparate = false,
            isFromAuction = false,
            isPrivate = false,
            isSaved = false,
            isPrepaidUpgrade = false,
            canBeUpgraded = false,
            wasConverted = true,
            wasUpgraded = false,
            wasRefunded = false,
            upgradedReceivedGiftId = "",
            prepaidUpgradeHash = "",
        ).toMedia(senderName)

        assertEquals(
            GiftKind.Regular(starCount = 50, sellStarCount = 40, wasConverted = true, wasUpgraded = false, wasRefunded = false),
            media.kind,
        )
        assertEquals("Všechno nejlepší", media.text)
        assertEquals("Jana", media.senderName)
        assertEquals("Petr", media.receiverName)
        assertEquals(11, media.sticker?.stickerFileId)
    }

    @Test
    fun sberatelskyDarekBereSamolepkuModeluABarvyPozadi() = runTest {
        val upgraded = UpgradedGift(
            id = 2,
            regularGiftId = 1,
            publisherChatId = 0,
            title = "Plush Pepe",
            name = "PlushPepe-42",
            number = 1_042,
            totalUpgradedCount = 0,
            maxUpgradedCount = 0,
            isBurned = false,
            isCrafted = false,
            isPremium = false,
            isThemeAvailable = false,
            usedThemeChatId = 0,
            hostId = null,
            ownerId = null,
            ownerAddress = "",
            ownerName = "",
            giftAddress = "",
            model = UpgradedGiftModel(name = "Ninja", sticker = sticker(21), rarity = UpgradedGiftAttributeRarityRare(), isCrafted = false),
            symbol = UpgradedGiftSymbol(name = "Hvězda", sticker = sticker(22), rarity = UpgradedGiftAttributeRarityRare()),
            backdrop = UpgradedGiftBackdrop(
                id = 3,
                name = "Smaragd",
                colors = UpgradedGiftBackdropColors(centerColor = 0x22AA44, edgeColor = 0x115522, symbolColor = 0, textColor = 0),
                rarity = UpgradedGiftAttributeRarityRare(),
            ),
            originalDetails = null,
            colors = null,
            resaleParameters = null,
            canSendPurchaseOffer = false,
            craftProbabilityPerMille = 0,
            valueCurrency = "",
            valueAmount = 0,
            valueUsdAmount = 0,
        )
        val media = MessageUpgradedGift(
            gift = upgraded,
            senderId = MessageSenderChat(-100),
            receiverId = MessageSenderUser(9),
            origin = UpgradedGiftOriginUpgrade(0),
            receivedGiftId = "",
            text = text(""),
            isPrivate = false,
            isSaved = false,
            canBeTransferred = false,
            wasTransferred = true,
            transferStarCount = 0,
            dropOriginalDetailsStarCount = 0,
            nextTransferDate = 0,
            nextResaleDate = 0,
            exportDate = 0,
            craftDate = 0,
        ).toMedia(senderName)

        assertEquals(
            GiftKind.Upgraded(
                title = "Plush Pepe",
                number = 1_042,
                modelName = "Ninja",
                symbolName = "Hvězda",
                backdropName = "Smaragd",
                centerColor = 0x22AA44,
                edgeColor = 0x115522,
                wasTransferred = true,
            ),
            media.kind,
        )
        // Samolepka je model (jak dárek vypadá), ne symbol.
        assertEquals(21, media.sticker?.stickerFileId)
        assertEquals("Kanál", media.senderName)
        // Příjemce, kterého TDLib nezná, zůstane bez jména.
        assertNull(media.receiverName)
    }

    @Test
    fun premiumKodAVyhraHvezd() = runTest {
        val premium = MessageGiftedPremium(
            gifterUserId = 7,
            receiverUserId = 0,
            text = text(""),
            currency = "EUR",
            amount = 0,
            cryptocurrency = "",
            cryptocurrencyAmount = 0,
            monthCount = 0,
            dayCount = 7,
            sticker = null,
        ).toMedia(senderName)
        assertEquals(GiftKind.Premium(months = 0, days = 7), premium.kind)
        assertNull(premium.receiverName)

        val code = MessagePremiumGiftCode(
            creatorId = null,
            text = text(""),
            isFromGiveaway = true,
            isUnclaimed = false,
            currency = "",
            amount = 0,
            cryptocurrency = "",
            cryptocurrencyAmount = 0,
            monthCount = 3,
            dayCount = 0,
            sticker = null,
            code = "ABC123",
        ).toMedia(senderName)
        assertEquals(GiftKind.PremiumCode(months = 3, days = 0, code = "ABC123", isUnclaimed = false), code.kind)
        assertNull(code.senderName)

        val prize = MessageGiveawayPrizeStars(
            starCount = 1_500,
            transactionId = "",
            boostedChatId = -100,
            giveawayMessageId = 5,
            isUnclaimed = false,
            sticker = null,
        ).toMedia(senderName)
        assertEquals(GiftKind.Stars(count = 1_500, fromGiveaway = true), prize.kind)
        assertEquals("Kanál", prize.senderName)
    }

    @Test
    fun tonSeDeliNaCelouCastADesetiny() {
        assertEquals(2L to "", tonParts(2_000_000_000))
        assertEquals(1L to "5", tonParts(1_500_000_000))
        // Víc než čtyři desetinná místa se nevypisuje.
        assertEquals(0L to "1234", tonParts(123_456_789))
        assertEquals(0L to "", tonParts(10))
    }
}
