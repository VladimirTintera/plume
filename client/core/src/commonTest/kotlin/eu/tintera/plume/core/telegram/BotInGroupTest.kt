package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotInGroupTest {

    @Test
    fun vlastnikPridaBotaVzdyJakoClena() {
        assertTrue(canAddBot(OwnMemberStatus.Creator(isMember = true), membersCanInvite = false, asAdmin = false))
        assertTrue(canAddBot(OwnMemberStatus.Creator(isMember = true), membersCanInvite = false, asAdmin = true))
        assertFalse(canAddBot(OwnMemberStatus.Creator(isMember = false), membersCanInvite = true, asAdmin = false))
    }

    @Test
    fun spravceZveSPravemZvatAJmenujeSPravemJmenovat() {
        val inviter = OwnMemberStatus.Admin(canPostMessages = true, canInviteUsers = true)
        assertTrue(canAddBot(inviter, membersCanInvite = false, asAdmin = false))
        assertFalse(canAddBot(inviter, membersCanInvite = true, asAdmin = true))

        val promoter = OwnMemberStatus.Admin(canPostMessages = true, canPromoteMembers = true)
        assertTrue(canAddBot(promoter, membersCanInvite = false, asAdmin = true))
    }

    @Test
    fun clenJenKdyzSkupinaDovoliZvatANikdyJakoSpravce() {
        assertTrue(canAddBot(OwnMemberStatus.Member, membersCanInvite = true, asAdmin = false))
        assertFalse(canAddBot(OwnMemberStatus.Member, membersCanInvite = false, asAdmin = false))
        assertFalse(canAddBot(OwnMemberStatus.Member, membersCanInvite = true, asAdmin = true))
    }

    @Test
    fun omezenyClenPodleVlastnichPravVyhozenyNikdy() {
        val restricted = OwnMemberStatus.Restricted(
            isMember = true,
            canSendMessages = true,
            content = ContentAccess.All,
            canInviteUsers = true,
        )
        assertTrue(canAddBot(restricted, membersCanInvite = false, asAdmin = false))
        assertFalse(canAddBot(restricted.copy(canInviteUsers = false), membersCanInvite = true, asAdmin = false))
        assertFalse(canAddBot(OwnMemberStatus.Left, membersCanInvite = true, asAdmin = false))
        assertFalse(canAddBot(OwnMemberStatus.Banned, membersCanInvite = true, asAdmin = false))
        assertFalse(canAddBot(null, membersCanInvite = true, asAdmin = false))
    }
}
