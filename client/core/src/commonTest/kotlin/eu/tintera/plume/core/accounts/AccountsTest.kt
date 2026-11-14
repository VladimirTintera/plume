package eu.tintera.plume.core.accounts

import eu.tintera.plume.core.telegram.AccountInfo
import kotlin.test.assertFalse
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.calls.CallAudioOutput
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.PhoneNumberDetails
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Seznam účtů: co se načte z disku, kdy účet přibude a kdy zmizí. Klienti TDLib jsou náhražky,
 * kontejnery účtů jen s nimi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountsTest {

    private val directory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "accounts-test-${Random.nextLong()}"
    private val registry = AccountRegistry(directory / "accounts.json")
    private val sessions = mutableMapOf<AccountSlot, FakeSession>()
    private val calls = mutableMapOf<AccountSlot, FakeCalls>()

    private fun calls(slot: AccountSlot) = calls.getOrPut(slot) { FakeCalls() }

    /** Klient, který každý slot dostane; dřív vyrobený (test nastavil stav), jinak nový. */
    private fun session(slot: AccountSlot) = sessions.getOrPut(slot) { FakeSession() }

    private fun TestScope.accounts(slotIds: Iterator<String> = listOf("aaaaaa", "bbbbbb", "cccccc").iterator()): Accounts {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return Accounts(
            open = { slot, _ ->
                koinApplication {
                    modules(
                        module {
                            single<TelegramSession> { session(slot) }
                            single<CallRepository> { calls(slot) }
                        },
                    )
                }
            },
            registry = registry,
            scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            newSlotId = { slotIds.next() },
            dispatcher = dispatcher,
        )
    }

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(directory)
    }

    @Test
    fun bezSeznamuJeJenPrimarniUcet() = runTest {
        val accounts = accounts()

        assertEquals(listOf(AccountSlot.Primary), accounts.all.value.map { it.slot })
        assertSame(accounts.all.value.single(), accounts.default.value)
    }

    @Test
    fun ulozenySeznamSeNactiIVychozimUctem() = runTest {
        registry.save(SavedAccounts(listOf(SavedAccount("0", 1), SavedAccount("bbbbbb", 2)), default = "bbbbbb"))

        val accounts = accounts()

        assertEquals(listOf(AccountSlot.Primary, AccountSlot("bbbbbb")), accounts.all.value.map { it.slot })
        assertEquals(AccountSlot("bbbbbb"), accounts.default.value.slot)
        assertEquals(2L, accounts.default.value.userId.value)
    }

    @Test
    fun pridanyUcetPribudeAzPoPrihlaseni() = runTest {
        session(AccountSlot.Primary).signIn(1)
        val accounts = accounts()

        val added = accounts.add()!!
        assertEquals(1, accounts.all.value.size)
        assertSame(added, accounts.adding.value)

        session(added.slot).signIn(2)
        advanceUntilIdle()

        assertEquals(listOf(AccountSlot.Primary, added.slot), accounts.all.value.map { it.slot })
        assertSame(added, accounts.default.value)
        assertNull(accounts.adding.value)
        assertEquals(listOf("0", "aaaaaa"), registry.load()!!.accounts.map { it.slot })
        assertEquals("aaaaaa", registry.load()!!.default)
    }

    @Test
    fun ucetKteryUzVAplikaciJeSeOdhlasi() = runTest {
        session(AccountSlot.Primary).signIn(1)
        val accounts = accounts()
        advanceUntilIdle()
        var rejected = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { accounts.alreadySignedIn.collect { rejected++ } }

        val added = accounts.add()!!
        session(added.slot).signIn(1)
        advanceUntilIdle()

        assertEquals(listOf(AccountSlot.Primary), accounts.all.value.map { it.slot })
        assertNull(accounts.adding.value)
        assertEquals(1, rejected)
        assertEquals(1, session(added.slot).logOuts)
        assertEquals(listOf(true), session(added.slot).shutdowns)
    }

    @Test
    fun odhlasenyUcetVedleJinehoZmizi() = runTest {
        registry.save(SavedAccounts(listOf(SavedAccount("0", 1), SavedAccount("bbbbbb", 2)), default = "0"))
        session(AccountSlot.Primary).signIn(1)
        session(AccountSlot("bbbbbb")).signIn(2)
        val accounts = accounts()

        session(AccountSlot.Primary).stage.value = AuthStage.WaitPhoneNumber
        advanceUntilIdle()

        assertEquals(listOf(AccountSlot("bbbbbb")), accounts.all.value.map { it.slot })
        assertEquals(AccountSlot("bbbbbb"), accounts.default.value.slot)
        assertEquals(listOf(true), session(AccountSlot.Primary).shutdowns)
        assertEquals(listOf("bbbbbb"), registry.load()!!.accounts.map { it.slot })
    }

    @Test
    fun posledniUcetZustaneIOdhlaseny() = runTest {
        session(AccountSlot.Primary).signIn(1)
        val accounts = accounts()

        session(AccountSlot.Primary).stage.value = AuthStage.WaitPhoneNumber
        advanceUntilIdle()

        assertEquals(listOf(AccountSlot.Primary), accounts.all.value.map { it.slot })
        assertTrue(session(AccountSlot.Primary).shutdowns.isEmpty())
    }

    @Test
    fun nadLimitemUcetPridatNejde() = runTest {
        registry.save(SavedAccounts(listOf(SavedAccount("0", 1), SavedAccount("bbbbbb", 2), SavedAccount("cccccc", 3))))
        val accounts = accounts()

        assertNull(accounts.add())
    }

    @Test
    fun zrusenePridaniUkonciKlienta() = runTest {
        val accounts = accounts()
        val added = accounts.add()!!

        accounts.cancelAdd()

        assertNull(accounts.adding.value)
        assertEquals(listOf(true), session(added.slot).shutdowns)
        assertEquals(listOf(AccountSlot.Primary), accounts.all.value.map { it.slot })
    }

    @Test
    fun adresarSlotuMimoSeznamSePriStartuSmaze() = runTest {
        registry.save(SavedAccounts(listOf(SavedAccount("0", 1), SavedAccount("bbbbbb", 2))))
        FileSystem.SYSTEM.createDirectories(directory / "bbbbbb" / "tdlib")
        FileSystem.SYSTEM.createDirectories(directory / "zzzzzz" / "tdlib")

        accounts()

        assertTrue(FileSystem.SYSTEM.exists(directory / "bbbbbb"))
        assertTrue(!FileSystem.SYSTEM.exists(directory / "zzzzzz"))
        assertTrue(FileSystem.SYSTEM.exists(directory / "accounts.json"))
    }

    /** Hovor je přes všechny účty jeden: zvonící, vytáčený i běžící hovor druhého účtu blokuje. */
    @Test
    fun hovorVJinemUctuBlokuje() = runTest {
        registry.save(SavedAccounts(listOf(SavedAccount("0", 1), SavedAccount("bbbbbb", 2))))
        val accounts = accounts()
        val other = AccountSlot("bbbbbb")

        assertFalse(accounts.inCallElsewhere(AccountSlot.Primary))

        for (phase in listOf(CallPhase.Ringing, CallPhase.Calling, CallPhase.Connecting, CallPhase.Active)) {
            calls(other).active.value = ActiveCall(id = 1, userId = 7, isOutgoing = false, phase = phase)
            assertTrue(accounts.inCallElsewhere(AccountSlot.Primary), "$phase")
            // Vlastní hovor účtu jiný hovor neblokuje — to řeší repository účtu samo.
            assertFalse(accounts.inCallElsewhere(other), "$phase")
        }

        calls(other).active.value = ActiveCall(id = 1, userId = 7, isOutgoing = false, phase = CallPhase.Ended)
        assertFalse(accounts.inCallElsewhere(AccountSlot.Primary))
    }

    @Test
    fun pushDostaneUcetPodlePrijemce() = runTest {
        registry.save(
            SavedAccounts(listOf(SavedAccount("0", 1, pushReceiverId = 101), SavedAccount("bbbbbb", 2, pushReceiverId = 202))),
        )
        val accounts = accounts()

        assertEquals(AccountSlot("bbbbbb"), accounts.pushTarget(202)?.slot)
        // Nešifrovaný push nese user id.
        assertEquals(AccountSlot.Primary, accounts.pushTarget(1)?.slot)
        assertNull(accounts.pushTarget(999))
        assertNull(accounts.pushTarget(null))
    }

    @Test
    fun sJedinymUctemDostanePushOn() = runTest {
        val accounts = accounts()

        assertEquals(AccountSlot.Primary, accounts.pushTarget(null)?.slot)
        assertEquals(AccountSlot.Primary, accounts.pushTarget(999)?.slot)
    }

    private class FakeCalls : CallRepository {
        override val active = MutableStateFlow<ActiveCall?>(null)
        override val supported = true
        override val supportsVideo = false
        override suspend fun call(userId: Long, video: Boolean) = error("call")
        override suspend fun accept(camera: Boolean) = error("accept")
        override suspend fun hangUp() = error("hangUp")
        override fun setMuted(muted: Boolean) = error("setMuted")
        override fun setHeld(held: Boolean) = error("setHeld")
        override suspend fun resume() = error("resume")
        override fun setResumeHandler(handler: (suspend () -> Boolean)?) = error("setResumeHandler")
        override fun setOutput(output: CallAudioOutput) = error("setOutput")
        override fun setCamera(on: Boolean) = error("setCamera")
        override fun previewCamera(on: Boolean) = error("previewCamera")
        override fun switchCamera() = error("switchCamera")
        override fun reportFailure(userId: Long, video: Boolean, reason: UiText) = error("reportFailure")
    }

    private class FakeSession : TelegramSession {
        val stage = MutableStateFlow<AuthStage>(AuthStage.Connecting)
        override val authStage: StateFlow<AuthStage> = stage
        var userId: Long? = null
        var logOuts = 0
        val shutdowns = mutableListOf<Boolean>()

        fun signIn(id: Long) {
            userId = id
            stage.value = AuthStage.Ready
        }

        override suspend fun me(): Result<AccountInfo> =
            userId?.let { Result.success(AccountInfo(it, "Účet", "$it", "", null)) } ?: Result.failure(IllegalStateException("odhlášený"))

        override suspend fun logOut(): Result<Unit> {
            logOuts++
            stage.value = AuthStage.WaitPhoneNumber
            return Result.success(Unit)
        }

        override suspend fun shutdown(destroy: Boolean) {
            shutdowns += destroy
        }

        override suspend fun start() = Unit
        override suspend fun submitPhoneNumber(phoneNumber: String) = error("submitPhoneNumber")
        override suspend fun submitCode(code: String) = error("submitCode")
        override suspend fun resendCode() = error("resendCode")
        override suspend fun submitEmailAddress(email: String) = error("submitEmailAddress")
        override suspend fun submitEmailCode(code: String) = error("submitEmailCode")
        override suspend fun resendEmailCode() = error("resendEmailCode")
        override suspend fun resetEmailAddress() = error("resetEmailAddress")
        override suspend fun submitPassword(password: String) = error("submitPassword")
        override suspend fun requestPasswordRecovery() = error("requestPasswordRecovery")
        override suspend fun checkPasswordRecoveryCode(code: String) = error("checkPasswordRecoveryCode")
        override suspend fun recoverPassword(code: String, newPassword: String, newHint: String) = error("recoverPassword")
        override suspend fun startQrLogin() = error("startQrLogin")
        override suspend fun restartLogin() = error("restartLogin")
        override suspend fun countries(): Result<List<Country>> = error("countries")
        override suspend fun suggestedCountryCode(): Result<String> = error("suggestedCountryCode")
        override suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails> = error("phoneNumberInfo")
    }
}
