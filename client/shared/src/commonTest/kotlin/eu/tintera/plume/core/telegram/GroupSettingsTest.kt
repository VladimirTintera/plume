package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupSettingsTest {

    private val owner = OwnMemberStatus.Creator(isMember = true)

    private fun offer(
        kind: MemberGroupKind = MemberGroupKind.Supergroup,
        own: OwnMemberStatus? = owner,
        isPublic: Boolean = false,
        hasLinkedChat: Boolean = false,
        joinToSendMessages: Boolean = false,
        canHideMembers: Boolean = false,
        canToggleAntiSpam: Boolean = false,
    ) = groupSettingsOffer(kind, own, isPublic, hasLinkedChat, joinToSendMessages, canHideMembers, canToggleAntiSpam)

    @Test
    fun vlastnikSoukromeSupergroupyNastaviHistoriiPomalyRezimReakceAOchranu() {
        val offer = offer()
        assertTrue(offer.slowMode)
        assertTrue(offer.reactions)
        assertEquals(setOf(GroupToggle.AllHistory, GroupToggle.ProtectedContent), offer.toggles)
    }

    /** Veřejná skupina historii má vždycky; zato schvalovat nové členy smysl dává. */
    @Test
    fun verejnaSkupinaMaSchvalovaniAleNeHistorii() {
        val toggles = offer(isPublic = true).toggles
        assertTrue(GroupToggle.JoinByRequest in toggles)
        assertFalse(GroupToggle.AllHistory in toggles)
    }

    @Test
    fun diskuseKanaluSchvalujeAzSPsanimJenProCleny() {
        assertEquals(
            setOf(GroupToggle.JoinToSend, GroupToggle.ProtectedContent),
            offer(hasLinkedChat = true).toggles,
        )
        assertTrue(GroupToggle.JoinByRequest in offer(hasLinkedChat = true, joinToSendMessages = true).toggles)
    }

    @Test
    fun skrytiClenuAAntispamRozhodujeServer() {
        val toggles = offer(canHideMembers = true, canToggleAntiSpam = true).toggles
        assertTrue(GroupToggle.HiddenMembers in toggles)
        assertTrue(GroupToggle.AggressiveAntiSpam in toggles)
    }

    @Test
    fun kanalMaPodpisyAleNePomalyRezimAniPrepinaceSkupiny() {
        val offer = offer(kind = MemberGroupKind.Channel, isPublic = true, canHideMembers = true)
        assertFalse(offer.slowMode)
        assertEquals(setOf(GroupToggle.SignMessages, GroupToggle.ShowMessageSender, GroupToggle.ProtectedContent), offer.toggles)
    }

    /** Základní skupina by se kvůli přepínačům supergroupy musela převést; reakce a ochrana jdou i tak. */
    @Test
    fun zakladniSkupinaMaJenReakceAOchranu() {
        val offer = offer(kind = MemberGroupKind.BasicGroup)
        assertFalse(offer.slowMode)
        assertTrue(offer.reactions)
        assertEquals(setOf(GroupToggle.ProtectedContent), offer.toggles)
    }

    @Test
    fun spravceDostaneJenToNaCoMaPrava() {
        val restrictOnly = OwnMemberStatus.Admin(canPostMessages = false, canRestrictMembers = true)
        val offer = offer(own = restrictOnly, isPublic = true)
        assertTrue(offer.slowMode)
        assertFalse(offer.reactions)
        // Ochrana obsahu je jen pro vlastníka.
        assertEquals(setOf(GroupToggle.JoinByRequest), offer.toggles)

        val infoOnly = OwnMemberStatus.Admin(canPostMessages = false, canChangeInfo = true)
        val info = offer(own = infoOnly)
        assertFalse(info.slowMode)
        assertTrue(info.reactions)
        assertEquals(setOf(GroupToggle.AllHistory), info.toggles)
    }

    @Test
    fun beznyClenAniVlastnikMimoSkupinuNenastaviNic() {
        assertEquals(GroupSettingsOffer(), offer(own = OwnMemberStatus.Member, canToggleAntiSpam = true))
        assertEquals(GroupSettingsOffer(), offer(own = OwnMemberStatus.Creator(isMember = false)))
        assertEquals(GroupSettingsOffer(), offer(own = null))
    }
}
