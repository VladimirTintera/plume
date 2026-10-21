package eu.tintera.plume.features.oauth

import eu.tintera.plume.core.telegram.OauthLogin
import eu.tintera.plume.core.telegram.OauthRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OauthViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeOauth(
        var request: OauthRequest,
        var acceptResult: Result<String> = Result.success("https://web.example/ok"),
        var codeResult: Result<Unit> = Result.success(Unit),
    ) : OauthLogin {
        override val pending = MutableStateFlow<List<String>>(emptyList())
        val checked = mutableListOf<String>()
        val accepted = mutableListOf<List<Any>>()
        val declined = mutableListOf<String>()

        override fun open(url: String) = pending.update { it + url }
        override fun dismiss(url: String) = pending.update { it - url }
        override suspend fun requestInfo(url: String) = Result.success(request.copy(url = url))
        override suspend fun checkMatchCode(url: String, code: String): Result<Unit> {
            checked += code
            return codeResult
        }
        override suspend fun accept(url: String, matchCode: String, allowWriteAccess: Boolean, allowPhoneNumber: Boolean): Result<String> {
            accepted += listOf(url, matchCode, allowWriteAccess, allowPhoneNumber)
            return acceptResult
        }
        override suspend fun decline(url: String): Result<Unit> {
            declined += url
            return Result.success(Unit)
        }
    }

    private fun request(matchCodeFirst: Boolean = false, codes: List<String> = emptyList(), write: Boolean = true) = OauthRequest(
        url = "",
        domain = "web.example",
        appName = null,
        fromApp = false,
        botName = "@web_bot",
        requestWriteAccess = write,
        requestPhoneNumber = false,
        browser = "Chrome",
        platform = "Windows",
        ipAddress = "1.2.3.4",
        location = "Praha",
        matchCodeFirst = matchCodeFirst,
        matchCodes = codes,
    )

    private val opened = mutableListOf<String>()

    @Test
    fun prihlaseniOtevreAdresuOdWebuAZadostZmizi() = runTest {
        val oauth = FakeOauth(request())
        val viewModel = OauthViewModel(oauth) { opened += it }
        oauth.open("tg://oauth/1")
        advanceUntilIdle()
        assertIs<OauthStage.Confirm>(viewModel.state.value?.stage)

        viewModel.accept(allowWriteAccess = true, allowPhoneNumber = true)
        advanceUntilIdle()

        // Telefon web nechtěl, takže se nepovolí, ani když je zaškrtnutý.
        assertEquals(listOf(listOf<Any>("tg://oauth/1", "", true, false)), oauth.accepted)
        assertEquals(listOf("https://web.example/ok"), opened)
        assertNull(viewModel.state.value)
    }

    @Test
    fun prazdnaAdresaZnamenaHotovo() = runTest {
        val oauth = FakeOauth(request(), acceptResult = Result.success(""))
        val viewModel = OauthViewModel(oauth) { opened += it }
        oauth.open("tg://oauth/1")
        advanceUntilIdle()
        viewModel.accept(allowWriteAccess = false, allowPhoneNumber = false)
        advanceUntilIdle()

        assertEquals(OauthStage.Done, viewModel.state.value?.stage)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun cisloSeOveriPredPotvrzenim() = runTest {
        val oauth = FakeOauth(request(matchCodeFirst = true, codes = listOf("12", "34", "56")))
        val viewModel = OauthViewModel(oauth) { opened += it }
        oauth.open("tg://oauth/1")
        advanceUntilIdle()
        assertEquals(OauthStage.MatchCode, viewModel.state.value?.stage)

        viewModel.pickCode("34")
        advanceUntilIdle()
        assertEquals(listOf("34"), oauth.checked)
        assertEquals(OauthStage.Confirm, viewModel.state.value?.stage)

        viewModel.accept(allowWriteAccess = true, allowPhoneNumber = false)
        advanceUntilIdle()
        assertEquals("34", oauth.accepted.single()[1])
    }

    @Test
    fun spatneCisloZadostZrusi() = runTest {
        val oauth = FakeOauth(request(matchCodeFirst = true, codes = listOf("12", "34")), codeResult = Result.failure(IllegalStateException("CODE_INVALID")))
        val viewModel = OauthViewModel(oauth) { opened += it }
        oauth.open("tg://oauth/1")
        advanceUntilIdle()
        viewModel.pickCode("12")
        advanceUntilIdle()

        assertIs<OauthStage.Failed>(viewModel.state.value?.stage)
        assertEquals(emptyList(), oauth.accepted)
    }

    @Test
    fun zavreniBezRozhodnutiZadostOdmitne() = runTest {
        val oauth = FakeOauth(request())
        val viewModel = OauthViewModel(oauth) { opened += it }
        oauth.open("tg://oauth/1")
        advanceUntilIdle()

        viewModel.close()
        advanceUntilIdle()

        assertEquals(listOf("tg://oauth/1"), oauth.declined)
        assertNull(viewModel.state.value)
    }

    @Test
    fun dalsiZadostPrijdeNaRadu() = runTest {
        val oauth = FakeOauth(request())
        val viewModel = OauthViewModel(oauth) { opened += it }
        oauth.open("tg://oauth/1")
        oauth.open("tg://oauth/2")
        advanceUntilIdle()
        assertEquals("tg://oauth/1", viewModel.state.value?.url)

        viewModel.decline()
        advanceUntilIdle()
        assertEquals("tg://oauth/2", viewModel.state.value?.url)
    }
}
