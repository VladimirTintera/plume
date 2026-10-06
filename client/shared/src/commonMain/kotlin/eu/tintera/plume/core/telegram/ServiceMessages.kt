package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageBasicGroupChatCreate
import dev.g000sha256.tdl.dto.MessageBotWriteAccessAllowed
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
import eu.tintera.plume.resources.service_bot_created
import eu.tintera.plume.resources.service_bot_write_access
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
import eu.tintera.plume.resources.service_game_score
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
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Text servisní zprávy, nebo `null` když o servisní zprávu nejde.
 *
 * Servisní zprávy nejsou od nikoho — jsou to oznámení o dění v chatu (někdo se připojil, změnil
 * se název, někdo připnul zprávu). Proto se kreslí na střed a bez bubliny; bublina je od
 * odesílatele a tyhle odesílatele nemají.
 *
 * **Nevypisují se jména.** TDLib vrací id uživatelů a jméno by znamenalo dotaz navíc na každou
 * takovou zprávu, často na někoho, kdo v chatu už dávno není. Bezejmenná formulace („Do skupiny
 * přibyli členové") je proti tomu vždycky pravdivá a nic nestojí.
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
    is MessageChatChangePhoto -> UiText(Res.string.service_photo_changed)
    is MessageChatDeletePhoto -> UiText(Res.string.service_photo_removed)
    is MessageChatSetTheme -> UiText(Res.string.service_theme_changed)
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

    is MessageVideoChatStarted -> UiText(Res.string.service_video_chat_started)
    is MessageVideoChatEnded -> UiText(Res.string.service_video_chat_ended)
    is MessageVideoChatScheduled -> UiText(Res.string.service_video_chat_scheduled)

    is MessageForumTopicCreated -> UiText(Res.string.service_topic_created, name)
    is MessageForumTopicEdited -> UiText(Res.string.service_topic_edited)
    is MessageForumTopicIsClosedToggled -> UiText(
        if (isClosed) Res.string.service_topic_closed else Res.string.service_topic_reopened,
    )
    is MessageForumTopicIsHiddenToggled -> UiText(
        if (isHidden) Res.string.service_topic_hidden else Res.string.service_topic_unhidden,
    )

    is MessageGameScore -> UiText(Res.string.service_game_score)

    is MessageInviteVideoChatParticipants -> UiText(Res.string.service_video_chat_invite)
    is MessageChatOwnerChanged, is MessageChatOwnerLeft -> UiText(Res.string.service_owner_changed)
    is MessageChatHasProtectedContentToggled -> UiText(
        if (newHasProtectedContent) Res.string.service_protected_content_on else Res.string.service_protected_content_off,
    )
    // Žádost o vypnutí ochrany obsahu musí odsouhlasit víc správců, takže platí jen chvíli;
    // vypršenou žádost Telegram nechává v historii stát, jen s jiným významem.
    is MessageChatHasProtectedContentDisableRequested -> UiText(
        if (isExpired) Res.string.service_protected_content_disable_expired
        else Res.string.service_protected_content_disable_requested,
    )
    is MessageChatBoost -> UiText(Res.string.service_boost)
    is MessagePollOptionAdded -> UiText(Res.string.service_poll_option_added)
    is MessagePollOptionDeleted -> UiText(Res.string.service_poll_option_deleted)
    is MessageChecklistTasksAdded -> UiText(Res.string.service_checklist_tasks_added)
    is MessageChecklistTasksDone -> UiText(Res.string.service_checklist_tasks_done)
    is MessagePaymentSuccessful -> UiText(Res.string.service_payment)

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
    is MessageGiveawayCompleted -> UiText(Res.string.service_giveaway_completed)

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
    is MessageSuggestedPostDeclined -> UiText(Res.string.service_suggested_post_declined)
    is MessageSuggestedPostPaid -> UiText(Res.string.service_suggested_post_paid)
    is MessageSuggestedPostRefunded -> UiText(Res.string.service_suggested_post_refunded)
    is MessageSuggestedPostApprovalFailed -> UiText(Res.string.service_suggested_post_failed)

    is MessageBotWriteAccessAllowed -> UiText(Res.string.service_bot_write_access)
    is MessageManagedBotCreated -> UiText(Res.string.service_bot_created)
    is MessageWebAppDataSent -> UiText(Res.string.service_web_app_data)
    is MessagePassportDataSent -> UiText(Res.string.service_passport_data)
    is MessagePaymentRefunded -> UiText(Res.string.service_payment_refunded)

    // Tenhle typ nese hotový text od serveru; překládat ho není co.
    is MessageCustomServiceAction -> UiText.Raw(text)

    // Kontakt NENÍ servisní zpráva — jen vypadá podobně. Popisuje ho previewText.
    is MessageContact -> null

    /*
     * `MessagePassportDataReceived`, `MessageWebAppDataReceived` a `MessagePaymentSuccessfulBot`
     * tu schválně nejsou: TDLib je označuje „for bots only" (u platby navíc firemní účet, který
     * aplikace neumí), takže k tomuhle účtu nemají jak dorazit. Kdyby přece jen dorazily, spadnou
     * mezi neznámé a zapíšou se do logu — a podle logu tabulka roste, ne podle fantazie.
     */
    else -> null
}

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
