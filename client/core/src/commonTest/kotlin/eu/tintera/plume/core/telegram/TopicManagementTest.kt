package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicManagementTest {

    private fun topic(isGeneral: Boolean = false, isOwn: Boolean = false) = ForumTopic(
        chatId = -100,
        id = if (isGeneral) 1 else 7,
        name = "Téma",
        icon = TopicIcon(color = TOPIC_COLORS.first(), customEmojiId = null),
        isGeneral = isGeneral,
        isClosed = false,
        isPinned = false,
        order = 1,
        isOwn = isOwn,
    )

    @Test
    fun vlastnikSmiVsechno() {
        assertEquals(
            TopicRights(canCreate = true, canManage = true, canDeleteAny = true),
            topicRights(OwnMemberStatus.Creator(isMember = true), membersCanCreate = false),
        )
        // Vlastník, který ve skupině není, nesmí nic.
        assertEquals(TopicRights(), topicRights(OwnMemberStatus.Creator(isMember = false), membersCanCreate = true))
    }

    @Test
    fun spravceJenCoMaVPravech() {
        val temata = OwnMemberStatus.Admin(canPostMessages = true, canManageTopics = true)
        assertEquals(TopicRights(canCreate = true, canManage = true), topicRights(temata, membersCanCreate = false))

        // Bez práva na témata zakládá jako člen a mazat smí jen s právem mazat zprávy.
        val mazani = OwnMemberStatus.Admin(canPostMessages = true, canDeleteMessages = true)
        assertEquals(TopicRights(canCreate = true, canDeleteAny = true), topicRights(mazani, membersCanCreate = true))
        assertEquals(TopicRights(canDeleteAny = true), topicRights(mazani, membersCanCreate = false))
    }

    @Test
    fun clenJenZakladaAJenKdyzSkupinaDovoli() {
        assertEquals(TopicRights(canCreate = true), topicRights(OwnMemberStatus.Member, membersCanCreate = true))
        assertEquals(TopicRights(), topicRights(OwnMemberStatus.Member, membersCanCreate = false))
        // Neznámé postavení: nenabízet nic.
        assertEquals(TopicRights(), topicRights(null, membersCanCreate = true))
    }

    @Test
    fun omezenyClenZakladaPodleSvychPrav() {
        val smi = OwnMemberStatus.Restricted(isMember = true, canSendMessages = true, content = ContentAccess.All, canCreateTopics = true)
        // Výchozí právo skupiny pro omezeného neplatí — ani když dovoluje, ani když zakazuje.
        assertEquals(TopicRights(canCreate = true), topicRights(smi, membersCanCreate = false))
        assertEquals(TopicRights(), topicRights(smi.copy(canCreateTopics = false), membersCanCreate = true))
        // Omezený, který už ve skupině není, nesmí nic.
        assertEquals(TopicRights(), topicRights(smi.copy(isMember = false), membersCanCreate = true))
    }

    @Test
    fun vlastniTemaSmiAutorSpravovatBezPrav() {
        val actions = topicActions(topic(isOwn = true), TopicRights())
        assertTrue(actions.rename && actions.close && actions.delete)
        // Připnout smí jen správce — pořadí témat patří celé skupině.
        assertFalse(actions.pin)
    }

    @Test
    fun ciziTemaJenSPravy() {
        assertEquals(TopicActions(), topicActions(topic(), TopicRights(canCreate = true)))
        val spravce = topicActions(topic(), TopicRights(canManage = true))
        assertTrue(spravce.pin && spravce.close && spravce.rename)
        assertFalse(spravce.delete)
        assertTrue(topicActions(topic(), TopicRights(canDeleteAny = true)).delete)
    }

    @Test
    fun obecneTemaNejdeSmazatJenSkryt() {
        val vse = TopicRights(canCreate = true, canManage = true, canDeleteAny = true)
        val actions = topicActions(topic(isGeneral = true, isOwn = true), vse)
        assertFalse(actions.delete)
        assertTrue(actions.hideGeneral)
        // Skrýt ho smí jen správce, ani jeho „autor".
        assertFalse(topicActions(topic(isGeneral = true, isOwn = true), TopicRights()).hideGeneral)
    }
}
