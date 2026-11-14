package eu.tintera.plume.features.links

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.telegram.FolderInvitePreview
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.InvitePreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Odkaz zvenčí s víc účty: na který se ptát, kterým účtem, a jak nese zvolený účet. */
class LinkAccountTest {

    /** Pozvánka, sdílená složka a mini aplikace jednají za účet — u nich se ptá. */
    @Test
    fun ptaSeUOdkazuKteryJednaZaUcet() {
        assertTrue(InternalLink.Invite("https://t.me/+abc", InvitePreview("Skupina", "", 3, isChannel = false, avatarFileId = null, createsJoinRequest = false)).actsAsAccount())
        assertTrue(InternalLink.FolderInvite("https://t.me/addlist/abc", FolderInvitePreview("Složka", emptyList(), null, 0, emptyList(), 0)).actsAsAccount())
        assertTrue(InternalLink.WebApp(botUserId = 1, shortName = "app", startParameter = "").actsAsAccount())
        assertTrue(InternalLink.MainWebApp(botUserId = 1, startParameter = "").actsAsAccount())
    }

    /** Chat, zpráva, příběh: otevře je výchozí účet bez ptaní. */
    @Test
    fun ostatniOtevreVychoziUcet() {
        assertFalse(InternalLink.Message(chatId = 1, messageId = 2).actsAsAccount())
        assertFalse(InternalLink.Story(posterChatId = 1, storyId = 2).actsAsAccount())
        assertFalse(InternalLink.StickerSet(setId = 1).actsAsAccount())
    }

    @Test
    fun pozadavekNeseUcet() {
        val requests = LinkOpenRequests()
        requests.request("tg://resolve?domain=a")
        assertNull(requests.request.value?.slot)
        requests.request("tg://resolve?domain=a", AccountSlot("abc"))
        assertEquals(AccountSlot("abc"), requests.request.value?.slot)
    }
}
