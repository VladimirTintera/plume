package eu.tintera.plume.features.channellimit

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.PublicChat
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.public_limit_failed
import eu.tintera.plume.resources.public_limit_release_failed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Limit vlastních veřejných chatů: kdy se sheet otevře (`PublicLinkLimitCheck`, sdílený založením
 * i úpravou chatu) a co v něm dělá odebrání odkazu (`PublicLinkLimitViewModel`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublicLinkLimitTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun channel(id: Long) = PublicChat(
        chat = ChatSummary(
            id = id,
            title = "Kanál $id",
            unreadCount = 0,
            lastMessagePreview = null,
            isPinned = false,
            order = id,
            avatarFileId = null,
            isMuted = false,
            isMarkedAsUnread = false,
            kind = ChatKind.Channel,
            lastMessageTimestamp = null,
            presence = null,
            activity = null,
            draft = null,
        ),
        username = "kanal_$id",
    )

    /** Server s limitem a vlastními veřejnými chaty; odebrání odkazu u [failing] selže. */
    private class Server(
        private val canCreate: Result<Boolean> = Result.success(true),
        private val owned: Result<List<PublicChat>> = Result.success(emptyList()),
        private val failing: Set<Long> = emptySet(),
    ) : FakeChatRepository() {
        var checks = 0
        val released = mutableListOf<Long>()

        /** Když je nastavené, odpověď na kontrolu limitu čeká na něj. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun canCreatePublicChat(): Result<Boolean> {
            checks++
            gate?.await()
            return canCreate
        }

        override suspend fun createdPublicChats() = owned

        override suspend fun releasePublicLink(chatId: Long): Result<Unit> {
            released += chatId
            return if (chatId in failing) Result.failure(IllegalStateException("CHAT_ADMIN_REQUIRED")) else Result.success(Unit)
        }
    }

    @Test
    fun limitOtevreSheetHnedPoVolbeVerejny() = runTest {
        val server = Server(canCreate = Result.success(false))
        val check = PublicLinkLimitCheck(server, this)
        check.publicChosen { true }
        advanceUntilIdle()
        assertEquals(1, server.checks)
        assertTrue(check.reached)

        check.closed()
        assertFalse(check.reached)
    }

    @Test
    fun podLimitemNebNezjistenymSeSheetNeotevre() = runTest {
        val allowed = PublicLinkLimitCheck(Server(canCreate = Result.success(true)), this)
        allowed.publicChosen { true }
        // Nezjištěný limit neblokuje — rozhodne server při uložení.
        val unknown = PublicLinkLimitCheck(Server(canCreate = Result.failure(IllegalStateException("Timeout"))), this)
        unknown.publicChosen { true }
        advanceUntilIdle()
        assertFalse(allowed.reached)
        assertFalse(unknown.reached)
    }

    @Test
    fun navratNaSoukromyPredOdpovediSheetNeotevre() = runTest {
        val server = Server(canCreate = Result.success(false)).apply { gate = CompletableDeferred() }
        val check = PublicLinkLimitCheck(server, this)
        var public = true
        check.publicChosen { public }
        advanceUntilIdle()

        // Odpověď ještě nepřišla a uživatel přepnul zpátky — otevřený sheet by překvapil.
        public = false
        check.privateChosen()
        server.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(check.reached)

        // I kdyby se zrušení minulo s odpovědí, rozhoduje stav přepínače po ní.
        val late = PublicLinkLimitCheck(Server(canCreate = Result.success(false)), this)
        late.publicChosen { public }
        advanceUntilIdle()
        assertFalse(late.reached)
    }

    @Test
    fun limitHlasenyAzPriUlozeniOtevreSheet() = runTest {
        val check = PublicLinkLimitCheck(Server(), this)
        check.hit()
        assertTrue(check.reached)
    }

    @Test
    fun odebraniOdkazuSePotvrzujeAUvolniMisto() = runTest {
        val server = Server(owned = Result.success(listOf(channel(1), channel(2))))
        val viewModel = PublicLinkLimitViewModel(server)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(listOf("kanal_1", "kanal_2"), viewModel.state.value.chats?.map { it.username })

        viewModel.askRelease(channel(2))
        assertEquals(channel(2), viewModel.state.value.confirming)
        assertTrue(server.released.isEmpty(), "bez potvrzení se nic neodebírá")

        viewModel.confirmRelease()
        advanceUntilIdle()
        assertEquals(listOf(2L), server.released)
        assertTrue(viewModel.state.value.released)
        assertNull(viewModel.state.value.releasing)
    }

    @Test
    fun zrusenePotvrzeniNicNeodebere() = runTest {
        val server = Server(owned = Result.success(listOf(channel(1))))
        val viewModel = PublicLinkLimitViewModel(server)
        viewModel.load()
        advanceUntilIdle()

        viewModel.askRelease(channel(1))
        viewModel.cancelRelease()
        viewModel.confirmRelease()
        advanceUntilIdle()
        assertTrue(server.released.isEmpty())
        assertFalse(viewModel.state.value.released)
    }

    @Test
    fun neuspesneOdebraniNechaSheetOtevrenySHlaskou() = runTest {
        val server = Server(owned = Result.success(listOf(channel(1))), failing = setOf(1L))
        val viewModel = PublicLinkLimitViewModel(server)
        viewModel.load()
        advanceUntilIdle()

        viewModel.askRelease(channel(1))
        viewModel.confirmRelease()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.released)
        assertNull(viewModel.state.value.releasing)
        assertEquals(UiText(Res.string.public_limit_release_failed), viewModel.state.value.error)
        assertEquals(listOf(channel(1)), viewModel.state.value.chats, "chat zůstává v seznamu, jde to zkusit znovu")
    }

    @Test
    fun nenactenySeznamMaHlasku() = runTest {
        val viewModel = PublicLinkLimitViewModel(Server(owned = Result.failure(IllegalStateException("Timeout"))))
        viewModel.load()
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.state.value.chats)
        assertEquals(UiText(Res.string.public_limit_failed), viewModel.state.value.error)
    }
}
