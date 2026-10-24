package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kdo smí do skupiny nebo kanálu přidávat lidi ([canInviteMembers]). */
class InviteMembersTest {

    @Test
    fun vlastnikVzdycky() {
        assertTrue(canInviteMembers(OwnMemberStatus.Creator(isMember = true), isChannel = true, membersCanInvite = false))
    }

    @Test
    fun spravceJenSPravemZvat() {
        val inviter = OwnMemberStatus.Admin(canPostMessages = true, canInviteUsers = true)
        val other = OwnMemberStatus.Admin(canPostMessages = true)
        assertTrue(canInviteMembers(inviter, isChannel = true, membersCanInvite = false))
        assertFalse(canInviteMembers(other, isChannel = false, membersCanInvite = true))
    }

    @Test
    fun clenSkupinyPodleVychozichPrav() {
        assertTrue(canInviteMembers(OwnMemberStatus.Member, isChannel = false, membersCanInvite = true))
        assertFalse(canInviteMembers(OwnMemberStatus.Member, isChannel = false, membersCanInvite = false))
    }

    @Test
    fun odberatelKanaluNikdy() {
        assertFalse(canInviteMembers(OwnMemberStatus.Member, isChannel = true, membersCanInvite = true))
    }

    @Test
    fun kdoVeSkupineNeniNikdy() {
        assertFalse(canInviteMembers(OwnMemberStatus.Left, isChannel = false, membersCanInvite = true))
        assertFalse(canInviteMembers(null, isChannel = false, membersCanInvite = true))
    }
}
