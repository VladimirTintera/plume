package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatFolderDraftTest {

    @Test
    fun vybraneChatyDrziPripnuteAVyrazujiVynechane() {
        val draft = ChatFolderDraft(pinnedChatIds = listOf(1, 2), includedChatIds = listOf(3), excludedChatIds = listOf(4, 5))
        val result = draft.withChosen(listOf(2, 3, 4))
        assertEquals(listOf(2L), result.pinnedChatIds)
        assertEquals(listOf(3L, 4L), result.includedChatIds)
        // Vybraný chat nemůže být zároveň vynechaný.
        assertEquals(listOf(5L), result.excludedChatIds)
    }

    @Test
    fun vynechanyChatZmiziZVybranych() {
        val draft = ChatFolderDraft(pinnedChatIds = listOf(1), includedChatIds = listOf(2, 3))
        val result = draft.withExcluded(listOf(1, 3))
        assertEquals(emptyList(), result.pinnedChatIds)
        assertEquals(listOf(2L), result.includedChatIds)
        assertEquals(listOf(1L, 3L), result.excludedChatIds)
    }

    @Test
    fun odebraniChatuDrzenehoDruhemHoVynecha() {
        val draft = ChatFolderDraft(includedChatIds = listOf(1), includeGroups = true)
        assertEquals(listOf(1L), draft.withoutChat(1, coveredByKind = true).excludedChatIds)
        assertEquals(emptyList(), draft.withoutChat(1, coveredByKind = false).excludedChatIds)
        assertEquals(emptyList(), draft.withoutChat(1, coveredByKind = true).includedChatIds)
    }

    @Test
    fun opakovaneOdebraniNezdvojiVynechani() {
        val draft = ChatFolderDraft(excludedChatIds = listOf(1), includeGroups = true)
        assertEquals(listOf(1L), draft.withoutChat(1, coveredByKind = true).excludedChatIds)
    }

    @Test
    fun novyNazevZahodiVlastniEmoji() {
        val emoji = listOf(MessageEntity(0, 2, MessageEntityType.CustomEmoji(5)))
        val draft = ChatFolderDraft(title = "Práce", titleEntities = emoji)
        assertEquals(emoji, draft.withTitle("Práce").titleEntities)
        assertEquals(emptyList(), draft.withTitle("Prace").titleEntities)
    }

    @Test
    fun problemy() {
        assertEquals(ChatFolderProblem.MissingTitle, ChatFolderDraft(title = "  ", includeBots = true).problem())
        assertEquals(ChatFolderProblem.TitleTooLong, ChatFolderDraft(title = "a".repeat(13), includeBots = true).problem())
        assertEquals(ChatFolderProblem.NoChats, ChatFolderDraft(title = "Boti").problem())
        assertNull(ChatFolderDraft(title = " ${"a".repeat(12)} ", includeBots = true).problem())
        assertNull(ChatFolderDraft(title = "Jeden", includedChatIds = listOf(1)).problem())
    }
}
