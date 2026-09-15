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
internal fun MessageContent.serviceText(): String? = when (this) {
    is MessageChatAddMembers -> "Do skupiny přibyli členové"
    is MessageChatJoinByLink -> "Někdo se připojil přes odkaz"
    is MessageChatJoinByRequest -> "Žádost o vstup byla schválena"
    is MessageChatDeleteMember -> "Ze skupiny někdo odešel"

    is MessageChatChangeTitle -> "Název změněn na „$title\""
    is MessageChatChangePhoto -> "Změněna fotka skupiny"
    is MessageChatDeletePhoto -> "Fotka skupiny odebrána"
    is MessageChatSetTheme -> "Změněn motiv chatu"
    is MessageChatSetBackground -> "Změněno pozadí chatu"

    is MessageChatSetMessageAutoDeleteTime -> when (messageAutoDeleteTime) {
        0 -> "Automatické mazání zpráv vypnuto"
        else -> "Zprávy se budou samy mazat"
    }

    is MessageBasicGroupChatCreate -> "Skupina „$title\" vytvořena"
    is MessageSupergroupChatCreate -> "Kanál „$title\" vytvořen"
    // Převod na supergrupu se uživateli jeví jako jedna a tatáž skupina, takže nemá cenu
    // vysvětlovat, co se změnilo pod kapotou.
    is MessageChatUpgradeTo -> "Skupina byla převedena"
    is MessageChatUpgradeFrom -> "Skupina byla převedena"

    is MessagePinMessage -> "Připnuta zpráva"
    is MessageScreenshotTaken -> "Pořízen snímek obrazovky"
    is MessageContactRegistered -> "Kontakt se připojil k Telegramu"
    is MessageProximityAlertTriggered -> "Někdo je poblíž"

    is MessageVideoChatStarted -> "Videohovor začal"
    is MessageVideoChatEnded -> "Videohovor skončil"
    is MessageVideoChatScheduled -> "Videohovor naplánován"

    is MessageForumTopicCreated -> "Téma „$name\" vytvořeno"
    is MessageForumTopicEdited -> "Téma upraveno"

    is MessageGameScore -> "Nové skóre ve hře"

    // Tenhle typ nese hotový text od serveru; překládat ho není co.
    is MessageCustomServiceAction -> text

    // Kontakt NENÍ servisní zpráva — jen vypadá podobně. Popisuje ho previewText.
    is MessageContact -> null

    else -> null
}
