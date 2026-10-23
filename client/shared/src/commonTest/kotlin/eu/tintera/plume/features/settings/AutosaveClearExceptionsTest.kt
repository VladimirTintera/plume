package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AutosaveCandidate
import eu.tintera.plume.core.telegram.AutosaveException
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.AutosaveScope
import eu.tintera.plume.core.telegram.AutosaveScopeSettings
import eu.tintera.plume.core.telegram.AutosaveSettings
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.autosave_save_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Odebrání všech výjimek ukládání do galerie naráz (`clearAutosaveSettingsExceptions`). */
class AutosaveClearExceptionsTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val off = AutosaveScopeSettings(photos = false, videos = false, maxVideoBytes = 512 * 1024)
    private val photos = off.copy(photos = true)

    private class Repository(var stored: AutosaveSettings, val fail: Boolean = false) : AutosaveRepository {
        var cleared = 0
        override suspend fun settings() = Result.success(stored)
        override suspend fun set(scope: AutosaveScope, settings: AutosaveScopeSettings?) = Result.success(Unit)
        override suspend fun clearExceptions(): Result<Unit> {
            cleared++
            if (fail) return Result.failure(IllegalStateException("Unexpected failure"))
            stored = stored.copy(exceptions = emptyList())
            return Result.success(Unit)
        }
        override val incoming: Flow<AutosaveCandidate> = emptyFlow()
    }

    private fun settings() = AutosaveSettings(
        privateChats = off,
        groups = photos,
        channels = off,
        exceptions = listOf(AutosaveException(1, photos), AutosaveException(2, off)),
    )

    @Test
    fun vyjimkyZmiziAVychoziPravidlaZustanou() = runTest {
        val repository = Repository(settings())
        val viewModel = AutosaveViewModel(repository, UnimplementedChatRepository())
        advanceUntilIdle()

        viewModel.clearExceptions()
        advanceUntilIdle()

        assertEquals(1, repository.cleared)
        val state = assertNotNull(viewModel.state.value.settings)
        assertEquals(emptyList(), state.exceptions)
        assertEquals(photos, state.groups)
    }

    @Test
    fun chybaNechaVyjimkyAOhlasiSe() = runTest {
        val repository = Repository(settings(), fail = true)
        val viewModel = AutosaveViewModel(repository, UnimplementedChatRepository())
        advanceUntilIdle()

        viewModel.clearExceptions()
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.settings?.exceptions?.size)
        assertEquals(UiText(Res.string.autosave_save_failed), viewModel.state.value.error)
    }
}
