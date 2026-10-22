package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ChatEventAccentColorChanged
import dev.g000sha256.tdl.dto.ChatEventAction
import dev.g000sha256.tdl.dto.ChatEventActiveUsernamesChanged
import dev.g000sha256.tdl.dto.ChatEventAutomaticTranslationToggled
import dev.g000sha256.tdl.dto.ChatEventAvailableReactionsChanged
import dev.g000sha256.tdl.dto.ChatEventBackgroundChanged
import dev.g000sha256.tdl.dto.ChatEventCustomEmojiStickerSetChanged
import dev.g000sha256.tdl.dto.ChatEventDescriptionChanged
import dev.g000sha256.tdl.dto.ChatEventEmojiStatusChanged
import dev.g000sha256.tdl.dto.ChatEventForumTopicCreated
import dev.g000sha256.tdl.dto.ChatEventForumTopicDeleted
import dev.g000sha256.tdl.dto.ChatEventForumTopicEdited
import dev.g000sha256.tdl.dto.ChatEventForumTopicPinned
import dev.g000sha256.tdl.dto.ChatEventForumTopicToggleIsClosed
import dev.g000sha256.tdl.dto.ChatEventForumTopicToggleIsHidden
import dev.g000sha256.tdl.dto.ChatEventHasAggressiveAntiSpamEnabledToggled
import dev.g000sha256.tdl.dto.ChatEventHasProtectedContentToggled
import dev.g000sha256.tdl.dto.ChatEventInviteLinkDeleted
import dev.g000sha256.tdl.dto.ChatEventInviteLinkEdited
import dev.g000sha256.tdl.dto.ChatEventInviteLinkRevoked
import dev.g000sha256.tdl.dto.ChatEventInvitesToggled
import dev.g000sha256.tdl.dto.ChatEventIsAllHistoryAvailableToggled
import dev.g000sha256.tdl.dto.ChatEventIsForumToggled
import dev.g000sha256.tdl.dto.ChatEventLinkedChatChanged
import dev.g000sha256.tdl.dto.ChatEventLogFilters
import dev.g000sha256.tdl.dto.ChatEventLocationChanged
import dev.g000sha256.tdl.dto.ChatEventMemberInvited
import dev.g000sha256.tdl.dto.ChatEventMemberJoined
import dev.g000sha256.tdl.dto.ChatEventMemberJoinedByInviteLink
import dev.g000sha256.tdl.dto.ChatEventMemberJoinedByRequest
import dev.g000sha256.tdl.dto.ChatEventMemberLeft
import dev.g000sha256.tdl.dto.ChatEventMemberPromoted
import dev.g000sha256.tdl.dto.ChatEventMemberRestricted
import dev.g000sha256.tdl.dto.ChatEventMemberSubscriptionExtended
import dev.g000sha256.tdl.dto.ChatEventMemberTagChanged
import dev.g000sha256.tdl.dto.ChatEventMessageAutoDeleteTimeChanged
import dev.g000sha256.tdl.dto.ChatEventMessageDeleted
import dev.g000sha256.tdl.dto.ChatEventMessageEdited
import dev.g000sha256.tdl.dto.ChatEventMessagePinned
import dev.g000sha256.tdl.dto.ChatEventMessageUnpinned
import dev.g000sha256.tdl.dto.ChatEventPermissionsChanged
import dev.g000sha256.tdl.dto.ChatEventPhotoChanged
import dev.g000sha256.tdl.dto.ChatEventPollStopped
import dev.g000sha256.tdl.dto.ChatEventProfileAccentColorChanged
import dev.g000sha256.tdl.dto.ChatEventShowMessageSenderToggled
import dev.g000sha256.tdl.dto.ChatEventSignMessagesToggled
import dev.g000sha256.tdl.dto.ChatEventSlowModeDelayChanged
import dev.g000sha256.tdl.dto.ChatEventStickerSetChanged
import dev.g000sha256.tdl.dto.ChatEventTitleChanged
import dev.g000sha256.tdl.dto.ChatEventUsernameChanged
import dev.g000sha256.tdl.dto.ChatEventVideoChatCreated
import dev.g000sha256.tdl.dto.ChatEventVideoChatEnded
import dev.g000sha256.tdl.dto.ChatEventVideoChatMuteNewParticipantsToggled
import dev.g000sha256.tdl.dto.ChatEventVideoChatParticipantIsMutedToggled
import dev.g000sha256.tdl.dto.ChatEventVideoChatParticipantVolumeLevelChanged
import dev.g000sha256.tdl.dto.ChatMemberStatus
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatMemberStatusBanned
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.ChatMemberStatusRestricted
import dev.g000sha256.tdl.dto.MessageSender
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.durationUiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.event_admin_dismissed
import eu.tintera.plume.resources.event_admin_made
import eu.tintera.plume.resources.event_admin_rights
import eu.tintera.plume.resources.event_antispam
import eu.tintera.plume.resources.event_appearance
import eu.tintera.plume.resources.event_auto_delete
import eu.tintera.plume.resources.event_banned
import eu.tintera.plume.resources.event_closed
import eu.tintera.plume.resources.event_description
import eu.tintera.plume.resources.event_emoji_set
import eu.tintera.plume.resources.event_forum
import eu.tintera.plume.resources.event_hidden
import eu.tintera.plume.resources.event_history
import eu.tintera.plume.resources.event_invite_deleted
import eu.tintera.plume.resources.event_invite_edited
import eu.tintera.plume.resources.event_invite_revoked
import eu.tintera.plume.resources.event_invites
import eu.tintera.plume.resources.event_joined
import eu.tintera.plume.resources.event_joined_link
import eu.tintera.plume.resources.event_joined_request
import eu.tintera.plume.resources.event_left
import eu.tintera.plume.resources.event_linked_chat
import eu.tintera.plume.resources.event_location
import eu.tintera.plume.resources.event_member_added
import eu.tintera.plume.resources.event_member_tag
import eu.tintera.plume.resources.event_message_deleted
import eu.tintera.plume.resources.event_message_edited
import eu.tintera.plume.resources.event_message_pinned
import eu.tintera.plume.resources.event_message_unpinned
import eu.tintera.plume.resources.event_off
import eu.tintera.plume.resources.event_on
import eu.tintera.plume.resources.event_open
import eu.tintera.plume.resources.event_owner
import eu.tintera.plume.resources.event_permissions
import eu.tintera.plume.resources.event_photo
import eu.tintera.plume.resources.event_photo_removed
import eu.tintera.plume.resources.event_poll_stopped
import eu.tintera.plume.resources.event_protected
import eu.tintera.plume.resources.event_reactions
import eu.tintera.plume.resources.event_restricted
import eu.tintera.plume.resources.event_show_sender
import eu.tintera.plume.resources.event_sign
import eu.tintera.plume.resources.event_slow_mode
import eu.tintera.plume.resources.event_sticker_set
import eu.tintera.plume.resources.event_subscription
import eu.tintera.plume.resources.event_title
import eu.tintera.plume.resources.event_topic_closed
import eu.tintera.plume.resources.event_topic_created
import eu.tintera.plume.resources.event_topic_deleted
import eu.tintera.plume.resources.event_topic_edited
import eu.tintera.plume.resources.event_topic_hidden
import eu.tintera.plume.resources.event_topic_pinned
import eu.tintera.plume.resources.event_topic_unpinned
import eu.tintera.plume.resources.event_translation
import eu.tintera.plume.resources.event_unbanned
import eu.tintera.plume.resources.event_unrestricted
import eu.tintera.plume.resources.event_username
import eu.tintera.plume.resources.event_username_removed
import eu.tintera.plume.resources.event_usernames
import eu.tintera.plume.resources.event_video_chat_ended
import eu.tintera.plume.resources.event_video_chat_mute_new
import eu.tintera.plume.resources.event_video_chat_muted
import eu.tintera.plume.resources.event_video_chat_started
import eu.tintera.plume.resources.event_video_chat_unmuted
import eu.tintera.plume.resources.event_video_chat_volume

/**
 * Záznam posledních akcí ve skupině nebo kanálu (`getChatEventLog`): kdo co udělal za posledních
 * 48 hodin. Vidí ho jen správci.
 *
 * **Popis je podstatné jméno, ne věta se slovesem** („Nový název: …", „Vyhození: Jan"). Čeština
 * by u slovesa potřebovala rod toho, kdo akci udělal („změnil" / „změnila"), a ten Telegram
 * neposílá — hádat ho ze jména nejde. Kdo to byl, říká řádek nad popisem.
 */
data class ChatLogEvent(
    val id: Long,
    /** Unix čas. */
    val date: Int,
    val actor: SenderInfo,
    val description: UiText,
    /** Co k tomu patří: text zprávy, nový popis, odkaz; `null` = nic. */
    val detail: UiText? = null,
    /**
     * Zprávu smazal agresivní antispam a jde nahlásit jako omyl
     * ([ProfileRepository.reportAntiSpamFalsePositive]): id smazané zprávy, jinak `null`.
     */
    val antiSpamMessageId: Long? = null,
)

/**
 * Omyl antispamu u události [eventId] je nahlášený: druhé nahlášení téže zprávy by nic
 * nepřidalo, tlačítko z řádku zmizí. Ostatní události zůstávají, jak byly.
 */
internal fun List<ChatLogEvent>.withAntiSpamReported(eventId: Long): List<ChatLogEvent> =
    map { if (it.id == eventId) it.copy(antiSpamMessageId = null) else it }

/** Stránka záznamu; [nextFromEventId] `null` = konec. */
data class ChatLogPage(val events: List<ChatLogEvent>, val nextFromEventId: Long?)

/** Kolik událostí na stránku — víc TDLib nevydá. */
const val CHAT_LOG_PAGE = 100

/**
 * Druh akcí v záznamu — čip filtru. Hrubší než `ChatEventLogFilters` (15 příznaků): uživatel
 * hledá „co se dělo se zprávami", ne rozdíl mezi úpravou a připnutím. Rozdělení odpovídá
 * filtrům oficiálních klientů, jen informace a nastavení skupiny jsou jeden čip.
 */
enum class ChatLogCategory {
    /** Úpravy, smazání, připnutí a odepnutí zpráv. */
    Messages,

    /** Příchody, odchody a pozvání. */
    Members,

    /** Povýšení, sesazení a úprava práv správců, štítky členů. */
    Admins,

    /** Omezení, vyhození a jejich zrušení. */
    Restrictions,

    /** Název, fotka, popis, odkaz i nastavení skupiny. */
    Settings,

    /** Pozvánky a prodloužení předplatného přes ně. */
    InviteLinks,

    VideoChats,

    Topics,
}

/**
 * Co ze záznamu ukázat: [query] hledá v textu událostí (TDLib `query`), [categories] zužuje druh,
 * [userIds] autory (správce). Prázdné = bez omezení.
 */
data class ChatLogFilter(
    val query: String = "",
    val categories: Set<ChatLogCategory> = emptySet(),
    val userIds: Set<Long> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank() || categories.isNotEmpty() || userIds.isNotEmpty()
}

/**
 * Příznaky pro `getChatEventLog`; `null` = všechny druhy. Prázdná množina NESMÍ jít jako objekt
 * se samými `false` — server by nevrátil nic.
 */
internal fun Set<ChatLogCategory>.toTdlFilters(): ChatEventLogFilters? {
    if (isEmpty()) return null
    val messages = ChatLogCategory.Messages in this
    val members = ChatLogCategory.Members in this
    val admins = ChatLogCategory.Admins in this
    val links = ChatLogCategory.InviteLinks in this
    return ChatEventLogFilters(
        messageEdits = messages,
        messageDeletions = messages,
        messagePins = messages,
        memberJoins = members,
        memberLeaves = members,
        memberInvites = members,
        memberPromotions = admins,
        memberRestrictions = ChatLogCategory.Restrictions in this,
        memberTagChanges = admins,
        infoChanges = ChatLogCategory.Settings in this,
        settingChanges = ChatLogCategory.Settings in this,
        inviteLinkChanges = links,
        videoChatChanges = ChatLogCategory.VideoChats in this,
        forumChanges = ChatLogCategory.Topics in this,
        subscriptionExtensions = links,
    )
}

/** Popis události: co se stalo a případně podrobnost. */
internal data class EventText(val description: UiText, val detail: UiText? = null)

/**
 * Událost slovy. [name] dá jméno člověka nebo chatu podle id (u změn členů, ztlumení ve
 * videochatu). Co aplikace neumí popsat podrobně, spadne na obecnou větu svého druhu — nová
 * událost v novější TDLib rozbije překlad (`when` bez `else`), neprojde tiše.
 */
internal suspend fun ChatEventAction.toEventText(name: suspend (MessageSender) -> String): EventText = when (this) {
    is ChatEventMessageEdited -> EventText(UiText(Res.string.event_message_edited), newMessage.previewText())
    is ChatEventMessageDeleted -> EventText(UiText(Res.string.event_message_deleted), message.previewText())
    is ChatEventMessagePinned -> EventText(UiText(Res.string.event_message_pinned), message.previewText())
    is ChatEventMessageUnpinned -> EventText(UiText(Res.string.event_message_unpinned), message.previewText())
    is ChatEventPollStopped -> EventText(UiText(Res.string.event_poll_stopped), message.previewText())
    is ChatEventMemberJoined -> EventText(UiText(Res.string.event_joined))
    is ChatEventMemberJoinedByInviteLink -> EventText(UiText(Res.string.event_joined_link), UiText.Raw(inviteLink.inviteLink))
    is ChatEventMemberJoinedByRequest -> EventText(UiText(Res.string.event_joined_request))
    is ChatEventMemberLeft -> EventText(UiText(Res.string.event_left))
    is ChatEventMemberInvited -> EventText(UiText(Res.string.event_member_added, name(userSender(userId))))
    is ChatEventMemberPromoted -> EventText(promotion(oldStatus, newStatus, name(userSender(userId))))
    is ChatEventMemberRestricted -> EventText(restriction(oldStatus, newStatus, name(memberId)))
    is ChatEventMemberSubscriptionExtended -> EventText(UiText(Res.string.event_subscription, name(userSender(userId))))
    is ChatEventMemberTagChanged -> EventText(UiText(Res.string.event_member_tag, name(userSender(userId))), newTag.takeIf { it.isNotBlank() }?.let(UiText::Raw))
    is ChatEventTitleChanged -> EventText(UiText(Res.string.event_title, newTitle))
    is ChatEventDescriptionChanged -> EventText(UiText(Res.string.event_description), newDescription.takeIf { it.isNotBlank() }?.let(UiText::Raw))
    is ChatEventUsernameChanged -> if (newUsername.isBlank()) {
        EventText(UiText(Res.string.event_username_removed))
    } else {
        EventText(UiText(Res.string.event_username, "@$newUsername"))
    }
    is ChatEventActiveUsernamesChanged -> EventText(UiText(Res.string.event_usernames), UiText.Raw(newUsernames.joinToString { "@$it" }))
    is ChatEventPhotoChanged -> EventText(UiText(if (newPhoto == null) Res.string.event_photo_removed else Res.string.event_photo))
    is ChatEventPermissionsChanged -> EventText(UiText(Res.string.event_permissions))
    is ChatEventSlowModeDelayChanged -> EventText(
        UiText(Res.string.event_slow_mode, if (newSlowModeDelay == 0) offText() else durationUiText(newSlowModeDelay)),
    )
    is ChatEventMessageAutoDeleteTimeChanged -> EventText(
        UiText(Res.string.event_auto_delete, if (newMessageAutoDeleteTime == 0) offText() else durationUiText(newMessageAutoDeleteTime)),
    )
    is ChatEventAvailableReactionsChanged -> EventText(UiText(Res.string.event_reactions))
    is ChatEventInvitesToggled -> EventText(UiText(Res.string.event_invites, onOff(canInviteUsers)))
    is ChatEventIsAllHistoryAvailableToggled -> EventText(UiText(Res.string.event_history, onOff(isAllHistoryAvailable)))
    is ChatEventHasProtectedContentToggled -> EventText(UiText(Res.string.event_protected, onOff(hasProtectedContent)))
    is ChatEventSignMessagesToggled -> EventText(UiText(Res.string.event_sign, onOff(signMessages)))
    is ChatEventShowMessageSenderToggled -> EventText(UiText(Res.string.event_show_sender, onOff(showMessageSender)))
    is ChatEventHasAggressiveAntiSpamEnabledToggled -> EventText(UiText(Res.string.event_antispam, onOff(hasAggressiveAntiSpamEnabled)))
    is ChatEventAutomaticTranslationToggled -> EventText(UiText(Res.string.event_translation, onOff(hasAutomaticTranslation)))
    is ChatEventIsForumToggled -> EventText(UiText(Res.string.event_forum, onOff(isForum)))
    is ChatEventLinkedChatChanged -> EventText(UiText(Res.string.event_linked_chat))
    is ChatEventLocationChanged -> EventText(UiText(Res.string.event_location))
    is ChatEventStickerSetChanged -> EventText(UiText(Res.string.event_sticker_set))
    is ChatEventCustomEmojiStickerSetChanged -> EventText(UiText(Res.string.event_emoji_set))
    is ChatEventAccentColorChanged, is ChatEventProfileAccentColorChanged, is ChatEventBackgroundChanged,
    is ChatEventEmojiStatusChanged,
    -> EventText(UiText(Res.string.event_appearance))
    is ChatEventInviteLinkEdited -> EventText(UiText(Res.string.event_invite_edited), UiText.Raw(newInviteLink.inviteLink))
    is ChatEventInviteLinkRevoked -> EventText(UiText(Res.string.event_invite_revoked), UiText.Raw(inviteLink.inviteLink))
    is ChatEventInviteLinkDeleted -> EventText(UiText(Res.string.event_invite_deleted), UiText.Raw(inviteLink.inviteLink))
    is ChatEventForumTopicCreated -> EventText(UiText(Res.string.event_topic_created, topicInfo.name))
    is ChatEventForumTopicEdited -> EventText(UiText(Res.string.event_topic_edited, newTopicInfo.name))
    is ChatEventForumTopicDeleted -> EventText(UiText(Res.string.event_topic_deleted, topicInfo.name))
    is ChatEventForumTopicToggleIsClosed -> EventText(
        UiText(Res.string.event_topic_closed, topicInfo.name, UiText(if (topicInfo.isClosed) Res.string.event_closed else Res.string.event_open)),
    )
    is ChatEventForumTopicToggleIsHidden -> EventText(
        UiText(Res.string.event_topic_hidden, topicInfo.name, UiText(if (topicInfo.isHidden) Res.string.event_hidden else Res.string.event_open)),
    )
    is ChatEventForumTopicPinned -> newTopicInfo?.let { EventText(UiText(Res.string.event_topic_pinned, it.name)) }
        ?: EventText(UiText(Res.string.event_topic_unpinned, oldTopicInfo?.name.orEmpty()))
    is ChatEventVideoChatCreated -> EventText(UiText(Res.string.event_video_chat_started))
    is ChatEventVideoChatEnded -> EventText(UiText(Res.string.event_video_chat_ended))
    is ChatEventVideoChatMuteNewParticipantsToggled -> EventText(UiText(Res.string.event_video_chat_mute_new, onOff(muteNewParticipants)))
    is ChatEventVideoChatParticipantIsMutedToggled -> EventText(
        UiText(if (isMuted) Res.string.event_video_chat_muted else Res.string.event_video_chat_unmuted, name(participantId)),
    )
    is ChatEventVideoChatParticipantVolumeLevelChanged -> EventText(UiText(Res.string.event_video_chat_volume, name(participantId)))
}

/** Povýšení, sesazení, úprava práv, nebo předání vlastnictví — podle starého a nového postavení. */
private fun promotion(old: ChatMemberStatus, new: ChatMemberStatus, who: String): UiText = when {
    new is ChatMemberStatusCreator -> UiText(Res.string.event_owner, who)
    new is ChatMemberStatusAdministrator && old is ChatMemberStatusAdministrator -> UiText(Res.string.event_admin_rights, who)
    new is ChatMemberStatusAdministrator -> UiText(Res.string.event_admin_made, who)
    else -> UiText(Res.string.event_admin_dismissed, who)
}

/** Vyhození, omezení a jejich zrušení — podle starého a nového postavení. */
private fun restriction(old: ChatMemberStatus, new: ChatMemberStatus, who: String): UiText = when {
    new is ChatMemberStatusBanned -> UiText(Res.string.event_banned, who)
    new is ChatMemberStatusRestricted -> UiText(Res.string.event_restricted, who)
    old is ChatMemberStatusBanned -> UiText(Res.string.event_unbanned, who)
    else -> UiText(Res.string.event_unrestricted, who)
}

/** „zapnuto" / „vypnuto" — příslovce, takže sedí k čemukoli bez ohledu na rod. */
private fun onOff(on: Boolean): UiText = UiText(if (on) Res.string.event_on else Res.string.event_off)

private fun offText(): UiText = UiText(Res.string.event_off)

private fun userSender(userId: Long): MessageSender = dev.g000sha256.tdl.dto.MessageSenderUser(userId = userId)
