package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageBasicGroupChatCreate
import dev.g000sha256.tdl.dto.MessageBotWriteAccessAllowed
import dev.g000sha256.tdl.dto.MessageSuggestBirthdate
import dev.g000sha256.tdl.dto.MessageSuggestProfilePhoto
import eu.tintera.plume.resources.content_suggested_photo
import eu.tintera.plume.core.ui.labelUiText
import eu.tintera.plume.resources.service_suggested_birthdate
import dev.g000sha256.tdl.dto.MessageChatShared
import dev.g000sha256.tdl.dto.MessageUsersShared
import dev.g000sha256.tdl.dto.BotWriteAccessAllowReasonAcceptedRequest
import dev.g000sha256.tdl.dto.BotWriteAccessAllowReasonAddedToAttachmentMenu
import dev.g000sha256.tdl.dto.BotWriteAccessAllowReasonConnectedWebsite
import dev.g000sha256.tdl.dto.BotWriteAccessAllowReasonLaunchedWebApp
import dev.g000sha256.tdl.dto.MessageChatAddedToCommunity
import dev.g000sha256.tdl.dto.MessageChatBoost
import dev.g000sha256.tdl.dto.MessageChatHasProtectedContentDisableRequested
import dev.g000sha256.tdl.dto.MessageChatHasProtectedContentToggled
import dev.g000sha256.tdl.dto.MessageChatJoinFromCommunity
import dev.g000sha256.tdl.dto.MessageChatRemovedFromCommunity
import dev.g000sha256.tdl.dto.MessageChatOwnerChanged
import dev.g000sha256.tdl.dto.MessageChatOwnerLeft
import dev.g000sha256.tdl.dto.MessageChecklistTasksAdded
import dev.g000sha256.tdl.dto.MessageChecklistTasksDone
import dev.g000sha256.tdl.dto.MessageDirectMessagePriceChanged
import dev.g000sha256.tdl.dto.MessageExpiredPhoto
import dev.g000sha256.tdl.dto.MessageExpiredVideo
import dev.g000sha256.tdl.dto.MessageExpiredVideoNote
import dev.g000sha256.tdl.dto.MessageExpiredVoiceNote
import dev.g000sha256.tdl.dto.MessageGift
import dev.g000sha256.tdl.dto.MessageGiftedGrams
import dev.g000sha256.tdl.dto.MessageGiftedPremium
import dev.g000sha256.tdl.dto.MessageGiftedStars
import dev.g000sha256.tdl.dto.MessageGiveawayCompleted
import dev.g000sha256.tdl.dto.MessageGiveawayCreated
import dev.g000sha256.tdl.dto.MessageGiveawayPrizeStars
import dev.g000sha256.tdl.dto.MessageInviteVideoChatParticipants
import dev.g000sha256.tdl.dto.MessageManagedBotCreated
import dev.g000sha256.tdl.dto.MessagePaidMessagePriceChanged
import dev.g000sha256.tdl.dto.MessagePaidMessagesRefunded
import dev.g000sha256.tdl.dto.MessagePassportDataSent
import dev.g000sha256.tdl.dto.MessagePaymentRefunded
import dev.g000sha256.tdl.dto.MessagePaymentSuccessful
import dev.g000sha256.tdl.dto.MessagePollOptionAdded
import dev.g000sha256.tdl.dto.MessagePollOptionDeleted
import dev.g000sha256.tdl.dto.MessagePremiumGiftCode
import dev.g000sha256.tdl.dto.MessageRefundedUpgradedGift
import dev.g000sha256.tdl.dto.MessageSuggestedPostApprovalFailed
import dev.g000sha256.tdl.dto.MessageSuggestedPostApproved
import dev.g000sha256.tdl.dto.MessageSuggestedPostDeclined
import dev.g000sha256.tdl.dto.MessageSuggestedPostPaid
import dev.g000sha256.tdl.dto.MessageSuggestedPostRefunded
import dev.g000sha256.tdl.dto.MessageUpgradedGift
import dev.g000sha256.tdl.dto.MessageUpgradedGiftPurchaseOffer
import dev.g000sha256.tdl.dto.MessageUpgradedGiftPurchaseOfferRejected
import dev.g000sha256.tdl.dto.MessageChatAddMembers
import dev.g000sha256.tdl.dto.MessageChatChangePhoto
import dev.g000sha256.tdl.dto.MessageChatChangeTitle
import dev.g000sha256.tdl.dto.MessageChatDeleteMember
import dev.g000sha256.tdl.dto.MessageChatDeletePhoto
import dev.g000sha256.tdl.dto.MessageChatJoinByLink
import dev.g000sha256.tdl.dto.MessageChatJoinByRequest
import dev.g000sha256.tdl.dto.MessageChatSetBackground
import dev.g000sha256.tdl.dto.MessageChatSetMessageAutoDeleteTime
import dev.g000sha256.tdl.dto.MessageChatSetTheme
import dev.g000sha256.tdl.dto.MessageChatUpgradeFrom
import dev.g000sha256.tdl.dto.MessageChatUpgradeTo
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.MessageContactRegistered
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageCustomServiceAction
import dev.g000sha256.tdl.dto.MessageForumTopicCreated
import dev.g000sha256.tdl.dto.MessageForumTopicEdited
import dev.g000sha256.tdl.dto.MessageForumTopicIsClosedToggled
import dev.g000sha256.tdl.dto.MessageForumTopicIsHiddenToggled
import dev.g000sha256.tdl.dto.MessageGameScore
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.MessageProximityAlertTriggered
import dev.g000sha256.tdl.dto.MessageScreenshotTaken
import dev.g000sha256.tdl.dto.MessageSupergroupChatCreate
import dev.g000sha256.tdl.dto.MessageVideoChatEnded
import dev.g000sha256.tdl.dto.MessageVideoChatScheduled
import dev.g000sha256.tdl.dto.MessageVideoChatStarted
import dev.g000sha256.tdl.dto.MessageWebAppDataSent
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.durationUiText
import eu.tintera.plume.core.ui.distanceUiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_expired_photo
import eu.tintera.plume.resources.content_expired_video
import eu.tintera.plume.resources.content_expired_video_note
import eu.tintera.plume.resources.content_expired_voice_note
import eu.tintera.plume.resources.service_bot_created
import eu.tintera.plume.resources.service_community_added
import eu.tintera.plume.resources.service_community_joined
import eu.tintera.plume.resources.service_community_removed
import eu.tintera.plume.resources.service_direct_message_price
import eu.tintera.plume.resources.service_direct_messages_off
import eu.tintera.plume.resources.service_direct_messages_on
import eu.tintera.plume.resources.service_gift_offer
import eu.tintera.plume.resources.service_gift_offer_expired
import eu.tintera.plume.resources.service_gift_offer_rejected
import eu.tintera.plume.resources.service_paid_message_price
import eu.tintera.plume.resources.service_paid_messages_free
import eu.tintera.plume.resources.service_paid_messages_refunded
import eu.tintera.plume.resources.service_passport_data
import eu.tintera.plume.resources.service_payment_refunded
import eu.tintera.plume.resources.service_protected_content_disable_expired
import eu.tintera.plume.resources.service_protected_content_disable_requested
import eu.tintera.plume.resources.service_suggested_post_approved
import eu.tintera.plume.resources.service_suggested_post_declined
import eu.tintera.plume.resources.service_suggested_post_declined_comment
import eu.tintera.plume.resources.service_suggested_post_failed
import eu.tintera.plume.resources.service_suggested_post_paid
import eu.tintera.plume.resources.service_suggested_post_refunded
import eu.tintera.plume.resources.service_topic_closed
import eu.tintera.plume.resources.service_topic_hidden
import eu.tintera.plume.resources.service_topic_reopened
import eu.tintera.plume.resources.service_topic_unhidden
import eu.tintera.plume.resources.service_web_app_data
import eu.tintera.plume.resources.service_auto_delete_off
import eu.tintera.plume.resources.service_boost
import eu.tintera.plume.resources.service_checklist_tasks_added
import eu.tintera.plume.resources.service_checklist_tasks_done
import eu.tintera.plume.resources.service_gift
import eu.tintera.plume.resources.service_gift_code
import eu.tintera.plume.resources.service_gift_named
import eu.tintera.plume.resources.service_gift_premium
import eu.tintera.plume.resources.service_gift_stars
import eu.tintera.plume.resources.service_gift_ton
import eu.tintera.plume.resources.service_giveaway_prize_stars
import eu.tintera.plume.resources.giveaway_premium_months
import eu.tintera.plume.resources.duration_days
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.service_giveaway_completed
import eu.tintera.plume.resources.service_giveaway_created
import eu.tintera.plume.resources.service_owner_changed
import eu.tintera.plume.resources.service_payment
import eu.tintera.plume.resources.service_poll_option_added
import eu.tintera.plume.resources.service_poll_option_deleted
import eu.tintera.plume.resources.service_protected_content_off
import eu.tintera.plume.resources.service_protected_content_on
import eu.tintera.plume.resources.service_video_chat_invite
import eu.tintera.plume.resources.auto_delete_summary
import eu.tintera.plume.resources.service_background_changed
import eu.tintera.plume.resources.service_channel_created
import eu.tintera.plume.resources.service_contact_joined
import eu.tintera.plume.resources.service_group_created
import eu.tintera.plume.resources.service_group_upgraded
import eu.tintera.plume.resources.service_join_approved
import eu.tintera.plume.resources.service_joined_by_link
import eu.tintera.plume.resources.service_member_left
import eu.tintera.plume.resources.service_members_added
import eu.tintera.plume.resources.service_message_pinned
import eu.tintera.plume.resources.service_nearby
import eu.tintera.plume.resources.service_photo_changed
import eu.tintera.plume.resources.service_photo_removed
import eu.tintera.plume.resources.service_screenshot
import eu.tintera.plume.resources.service_theme_changed
import eu.tintera.plume.resources.service_title_changed
import eu.tintera.plume.resources.service_topic_created
import eu.tintera.plume.resources.service_topic_edited
import eu.tintera.plume.resources.service_video_chat_ended
import eu.tintera.plume.resources.service_video_chat_scheduled
import eu.tintera.plume.resources.service_video_chat_started
import eu.tintera.plume.resources.service_channel_photo_changed
import eu.tintera.plume.resources.service_channel_photo_removed
import eu.tintera.plume.resources.service_theme_reset
import eu.tintera.plume.resources.service_live_stream_started
import eu.tintera.plume.resources.service_live_stream_ended
import eu.tintera.plume.resources.service_live_stream_scheduled
import eu.tintera.plume.resources.service_live_stream_invite
import eu.tintera.plume.resources.service_owner_left
import eu.tintera.plume.resources.service_owner_left_successor
import eu.tintera.plume.resources.service_live_stream_ended_after
import eu.tintera.plume.resources.service_video_chat_ended_after
import eu.tintera.plume.resources.service_topic_renamed
import eu.tintera.plume.resources.service_topic_icon_removed
import eu.tintera.plume.resources.service_topic_icon_changed
import eu.tintera.plume.resources.service_game_score_value
import eu.tintera.plume.resources.service_boost_count
import eu.tintera.plume.resources.service_poll_option_added_named
import eu.tintera.plume.resources.service_poll_option_deleted_named
import eu.tintera.plume.resources.service_giveaway_completed_winners
import eu.tintera.plume.resources.service_giveaway_completed_unclaimed
import eu.tintera.plume.resources.giveaway_unclaimed_prizes
import eu.tintera.plume.resources.service_bot_write_access_website
import eu.tintera.plume.resources.service_bot_write_access_attachment_menu
import eu.tintera.plume.resources.service_bot_write_access_web_app
import eu.tintera.plume.resources.service_bot_write_access_accepted
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.dateUiText
import eu.tintera.plume.resources.service_video_chat_scheduled_at
import eu.tintera.plume.resources.service_live_stream_scheduled_at
import eu.tintera.plume.resources.service_payment_amount
import eu.tintera.plume.resources.service_payment_amount_for
import eu.tintera.plume.resources.service_payment_recurring
import eu.tintera.plume.resources.service_payment_refunded_amount
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Text servisní zprávy, nebo `null` když o servisní zprávu nejde.
 *
 * Servisní zprávy nejsou od nikoho — jsou to oznámení o dění v chatu (někdo se připojil, změnil
 * se název, někdo připnul zprávu). Proto se kreslí na střed a bez bubliny; bublina je od
 * odesílatele a tyhle odesílatele nemají.
 *
 * **Nevypisují se jména** — je to věta, která platí vždycky („Do skupiny přibyli členové").
 * Zprávy o členech, kde jméno chybělo nejvíc, dostanou jména zvlášť ([memberServiceText]),
 * když je volající zná; tahle věta zůstává jako náhrada, když je nezná.
 *
 * `null` pro obyčejný obsah je zároveň odpověď na otázku „je tohle servisní zpráva?" — jedno
 * místo rozhoduje o textu i o způsobu vykreslení, takže se ty dva nemůžou rozejít.
 *
 * [isChannelPost] je `Message.isChannelPost` zprávy, které obsah patří. Sám obsah někdy neřekne,
 * jestli jde o kanál, nebo o skupinu (viz [supergroupCreatedText]); výchozí hodnotu parametr
 * schválně nemá, ať se na to žádné volající místo nezapomene zeptat.
 */
internal fun MessageContent.serviceText(isChannelPost: Boolean): UiText? = when (this) {
    is MessageChatAddMembers -> UiText(Res.string.service_members_added)
    is MessageChatJoinByLink -> UiText(Res.string.service_joined_by_link)
    is MessageChatJoinByRequest -> UiText(Res.string.service_join_approved)
    is MessageChatDeleteMember -> UiText(Res.string.service_member_left)

    is MessageChatChangeTitle -> UiText(Res.string.service_title_changed, title)
    // Fotku mění skupina i kanál a obsah to neříká — rozliší to až zpráva, jako u založení.
    is MessageChatChangePhoto -> UiText(
        if (isChannelPost) Res.string.service_channel_photo_changed else Res.string.service_photo_changed,
    )
    is MessageChatDeletePhoto -> UiText(
        if (isChannelPost) Res.string.service_channel_photo_removed else Res.string.service_photo_removed,
    )
    // Bez motivu = vrácení na výchozí, ne změna.
    is MessageChatSetTheme -> UiText(if (theme == null) Res.string.service_theme_reset else Res.string.service_theme_changed)
    is MessageChatSetBackground -> UiText(Res.string.service_background_changed)

    // S délkou: „Zprávy se budou samy mazat" neřeklo, za jak dlouho.
    is MessageChatSetMessageAutoDeleteTime -> when (messageAutoDeleteTime) {
        0 -> UiText(Res.string.service_auto_delete_off)
        else -> UiText(Res.string.auto_delete_summary, durationUiText(messageAutoDeleteTime))
    }

    is MessageBasicGroupChatCreate -> UiText(Res.string.service_group_created, title)
    is MessageSupergroupChatCreate -> UiText(supergroupCreatedText(isChannelPost), title)
    // Převod na supergrupu se uživateli jeví jako jedna a tatáž skupina, takže nemá cenu
    // vysvětlovat, co se změnilo pod kapotou.
    is MessageChatUpgradeTo -> UiText(Res.string.service_group_upgraded)
    is MessageChatUpgradeFrom -> UiText(Res.string.service_group_upgraded)

    is MessagePinMessage -> UiText(Res.string.service_message_pinned)
    is MessageScreenshotTaken -> UiText(Res.string.service_screenshot)
    is MessageContactRegistered -> UiText(Res.string.service_contact_joined)
    is MessageProximityAlertTriggered -> UiText(Res.string.service_nearby, distanceUiText(distance))

    // Videochat kanálu je živé vysílání — tak ho jmenuje i pruh nad konverzací.
    is MessageVideoChatStarted -> UiText(
        if (isChannelPost) Res.string.service_live_stream_started else Res.string.service_video_chat_started,
    )
    // S délkou v závorce: „Videochat skončil" neřeklo, jestli trval minutu, nebo dvě hodiny.
    is MessageVideoChatEnded -> when {
        duration <= 0 -> UiText(if (isChannelPost) Res.string.service_live_stream_ended else Res.string.service_video_chat_ended)
        else -> UiText(
            if (isChannelPost) Res.string.service_live_stream_ended_after else Res.string.service_video_chat_ended_after,
            durationUiText(duration),
        )
    }
    // S časem začátku: „naplánován" bez data neřeklo, kdy se připojit.
    is MessageVideoChatScheduled -> when {
        startDate <= 0 -> UiText(
            if (isChannelPost) Res.string.service_live_stream_scheduled else Res.string.service_video_chat_scheduled,
        )
        else -> UiText(
            if (isChannelPost) Res.string.service_live_stream_scheduled_at else Res.string.service_video_chat_scheduled_at,
            startDate.dateUiText(),
            startDate.asTimeOfDay(),
        )
    }

    is MessageForumTopicCreated -> UiText(Res.string.service_topic_created, name)
    // Nový název nese zpráva sama; bez něj se měnila jen ikona (`editIconCustomEmojiId`).
    is MessageForumTopicEdited -> when {
        name.isNotBlank() -> UiText(Res.string.service_topic_renamed, name)
        editIconCustomEmojiId && iconCustomEmojiId == 0L -> UiText(Res.string.service_topic_icon_removed)
        editIconCustomEmojiId -> UiText(Res.string.service_topic_icon_changed)
        else -> UiText(Res.string.service_topic_edited)
    }
    is MessageForumTopicIsClosedToggled -> UiText(
        if (isClosed) Res.string.service_topic_closed else Res.string.service_topic_reopened,
    )
    is MessageForumTopicIsHiddenToggled -> UiText(
        if (isHidden) Res.string.service_topic_hidden else Res.string.service_topic_unhidden,
    )

    is MessageGameScore -> UiText(Res.string.service_game_score_value, score.grouped())

    is MessageInviteVideoChatParticipants -> UiText(
        if (isChannelPost) Res.string.service_live_stream_invite else Res.string.service_video_chat_invite,
    )
    is MessageChatOwnerChanged -> UiText(Res.string.service_owner_changed)
    // Vlastník jen odešel: nástupce chat převezme, teprve když se nevrátí, a nemusí žádný být.
    // „Chat má nového vlastníka" tu tvrdilo něco, co se možná nikdy nestane.
    is MessageChatOwnerLeft -> UiText(
        if (newOwnerUserId == 0L) Res.string.service_owner_left else Res.string.service_owner_left_successor,
    )
    is MessageChatHasProtectedContentToggled -> UiText(
        if (newHasProtectedContent) Res.string.service_protected_content_on else Res.string.service_protected_content_off,
    )
    // Žádost o vypnutí ochrany obsahu musí odsouhlasit víc správců, takže platí jen chvíli;
    // vypršenou žádost Telegram nechává v historii stát, jen s jiným významem.
    is MessageChatHasProtectedContentDisableRequested -> UiText(
        if (isExpired) Res.string.service_protected_content_disable_expired
        else Res.string.service_protected_content_disable_requested,
    )
    is MessageChatBoost ->
        if (boostCount <= 1) UiText(Res.string.service_boost)
        else UiText(Res.plurals.service_boost_count, boostCount, boostCount)
    // Text možnosti nese zpráva sama; ťuknutím se skočí na anketu (`serviceTargetMessageId`).
    is MessagePollOptionAdded -> text.text.ifBlank { null }
        ?.let { UiText(Res.string.service_poll_option_added_named, quotedTask(it)) }
        ?: UiText(Res.string.service_poll_option_added)
    is MessagePollOptionDeleted -> text.text.ifBlank { null }
        ?.let { UiText(Res.string.service_poll_option_deleted_named, quotedTask(it)) }
        ?: UiText(Res.string.service_poll_option_deleted)
    is MessageChecklistTasksAdded -> UiText(Res.string.service_checklist_tasks_added)
    is MessageChecklistTasksDone -> UiText(Res.string.service_checklist_tasks_done)
    // Částka (a za co) je to, co se z oznámení o platbě chce vědět.
    is MessagePaymentSuccessful -> paymentText(Price(totalAmount, currency), invoiceName, isRecurring)

    // Dárky kreslí oficiální klient jako oznámení, ne jako zprávu od někoho. V konverzaci je
    // z nich karta (`MessageMedia.Gift`); tahle věta jde do seznamu chatů, citace a notifikace,
    // a proto říká, CO se darovalo — „Byl poslán dárek" u hvězd i Premium nic neřekl.
    is MessageGift, is MessageRefundedUpgradedGift -> UiText(Res.string.service_gift)
    is MessageUpgradedGift -> UiText(Res.string.service_gift_named, "${gift.title} #${gift.number}")
    is MessageGiftedPremium -> UiText(Res.string.service_gift_premium, premiumPeriod(monthCount, dayCount))
    is MessagePremiumGiftCode -> UiText(Res.string.service_gift_code)
    is MessageGiftedStars -> starAmount(Res.plurals.service_gift_stars, starCount)
    is MessageGiveawayPrizeStars -> starAmount(Res.plurals.service_giveaway_prize_stars, starCount)
    is MessageGiftedGrams -> UiText(Res.string.service_gift_ton, tonAmount(gramAmount))
    is MessageUpgradedGiftPurchaseOffer -> UiText(Res.string.service_gift_offer)
    is MessageUpgradedGiftPurchaseOfferRejected -> UiText(
        if (wasExpired) Res.string.service_gift_offer_expired else Res.string.service_gift_offer_rejected,
    )
    is MessageGiveawayCreated -> UiText(Res.string.service_giveaway_created)
    // Počet výherců (a nerozdaných cen) je to, co se z oznámení chce vědět. Jména výherců
    // nese až `MessageGiveawayWinners`, tahle zpráva jen počty.
    is MessageGiveawayCompleted -> when {
        winnerCount <= 0 -> UiText(Res.string.service_giveaway_completed)
        unclaimedPrizeCount <= 0 -> UiText(Res.plurals.service_giveaway_completed_winners, winnerCount, winnerCount)
        else -> UiText(
            Res.string.service_giveaway_completed_unclaimed,
            UiText(Res.plurals.service_giveaway_completed_winners, winnerCount, winnerCount),
            UiText(Res.plurals.giveaway_unclaimed_prizes, unclaimedPrizeCount, unclaimedPrizeCount),
        )
    }

    // Komunity (TDLib 15). Název komunity se nevypisuje ze stejného důvodu jako jména: znamenal
    // by dotaz navíc u každé takové zprávy, a věta je pravdivá i bez něj.
    is MessageChatAddedToCommunity -> UiText(Res.string.service_community_added)
    is MessageChatRemovedFromCommunity -> UiText(Res.string.service_community_removed)
    is MessageChatJoinFromCommunity -> UiText(Res.string.service_community_joined)

    /*
     * Placené zprávy (TDLib 15). Cena JE obsah toho oznámení, takže se vypisuje — a protože se
     * počítá, jde přes `plurals`, ne přes `when` nad číslem (čeština má tři tvary).
     *
     * U kanálu jsou to dvě nezávislé věci: `isEnabled` říká, jestli skupina přímých zpráv vůbec
     * je, `paidMessageStarCount` kolik stojí. Nula u zapnuté skupiny je tedy zlevnění na nulu,
     * ne vypnutí.
     */
    is MessageDirectMessagePriceChanged -> when {
        !isEnabled -> UiText(Res.string.service_direct_messages_off)
        paidMessageStarCount <= 0 -> UiText(Res.string.service_direct_messages_on)
        else -> starSentence(Res.plurals.service_direct_message_price, paidMessageStarCount)
    }
    is MessagePaidMessagePriceChanged ->
        if (paidMessageStarCount <= 0) UiText(Res.string.service_paid_messages_free)
        else starSentence(Res.plurals.service_paid_message_price, paidMessageStarCount)
    is MessagePaidMessagesRefunded -> starSentence(Res.plurals.service_paid_messages_refunded, starCount)

    // Navržené příspěvky (TDLib 15). Cena ani komentář správce se nevypisují — oznámení nese
    // hlavně to, jak návrh dopadl, a na komentář se stejně nedá odpovědět.
    is MessageSuggestedPostApproved -> UiText(Res.string.service_suggested_post_approved)
    // Komentář správce je důvod odmítnutí — bez něj by autor nevěděl proč.
    is MessageSuggestedPostDeclined -> comment.trim().takeIf { it.isNotEmpty() }
        ?.let { UiText(Res.string.service_suggested_post_declined_comment, it) }
        ?: UiText(Res.string.service_suggested_post_declined)
    is MessageSuggestedPostPaid -> UiText(Res.string.service_suggested_post_paid)
    is MessageSuggestedPostRefunded -> UiText(Res.string.service_suggested_post_refunded)
    is MessageSuggestedPostApprovalFailed -> UiText(Res.string.service_suggested_post_failed)

    // Proč bot smí psát: bez důvodu to vypadalo, jako by si to bot povolil sám.
    // Co účet sdílel s botem (odpověď na tlačítko klávesnice). Jména a název jsou ve zprávě
    // jen pro boty; uživatelský účet je dohledá podle id (`namedSharedWithBotText`), tohle je
    // věta pro případ, že nic dohledat nejde.
    is MessageChatShared -> sharedChatText(chat.title)
    is MessageUsersShared -> sharedUsersText(users.map { it.ownName() })

    // Fotku samotnou a „Nastavit" kreslí konverzace (`ChatMessage.suggestedPhoto`).
    is MessageSuggestProfilePhoto -> UiText(Res.string.content_suggested_photo)
    // Datum je obsah návrhu. Jméno (kdo navrhuje) a „Nastavit" přidává konverzace.
    is MessageSuggestBirthdate -> UiText(Res.string.service_suggested_birthdate, birthdate.toDomain().labelUiText())

    is MessageBotWriteAccessAllowed -> when (val why = reason) {
        is BotWriteAccessAllowReasonConnectedWebsite -> UiText(Res.string.service_bot_write_access_website, why.domainName)
        is BotWriteAccessAllowReasonAddedToAttachmentMenu -> UiText(Res.string.service_bot_write_access_attachment_menu)
        is BotWriteAccessAllowReasonLaunchedWebApp -> UiText(
            Res.string.service_bot_write_access_web_app,
            why.webApp.title.ifBlank { why.webApp.shortName },
        )
        is BotWriteAccessAllowReasonAcceptedRequest -> UiText(Res.string.service_bot_write_access_accepted)
    }
    is MessageManagedBotCreated -> UiText(Res.string.service_bot_created)
    is MessageWebAppDataSent -> UiText(Res.string.service_web_app_data)
    is MessagePassportDataSent -> UiText(Res.string.service_passport_data)
    is MessagePaymentRefunded ->
        if (totalAmount <= 0) UiText(Res.string.service_payment_refunded)
        else UiText(Res.string.service_payment_refunded_amount, Price(totalAmount, currency).asUiText())

    // Tenhle typ nese hotový text od serveru; překládat ho není co.
    is MessageCustomServiceAction -> UiText.Raw(text)

    // Kontakt NENÍ servisní zpráva — jen vypadá podobně. Popisuje ho previewText.
    is MessageContact -> null

    /*
     * Mizející médium, které vypršelo. Je to oznámení o tom, co se se zprávou STALO, ne obsah od
     * odesílatele: v bublině se „Fotka vypršela" četla, jako by to protistrana napsala. Soubor
     * už na serveru není, takže není co ukázat ani stáhnout.
     */
    is MessageExpiredPhoto -> UiText(Res.string.content_expired_photo)
    is MessageExpiredVideo -> UiText(Res.string.content_expired_video)
    is MessageExpiredVideoNote -> UiText(Res.string.content_expired_video_note)
    is MessageExpiredVoiceNote -> UiText(Res.string.content_expired_voice_note)

    /*
     * `MessagePassportDataReceived`, `MessageWebAppDataReceived` a `MessagePaymentSuccessfulBot`
     * tu schválně nejsou: TDLib je označuje „for bots only" (u platby navíc firemní účet, který
     * aplikace neumí), takže k tomuhle účtu nemají jak dorazit. Kdyby přece jen dorazily, spadnou
     * mezi neznámé a zapíšou se do logu — a podle logu tabulka roste, ne podle fantazie.
     */
    else -> null
}

/**
 * Věta o zaplacené faktuře: částka, a když ji faktura má, i za co. Opakovaná platba
 * (předplatné) to říká v závorce — jinak by vypadala jako jednorázová.
 */
internal fun paymentText(price: Price, invoiceName: String, isRecurring: Boolean): UiText {
    if (price.amount <= 0) return UiText(Res.string.service_payment)
    val sentence = if (invoiceName.isBlank()) {
        UiText(Res.string.service_payment_amount, price.asUiText())
    } else {
        UiText(Res.string.service_payment_amount_for, price.asUiText(), invoiceName)
    }
    return if (isRecurring) UiText(Res.string.service_payment_recurring, sentence) else sentence
}

/**
 * Kam oznámení vede ťuknutím mimo tenhle chat: sdílený chat, nebo jediný sdílený člověk.
 * U víc lidí nevede nikam — nebylo by jasné, ke komu.
 */
internal fun MessageContent.serviceOpenTarget(): ServiceOpen? = when (this) {
    is MessageChatShared -> chat.chatId.takeIf { it != 0L }?.let(ServiceOpen::Chat)
    is MessageUsersShared -> users.singleOrNull()?.userId?.takeIf { it != 0L }?.let(ServiceOpen::User)
    else -> null
}

/**
 * Zpráva v témže chatu, na kterou servisní oznámení ukazuje: připnutá zpráva, anketa se změněnou
 * možností, soutěž a hra. Ťuknutí na oznámení na ni skočí; `null` = oznámení nikam nevede.
 */
internal fun MessageContent.serviceTargetMessageId(): Long? = when (this) {
    is MessagePinMessage -> messageId
    is MessagePollOptionAdded -> pollMessageId
    is MessagePollOptionDeleted -> pollMessageId
    is MessageGiveawayCompleted -> giveawayMessageId
    is MessageGameScore -> gameMessageId
    else -> null
}.takeIf { it != 0L }

/**
 * Věta o založení supergroupy — tedy skupiny, NEBO kanálu.
 *
 * TDLib posílá `MessageSupergroupChatCreate` pro obojí („a newly created supergroup or channel")
 * a obsah sám druh nenese. Dokud tu natvrdo stál kanál, hlásila každá nově založená skupina
 * „Kanál „X“ vytvořen" — v konverzaci i v náhledu v seznamu chatů. Rozliší to až zpráva:
 * v kanálu je každý příspěvek, i servisní, `isChannelPost`.
 */
internal fun supergroupCreatedText(isChannelPost: Boolean): StringResource =
    if (isChannelPost) Res.string.service_channel_created else Res.string.service_group_created

/**
 * Věta s počtem hvězd.
 *
 * Počet posílá TDLib jako `Long`, plurál bere `Int`. Cena, která se do `Int` nevejde,
 * neexistuje, ale přetečení by z ní udělalo záporné číslo — a to by byla nesmyslná věta místo
 * velkého čísla, takže se ořezává.
 */
/** Jako [starSentence], jen s číslem oddělovaným po tisících („1 500 hvězd"). */
private fun starAmount(resource: PluralStringResource, count: Long): UiText {
    val quantity = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return UiText(resource, quantity, count.grouped())
}

/** Délka předplatného Premium: měsíce, a jen když nejsou, dny. */
internal fun premiumPeriod(months: Int, days: Int): UiText =
    if (months > 0 || days <= 0) UiText(Res.plurals.giveaway_premium_months, months, months)
    else UiText(Res.plurals.duration_days, days, days)

/** Částka TON s desetinnou čárkou jazyka (`decimal_number`). */
internal fun tonAmount(nanoAmount: Long): UiText {
    val (whole, fraction) = tonParts(nanoAmount)
    return if (fraction.isEmpty()) UiText.Raw(whole.grouped()) else UiText(Res.string.decimal_number, whole.grouped(), fraction)
}

private fun starSentence(resource: PluralStringResource, count: Long): UiText {
    val quantity = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return UiText(resource, quantity, quantity)
}

/**
 * Kam vede oznámení o převodu skupiny. Id chatu se skládá konvencí TDLib: základní skupina
 * má záporné id skupiny, supergroupa [supergroupChatId]. Čistá funkce — `toChatMessage` je obří
 * suspend funkce a další suspend volání v ní umí shodit ověření třídy (viz docs/poznatky/typy-zprav.md, „Sdílený
 * příběh").
 */
internal fun MessageContent.upgradeLink(): GroupUpgradeLink? = when (this) {
    is MessageChatUpgradeFrom -> GroupUpgradeLink(chatId = -basicGroupId, toNewGroup = false)
    is MessageChatUpgradeTo -> GroupUpgradeLink(chatId = supergroupChatId(supergroupId), toNewGroup = true)
    else -> null
}
