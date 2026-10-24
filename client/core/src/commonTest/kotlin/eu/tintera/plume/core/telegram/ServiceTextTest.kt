package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageChatDeletePhoto
import dev.g000sha256.tdl.dto.MessageChatOwnerChanged
import dev.g000sha256.tdl.dto.MessageChatOwnerLeft
import dev.g000sha256.tdl.dto.MessageChatSetMessageAutoDeleteTime
import dev.g000sha256.tdl.dto.MessageChatSetTheme
import dev.g000sha256.tdl.dto.MessageVideoChatEnded
import dev.g000sha256.tdl.dto.MessageVideoChatStarted
import dev.g000sha256.tdl.dto.MessageChatUpgradeFrom
import dev.g000sha256.tdl.dto.MessageChatUpgradeTo
import dev.g000sha256.tdl.dto.MessageCustomServiceAction
import dev.g000sha256.tdl.dto.MessageExpiredPhoto
import dev.g000sha256.tdl.dto.MessageExpiredVoiceNote
import dev.g000sha256.tdl.dto.MessageSupergroupChatCreate
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_delete_summary
import eu.tintera.plume.resources.content_expired_photo
import eu.tintera.plume.resources.content_expired_voice_note
import eu.tintera.plume.resources.duration_days
import eu.tintera.plume.resources.service_auto_delete_off
import eu.tintera.plume.resources.service_channel_photo_removed
import eu.tintera.plume.resources.service_group_created
import eu.tintera.plume.resources.service_live_stream_ended
import eu.tintera.plume.resources.service_live_stream_started
import eu.tintera.plume.resources.service_owner_changed
import eu.tintera.plume.resources.service_owner_left
import eu.tintera.plume.resources.service_owner_left_successor
import eu.tintera.plume.resources.service_photo_removed
import eu.tintera.plume.resources.service_theme_reset
import eu.tintera.plume.resources.service_video_chat_started
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Servisní věta je ODKAZ do prostředků, ne hotový řetězec: převedená zpráva přežije změnu
 * jazyka a přeložit se musí až tam, kde se kreslí.
 */
class ServiceTextTest {

    @Test
    fun nazevJeArgumentVety() {
        assertEquals(
            UiText(Res.string.service_group_created, "Plume"),
            MessageSupergroupChatCreate(title = "Plume").serviceText(isChannelPost = false),
        )
    }

    @Test
    fun delkaMazaniJeVnorenyPlural() {
        assertEquals(
            UiText(Res.string.auto_delete_summary, UiText(Res.plurals.duration_days, 1, 1)),
            MessageChatSetMessageAutoDeleteTime(messageAutoDeleteTime = 86_400, fromUserId = 0L)
                .serviceText(isChannelPost = false),
        )
        assertEquals(
            UiText(Res.string.service_auto_delete_off),
            MessageChatSetMessageAutoDeleteTime(messageAutoDeleteTime = 0, fromUserId = 0L)
                .serviceText(isChannelPost = false),
        )
    }

    @Test
    fun vyprseleMediumJeOznameniNeBublina() {
        // Ne-null = kreslí se na střed jako oznámení; v bublině se četlo jako text od protistrany.
        assertEquals(UiText(Res.string.content_expired_photo), MessageExpiredPhoto().serviceText(isChannelPost = false))
        assertEquals(UiText(Res.string.content_expired_voice_note), MessageExpiredVoiceNote().serviceText(isChannelPost = false))
    }

    @Test
    fun textOdServeruSeNeprekladá() {
        assertEquals(UiText.Raw("Hotovo"), MessageCustomServiceAction(text = "Hotovo").serviceText(isChannelPost = false))
    }

    @Test
    fun prevodSkupinyVedeDoDruheSkupiny() {
        // Id chatu podle konvence TDLib: základní skupina záporné id, supergroupa −10¹² − id.
        assertEquals(
            GroupUpgradeLink(chatId = -42L, toNewGroup = false),
            MessageChatUpgradeFrom(title = "Plume", basicGroupId = 42L).upgradeLink(),
        )
        assertEquals(
            GroupUpgradeLink(chatId = -1_000_000_000_042L, toNewGroup = true),
            MessageChatUpgradeTo(supergroupId = 42L).upgradeLink(),
        )
        assertNull(MessageSupergroupChatCreate(title = "Plume").upgradeLink())
    }

    @Test
    fun odchodVlastnikaNeniNovyVlastnik() {
        // Nástupce chat převezme, jen když se vlastník nevrátí — a nemusí žádný být.
        assertEquals(UiText(Res.string.service_owner_left), MessageChatOwnerLeft(newOwnerUserId = 0L).serviceText(isChannelPost = false))
        assertEquals(
            UiText(Res.string.service_owner_left_successor),
            MessageChatOwnerLeft(newOwnerUserId = 7L).serviceText(isChannelPost = false),
        )
        assertEquals(UiText(Res.string.service_owner_changed), MessageChatOwnerChanged(newOwnerUserId = 7L).serviceText(isChannelPost = false))
    }

    @Test
    fun videochatKanaluJeZiveVysilani() {
        assertEquals(UiText(Res.string.service_live_stream_started), MessageVideoChatStarted(groupCallId = 1).serviceText(isChannelPost = true))
        assertEquals(UiText(Res.string.service_live_stream_ended), MessageVideoChatEnded(duration = 0).serviceText(isChannelPost = true))
        assertEquals(UiText(Res.string.service_video_chat_started), MessageVideoChatStarted(groupCallId = 1).serviceText(isChannelPost = false))
    }

    @Test
    fun fotkaKanaluNeniFotkaSkupiny() {
        assertEquals(UiText(Res.string.service_channel_photo_removed), MessageChatDeletePhoto().serviceText(isChannelPost = true))
        assertEquals(UiText(Res.string.service_photo_removed), MessageChatDeletePhoto().serviceText(isChannelPost = false))
    }

    @Test
    fun motivBezMotivuJeVraceni() {
        assertEquals(UiText(Res.string.service_theme_reset), MessageChatSetTheme(theme = null).serviceText(isChannelPost = false))
    }
}
