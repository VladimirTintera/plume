package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.UnimplementedOnDeviceAi
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ReactionChoices
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageInfo
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.MessageReadState
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.UnimplementedStickerRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.items.ContextVerb
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_bookmark_added
import eu.tintera.plume.resources.conversation_bookmark_removed
import eu.tintera.plume.resources.conversation_favorite_failed
import eu.tintera.plume.resources.conversation_favorite_removed
import eu.tintera.plume.resources.message_info_failed
import eu.tintera.plume.resources.message_info_link_copied
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationMessageMenuTest {

    private class FakeSettings : UnimplementedAppSettings() {
        override val translationLanguage = MutableStateFlow<String?>("cs")
        override val onDeviceAiEnabled = MutableStateFlow(true)
        override val onDeviceAiVariant = MutableStateFlow(ModelVariant())
        override val messageContextEnabled = MutableStateFlow(true)
        override val messageBookmarks = MutableStateFlow<List<MessageBookmark>>(emptyList())

        override fun setMessageBookmarks(bookmarks: List<MessageBookmark>) {
            messageBookmarks.value = bookmarks
        }
    }

    private class FakeRepository : UnimplementedConversationRepository() {
        var actions: Result<MessageActions> = Result.success(rights())
        var info: CompletableDeferred<Result<MessageInfo>>? = null
        var infoResult: Result<MessageInfo> = Result.success(INFO)

        override suspend fun availableReactions(chatId: Long, messageId: Long) = Result.success(ReactionChoices(listOf("👍", "❤")))
        override suspend fun messageActions(chatId: Long, messageId: Long) = actions
        override suspend fun messageInfo(chatId: Long, messageId: Long) = info?.await() ?: infoResult
    }

    private class FakeStickers(favorite: Int?) : UnimplementedStickerRepository() {
        val favorites = listOfNotNull(favorite).map { StickerItem(id = it.toLong(), sendFileId = it, preview = sticker(it)) }
        val changed = mutableListOf<Pair<Int, Boolean>>()
        var result: Result<Unit> = Result.success(Unit)

        override fun favoriteStickers(): Flow<List<StickerItem>> = flowOf(favorites)

        override suspend fun setFavorite(stickerFileId: Int, favorite: Boolean): Result<Unit> {
            changed += stickerFileId to favorite
            return result
        }
    }

    private class FakeAi(private val status: OnDeviceAiStatus) : UnimplementedOnDeviceAi() {
        override suspend fun status(variant: ModelVariant) = status
    }

    private class FakeChats : UnimplementedChatRepository() {
        override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(ACCOUNT)
    }

    private val settings = FakeSettings()
    private val repository = FakeRepository()
    private val copied = mutableListOf<String>()

    private fun TestScope.menu(
        state: MutableStateFlow<ConversationState>,
        detected: LanguageDetector = LanguageDetector { "de" },
        ai: FakeAi? = FakeAi(OnDeviceAiStatus.Ready),
        stickers: FakeStickers = FakeStickers(favorite = null),
        confirmsCopy: Boolean = false,
        canJoinWifi: Boolean = true,
    ): ConversationMessageMenu {
        val scope = detachedScope()
        val translation = ConversationTranslation(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            repository = repository,
            settings = settings,
            onDeviceTranslator = null,
            languageDetector = detected,
            copyToClipboard = { copied += it },
            clipboardConfirmsCopy = confirmsCopy,
            logger = testLogger,
        )
        val context = ConversationMessageContext(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            settings = settings,
            openUrl = {},
            copyToClipboard = { copied += it },
            clipboardConfirmsCopy = confirmsCopy,
            scamWarnings = null,
            messageContexts = null,
            addCalendarEvent = null,
            saveNewContact = null,
            joinWifiNetwork = null,
            canJoinWifi = canJoinWifi,
            loadRates = null,
            homeCurrency = "CZK",
            metricUnits = true,
            closeMenus = {},
            openLink = {},
            requestRemind = {},
            logger = testLogger,
        )
        return ConversationMessageMenu(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            repository = repository,
            settings = settings,
            copyToClipboard = { copied += it },
            clipboardConfirmsCopy = confirmsCopy,
            languageDetector = detected,
            onDeviceAi = ai,
            bookmarks = MessageBookmarks(settings, FakeChats()),
            stickerRepository = stickers,
            messageContext = context,
            translation = translation,
            logger = testLogger,
        )
    }

    @Test
    fun nabidkaNeseReakcePravaAVybranaEmoji() = runTest {
        val message = testMessage(1, reactions = listOf(MessageReaction(emoji = "👍", count = 2, isChosen = true)))
        val state = testState(message, testMessage(2))
        val menu = menu(state)
        menu.showMessageMenu(1, listOf(MessageMenuAction.Reply), pressed = 2)
        advanceUntilIdle()

        val open = assertNotNull(state.value.messageMenu)
        assertEquals(listOf("👍", "❤"), open.emoji)
        assertEquals(setOf("👍"), open.chosenEmoji)
        assertEquals(rights(), open.actions)
        assertEquals(listOf<MessageMenuAction>(MessageMenuAction.Reply), open.items)
        assertNull(open.favoriteSticker, "u zprávy bez samolepky se oblíbenost nenabízí")
        assertEquals(false, open.bookmarked)
        assertEquals(2L, open.pressedMessageId, "fotka uprostřed alba, na kterou uživatel podržel")
    }

    @Test
    fun bezPravOdServeruSeNabidneZaloha() = runTest {
        val state = testState(testMessage(1))
        repository.actions = Result.failure(IllegalStateException("x"))
        menu(state).showMessageMenu(1, emptyList(), pressed = 1)
        advanceUntilIdle()
        assertEquals(MessageActions.Fallback, state.value.messageMenu?.actions)
        assertNull(state.value.messageMenu?.pressedMessageId)
    }

    @Test
    fun prelozitPoznaZpravuUzVJazycePrekladu() = runTest {
        val text = "Dobrý den, posílám vám tu smlouvu"
        val inCzech = testState(testMessage(1, text = text))
        menu(inCzech, detected = { "cs" }).showMessageMenu(1, listOf(MessageMenuAction.Translate))
        advanceUntilIdle()
        assertTrue(inCzech.value.messageMenu?.inTranslationLanguage == true)

        val inGerman = testState(testMessage(1, text = text))
        menu(inGerman, detected = { "de" }).showMessageMenu(1, listOf(MessageMenuAction.Translate))
        advanceUntilIdle()
        assertFalse(inGerman.value.messageMenu?.inTranslationLanguage ?: true)
    }

    @Test
    fun pomaleRozpoznaniJazykaNabidkuNezdrzi() = runTest {
        val state = testState(testMessage(1, text = "Dobrý den, posílám vám tu smlouvu"))
        menu(state, detected = { awaitCancellation() }).showMessageMenu(1, listOf(MessageMenuAction.Translate))
        advanceUntilIdle()
        val open = assertNotNull(state.value.messageMenu, "nabídka se otevře i bez rozpoznaného jazyka")
        assertFalse(open.inTranslationLanguage, "nerozpoznaný jazyk = „Přeložit\" se nabídne")
    }

    @Test
    fun vysvetlitJenSeZapnutouAiAModelemKteryTelefonUmi() = runTest {
        suspend fun TestScope.explain(ai: FakeAi?, enabled: Boolean = true): Boolean {
            settings.onDeviceAiEnabled.value = enabled
            val state = testState(testMessage(1))
            menu(state, ai = ai).showMessageMenu(1, listOf(MessageMenuAction.Explain))
            advanceUntilIdle()
            return state.value.messageMenu!!.explainAvailable
        }
        assertTrue(explain(FakeAi(OnDeviceAiStatus.Ready)))
        assertTrue(explain(FakeAi(OnDeviceAiStatus.Downloadable)), "stažený model být nemusí")
        assertFalse(explain(FakeAi(OnDeviceAiStatus.Unsupported)))
        assertFalse(explain(FakeAi(OnDeviceAiStatus.Ready), enabled = false))
        assertFalse(explain(null))
    }

    @Test
    fun akceZeZpravySeRozbaliNaJednotlivePolozky() = runTest {
        val state = testState(testMessage(1, text = "Wi-Fi: Plume_Home, heslo: tajne1234"))
        menu(state).showMessageMenu(1, listOf(MessageMenuAction.Reply, MessageMenuAction.ContextActions))
        advanceUntilIdle()

        val open = assertNotNull(state.value.messageMenu)
        assertEquals(listOf<ContextFinding>(ContextFinding.Wifi("Plume_Home", "tajne1234")), open.contextFindings)
        assertEquals(MessageMenuAction.Reply, open.items.first())
        assertFalse(MessageMenuAction.ContextActions in open.items)
        val wifi = open.items.filterIsInstance<MessageMenuAction.ContextItem>().single()
        assertEquals(ContextVerb.JoinWifi, wifi.verb)

        val noWifi = testState(testMessage(1, text = "Wi-Fi: Plume_Home, heslo: tajne1234"))
        menu(noWifi, canJoinWifi = false).showMessageMenu(1, listOf(MessageMenuAction.ContextActions))
        advanceUntilIdle()
        assertEquals(
            ContextVerb.Wifi,
            noWifi.value.messageMenu?.items?.filterIsInstance<MessageMenuAction.ContextItem>()?.single()?.verb,
            "telefon, který se připojit neumí, ukáže síť a heslo",
        )
    }

    @Test
    fun oblibenaSamolepkaSeOdeberePresNabidku() = runTest {
        val state = testState(testMessage(1).copy(text = "", media = sticker(STICKER)))
        val stickers = FakeStickers(favorite = STICKER)
        val menu = menu(state, stickers = stickers)
        menu.showMessageMenu(1, emptyList())
        advanceUntilIdle()
        assertEquals(true, state.value.messageMenu?.favoriteSticker)

        menu.toggleFavoriteSticker(1)
        assertNull(state.value.messageMenu)
        advanceUntilIdle()
        assertEquals(listOf(STICKER to false), stickers.changed)
        assertEquals(UiText(Res.string.conversation_favorite_removed), state.value.notice)
    }

    @Test
    fun nepovedenaOblibenaSamolepkaSeOhlasiChybou() = runTest {
        val state = testState(testMessage(1).copy(text = "", media = sticker(STICKER)))
        val stickers = FakeStickers(favorite = null).apply { result = Result.failure(IllegalStateException("x")) }
        val menu = menu(state, stickers = stickers)
        menu.showMessageMenu(1, emptyList())
        advanceUntilIdle()
        assertEquals(false, state.value.messageMenu?.favoriteSticker)

        menu.toggleFavoriteSticker(1)
        advanceUntilIdle()
        assertEquals(listOf(STICKER to true), stickers.changed)
        assertEquals(UiText(Res.string.conversation_favorite_failed), state.value.error)
    }

    @Test
    fun zalozkaSePridaSOtiskemZpravyAOdebereZNabidky() = runTest {
        val state = testState(testMessage(1, text = "Kód je 4711"))
        state.value = state.value.copy(info = testChatInfo())
        val menu = menu(state)
        menu.showMessageMenu(1, emptyList())
        advanceUntilIdle()
        menu.toggleBookmark(1)
        advanceUntilIdle()

        val saved = settings.messageBookmarks.value.single()
        assertEquals(ACCOUNT, saved.accountId)
        assertEquals("Kód je 4711", saved.text)
        assertEquals("Test", saved.sender, "bez jména odesílatele se vezme název chatu")
        assertEquals(UiText(Res.string.conversation_bookmark_added), state.value.notice)

        menu.showMessageMenu(1, emptyList())
        advanceUntilIdle()
        assertEquals(true, state.value.messageMenu?.bookmarked)
        menu.toggleBookmark(1)
        assertTrue(settings.messageBookmarks.value.isEmpty())
        assertEquals(UiText(Res.string.conversation_bookmark_removed), state.value.notice)
    }

    @Test
    fun informaceOZpraveSeDoctouDoOtevrenehoSheetu() = runTest {
        val state = testState(testMessage(1))
        val menu = menu(state)
        menu.showMessageInfo(1)
        assertNull(state.value.messageInfo?.info, "sheet se otevře hned, údaje dojdou")
        advanceUntilIdle()
        assertEquals(INFO, state.value.messageInfo?.info)
    }

    @Test
    fun informacePoZavreniSheetuHoZnovuNeotevrou() = runTest {
        val state = testState(testMessage(1))
        val waiting = CompletableDeferred<Result<MessageInfo>>()
        repository.info = waiting
        val menu = menu(state)
        menu.showMessageInfo(1)
        runCurrent()
        menu.dismissMessageInfo()

        waiting.complete(Result.success(INFO))
        advanceUntilIdle()
        assertNull(state.value.messageInfo)
    }

    @Test
    fun nepovedeneInformaceZavrouSheetSChybou() = runTest {
        val state = testState(testMessage(1))
        repository.infoResult = Result.failure(IllegalStateException("x"))
        menu(state).showMessageInfo(1)
        advanceUntilIdle()
        assertNull(state.value.messageInfo)
        assertEquals(UiText(Res.string.message_info_failed), state.value.error)
    }

    @Test
    fun odkazNaZpravuJdeDoSchrankyASheetSeZavre() = runTest {
        val state = testState(testMessage(1))
        val menu = menu(state)
        menu.showMessageInfo(1)
        advanceUntilIdle()
        menu.copyMessageLink("https://t.me/c/1/1")
        advanceUntilIdle()
        assertEquals(listOf("https://t.me/c/1/1"), copied)
        assertNull(state.value.messageInfo)
        assertEquals(UiText(Res.string.message_info_link_copied), state.value.notice)

        val confirmed = testState(testMessage(1))
        menu(confirmed, confirmsCopy = true).copyMessageLink("https://t.me/c/1/1")
        advanceUntilIdle()
        assertNull(confirmed.value.notice, "Android 13+ ukáže schránku sám")
    }

    @Test
    fun kopirovaniBereTextZpravyAZavreNabidku() = runTest {
        val state = testState(testMessage(1, text = "Ahoj"))
        val menu = menu(state)
        menu.showMessageMenu(1, emptyList())
        advanceUntilIdle()
        menu.copyMessage(1)
        advanceUntilIdle()
        assertEquals(listOf("Ahoj"), copied)
        assertNull(state.value.messageMenu)
    }

    @Test
    fun vyberTextuZavreNabidkuAOtevreZpravu() = runTest {
        val state = testState(testMessage(1, text = "Ahoj"))
        val menu = menu(state)
        menu.showMessageMenu(1, emptyList())
        advanceUntilIdle()
        menu.selectText(1)
        assertNull(state.value.messageMenu)
        assertEquals(1L, state.value.selectingText?.id)
        menu.dismissTextSelection()
        assertNull(state.value.selectingText)
    }

    @Test
    fun odznakPrecteniJenUVlastniZpravyADruheTuknutiHoSchova() = runTest {
        val state = testState(testMessage(1, outgoing = true), testMessage(2))
        val menu = menu(state)
        menu.toggleReadReceipt(2)
        assertNull(state.value.readReceiptFor)

        menu.toggleReadReceipt(1)
        assertEquals(1L, state.value.readReceiptFor)
        menu.toggleReadReceipt(1)
        assertNull(state.value.readReceiptFor)
    }

    @Test
    fun nabidkaNadOznamenimJenKdyzJdeSmazat() = runTest {
        val state = testState(testMessage(1))
        repository.actions = Result.success(rights(canDeleteForSelf = false))
        val menu = menu(state)
        menu.showNoticeMenu(1)
        advanceUntilIdle()
        assertNull(state.value.noticeMenu)

        repository.actions = Result.success(rights(canDeleteForSelf = true))
        menu.showNoticeMenu(1)
        advanceUntilIdle()
        assertEquals(NoticeMenu(1, canDeleteForSelf = true, canDeleteForAll = false), state.value.noticeMenu)
    }

    private companion object {
        const val ACCOUNT = 500L
        const val STICKER = 33

        val INFO = MessageInfo(sentAt = 1, editedAt = null, readState = MessageReadState.Unread, viewers = emptyList(), link = null)

        fun rights(canDeleteForSelf: Boolean = true) = MessageActions(
            canReply = true,
            canCopy = true,
            canDeleteForSelf = canDeleteForSelf,
            canDeleteForAll = false,
            canEdit = false,
            canPin = false,
            canForward = true,
            canSave = true,
            canReport = false,
        )

        fun sticker(fileId: Int) = MessageMedia.Sticker(
            stickerFileId = fileId,
            setId = 0,
            fileId = fileId,
            animationFileId = null,
            thumbnailFileId = null,
            width = 512,
            height = 512,
            emoji = "🙂",
        )
    }
}
