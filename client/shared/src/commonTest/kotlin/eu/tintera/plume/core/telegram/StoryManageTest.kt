package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.StoryPrivacySettingsCloseFriends
import dev.g000sha256.tdl.dto.StoryPrivacySettingsContacts
import dev.g000sha256.tdl.dto.StoryPrivacySettingsEveryone
import dev.g000sha256.tdl.dto.StoryPrivacySettingsSelectedUsers
import kotlin.test.Test
import kotlin.test.assertEquals

/** Správa vlastního příběhu: připnuté v profilu a převod viditelnosti z / do TDLib. */
class StoryManageTest {

    @Test
    fun pripnutyJdeNaZacatekAStropSeDrzi() {
        assertEquals(listOf(5, 1, 2), pinnedAfter(listOf(1, 2), 5, pinned = true))
        assertEquals(listOf(5, 1, 2), pinnedAfter(listOf(1, 2, 3), 5, pinned = true, max = 3))
        // Znovu připnutý se přesune dopředu, nezdvojí se.
        assertEquals(listOf(2, 1, 3), pinnedAfter(listOf(1, 2, 3), 2, pinned = true))
    }

    @Test
    fun odepnutiHoJenVyjme() {
        assertEquals(listOf(1, 3), pinnedAfter(listOf(1, 2, 3), 2, pinned = false))
        assertEquals(listOf(1, 3), pinnedAfter(listOf(1, 3), 2, pinned = false))
    }

    @Test
    fun viditelnostTamIZpet() {
        assertEquals(StoryAudience.Everyone, StoryPrivacySettingsEveryone(exceptUserIds = longArrayOf(4)).toAudience())
        assertEquals(StoryAudience.Contacts, StoryPrivacySettingsContacts(exceptUserIds = longArrayOf()).toAudience())
        assertEquals(StoryAudience.CloseFriends, StoryPrivacySettingsCloseFriends().toAudience())
        assertEquals(StoryAudience.Selected(listOf(1, 2)), StoryPrivacySettingsSelectedUsers(userIds = longArrayOf(1, 2)).toAudience())

        val selected = StoryAudience.Selected(listOf(1, 2)).toPrivacySettings() as StoryPrivacySettingsSelectedUsers
        assertEquals(listOf(1L, 2L), selected.userIds.toList())
        // Výjimky Plume nenastavuje: „všichni" jsou bez výjimek.
        assertEquals(0, (StoryAudience.Everyone.toPrivacySettings() as StoryPrivacySettingsEveryone).exceptUserIds.size)
    }
}
