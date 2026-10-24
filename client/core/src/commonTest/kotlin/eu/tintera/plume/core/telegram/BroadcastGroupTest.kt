package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kdy nabídnout převod na gigaskupinu ([offersBroadcastGroup]). */
class BroadcastGroupTest {

    private val group = EditableChat(
        chatId = -100123,
        title = "Velká skupina",
        description = "",
        photoFileId = null,
        photoThumbnailFileId = null,
        isChannel = false,
        rights = ChatEditRights(canChangeInfo = true),
        permissions = GroupPermissions(
            sendMessages = true, sendPhotos = true, sendVideos = true, sendDocuments = true,
            sendVoiceNotes = true, sendVideoNotes = true, sendStickers = true, sendPolls = true,
            react = true, inviteUsers = true, pinMessages = false, changeInfo = false,
        ),
        supergroupId = 123,
        isOwner = true,
    )

    @Test
    fun vlastnikovaDoporucenaSkupinaSeNabidne() {
        assertTrue(offersBroadcastGroup(group, suggested = setOf(123)))
    }

    /** Bez doporučení od Telegramu by převod skončil chybou — server ho dovolí až u velké skupiny. */
    @Test
    fun bezDoporuceniSeNenabizi() {
        assertFalse(offersBroadcastGroup(group, suggested = emptySet()))
        assertFalse(offersBroadcastGroup(group, suggested = setOf(999)))
    }

    @Test
    fun spravceKteryNeniVlastnikSeNenabizi() {
        assertFalse(offersBroadcastGroup(group.copy(isOwner = false), suggested = setOf(123)))
    }

    @Test
    fun kanalNeboUzPrevedenaSeNenabizi() {
        assertFalse(offersBroadcastGroup(group.copy(isChannel = true), suggested = setOf(123)))
        assertFalse(offersBroadcastGroup(group.copy(isBroadcastGroup = true), suggested = setOf(123)))
    }

    /** Základní skupina nemá id supergroupy — převádí se až po převodu na supergroupu. */
    @Test
    fun zakladniSkupinaSeNenabizi() {
        assertFalse(offersBroadcastGroup(group.copy(supergroupId = null, isBasicGroup = true), suggested = setOf(123)))
    }
}
