package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatLogFilterTest {

    /** Bez vybraného druhu jde `null` — objekt se samými `false` by ze serveru nevrátil nic. */
    @Test
    fun bezDruhuJdeNull() {
        assertNull(emptySet<ChatLogCategory>().toTdlFilters())
    }

    @Test
    fun zpravyZapnouUpravySmazaniIPripnuti() {
        val filters = setOf(ChatLogCategory.Messages).toTdlFilters()!!
        assertTrue(filters.messageEdits && filters.messageDeletions && filters.messagePins)
        assertFalse(filters.memberJoins || filters.memberRestrictions || filters.infoChanges || filters.forumChanges)
    }

    @Test
    fun nastaveniNeseInformaceINastaveni() {
        val filters = setOf(ChatLogCategory.Settings, ChatLogCategory.InviteLinks).toTdlFilters()!!
        assertTrue(filters.infoChanges && filters.settingChanges)
        assertTrue(filters.inviteLinkChanges && filters.subscriptionExtensions)
        assertFalse(filters.messageEdits || filters.memberPromotions || filters.videoChatChanges)
    }

    /** Každý druh něco zapne — čip, který by nic nefiltroval, by vracel prázdný záznam. */
    @Test
    fun kazdyDruhNecoZapne() {
        ChatLogCategory.entries.forEach { category ->
            val f = setOf(category).toTdlFilters()!!
            val any = listOf(
                f.messageEdits, f.messageDeletions, f.messagePins, f.memberJoins, f.memberLeaves,
                f.memberInvites, f.memberPromotions, f.memberRestrictions, f.memberTagChanges,
                f.infoChanges, f.settingChanges, f.inviteLinkChanges, f.videoChatChanges,
                f.forumChanges, f.subscriptionExtensions,
            ).any { it }
            assertTrue(any, "$category nic nezapne")
        }
    }

    @Test
    fun aktivniFiltr() {
        assertFalse(ChatLogFilter().isActive)
        assertFalse(ChatLogFilter(query = "  ").isActive)
        assertTrue(ChatLogFilter(query = "jan").isActive)
        assertTrue(ChatLogFilter(userIds = setOf(1L)).isActive)
        assertEquals(ChatLogFilter(), ChatLogFilter(query = "", categories = emptySet()))
    }
}
