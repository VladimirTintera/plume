package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.EmojiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Skrytá pilulka čeká na pauzu v psaní, ukázaná se mění hned a mezi písmeny nezmizí. */
@OptIn(ExperimentalCoroutinesApi::class)
class EmojiSuggestionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeEmojis : EmojiRepository {
        val asked = mutableListOf<String>()
        val answers = mutableMapOf("hear" to listOf("👂"), "heart" to listOf("❤️"))

        override suspend fun searchEmojis(text: String, languageCodes: List<String>) = emptyList<String>()

        override suspend fun keywordEmojis(word: String, languageCodes: List<String>): List<String> {
            asked += word
            return answers[word].orEmpty()
        }
    }

    private class FakeSettings : UnimplementedAppSettings() {
        override val emojiSkinTones: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
        override val recentEmojis = MutableStateFlow(emptyList<String>())
    }

    private val emojis = FakeEmojis()
    // Líně: `viewModelScope` musí vzniknout až po `setMain`.
    private val viewModel by lazy { EmojiSuggestionsViewModel(emojis, FakeSettings()) }

    /** `suggestions` je `WhileSubscribed`, bez odběratele by se nic nehledalo. */
    private fun TestScope.subscribed() = backgroundScope.launch { viewModel.suggestions.collect {} }

    @Test
    fun skrytaPilulkaCekaNaPauzuVPsani() = runTest {
        subscribed()
        viewModel.lookup("heart", emptyList())

        advanceTimeBy(599)
        runCurrent()
        assertNull(viewModel.suggestions.value)
        assertEquals(emptyList(), emojis.asked)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("heart", viewModel.suggestions.value?.word)
    }

    @Test
    fun priPsaniBezPauzySeNeptaNaRozepsanaSlova() = runTest {
        subscribed()
        for (word in listOf("he", "hea", "hear")) {
            viewModel.lookup(word, emptyList())
            advanceTimeBy(300)
            runCurrent()
        }
        viewModel.lookup("heart", emptyList())
        advanceUntilIdle()

        assertEquals(listOf("heart"), emojis.asked)
    }

    @Test
    fun ukazanaPilulkaSeMeniHnedAStaraSadaNezmizi() = runTest {
        subscribed()
        viewModel.lookup("hear", emptyList())
        advanceUntilIdle()
        assertEquals("hear", viewModel.suggestions.value?.word)

        viewModel.lookup("heart", emptyList())
        // Slovo se změnilo, odpověď ještě nedoběhla: pilulka drží starou sadu.
        assertEquals("hear", viewModel.suggestions.value?.word)

        runCurrent()
        assertEquals("heart", viewModel.suggestions.value?.word)
    }

    @Test
    fun slovoBezShodyPilulkuSchovaAPakSeZnovuCeka() = runTest {
        subscribed()
        viewModel.lookup("heart", emptyList())
        advanceUntilIdle()

        viewModel.lookup("hearts", emptyList())
        runCurrent()
        assertNull(viewModel.suggestions.value)

        viewModel.lookup("heart", emptyList())
        runCurrent()
        assertNull(viewModel.suggestions.value)
        advanceTimeBy(600)
        runCurrent()
        assertEquals("heart", viewModel.suggestions.value?.word)
    }

    @Test
    fun konecSlovaPilulkuSchovaHned() = runTest {
        subscribed()
        viewModel.lookup("heart", emptyList())
        advanceUntilIdle()

        viewModel.lookup(null, emptyList())
        runCurrent()
        assertNull(viewModel.suggestions.value)
    }
}
