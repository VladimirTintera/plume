package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageBody
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.core.translation.TranslationPack
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.server_summary_copied
import eu.tintera.plume.resources.translation_copied
import eu.tintera.plume.resources.translation_device_same
import eu.tintera.plume.resources.translation_device_unknown
import eu.tintera.plume.resources.translation_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTranslationTest {

    private class FakeSettings(language: String?, onDevice: Boolean) : UnimplementedAppSettings() {
        override val translationLanguage = MutableStateFlow(language)
        override val translateOnDevice = MutableStateFlow(onDevice)

        override fun setTranslationLanguage(code: String?) {
            translationLanguage.value = code
        }

        override fun setTranslateOnDevice(onDevice: Boolean) {
            translateOnDevice.value = onDevice
        }
    }

    /** Server odpovídá, až test řekne — kvůli zastaralým výsledkům. Bez čekání vrací „[jazyk] text". */
    private class FakeRepository : UnimplementedConversationRepository() {
        val translated = mutableListOf<Pair<Long, String>>()
        val summarized = mutableListOf<Pair<Long, String?>>()
        val pending = mutableMapOf<String, CompletableDeferred<Result<MessageBody>>>()
        var failure: Throwable? = null

        override suspend fun translateMessage(chatId: Long, messageId: Long, languageCode: String): Result<MessageBody> {
            translated += messageId to languageCode
            return answer(languageCode)
        }

        override suspend fun summarizeMessage(chatId: Long, messageId: Long, languageCode: String?): Result<MessageBody> {
            summarized += messageId to languageCode
            return answer(languageCode ?: "vlastní")
        }

        private suspend fun answer(language: String): Result<MessageBody> {
            failure?.let { return Result.failure(it) }
            return pending[language]?.await() ?: Result.success(MessageBody("[$language] text"))
        }
    }

    private class FakeTranslator(var models: Boolean = true) : OnDeviceTranslator {
        val downloads = mutableListOf<Pair<String, String>>()
        val translations = mutableListOf<Triple<String, String, String>>()

        override fun supports(language: String) = language in setOf("cs", "de", "en")
        override suspend fun hasModels(source: String, target: String) = models

        override suspend fun download(source: String, target: String): Result<Unit> {
            downloads += source to target
            models = true
            return Result.success(Unit)
        }

        override suspend fun translate(text: String, source: String, target: String): Result<String> {
            translations += Triple(text, source, target)
            return Result.success("$source→$target: $text")
        }

        override suspend fun downloadedPacks(): List<TranslationPack> = emptyList()
        override suspend fun deletePack(language: String): Result<Unit> = Result.success(Unit)
    }

    private val repository = FakeRepository()
    private val copied = mutableListOf<String>()

    private fun TestScope.translation(
        state: MutableStateFlow<ConversationState>,
        settings: FakeSettings = FakeSettings(language = "cs", onDevice = false),
        translator: OnDeviceTranslator? = null,
        detected: String? = "de",
        confirmsCopy: Boolean = false,
    ) = ConversationTranslation(
        chatId = TEST_CHAT,
        state = state,
        scope = this,
        repository = repository,
        settings = settings,
        onDeviceTranslator = translator,
        languageDetector = { detected },
        copyToClipboard = { copied += it },
        clipboardConfirmsCopy = confirmsCopy,
        logger = testLogger,
    )

    private fun menu(messageId: Long, canCopy: Boolean) = MessageMenu(
        messageId = messageId,
        timestamp = 0,
        isOutgoing = false,
        emoji = emptyList(),
        chosenEmoji = emptySet(),
        actions = MessageActions(
            canReply = true,
            canCopy = canCopy,
            canDeleteForSelf = true,
            canDeleteForAll = false,
            canEdit = false,
            canPin = false,
            canForward = true,
            canSave = true,
            canReport = false,
        ),
        items = emptyList(),
        favoriteSticker = null,
    )

    @Test
    fun prekladJdeNaServerDoZvolenehoJazykaAZavreNabidku() = runTest {
        val state = testState(testMessage(1, text = "Hallo"))
        state.value = state.value.copy(messageMenu = menu(1, canCopy = true))
        translation(state).showTranslation(1)
        assertNull(state.value.messageMenu)
        assertNull(state.value.translation?.body, "sheet se otevře hned, s načítáním")
        assertNull(state.value.translation?.onDevice, "bez překladače v telefonu se přepínač nekreslí")

        advanceUntilIdle()
        assertEquals(listOf(1L to "cs"), repository.translated)
        assertEquals(MessageBody("[cs] text"), state.value.translation?.body)
    }

    @Test
    fun nepovedenyPrekladZustaneVSheetuJakoVeta() = runTest {
        val state = testState(testMessage(1))
        repository.failure = IllegalStateException("Have no access to the chat")
        translation(state).showTranslation(1)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.translation_failed), state.value.translation?.error)
        assertNull(state.value.notice, "pod modálním sheetem by snackbar vidět nebyl")
    }

    @Test
    fun odpovedNaPredchoziJazykSeZahodi() = runTest {
        val state = testState(testMessage(1))
        val cs = CompletableDeferred<Result<MessageBody>>()
        repository.pending["cs"] = cs
        val translation = translation(state)
        translation.showTranslation(1)
        runCurrent()

        translation.changeTranslationLanguage("en")
        advanceUntilIdle()
        assertEquals(MessageBody("[en] text"), state.value.translation?.body)

        cs.complete(Result.success(MessageBody("[cs] pozdě")))
        advanceUntilIdle()
        assertEquals("en", state.value.translation?.languageCode)
        assertEquals(MessageBody("[en] text"), state.value.translation?.body)
    }

    @Test
    fun odpovedPoZavreniSheetuHoZnovuNeotevre() = runTest {
        val state = testState(testMessage(1))
        val cs = CompletableDeferred<Result<MessageBody>>()
        repository.pending["cs"] = cs
        val translation = translation(state)
        translation.showTranslation(1)
        runCurrent()
        translation.dismissTranslation()

        cs.complete(Result.success(MessageBody("[cs] pozdě")))
        advanceUntilIdle()
        assertNull(state.value.translation)
    }

    @Test
    fun zvolenyJazykSePamatujeAJazykAplikaceJakoVychozi() = runTest {
        val state = testState(testMessage(1))
        val settings = FakeSettings(language = null, onDevice = false)
        val translation = translation(state, settings)
        val appLanguage = normalizedLanguageCode(platformLanguageCode)
        assertEquals(appLanguage, translation.translationTarget())

        translation.showTranslation(1)
        val other = if (appLanguage == "ja") "ko" else "ja"
        translation.changeTranslationLanguage(other)
        assertEquals(other, settings.translationLanguage.value)
        assertEquals(other, translation.translationTarget())

        translation.changeTranslationLanguage(appLanguage)
        assertNull(settings.translationLanguage.value, "jazyk aplikace se ukládá jako výchozí, ne jako kód")
    }

    @Test
    fun shrnutiVeVlastnimJazyceZpravySeNeprekladaANeseKopirovaniZNabidky() = runTest {
        val state = testState(testMessage(1).copy(summaryLanguage = "cs"))
        state.value = state.value.copy(messageMenu = menu(1, canCopy = false))
        translation(state, translator = FakeTranslator()).showSummary(1)
        advanceUntilIdle()

        assertEquals(listOf<Pair<Long, String?>>(1L to null), repository.summarized)
        val sheet = state.value.translation
        assertTrue(sheet?.summary == true)
        assertFalse(sheet?.canCopy ?: true, "chráněný obsah nesmí do schránky ani převyprávěný")
        assertNull(sheet?.onDevice, "shrnutí dělá jen server")
    }

    @Test
    fun kopirovaniPrekladuZavreSheetAOhlasiSe() = runTest {
        val state = testState(testMessage(1))
        val translation = translation(state)
        translation.showTranslation(1)
        advanceUntilIdle()

        translation.copyTranslation()
        advanceUntilIdle()
        assertEquals(listOf("[cs] text"), copied)
        assertNull(state.value.translation)
        assertEquals(UiText(Res.string.translation_copied), state.value.notice)
    }

    @Test
    fun kopirovaniShrnutiMaVlastniHlaskuAKdeToPotvrdiSystemZadnou() = runTest {
        val summary = testState(testMessage(1).copy(summaryLanguage = "en"))
        summary.value = summary.value.copy(messageMenu = menu(1, canCopy = true))
        translation(summary).apply { showSummary(1); advanceUntilIdle(); copyTranslation() }
        assertEquals(UiText(Res.string.server_summary_copied), summary.value.notice)

        val confirmed = testState(testMessage(1))
        translation(confirmed, confirmsCopy = true).apply { showTranslation(1); advanceUntilIdle(); copyTranslation() }
        advanceUntilIdle()
        assertNull(confirmed.value.notice)
        assertEquals(2, copied.size)
    }

    @Test
    fun shrnutiBezPravaKopirovatDoSchrankyNejde() = runTest {
        val state = testState(testMessage(1).copy(summaryLanguage = "en"))
        state.value = state.value.copy(messageMenu = menu(1, canCopy = false))
        val translation = translation(state)
        translation.showSummary(1)
        advanceUntilIdle()

        translation.copyTranslation()
        advanceUntilIdle()
        assertTrue(copied.isEmpty())
        assertTrue(state.value.translation != null, "sheet zůstane otevřený")
    }

    @Test
    fun vTelefonuSeChybejiciBalickyNestahujiBezPotvrzeni() = runTest {
        val state = testState(testMessage(1, text = "Hallo"))
        val translator = FakeTranslator(models = false)
        val translation = translation(state, FakeSettings(language = "cs", onDevice = true), translator)
        translation.showTranslation(1)
        advanceUntilIdle()
        assertTrue(state.value.translation?.needsDownload == true)
        assertTrue(translator.downloads.isEmpty())
        assertTrue(repository.translated.isEmpty(), "obsah zprávy neodešel na server")

        translation.downloadTranslationModels()
        advanceUntilIdle()
        assertEquals(listOf("de" to "cs"), translator.downloads)
        assertEquals(MessageBody("de→cs: Hallo"), state.value.translation?.body)
        assertFalse(state.value.translation?.downloading ?: true)
    }

    @Test
    fun nerozpoznanyJazykSeDaPrelozitJednouNaServeru() = runTest {
        val state = testState(testMessage(1))
        val settings = FakeSettings(language = "cs", onDevice = true)
        val translation = translation(state, settings, FakeTranslator(), detected = null)
        translation.showTranslation(1)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.translation_device_unknown), state.value.translation?.error)

        translation.translateOnServer()
        advanceUntilIdle()
        assertEquals(listOf(1L to "cs"), repository.translated)
        assertEquals(false, state.value.translation?.onDevice)
        assertTrue(settings.translateOnDevice.value, "jednorázový překlad na serveru volbu nemění")
    }

    @Test
    fun zpravaUzVCilovemJazyceSeVTelefonuNeprekladaSamaDoSebe() = runTest {
        val state = testState(testMessage(1))
        val translator = FakeTranslator()
        translation(state, FakeSettings(language = "cs", onDevice = true), translator, detected = "cs").showTranslation(1)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.translation_device_same), state.value.translation?.error)
        assertTrue(translator.translations.isEmpty())
    }

    @Test
    fun prepinacVSheetuSiVolbuZapamatujeAPrelozZnovu() = runTest {
        val state = testState(testMessage(1, text = "Hallo"))
        val settings = FakeSettings(language = "cs", onDevice = false)
        val translator = FakeTranslator()
        val translation = translation(state, settings, translator)
        translation.showTranslation(1)
        advanceUntilIdle()
        assertEquals(false, state.value.translation?.onDevice)

        translation.setTranslateOnDevice(true)
        advanceUntilIdle()
        assertTrue(settings.translateOnDevice.value)
        assertEquals(MessageBody("de→cs: Hallo"), state.value.translation?.body)

        translation.setTranslateOnDevice(false)
        advanceUntilIdle()
        assertFalse(settings.translateOnDevice.value)
        assertEquals(2, repository.translated.size)
        assertEquals(MessageBody("[cs] text"), state.value.translation?.body)
    }
}
