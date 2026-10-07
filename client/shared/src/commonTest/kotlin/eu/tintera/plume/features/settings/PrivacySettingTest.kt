package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.PrivacyBase
import eu.tintera.plume.core.telegram.PrivacyChoice
import eu.tintera.plume.core.telegram.PrivacyMember
import eu.tintera.plume.core.telegram.PrivacyRepository
import eu.tintera.plume.core.telegram.PrivacyRule
import eu.tintera.plume.core.telegram.PrivacySetting
import eu.tintera.plume.features.chats.FakeChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun chat(id: Long, title: String, kind: ChatKind) = ChatSummary(
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

    /** Server, který zápisy jen zaznamená; update pošle, až mu to test řekne. */
    private class FakePrivacy(initial: List<PrivacyRule>) : PrivacyRepository {
        val server = MutableStateFlow(Result.success(initial))
        val writes = mutableListOf<List<PrivacyRule>>()
        var failWrites = false

        override fun rules(setting: PrivacySetting): Flow<Result<List<PrivacyRule>>> = server
        override suspend fun setRules(setting: PrivacySetting, rules: List<PrivacyRule>): Result<Unit> {
            writes += rules
            return if (failWrites) Result.failure(IllegalStateException("server")) else Result.success(Unit)
        }
        override suspend fun showsReadDate() = Result.success(true)
        override suspend fun setShowsReadDate(show: Boolean) = Result.success(Unit)
        override suspend fun members(userIds: List<Long>, chatIds: List<Long>) = emptyList<PrivacyMember>()
        override suspend fun memberOf(chatId: Long) = Result.failure<PrivacyMember>(IllegalStateException())
    }

    @Test
    fun hledaniPodleCislaJenSeSkrytymCislem() {
        val visible = mapOf(PrivacySetting.PhoneNumber to PrivacyChoice(PrivacyBase.Everybody))
        val hidden = mapOf(PrivacySetting.PhoneNumber to PrivacyChoice(PrivacyBase.Contacts))
        assertFalse(privacyRowShown(PrivacySetting.FindByPhone, visible))
        assertTrue(privacyRowShown(PrivacySetting.FindByPhone, hidden))
        // Dokud se číslo nenačte, řádek nic netvrdí.
        assertFalse(privacyRowShown(PrivacySetting.FindByPhone, emptyMap()))
        assertTrue(privacyRowShown(PrivacySetting.LastSeen, emptyMap()))
    }

    @Test
    fun vyjimkouNemuzeBytKanalUlozeneZpravyAniKdoUzVSeznamuJe() {
        val contacts = listOf(chat(1, "Jana", ChatKind.Private), chat(999, "Já", ChatKind.Private), chat(2, "Petr", ChatKind.Private))
        val groups = listOf(chat(-10, "Rodina", ChatKind.Group), chat(-20, "Zprávy", ChatKind.Channel))
        val (shownContacts, shownGroups, others) = exceptionCandidates(
            query = "",
            contacts = contacts,
            groups = groups,
            searchResults = listOf(chat(3, "Cizí", ChatKind.Private)),
            savedMessagesChatId = 999,
            excluded = setOf(2),
        )
        assertEquals(listOf(1L), shownContacts.map { it.id })
        assertEquals(listOf(-10L), shownGroups.map { it.id })
        // Bez dotazu se nikdo cizí nenabízí.
        assertEquals(emptyList(), others)
    }

    @Test
    fun hledaniBezDiakritikyAOstatniBezZnamych() {
        val contacts = listOf(chat(1, "Šárka", ChatKind.Private), chat(2, "Petr", ChatKind.Private))
        val (shownContacts, _, others) = exceptionCandidates(
            query = "sar",
            contacts = contacts,
            groups = emptyList(),
            searchResults = listOf(chat(1, "Šárka", ChatKind.Private), chat(5, "Sára", ChatKind.Private), chat(5, "Sára", ChatKind.Private)),
            savedMessagesChatId = null,
            excluded = emptySet(),
        )
        assertEquals(listOf(1L), shownContacts.map { it.id })
        assertEquals(listOf(5L), others.map { it.id })
    }

    @Test
    fun rychlePrepnutiTamAZpatkySeZapise() = runTest {
        val privacy = FakePrivacy(listOf(PrivacyRule.AllowContacts))
        val viewModel = PrivacySettingViewModel(PrivacySetting.LastSeen, privacy, FakeChatRepository())
        advanceUntilIdle()

        viewModel.chooseBase(PrivacyBase.Nobody)
        viewModel.chooseBase(PrivacyBase.Contacts)
        advanceUntilIdle()

        // Server update mezitím nepřišel; návrat musí odejít, jinak by na serveru zůstalo „Nikdo".
        assertEquals(listOf(listOf(PrivacyRule.RestrictAll), listOf(PrivacyRule.AllowContacts)), privacy.writes)
        assertEquals(PrivacyBase.Contacts, viewModel.state.value.choice?.base)
    }

    @Test
    fun selhanyZapisVratiVolbu() = runTest {
        val privacy = FakePrivacy(listOf(PrivacyRule.AllowAll)).apply { failWrites = true }
        val viewModel = PrivacySettingViewModel(PrivacySetting.ProfilePhoto, privacy, FakeChatRepository())
        advanceUntilIdle()

        viewModel.chooseBase(PrivacyBase.Nobody)
        advanceUntilIdle()

        assertEquals(PrivacyBase.Everybody, viewModel.state.value.choice?.base)
        assertNotNull(viewModel.state.value.error)

        // Po selhání se další pokus o totéž musí poslat znovu.
        privacy.failWrites = false
        viewModel.chooseBase(PrivacyBase.Nobody)
        advanceUntilIdle()
        assertEquals(2, privacy.writes.size)
    }

    @Test
    fun skryteVyjimkyPrezijiPrepnutiNaVsechny() = runTest {
        val privacy = FakePrivacy(listOf(PrivacyRule.AllowUsers(listOf(1)), PrivacyRule.AllowContacts))
        val viewModel = PrivacySettingViewModel(PrivacySetting.LastSeen, privacy, FakeChatRepository())
        advanceUntilIdle()

        viewModel.chooseBase(PrivacyBase.Everybody)
        advanceUntilIdle()
        // Server potvrdí jen platná pravidla.
        privacy.server.value = Result.success(listOf(PrivacyRule.AllowAll))
        advanceUntilIdle()
        viewModel.chooseBase(PrivacyBase.Contacts)
        advanceUntilIdle()

        assertEquals(listOf(PrivacyRule.AllowUsers(listOf(1)), PrivacyRule.AllowContacts), privacy.writes.last())
    }
}
