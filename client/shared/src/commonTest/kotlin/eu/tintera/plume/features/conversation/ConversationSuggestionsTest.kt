package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.location.GeoPoint
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.InlineBotInfo
import eu.tintera.plume.core.telegram.InlineQueryDraft
import eu.tintera.plume.core.telegram.InlineResult
import eu.tintera.plume.core.telegram.InlineResultsButton
import eu.tintera.plume.core.telegram.InlineResultsPage
import eu.tintera.plume.core.telegram.InlineRowKind
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.MentionCandidate
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSuggestionsTest {

    private data class InlineAsk(val botUserId: Long, val query: String, val offset: String, val location: GeoPoint?)

    private class FakeRepository : UnimplementedConversationRepository() {
        val mentionQueries = mutableListOf<String>()
        val inlineAsks = mutableListOf<InlineAsk>()
        val sentResults = mutableListOf<Pair<Long, String>>()
        var bots = mapOf("gif" to bot(10, "gif"))
        var page: (InlineAsk) -> Result<InlineResultsPage> = { ask ->
            Result.success(InlineResultsPage(queryId = 7, results = listOf(row("${ask.query}-${ask.offset}")), nextOffset = "dalsi", button = null))
        }

        override suspend fun mentionCandidates(chatId: Long, query: String): Result<List<MentionCandidate>> {
            mentionQueries += query
            return Result.success(listOf(candidate(1, "Eva", "eva"), candidate(2, "Jan", null)))
        }

        val hashtagQueries = mutableListOf<String>()
        var recentBots = listOf(candidate(10, "GIF Search", "gif"), candidate(11, "Wikipedia", "wiki"))

        override suspend fun recentInlineBots(): Result<List<MentionCandidate>> = Result.success(recentBots)

        val hashtagHistory = mutableListOf("plume", "plumeapp", "plumetest", "telegram")

        override suspend fun recentHashtags(prefix: String): Result<List<String>> {
            hashtagQueries += prefix
            return Result.success(hashtagHistory.filter { it.startsWith(prefix) })
        }

        override suspend fun removeRecentHashtag(hashtag: String): Result<Unit> {
            hashtagHistory -= hashtag
            return Result.success(Unit)
        }

        override suspend fun inlineBot(username: String): Result<InlineBotInfo?> = Result.success(bots[username])

        override suspend fun inlineResults(botUserId: Long, chatId: Long, query: String, offset: String, location: GeoPoint?): Result<InlineResultsPage> {
            val ask = InlineAsk(botUserId, query, offset, location)
            inlineAsks += ask
            return page(ask)
        }

        override suspend fun sendInlineResult(chatId: Long, queryId: Long, resultId: String, replyToMessageId: Long?, quote: ReplyQuote?): Result<ChatMessage> {
            sentResults += queryId to resultId
            return Result.success(testMessage(800, outgoing = true))
        }
    }

    /** Odeslání média, jak by ho udělalo psaní: rovnou zavolá [send]. */
    private class DirectSender(private val scope: CoroutineScope) : MediaSender {
        override fun sendMedia(
            failureMessage: StringResource,
            scheduled: Boolean,
            send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
        ) {
            scope.launch { send(null, null) }
        }
    }

    private val repository = FakeRepository()
    private var draft = ComposedText("")
    private val draftChanges = mutableListOf<String>()
    private val botStarts = mutableListOf<InternalLink.BotStart>()
    private val webApps = mutableListOf<WebAppRequest>()

    private fun TestScope.suggestions(state: MutableStateFlow<ConversationState>) = ConversationSuggestions(
        chatId = TEST_CHAT,
        state = state,
        scope = this,
        repository = repository,
        media = DirectSender(this),
        composedDraft = { draft },
        onDraftChanged = { draftChanges += it.text },
        openBotStart = { botStarts += it },
        requestWebApp = { webApps += it },
        logger = testLogger,
    )

    private fun groupState() = MutableStateFlow(ConversationState(info = testChatInfo(isGroup = true)))

    @Test
    fun zminkaHledaClenyPoPauzeVPsani() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)

        suggestions.mentionQuery("ev")
        advanceTimeBy(MENTION_QUERY_DELAY.inWholeMilliseconds - 1)
        runCurrent()
        assertTrue(repository.mentionQueries.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("ev"), repository.mentionQueries)
        assertEquals("ev", state.value.mentionSuggestions?.query)
        assertEquals(2, state.value.mentionSuggestions?.candidates?.size)
    }

    @Test
    fun samotnyZavinacSeHledaHned() = runTest {
        val state = groupState()
        suggestions(state).mentionQuery("")
        runCurrent()

        assertEquals(listOf(""), repository.mentionQueries)
    }

    @Test
    fun vSoukromemChatuSeZminkyNenabizeji() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo()))
        suggestions(state).mentionQuery("ev")
        advanceUntilIdle()

        assertTrue(repository.mentionQueries.isEmpty())
        assertNull(state.value.mentionSuggestions)
    }

    @Test
    fun zminkaJmenemCekaNaOdeslaniAUzivatelskymJmenemNe() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)

        suggestions.mentionPicked(candidate(1, "Eva", "eva"))
        suggestions.mentionPicked(candidate(2, "Jan", null))

        assertEquals(listOf(NameMention("Jan", 2)), suggestions.takeNameMentions())
        assertTrue(suggestions.takeNameMentions().isEmpty(), "zmínky se vyberou jen jednou")
    }

    @Test
    fun inlineDotazOdejdeAzPoPauzeAUkazeVysledky() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)

        suggestions.inlineQuery(InlineQueryDraft("gif", "kočka"))
        advanceTimeBy(INLINE_QUERY_DELAY.inWholeMilliseconds - 1)
        runCurrent()
        assertTrue(repository.inlineAsks.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(InlineAsk(10, "kočka", "", null)), repository.inlineAsks)
        val inline = assertNotNull(state.value.inline)
        assertEquals(listOf("kočka-"), inline.results.map { it.id })
    }

    @Test
    fun dalsiPismenoZrusiRozbehnutyInlineDotaz() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)

        suggestions.inlineQuery(InlineQueryDraft("gif", "ko"))
        advanceTimeBy(INLINE_QUERY_DELAY.inWholeMilliseconds / 2)
        suggestions.inlineQuery(InlineQueryDraft("gif", "kočka"))
        advanceUntilIdle()

        assertEquals(listOf("kočka"), repository.inlineAsks.map { it.query })
    }

    @Test
    fun neznamyBotPanelSchova() = runTest {
        val state = groupState()
        suggestions(state).inlineQuery(InlineQueryDraft("nikdo", "x"))
        advanceUntilIdle()

        assertNull(state.value.inline)
        assertTrue(repository.inlineAsks.isEmpty())
    }

    @Test
    fun botKteryInlineNeumiSeUzNepta() = runTest {
        repository.page = { Result.failure(IllegalStateException("Bot doesn't support inline queries")) }
        val state = groupState()
        val suggestions = suggestions(state)

        suggestions.inlineQuery(InlineQueryDraft("gif", "a"))
        advanceUntilIdle()
        assertNull(state.value.inline)
        suggestions.inlineQuery(InlineQueryDraft("gif", "ab"))
        advanceUntilIdle()

        assertEquals(1, repository.inlineAsks.size, "druhý dotaz na bota, který neumí, už neodešel")
    }

    @Test
    fun dalsiStrankaVysledkuSePripoji() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)
        suggestions.inlineQuery(InlineQueryDraft("gif", "a"))
        advanceUntilIdle()

        suggestions.loadMoreInline()
        advanceUntilIdle()

        assertEquals(InlineAsk(10, "a", "dalsi", null), repository.inlineAsks.last())
        assertEquals(listOf("a-", "a-dalsi"), state.value.inline?.results?.map { it.id })
    }

    @Test
    fun botSPolohouCekaNaPolohuAPakJiPosle() = runTest {
        repository.bots = mapOf("mapa" to bot(11, "mapa", needLocation = true))
        val state = groupState()
        val suggestions = suggestions(state)

        suggestions.inlineQuery(InlineQueryDraft("mapa", "kavárna"))
        advanceUntilIdle()
        assertTrue(repository.inlineAsks.isEmpty(), "bez polohy by bot odpověděl něčím jiným")
        assertNotNull(state.value.inline, "panel bota už je vidět")

        suggestions.setInlineLocation(GeoPoint(50.0, 14.4))
        advanceUntilIdle()

        assertEquals(listOf(InlineAsk(11, "kavárna", "", GeoPoint(50.0, 14.4))), repository.inlineAsks)
    }

    @Test
    fun vybranyVysledekOdejdeAPanelZmizi() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)
        suggestions.inlineQuery(InlineQueryDraft("gif", "a"))
        advanceUntilIdle()

        suggestions.sendInlineResult(row("a-"))
        advanceUntilIdle()

        assertEquals(listOf(7L to "a-"), repository.sentResults)
        assertNull(state.value.inline)
    }

    @Test
    fun tlacitkoNadVysledkySpustiBotaNeboMiniAplikaci() = runTest {
        val state = groupState()
        val suggestions = suggestions(state)

        repository.page = { Result.success(InlineResultsPage(7, emptyList(), "", InlineResultsButton.StartBot("Nastavit", "setup"))) }
        suggestions.inlineQuery(InlineQueryDraft("gif", "a"))
        advanceUntilIdle()
        suggestions.pressInlineButton()
        assertEquals(listOf(InternalLink.BotStart(10, "setup", autostart = true)), botStarts)

        repository.page = { Result.success(InlineResultsPage(8, emptyList(), "", InlineResultsButton.WebApp("Otevřít", "https://app"))) }
        suggestions.inlineQuery(InlineQueryDraft("gif", "b"))
        advanceUntilIdle()
        suggestions.pressInlineButton()
        assertEquals("https://app", webApps.single().url)
    }

    @Test
    fun inlineBotSeVlozeDoPrazdnehoPole() = runTest {
        val state = groupState()
        suggestions(state).useInlineBot("gif", "kočka")

        assertEquals("@gif kočka", state.value.inputFill?.text)
        assertEquals(listOf("@gif kočka"), draftChanges)
    }

    @Test
    fun rozepsanyTextInlineBotNeprepise() = runTest {
        draft = ComposedText("rozepsaná zpráva")
        val state = groupState()
        suggestions(state).useInlineBot("gif", "kočka")

        assertNull(state.value.inputFill)
        assertNotNull(state.value.notice)
        assertTrue(draftChanges.isEmpty())
    }

    @Test
    fun naZacatkuZpravyNabidneNedavneInlineBotyIVSoukromemChatu() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo()))
        suggestions(state).mentionQuery("gi", atStart = true)
        advanceUntilIdle()

        // Členy v soukromém chatu nehledá, boty filtruje podle jména.
        assertTrue(repository.mentionQueries.isEmpty())
        assertEquals(listOf(10L), state.value.mentionSuggestions?.candidates?.map { it.userId })
    }

    @Test
    fun uprostredVetyInlineBotyNenabizi() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo()))
        suggestions(state).mentionQuery("gi", atStart = false)
        advanceUntilIdle()

        assertNull(state.value.mentionSuggestions)
    }

    @Test
    fun veSkupineJdouBotyPredCleny() = runTest {
        val state = groupState()
        suggestions(state).mentionQuery("", atStart = true)
        advanceUntilIdle()

        assertEquals(listOf(10L, 11L, 1L, 2L), state.value.mentionSuggestions?.candidates?.map { it.userId })
    }

    @Test
    fun sPrilohouInlineBotyNenabizi() = runTest {
        val state = MutableStateFlow(
            ConversationState(info = testChatInfo(), attachments = listOf(OutgoingAttachment.Document("soubor.pdf"))),
        )
        suggestions(state).mentionQuery("", atStart = true)
        advanceUntilIdle()

        assertNull(state.value.mentionSuggestions)
    }

    @Test
    fun hashtagyPodlePrefixuBezPraveNapsaneho() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo()))
        val suggestions = suggestions(state)

        suggestions.hashtagQuery("plume")
        advanceUntilIdle()

        assertEquals(listOf("plume"), repository.hashtagQueries)
        // Celý napsaný hashtag se nenabízí, delší ano.
        assertEquals(listOf("plumeapp", "plumetest"), state.value.hashtagSuggestions?.hashtags)

        suggestions.hashtagPicked()
        assertNull(state.value.hashtagSuggestions)
    }

    /** Podržení v panelu: hashtag zmizí z historie a panel se přečte znovu se zbytkem. */
    @Test
    fun odebranyHashtagZmiziZNapovidani() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo()))
        val suggestions = suggestions(state)
        suggestions.hashtagQuery("plume")
        advanceUntilIdle()

        suggestions.removeHashtag("plumeapp")
        advanceUntilIdle()

        assertEquals(listOf("plume", "plumetest", "telegram"), repository.hashtagHistory)
        assertEquals(listOf("plumetest"), state.value.hashtagSuggestions?.hashtags)
        assertEquals(listOf("plume", "plume"), repository.hashtagQueries)
    }

    @Test
    fun hashtagyKdeSeNesmiPsatNenapovida() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo(canJoin = true)))
        suggestions(state).hashtagQuery("")
        advanceUntilIdle()

        assertTrue(repository.hashtagQueries.isEmpty())
        assertNull(state.value.hashtagSuggestions)
    }

    private companion object {
        fun bot(id: Long, username: String, needLocation: Boolean = false) =
            InlineBotInfo(userId = id, username = username, placeholder = "", needLocation = needLocation)

        fun row(id: String) = InlineResult.Row(id = id, kind = InlineRowKind.Article, title = id, description = "", thumbnailFileId = null)

        fun candidate(id: Long, firstName: String, username: String?) =
            MentionCandidate(userId = id, name = firstName, firstName = firstName, username = username, avatarFileId = null)
    }
}
