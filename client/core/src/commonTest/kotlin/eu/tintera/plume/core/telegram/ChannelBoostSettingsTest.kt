package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ProfileTabGifs
import dev.g000sha256.tdl.dto.ProfileTabPosts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Správa kanálu za boosty: kdo smí nastavit boosty pro obejití omezení, reklamy a hlavní
 * záložku profilu (`groupSettingsOffer`), a převod záložky do TDLib a zpět.
 */
class ChannelBoostSettingsTest {

    private val owner = OwnMemberStatus.Creator(isMember = true)

    private fun offer(kind: MemberGroupKind, own: OwnMemberStatus?) =
        groupSettingsOffer(kind, own, isPublic = false, hasLinkedChat = false, joinToSendMessages = false, canHideMembers = false, canToggleAntiSpam = false)

    @Test
    fun boostyProObejitiOmezeniJenSupergroupaSPravemOmezovat() {
        assertTrue(offer(MemberGroupKind.Supergroup, owner).unrestrictBoosts)
        assertTrue(offer(MemberGroupKind.Supergroup, OwnMemberStatus.Admin(canPostMessages = false, canRestrictMembers = true)).unrestrictBoosts)
        assertFalse(offer(MemberGroupKind.Supergroup, OwnMemberStatus.Admin(canPostMessages = false, canChangeInfo = true)).unrestrictBoosts)
        assertFalse(offer(MemberGroupKind.Channel, owner).unrestrictBoosts)
        assertFalse(offer(MemberGroupKind.BasicGroup, owner).unrestrictBoosts)
    }

    @Test
    fun reklamyJenVlastnikKanalu() {
        assertTrue(offer(MemberGroupKind.Channel, owner).sponsoredMessages)
        assertFalse(offer(MemberGroupKind.Channel, OwnMemberStatus.Admin(canPostMessages = true, canChangeInfo = true)).sponsoredMessages)
        assertFalse(offer(MemberGroupKind.Supergroup, owner).sponsoredMessages)
    }

    @Test
    fun hlavniZalozkaKanalSPravemMenitUdaje() {
        assertTrue(offer(MemberGroupKind.Channel, OwnMemberStatus.Admin(canPostMessages = true, canChangeInfo = true)).mainTab)
        assertFalse(offer(MemberGroupKind.Channel, OwnMemberStatus.Admin(canPostMessages = true)).mainTab)
        assertFalse(offer(MemberGroupKind.Supergroup, owner).mainTab)
    }

    @Test
    fun reklamyVypnoutAzOdUrovne() {
        assertFalse(SponsoredMessagesSetting(enabled = true, level = 2, minLevel = 50).canDisable)
        assertTrue(SponsoredMessagesSetting(enabled = true, level = 50, minLevel = 50).canDisable)
        // Neznámá mez (server neodpověděl) nesmí vypnutí pustit.
        assertFalse(SponsoredMessagesSetting(enabled = true, level = 0, minLevel = Int.MAX_VALUE).canDisable)
    }

    @Test
    fun zalozkaTamAZpet() {
        ProfileMainTab.entries.forEach { tab -> assertEquals(tab, tab.toTdl().toDomain()) }
        assertIs<ProfileTabPosts>(ProfileMainTab.Posts.toTdl())
        assertIs<ProfileTabGifs>(ProfileMainTab.Gifs.toTdl())
    }

    @Test
    fun pocetBoostuJeNulaAzOsm() {
        assertEquals((0..8).toList(), UNRESTRICT_BOOST_COUNTS)
    }
}
