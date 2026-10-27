package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.navigation.ContactOffers
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.BotAnswer
import eu.tintera.plume.core.telegram.BotButton
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.BotKeyboard
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatPoll
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.PollAnswer
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.telegram.ReplyKeyboardButton
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.UnsupportedBotButton
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationBotsTest {

    private class FakeRepository : UnimplementedConversationRepository() {
        var startResult: Result<ChatMessage> = Result.success(testMessage(700))
        var answer: Result<BotAnswer> = Result.success(BotAnswer(text = "", alert = false, url = ""))
        val started = mutableListOf<String>()
        val pressed = mutableListOf<Triple<Long, Int, Int>>()
        val votes = mutableListOf<Pair<Long, List<Int>>>()
        val keyboardTexts = mutableListOf<Pair<String, Long?>>()
        val dismissedMarkup = mutableListOf<Long>()
        val pollOptions = mutableListOf<String>()

        override suspend fun startBot(chatId: Long, parameter: String): Result<ChatMessage> {
            started += parameter
            return startResult
        }

        override suspend fun pressBotButton(chatId: Long, messageId: Long, row: Int, column: Int, text: String): Result<BotAnswer> {
            pressed += Triple(messageId, row, column)
            return answer
        }

        override suspend fun vote(chatId: Long, messageId: Long, answerIndexes: List<Int>): Result<Unit> {
            votes += messageId to answerIndexes
            return Result.success(Unit)
        }

        override suspend fun sendKeyboardText(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage> {
            keyboardTexts += text to replyToMessageId
            return Result.success(testMessage(701, text, outgoing = true))
        }

        override suspend fun dismissReplyMarkup(chatId: Long, messageId: Long): Result<Unit> {
            dismissedMarkup += messageId
            return Result.success(Unit)
        }

        override suspend fun addPollOption(chatId: Long, messageId: Long, text: String, media: PollDraftMedia?): Result<Unit> {
            pollOptions += text
            return Result.success(Unit)
        }
    }

    /** Odkazy bez cíle v aplikaci — vše je „cizí web", takže tlačítko s odkazem se ptá. */
    private class NoInternalLinks : UnimplementedChatRepository() {
        override suspend fun resolveLink(url: String) = Result.success<InternalLink?>(null)
    }

    private class FakeSettings : UnimplementedAppSettings() {
        val usedKeyboards = mutableListOf<Long>()
        override fun markOneTimeKeyboardUsed(chatId: Long, messageId: Long) {
            usedKeyboards += messageId
        }
    }

    private val repository = FakeRepository()
    private val settings = FakeSettings()
    private val botStarts = BotStartRequests()
    private val contactOffers = ContactOffers()
    private val copied = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val replies = mutableListOf<Long>()
    private val draftChanges = mutableListOf<String>()

    private fun TestScope.bots(state: MutableStateFlow<ConversationState>, confirmsCopy: Boolean = false): ConversationBots {
        val jumper = object : MessageJumper {
            override fun jumpToMessage(messageId: Long, fromSearch: Boolean, anchor: ScrollAnchor) = Unit
        }
        val links = ConversationLinks(
            chatId = TEST_CHAT,
            forumTopicId = null,
            savedTopicId = null,
            state = state,
            scope = this,
            repository = repository,
            chatRepository = NoInternalLinks(),
            openUrl = { opened += it },
            shareRequests = ShareRequests(),
            botStartRequests = botStarts,
            contactOffers = contactOffers,
            openProxy = {},
            jumper = jumper,
            logger = testLogger,
        )
        val suggestions = ConversationSuggestions(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            repository = repository,
            media = object : MediaSender {
                override fun sendMedia(
                    failureMessage: StringResource,
                    scheduled: Boolean,
                    send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
                ) = error("napovídání v testu nic neposílá")
            },
            composedDraft = { ComposedText("") },
            onDraftChanged = { draftChanges += it.text },
            openBotStart = {},
            requestWebApp = {},
            logger = testLogger,
        )
        return ConversationBots(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            repository = repository,
            chatRepository = NoInternalLinks(),
            settings = settings,
            openUrl = { opened += it },
            copyToClipboard = { copied += it },
            clipboardConfirmsCopy = confirmsCopy,
            botStartRequests = botStarts,
            links = links,
            suggestions = suggestions,
            replyTo = { replies += it },
            logger = testLogger,
        )
    }

    /** Zpráva bota s jedním řádkem tlačítek. */
    private fun withButtons(vararg buttons: BotButton) =
        testMessage(1).copy(keyboard = BotKeyboard(rows = listOf(buttons.toList())))

    private fun stateWith(vararg messages: ChatMessage, group: Boolean = false) = MutableStateFlow(
        ConversationState(messages = messages.sortedByDescending { it.id }, info = testChatInfo(isGroup = group)),
    )

    @Test
    fun startBotaOdejdeSParametremZOdkazuAVyridiHo() = runTest {
        botStarts.request(TEST_CHAT, "ref42")
        val state = stateWith()
        state.value = state.value.copy(botStartParameter = "ref42")
        val bots = bots(state)

        bots.startBot()
        assertTrue(state.value.startingBot)
        bots.startBot()
        advanceUntilIdle()

        assertEquals(listOf("ref42"), repository.started, "dvojí ťuknutí pošle Start jednou")
        assertFalse(state.value.startingBot)
        assertTrue(state.value.messages.any { it.id == 700L })
        assertNull(botStarts.parameter(TEST_CHAT).first())
    }

    @Test
    fun nepovedenyStartSeOhlasi() = runTest {
        repository.startResult = Result.failure(IllegalStateException("BOT_INVALID"))
        val state = stateWith()
        val bots = bots(state)

        bots.startBot()
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertFalse(state.value.startingBot)
    }

    @Test
    fun odpovedBotaNaTlacitkoJeUpozorneniNeboOkno() = runTest {
        val state = stateWith(withButtons(BotButton("Ano", BotButtonKind.Callback)))
        val bots = bots(state)

        repository.answer = Result.success(BotAnswer(text = "Hotovo", alert = false, url = ""))
        bots.pressBotButton(1, 0, 0)
        advanceUntilIdle()
        assertNotNull(state.value.notice)
        assertNull(state.value.dialog<ConversationDialog.BotAlert>()?.text)

        repository.answer = Result.success(BotAnswer(text = "Pozor!", alert = true, url = ""))
        bots.pressBotButton(1, 0, 0)
        advanceUntilIdle()
        assertEquals("Pozor!", state.value.dialog<ConversationDialog.BotAlert>()?.text)
        assertNull(state.value.pressingBotButton)
    }

    @Test
    fun tlacitkoSeBehemCekaniNaBotaNemackaZnovu() = runTest {
        val state = stateWith(withButtons(BotButton("Ano", BotButtonKind.Callback)))
        val bots = bots(state)

        bots.pressBotButton(1, 0, 0)
        bots.pressBotButton(1, 0, 0)
        advanceUntilIdle()

        assertEquals(1, repository.pressed.size)
    }

    @Test
    fun odkazZOdpovediBotaSeNapredPotvrdi() = runTest {
        repository.answer = Result.success(BotAnswer(text = "", alert = false, url = "https://bot.example/hra"))
        val state = stateWith(withButtons(BotButton("Hrát", BotButtonKind.Callback)))
        bots(state).pressBotButton(1, 0, 0)
        advanceUntilIdle()

        assertEquals("https://bot.example/hra", state.value.dialog<ConversationDialog.ConfirmLink>()?.url)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun tlacitkoSOdkazemSeNapredPotvrdi() = runTest {
        val state = stateWith(withButtons(BotButton("Web", BotButtonKind.Url("https://example.com"))))
        bots(state).pressBotButton(1, 0, 0)
        advanceUntilIdle()

        assertEquals("https://example.com", state.value.dialog<ConversationDialog.ConfirmLink>()?.url)
    }

    @Test
    fun kopirovaniTextuZTlacitkaSeOhlasiJenKdyzToNeudelaSystem() = runTest {
        val state = stateWith(withButtons(BotButton("Kód", BotButtonKind.CopyText("ABC-123"))))
        bots(state).pressBotButton(1, 0, 0)
        advanceUntilIdle()
        assertEquals(listOf("ABC-123"), copied)
        assertNotNull(state.value.notice)

        val confirmed = stateWith(withButtons(BotButton("Kód", BotButtonKind.CopyText("ABC-123"))))
        bots(confirmed, confirmsCopy = true).pressBotButton(1, 0, 0)
        advanceUntilIdle()
        assertNull(confirmed.value.notice)
    }

    @Test
    fun prepnutiDoInlineVTomhleChatuVlozDotazDoPole() = runTest {
        val state = stateWith(withButtons(BotButton("Hledat", BotButtonKind.SwitchInline("gif", "kočka", SwitchInlineTarget.Current))))
        bots(state).pressBotButton(1, 0, 0)

        assertEquals("@gif kočka", state.value.inputFill?.text)
        assertEquals(listOf("@gif kočka"), draftChanges)
    }

    @Test
    fun nepodporovaneTlacitkoVysvetli() = runTest {
        val state = stateWith(withButtons(BotButton("Koupit", BotButtonKind.Unsupported(UnsupportedBotButton.Buy))))
        bots(state).pressBotButton(1, 0, 0)

        assertNotNull(state.value.notice)
        assertTrue(repository.pressed.isEmpty())
    }

    @Test
    fun hlasujeSeJenTamKdeToAnketaDovoli() = runTest {
        val open = testMessage(1).copy(poll = poll(chosen = false))
        val closed = testMessage(2).copy(poll = poll(chosen = false, closed = true))
        val voted = testMessage(3).copy(poll = poll(chosen = true, revoting = false))
        val state = stateWith(open, closed, voted)
        val bots = bots(state)

        bots.vote(1, listOf(0))
        bots.vote(2, listOf(0))
        bots.vote(3, emptyList())
        advanceUntilIdle()

        assertEquals(listOf(1L to listOf(0)), repository.votes)
    }

    @Test
    fun hlasJdeVzitZpetJenKdyzToAnketaDovoli() = runTest {
        val state = stateWith(testMessage(1).copy(poll = poll(chosen = true, revoting = true)))
        bots(state).vote(1, emptyList())
        advanceUntilIdle()

        assertEquals(listOf(1L to emptyList<Int>()), repository.votes)
    }

    @Test
    fun tlacitkoKlavesniceVeSkupineOdpovidaNaZpravuBota() = runTest {
        val state = stateWith(testMessage(5), group = true)
        state.value = state.value.copy(replyMarkup = keyboard(messageId = 5, oneTime = false))
        bots(state).pressReplyKeyboardButton(0, 0)
        advanceUntilIdle()

        assertEquals(listOf<Pair<String, Long?>>("Ano" to 5L), repository.keyboardTexts)
        assertTrue(settings.usedKeyboards.isEmpty())
    }

    @Test
    fun jednorazovaKlavesnicePoPouzitiZmizi() = runTest {
        val state = stateWith(testMessage(5))
        state.value = state.value.copy(replyMarkup = keyboard(messageId = 5, oneTime = true))
        bots(state).pressReplyKeyboardButton(0, 0)
        advanceUntilIdle()

        assertEquals(listOf<Pair<String, Long?>>("Ano" to null), repository.keyboardTexts, "v soukromém chatu se neodpovídá")
        assertEquals(listOf(5L), settings.usedKeyboards)
        assertEquals(listOf(5L), repository.dismissedMarkup)
    }

    @Test
    fun cisloAPolohaZKlavesniceSeNapredPotvrdi() = runTest {
        val state = stateWith(testMessage(5))
        state.value = state.value.copy(replyMarkup = keyboard(5, oneTime = false, ReplyKeyboardButtonKind.PhoneNumber, ReplyKeyboardButtonKind.Location))
        val bots = bots(state)

        bots.pressReplyKeyboardButton(0, 0)
        assertEquals(ConversationDialog.SharePhone, state.value.dialog)
        // Dialog je nejvýš jeden (`ConversationDialog`): druhé tlačítko první potvrzení nahradí.
        bots.pressReplyKeyboardButton(0, 1)
        assertEquals(ConversationDialog.ShareLocation, state.value.dialog)
        assertTrue(repository.keyboardTexts.isEmpty())
    }

    @Test
    fun polohaBotoviBezZjistenePolohySeOhlasi() = runTest {
        val state = stateWith()
        state.value = state.value.copy(dialog = ConversationDialog.ShareLocation)
        bots(state).sendKeyboardLocation(point = null)

        assertNull(state.value.dialog)
        assertNotNull(state.value.notice)
    }

    @Test
    fun prikazOdejdeJenTamKamSePsatSmi() = runTest {
        val writable = stateWith()
        bots(writable).sendBotCommand("/start")
        val readOnly = MutableStateFlow(ConversationState(info = testChatInfo(canJoin = true)))
        bots(readOnly).sendBotCommand("/start")
        advanceUntilIdle()

        assertEquals(listOf<Pair<String, Long?>>("/start" to null), repository.keyboardTexts)
    }

    @Test
    fun vynucenaOdpovedPrepnePoleJednouAJenKdyzSeNicNePise() = runTest {
        val state = stateWith(testMessage(5))
        val bots = bots(state)

        bots.applyForceReply(5)
        bots.applyForceReply(5)
        runCurrent()
        assertEquals(listOf(5L), replies)

        val editing = stateWith(testMessage(6))
        editing.value = editing.value.copy(editing = testMessage(6))
        bots(editing).applyForceReply(6)
        runCurrent()
        assertEquals(listOf(5L), replies, "rozpracovanou úpravu vynucená odpověď nepřebije")
    }

    @Test
    fun novaMoznostAnketySeOrizneAPrazdnaNeodejde() = runTest {
        val state = stateWith(testMessage(1))
        val bots = bots(state)

        state.value = state.value.copy(dialog = ConversationDialog.AddPollOption(1))
        bots.addPollOption("  Možná  ", media = null)
        state.value = state.value.copy(dialog = ConversationDialog.AddPollOption(1))
        bots.addPollOption("   ", media = null)
        advanceUntilIdle()

        assertEquals(listOf("Možná"), repository.pollOptions)
        assertNull(state.value.dialog)
    }

    private fun keyboard(messageId: Long, oneTime: Boolean, vararg kinds: ReplyKeyboardButtonKind) = ChatReplyMarkup.Keyboard(
        messageId = messageId,
        placeholder = "",
        rows = listOf(
            if (kinds.isEmpty()) listOf(ReplyKeyboardButton("Ano", ReplyKeyboardButtonKind.Text))
            else kinds.map { ReplyKeyboardButton(it.toString(), it) },
        ),
        oneTime = oneTime,
    )

    private fun poll(chosen: Boolean, closed: Boolean = false, revoting: Boolean = true) = ChatPoll(
        question = "Kdy?",
        options = listOf(
            PollAnswer(index = 0, text = "Ráno", voters = if (chosen) 1 else 0, percentage = if (chosen) 100 else 0, isChosen = chosen),
            PollAnswer(index = 1, text = "Večer", voters = 0, percentage = 0, isChosen = false),
        ),
        totalVoters = if (chosen) 1 else 0,
        isAnonymous = true,
        multipleAnswers = false,
        allowsRevoting = revoting,
        isClosed = closed,
        canSeeResults = true,
        canGetVoters = false,
    )
}
