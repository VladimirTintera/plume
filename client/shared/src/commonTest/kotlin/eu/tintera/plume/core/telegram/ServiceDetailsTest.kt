package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.BotWriteAccessAllowReasonAcceptedRequest
import dev.g000sha256.tdl.dto.BotWriteAccessAllowReasonConnectedWebsite
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.MessageBotWriteAccessAllowed
import dev.g000sha256.tdl.dto.MessageChatBoost
import dev.g000sha256.tdl.dto.MessageForumTopicEdited
import dev.g000sha256.tdl.dto.MessageGameScore
import dev.g000sha256.tdl.dto.MessageGiveawayCompleted
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.MessagePollOptionAdded
import dev.g000sha256.tdl.dto.MessagePollOptionDeleted
import dev.g000sha256.tdl.dto.MessageScreenshotTaken
import dev.g000sha256.tdl.dto.MessageVideoChatEnded
import dev.g000sha256.tdl.dto.TextEntity
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.duration_minutes
import eu.tintera.plume.resources.giveaway_unclaimed_prizes
import eu.tintera.plume.resources.service_boost
import eu.tintera.plume.resources.service_boost_count
import eu.tintera.plume.resources.service_bot_write_access_accepted
import eu.tintera.plume.resources.service_bot_write_access_website
import eu.tintera.plume.resources.service_game_score_value
import eu.tintera.plume.resources.service_giveaway_completed
import eu.tintera.plume.resources.service_giveaway_completed_unclaimed
import eu.tintera.plume.resources.service_giveaway_completed_winners
import eu.tintera.plume.resources.service_live_stream_ended_after
import eu.tintera.plume.resources.service_poll_option_added_named
import eu.tintera.plume.resources.service_poll_option_deleted
import eu.tintera.plume.resources.service_poll_option_deleted_named
import eu.tintera.plume.resources.service_topic_edited
import eu.tintera.plume.resources.service_topic_icon_changed
import eu.tintera.plume.resources.service_topic_icon_removed
import eu.tintera.plume.resources.service_topic_renamed
import eu.tintera.plume.resources.service_video_chat_ended_after
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Servisní věty s údaji, které TDLib ve zprávě posílá a dřív se zahazovaly. */
class ServiceDetailsTest {

    @Test
    fun konecVideochatuNeseDelku() {
        assertEquals(
            UiText(Res.string.service_video_chat_ended_after, UiText(Res.plurals.duration_minutes, 90, 90)),
            MessageVideoChatEnded(duration = 90 * 60).serviceText(isChannelPost = false),
        )
        assertEquals(
            UiText(Res.string.service_live_stream_ended_after, UiText(Res.plurals.duration_minutes, 5, 5)),
            MessageVideoChatEnded(duration = 5 * 60).serviceText(isChannelPost = true),
        )
    }

    @Test
    fun upraveneTemaRikaCoSeZmenilo() {
        assertEquals(
            UiText(Res.string.service_topic_renamed, "Výlet"),
            MessageForumTopicEdited(name = "Výlet", editIconCustomEmojiId = true, iconCustomEmojiId = 5L).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_topic_icon_changed),
            MessageForumTopicEdited(name = "", editIconCustomEmojiId = true, iconCustomEmojiId = 5L).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_topic_icon_removed),
            MessageForumTopicEdited(name = "", editIconCustomEmojiId = true, iconCustomEmojiId = 0L).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_topic_edited),
            MessageForumTopicEdited(name = "", editIconCustomEmojiId = false, iconCustomEmojiId = 0L).serviceText(false),
        )
    }

    @Test
    fun moznostAnketyMaText() {
        assertEquals(
            UiText(Res.string.service_poll_option_added_named, "Pizza"),
            MessagePollOptionAdded(pollMessageId = 7L, optionId = "a", text = text("Pizza")).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_poll_option_deleted_named, "Sushi"),
            MessagePollOptionDeleted(pollMessageId = 7L, optionId = "b", text = text("Sushi")).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_poll_option_deleted),
            MessagePollOptionDeleted(pollMessageId = 7L, optionId = "b", text = text(" ")).serviceText(false),
        )
    }

    @Test
    fun soutezNesePocty() {
        val winners = UiText(Res.plurals.service_giveaway_completed_winners, 3, 3)
        assertEquals(winners, giveaway(winners = 3, unclaimed = 0).serviceText(false))
        assertEquals(
            UiText(Res.string.service_giveaway_completed_unclaimed, winners, UiText(Res.plurals.giveaway_unclaimed_prizes, 2, 2)),
            giveaway(winners = 3, unclaimed = 2).serviceText(false),
        )
        assertEquals(UiText(Res.string.service_giveaway_completed), giveaway(winners = 0, unclaimed = 0).serviceText(false))
    }

    @Test
    fun boostSPoctem() {
        assertEquals(UiText(Res.string.service_boost), MessageChatBoost(boostCount = 1).serviceText(false))
        assertEquals(UiText(Res.plurals.service_boost_count, 4, 4), MessageChatBoost(boostCount = 4).serviceText(false))
    }

    @Test
    fun skoreJeVeVete() {
        assertEquals(
            UiText(Res.string.service_game_score_value, 1500.grouped()),
            MessageGameScore(gameMessageId = 1L, gameId = 2L, score = 1500).serviceText(false),
        )
    }

    @Test
    fun botRikaProcSmiPsat() {
        assertEquals(
            UiText(Res.string.service_bot_write_access_website, "example.com"),
            MessageBotWriteAccessAllowed(BotWriteAccessAllowReasonConnectedWebsite("example.com")).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_bot_write_access_accepted),
            MessageBotWriteAccessAllowed(BotWriteAccessAllowReasonAcceptedRequest()).serviceText(false),
        )
    }

    @Test
    fun oznameniVedeNaZpravuOKtereMluvi() {
        assertEquals(42L, MessagePinMessage(messageId = 42L).serviceTargetMessageId())
        assertEquals(7L, MessagePollOptionAdded(pollMessageId = 7L, optionId = "a", text = text("x")).serviceTargetMessageId())
        assertEquals(9L, giveaway(winners = 1, unclaimed = 0, messageId = 9L).serviceTargetMessageId())
        assertEquals(1L, MessageGameScore(gameMessageId = 1L, gameId = 2L, score = 3).serviceTargetMessageId())
        // Nula = zpráva neznámá (smazaná), nikam se neskáče.
        assertNull(MessagePinMessage(messageId = 0L).serviceTargetMessageId())
        assertNull(MessageScreenshotTaken().serviceTargetMessageId())
    }

    private fun text(value: String) = FormattedText(text = value, entities = emptyArray<TextEntity>())

    private fun giveaway(winners: Int, unclaimed: Int, messageId: Long = 1L) = MessageGiveawayCompleted(
        giveawayMessageId = messageId,
        winnerCount = winners,
        isStarGiveaway = false,
        unclaimedPrizeCount = unclaimed,
    )
}
