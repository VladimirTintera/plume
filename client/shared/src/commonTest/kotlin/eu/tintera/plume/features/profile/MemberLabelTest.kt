package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.MemberRole
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.telegram.SenderInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberLabelTest {
    private fun member(role: MemberRole, tag: String? = null, isBot: Boolean = false) =
        ProfileMember(SenderInfo(id = 1, title = "Jana", avatarFileId = null), role, tag, isBot)

    @Test
    fun vlastnikASpravceMajiStitekVzdy() {
        assertTrue(member(MemberRole.Owner).hasLabel)
        assertTrue(member(MemberRole.Admin).hasLabel)
    }

    @Test
    fun beznyClenJenSVlastnimStitkem() {
        assertFalse(member(MemberRole.Member).hasLabel)
        assertTrue(member(MemberRole.Member, tag = "Moderátor").hasLabel)
    }

    @Test
    fun botStitekNezaklada() {
        // Bot se ukazuje pod jménem; štítek na konci řádku patří roli.
        assertFalse(member(MemberRole.Member, isBot = true).hasLabel)
    }
}
