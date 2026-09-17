package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageBasicGroupChatCreate
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
import dev.g000sha256.tdl.dto.MessageGameScore
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.MessageProximityAlertTriggered
import dev.g000sha256.tdl.dto.MessageScreenshotTaken
import dev.g000sha256.tdl.dto.MessageSupergroupChatCreate
import dev.g000sha256.tdl.dto.MessageVideoChatEnded
import dev.g000sha256.tdl.dto.MessageVideoChatScheduled
import dev.g000sha256.tdl.dto.MessageVideoChatStarted
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_auto_delete_off
import eu.tintera.plume.resources.service_auto_delete_on
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
import org.jetbrains.compose.resources.getString

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
 */
internal suspend fun MessageContent.serviceText(): String? = when (this) {
    is MessageChatAddMembers -> getString(Res.string.service_members_added)
    is MessageChatJoinByLink -> getString(Res.string.service_joined_by_link)
    is MessageChatJoinByRequest -> getString(Res.string.service_join_approved)
    is MessageChatDeleteMember -> getString(Res.string.service_member_left)

    is MessageChatChangeTitle -> getString(Res.string.service_title_changed, title)
    is MessageChatChangePhoto -> getString(Res.string.service_photo_changed)
    is MessageChatDeletePhoto -> getString(Res.string.service_photo_removed)
    is MessageChatSetTheme -> getString(Res.string.service_theme_changed)
    is MessageChatSetBackground -> getString(Res.string.service_background_changed)

    is MessageChatSetMessageAutoDeleteTime -> when (messageAutoDeleteTime) {
        0 -> getString(Res.string.service_auto_delete_off)
        else -> getString(Res.string.service_auto_delete_on)
    }

    is MessageBasicGroupChatCreate -> getString(Res.string.service_group_created, title)
    is MessageSupergroupChatCreate -> getString(Res.string.service_channel_created, title)
    // Převod na supergrupu se uživateli jeví jako jedna a tatáž skupina, takže nemá cenu
    // vysvětlovat, co se změnilo pod kapotou.
    is MessageChatUpgradeTo -> getString(Res.string.service_group_upgraded)
    is MessageChatUpgradeFrom -> getString(Res.string.service_group_upgraded)

    is MessagePinMessage -> getString(Res.string.service_message_pinned)
    is MessageScreenshotTaken -> getString(Res.string.service_screenshot)
    is MessageContactRegistered -> getString(Res.string.service_contact_joined)
    is MessageProximityAlertTriggered -> getString(Res.string.service_nearby)

    is MessageVideoChatStarted -> getString(Res.string.service_video_chat_started)
    is MessageVideoChatEnded -> getString(Res.string.service_video_chat_ended)
    is MessageVideoChatScheduled -> getString(Res.string.service_video_chat_scheduled)

    is MessageForumTopicCreated -> getString(Res.string.service_topic_created, name)
    is MessageForumTopicEdited -> getString(Res.string.service_topic_edited)

    is MessageGameScore -> getString(Res.string.service_game_score)

    // Tenhle typ nese hotový text od serveru; překládat ho není co.
    is MessageCustomServiceAction -> text

    // Kontakt NENÍ servisní zpráva — jen vypadá podobně. Popisuje ho previewText.
    is MessageContact -> null

    else -> null
}
