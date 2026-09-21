package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class WriteAccessTest {

    @Test
    fun soukromyChatPiseVzdy() {
        assertEquals(WriteAccess.Allowed, writeAccess(isChannel = false, status = null, membersCanSend = false))
    }

    @Test
    fun vKanaluPiseJenVlastnikASpravceSPravem() {
        assertEquals(WriteAccess.Allowed, writeAccess(true, OwnMemberStatus.Creator(isMember = true), false))
        assertEquals(WriteAccess.Allowed, writeAccess(true, OwnMemberStatus.Admin(canPostMessages = true), false))
        assertEquals(WriteAccess.ChannelReadOnly, writeAccess(true, OwnMemberStatus.Admin(canPostMessages = false), false))
        assertEquals(WriteAccess.ChannelReadOnly, writeAccess(true, OwnMemberStatus.Member, true))
    }

    @Test
    fun veSkupineRozhodujiVychoziPrava() {
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Member, membersCanSend = true))
        assertEquals(WriteAccess.Restricted, writeAccess(false, OwnMemberStatus.Member, membersCanSend = false))
        // Správce smí psát i do skupiny, kde členové nesmějí.
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Admin(canPostMessages = false), false))
    }

    @Test
    fun omezenemuClenoviPlatiJehoVlastniPrava() {
        // Vlastní práva omezeného člena přebíjejí výchozí práva skupiny — oběma směry.
        assertEquals(WriteAccess.Restricted, writeAccess(false, OwnMemberStatus.Restricted(true, canSendMessages = false), true))
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Restricted(true, canSendMessages = true), false))
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Restricted(isMember = false, canSendMessages = true), true))
    }

    @Test
    fun kdoVChatuNeniNepise() {
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Left, true))
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Banned, true))
        assertEquals(WriteAccess.NotMember, writeAccess(true, OwnMemberStatus.Creator(isMember = false), true))
    }
}
