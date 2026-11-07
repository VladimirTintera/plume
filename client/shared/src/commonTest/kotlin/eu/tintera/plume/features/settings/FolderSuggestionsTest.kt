package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.FolderCategory
import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.NoopOnDeviceAi
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Návrh složek modelem ([FolderSuggestions], [groupSuggestions]). */
class FolderSuggestionsTest {

    private fun chat(id: Long, title: String, kind: ChatKind = ChatKind.Private) = ChatSummary(
        id = id,
        title = title,
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )

    private class Settings : UnimplementedAppSettings() {
        override val onDeviceAiEnabled: StateFlow<Boolean> = MutableStateFlow(true)
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(AiModels())
    }

    private class FakeAi(val answer: String, val status: OnDeviceAiStatus = OnDeviceAiStatus.Ready) : OnDeviceAi by NoopOnDeviceAi {
        val prompts = mutableListOf<String>()
        override suspend fun status(variant: ModelVariant) = status
        override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> {
            prompts += prompt
            return flowOf(Generated(answer))
        }
    }

    private val chats = listOf(chat(1, "Uložené zprávy"), chat(2, "Šéf"), chat(3, "Tým Plume", ChatKind.Group), chat(4, "Máma"), chat(5, "ČT24", ChatKind.Channel))

    private val title: suspend (FolderCategory) -> String = { it.name }

    /** Kategorie s jedním chatem se nenabízí; Uložené zprávy model vůbec nedostane. */
    @Test
    fun skupinyAzOdDvouChatu() = runTest {
        // Po vynechání Uložených: 1 = Šéf, 2 = Tým, 3 = Máma, 4 = ČT24.
        val ai = FakeAi("1: WORK\n2: WORK\n3: FAMILY\n4: NEWS")
        val result = FolderSuggestions(ai, Settings()).suggest(chats, excludedChatIds = setOf(1), existingTitles = emptySet(), titleOf = title)
        assertEquals(listOf(SuggestedFolder(FolderCategory.Work, listOf(2, 3), listOf("Šéf", "Tým Plume"))), result)
        assertTrue("Uložené zprávy" !in ai.prompts.single())
    }

    /** Složka stejného jména už je: nenabízí se znovu (bez ohledu na velikost písmen). */
    @Test
    fun existujiciSlozkaSeNenabizi() = runTest {
        val ai = FakeAi("1: WORK\n2: WORK")
        val result = FolderSuggestions(ai, Settings()).suggest(chats, emptySet(), existingTitles = setOf(" work "), titleOf = title)
        assertEquals(emptyList(), result)
    }

    @Test
    fun nepripravenyModelVratiNull() = runTest {
        val ai = FakeAi("", status = OnDeviceAiStatus.Downloadable)
        assertNull(FolderSuggestions(ai, Settings()).suggest(chats, emptySet(), emptySet(), title))
        assertEquals(emptyList(), ai.prompts)
    }

    @Test
    fun seskupeniVPoradiKategoriiAChatu() {
        val listed = listOf(chat(10, "A"), chat(11, "B"), chat(12, "C"), chat(13, "D"))
        val grouped = groupSuggestions(
            mapOf(0 to FolderCategory.Hobbies, 1 to FolderCategory.Work, 2 to FolderCategory.Hobbies, 3 to FolderCategory.Work),
            listed,
        )
        assertEquals(listOf(FolderCategory.Work, FolderCategory.Hobbies), grouped.map { it.category })
        assertEquals(listOf(11L, 13L), grouped.first().chatIds)
    }
}
