package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.navigation.ContactOffers
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.CommentThread
import eu.tintera.plume.core.telegram.ForwardOrigin
import eu.tintera.plume.core.telegram.GuardDecision
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.JoinResult
import eu.tintera.plume.core.telegram.InvitePreview
import eu.tintera.plume.core.telegram.LinkUserNotFoundException
import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyKind
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.join_guard_declined
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationLinksTest {

    /** Navigace, kterou části poslaly (viz `ConversationNavEvent`). */
    private val navigated = mutableListOf<ConversationNavEvent>()

    private val lastOpenChat: ChatTarget? get() = navigated.filterIsInstance<ConversationNavEvent.OpenChat>().lastOrNull()?.target

    private val lastOpenProfile: Long? get() = navigated.filterIsInstance<ConversationNavEvent.OpenProfile>().lastOrNull()?.chatId

    private val lastOpenWebApp: WebAppRequest? get() = navigated.filterIsInstance<ConversationNavEvent.OpenWebApp>().lastOrNull()?.request

    private class FakeChats(
        private val links: Map<String, Result<InternalLink?>>,
        private val join: Result<JoinResult> = Result.success(JoinResult.Joined(OTHER)),
    ) : UnimplementedChatRepository() {
        /** Rozhodnutí strážního bota; dokud se nedoplní, čekání visí jako na skutečný update. */
        val guardDecision = CompletableDeferred<GuardDecision>()
        val awaitedQueries = mutableListOf<Long>()

        override suspend fun resolveLink(url: String): Result<InternalLink?> = links[url] ?: Result.success(null)
        override suspend fun joinByInvite(link: String): Result<JoinResult> = join
        override suspend fun awaitGuardDecision(queryId: Long): GuardDecision {
            awaitedQueries += queryId
            return guardDecision.await()
        }
        override suspend fun privateChat(userId: Long): Result<Long> = Result.success(userId + 1000)
    }

    private class FakeRepository : UnimplementedConversationRepository() {
        val startedBots = mutableListOf<Pair<Long, String>>()
        var thread: Result<CommentThread> = Result.success(CommentThread(chatId = 300, rootMessageId = 9))

        override suspend fun startBot(chatId: Long, parameter: String): Result<ChatMessage> {
            startedBots += chatId to parameter
            return Result.success(testMessage(1))
        }

        override suspend fun commentThread(chatId: Long, messageId: Long) = thread
    }

    private class RecordingJumper : MessageJumper {
        val jumps = mutableListOf<Long>()

        override fun jumpToMessage(messageId: Long, fromSearch: Boolean, anchor: ScrollAnchor) {
            jumps += messageId
        }
    }

    private val repository = FakeRepository()
    private val jumper = RecordingJumper()
    private val opened = mutableListOf<String>()
    private val proxies = mutableListOf<ProxyConfig>()
    private val shares = ShareRequests()
    private val botStarts = BotStartRequests()
    private val contactOffers = ContactOffers()

    private fun TestScope.links(
        state: MutableStateFlow<ConversationState>,
        chats: FakeChats,
        forumTopicId: Int? = null,
    ) = ConversationLinks(
            slot = AccountSlot.Primary,
        chatId = TEST_CHAT,
        forumTopicId = forumTopicId,
        savedTopicId = null,
        state = state,
        navigate = navigated::add,
        scope = this,
        repository = repository,
        chatRepository = chats,
        openUrl = { opened += it },
        shareRequests = shares,
        botStartRequests = botStarts,
        contactOffers = contactOffers,
        openProxy = { proxies += it },
        jumper = jumper,
        logger = testLogger,
    )

    private fun links(url: String, link: InternalLink?) = FakeChats(mapOf(url to Result.success(link)))

    @Test
    fun ciziOdkazSeOtevreRovnou() = runTest {
        val state = testState()
        links(state, links("https://example.com", null)).openLink("https://example.com")
        advanceUntilIdle()

        assertEquals(listOf("https://example.com"), opened)
    }

    @Test
    fun odkazZTlacitkaSeNapredPotvrdi() = runTest {
        val state = testState()
        val links = links(state, links("https://example.com", null))

        links.openLink("https://example.com", confirmExternal = true)
        advanceUntilIdle()
        assertEquals("https://example.com", state.value.dialog<ConversationDialog.ConfirmLink>()?.url)
        assertTrue(opened.isEmpty())

        links.openConfirmedLink()
        assertEquals(listOf("https://example.com"), opened)
        assertNull(state.value.dialog<ConversationDialog.ConfirmLink>()?.url)
    }

    @Test
    fun odkazNaZpravuVTemzeChatuSkoci() = runTest {
        val state = testState()
        links(state, links("t.me/c/1/5", InternalLink.Message(TEST_CHAT, 5))).openLink("t.me/c/1/5")
        advanceUntilIdle()

        assertEquals(listOf(5L), jumper.jumps)
        assertNull(lastOpenChat)
    }

    @Test
    fun odkazNaZpravuJinehoChatuHoOtevre() = runTest {
        val state = testState()
        links(state, links("t.me/c/2/5", InternalLink.Message(OTHER, 5))).openLink("t.me/c/2/5")
        advanceUntilIdle()

        assertEquals(ChatTarget(OTHER, 5), lastOpenChat)
        assertTrue(jumper.jumps.isEmpty())
    }

    @Test
    fun vTematuOdkazNaZpravuJinehoTematuOtevreChat() = runTest {
        val state = testState()
        val link = InternalLink.Message(TEST_CHAT, 5, topicId = 8)
        links(state, links("t.me/c/1/8/5", link), forumTopicId = 3).openLink("t.me/c/1/8/5")
        advanceUntilIdle()

        assertTrue(jumper.jumps.isEmpty(), "zpráva z jiného tématu v téhle historii není")
        assertEquals(ChatTarget(TEST_CHAT, 5, topicId = 8), lastOpenChat)
    }

    @Test
    fun odkazNaChatSTextemPredaTextDoSdileni() = runTest {
        val state = testState()
        links(state, links("t.me/eva?text=ahoj", InternalLink.Chat(OTHER, draftText = "ahoj"))).openLink("t.me/eva?text=ahoj")
        advanceUntilIdle()

        val shared = assertNotNull(shares.incoming.first())
        assertEquals("ahoj", shared.text)
        assertEquals(OTHER, shared.targetChatId)
        assertEquals(ChatTarget(OTHER), lastOpenChat)
    }

    @Test
    fun odkazNaProfilOtevreProfil() = runTest {
        val state = testState()
        links(state, links("t.me/eva", InternalLink.Chat(OTHER, openProfile = true))).openLink("t.me/eva")
        advanceUntilIdle()

        assertEquals(OTHER, lastOpenProfile)
        assertNull(lastOpenChat)
    }

    @Test
    fun kontaktniOdkazNaNekontaktOtevreProfilSNabidkou() = runTest {
        val state = testState()
        val url = "t.me/contact/abc"
        links(state, links(url, InternalLink.Chat(OTHER, openProfile = true, offerContact = true))).openLink(url)
        advanceUntilIdle()

        assertEquals(OTHER, lastOpenProfile)
        assertNull(lastOpenChat)
        assertTrue(contactOffers.consume(OTHER), "profil si nabídku vyzvedne")
        assertFalse(contactOffers.consume(OTHER), "a jen jednou")
    }

    @Test
    fun kontaktniOdkazNaKontaktOtevreChatBezNabidky() = runTest {
        val state = testState()
        val url = "t.me/contact/abc"
        links(state, links(url, InternalLink.Chat(OTHER))).openLink(url)
        advanceUntilIdle()

        assertEquals(ChatTarget(OTHER), lastOpenChat)
        assertFalse(contactOffers.consume(OTHER))
    }

    @Test
    fun pozvankaOtevreSheetAPrijetiOtevreChat() = runTest {
        val state = testState()
        val invite = InternalLink.Invite("t.me/+abc", invitePreview())
        val links = links(state, links("t.me/+abc", invite))

        links.openLink("t.me/+abc")
        advanceUntilIdle()
        assertEquals("t.me/+abc", state.value.sheet<InviteSheetState>()?.link)

        links.joinInvite()
        advanceUntilIdle()
        assertNull(state.value.sheet<InviteSheetState>())
        assertEquals(ChatTarget(OTHER), lastOpenChat)
    }

    @Test
    fun zadostOVstupSeJenOhlasi() = runTest {
        val state = testState()
        val chats = FakeChats(
            mapOf("t.me/+abc" to Result.success(InternalLink.Invite("t.me/+abc", invitePreview()))),
            join = Result.success(JoinResult.Requested),
        )
        val links = links(state, chats)
        links.openLink("t.me/+abc")
        advanceUntilIdle()

        links.joinInvite()
        advanceUntilIdle()

        assertNull(state.value.sheet<InviteSheetState>())
        assertNotNull(state.value.notice)
        assertNull(lastOpenChat)
    }

    private fun TestScope.guardedInvite(state: MutableStateFlow<ConversationState>): Pair<ConversationLinks, FakeChats> {
        val chats = FakeChats(
            mapOf("t.me/+abc" to Result.success(InternalLink.Invite("t.me/+abc", invitePreview()))),
            join = Result.success(JoinResult.GuardBot(botUserId = BOT, queryId = QUERY)),
        )
        val links = links(state, chats)
        links.openLink("t.me/+abc")
        advanceUntilIdle()
        links.joinInvite()
        advanceUntilIdle()
        return links to chats
    }

    @Test
    fun strazniBotOtevreSvouMiniAplikaciAPoSchvaleniChat() = runTest {
        val state = testState()
        val (_, chats) = guardedInvite(state)

        assertNull(state.value.sheet<InviteSheetState>())
        assertEquals(WebAppRequest(BOT, url = "", launch = false, guardQueryId = QUERY), lastOpenWebApp)
        assertEquals(listOf(QUERY), chats.awaitedQueries)
        assertNull(lastOpenChat)

        chats.guardDecision.complete(GuardDecision.Approved(OTHER))
        advanceUntilIdle()
        assertEquals(ChatTarget(OTHER), lastOpenChat)
    }

    @Test
    fun odmitnutiStraznimBotemMaVlastniVetu() = runTest {
        val state = testState()
        val (_, chats) = guardedInvite(state)

        chats.guardDecision.complete(GuardDecision.Declined)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.join_guard_declined), state.value.error)
        assertNull(lastOpenChat)
    }

    @Test
    fun odlozeniStraznimBotemJeZadost() = runTest {
        val state = testState()
        val (_, chats) = guardedInvite(state)

        chats.guardDecision.complete(GuardDecision.Queued)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.invite_request_sent), state.value.notice)
        assertNull(lastOpenChat)
    }

    @Test
    fun okamziteOdmitnutiNeniObecnaChyba() = runTest {
        val state = testState()
        val chats = FakeChats(
            mapOf("t.me/+abc" to Result.success(InternalLink.Invite("t.me/+abc", invitePreview()))),
            join = Result.success(JoinResult.Declined),
        )
        val links = links(state, chats)
        links.openLink("t.me/+abc")
        advanceUntilIdle()

        links.joinInvite()
        advanceUntilIdle()
        assertNull(state.value.sheet<InviteSheetState>())
        assertEquals(UiText(Res.string.join_guard_declined), state.value.error)
    }

    @Test
    fun sadaSamolepekAProxyJdouSvouCestou() = runTest {
        val state = testState()
        val proxy = ProxyConfig(server = "proxy.example", port = 443, kind = ProxyKind.Mtproto("ee"))
        val chats = FakeChats(
            mapOf(
                "t.me/addstickers/x" to Result.success(InternalLink.StickerSet(77)),
                "t.me/proxy" to Result.success(InternalLink.Proxy(proxy)),
            ),
        )
        val links = links(state, chats)

        links.openLink("t.me/addstickers/x")
        links.openLink("t.me/proxy")
        advanceUntilIdle()

        assertEquals(77L, state.value.openStickerSet)
        assertEquals(listOf(proxy), proxies)
    }

    @Test
    fun startBotaZOdkazuCekaNaTlacitkoStart() = runTest {
        val state = testState()
        links(state, links("t.me/bot?start=x", InternalLink.BotStart(OTHER, "x", autostart = false))).openLink("t.me/bot?start=x")
        advanceUntilIdle()

        assertEquals("x", botStarts.parameter(OTHER).first())
        assertTrue(repository.startedBots.isEmpty())
        assertEquals(ChatTarget(OTHER), lastOpenChat)
    }

    @Test
    fun automatickyStartBotaRovnouOdejde() = runTest {
        val state = testState()
        links(state, links("t.me/bot?start=x", InternalLink.BotStart(TEST_CHAT, "x", autostart = true))).openLink("t.me/bot?start=x")
        advanceUntilIdle()

        assertEquals(listOf(TEST_CHAT to "x"), repository.startedBots)
        assertNull(lastOpenChat, "bot je tenhle chat, není kam odcházet")
    }

    @Test
    fun neznamyUzivatelZOdkazuMaVlastniHlasku() = runTest {
        val state = testState()
        val chats = FakeChats(mapOf("t.me/nikdo" to Result.failure(LinkUserNotFoundException())))
        links(state, chats).openLink("t.me/nikdo")
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun puvodPreposlaniVTemzeChatuSkociJinakOtevreChat() = runTest {
        val state = testState()
        val links = links(state, FakeChats(emptyMap()))

        links.openForwardOrigin(ForwardOrigin.Chat(TEST_CHAT, 4, name = null))
        assertEquals(listOf(4L), jumper.jumps)

        links.openForwardOrigin(ForwardOrigin.Chat(OTHER, 4, name = null))
        advanceUntilIdle()
        assertEquals(ChatTarget(OTHER, 4), lastOpenChat)
    }

    @Test
    fun puvodPreposlaniOdUzivateleOtevreJehoChat() = runTest {
        val state = testState()
        links(state, FakeChats(emptyMap())).openForwardOrigin(ForwardOrigin.User(5, name = "Eva"))
        advanceUntilIdle()

        assertEquals(ChatTarget(1005), lastOpenChat)
    }

    @Test
    fun komentareOtevrouVlaknoDiskusniSkupiny() = runTest {
        val state = testState()
        links(state, FakeChats(emptyMap())).openComments(9)
        advanceUntilIdle()

        val target = assertIs<ChatTarget>(lastOpenChat)
        assertEquals(300L, target.chatId)
        assertEquals(9L, target.threadId)
    }

    @Test
    fun nedostupneKomentareSeOhlasi() = runTest {
        repository.thread = Result.failure(IllegalStateException("MSG_ID_INVALID"))
        val state = testState()
        links(state, FakeChats(emptyMap())).openComments(9)
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertNull(lastOpenChat)
    }

    private fun invitePreview() = InvitePreview(
        title = "Skupina",
        description = "",
        memberCount = 3,
        isChannel = false,
        avatarFileId = null,
        createsJoinRequest = false,
    )

    private companion object {
        const val OTHER = 200L
        const val BOT = 500L
        const val QUERY = 77L
    }
}
