package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.ChatNotificationOption
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.NotificationException
import eu.tintera.plume.core.telegram.NotificationScope
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.telegram.NotificationSoundRepository
import eu.tintera.plume.core.telegram.NotificationTone
import eu.tintera.plume.core.telegram.ScopeSettings
import eu.tintera.plume.core.telegram.ToneLimits
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notif_sound_upload_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/** Výběr zvuku notifikací: zápis do rozsahu, nahrání a jeho chyba. */
class NotificationSoundsViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun vybranyZvukSeZapiseDoRozsahu() = runTest {
        val settings = SoundSettings()
        val viewModel = NotificationSoundsViewModel(FakeTones(), settings)
        advanceUntilIdle()

        viewModel.open(NotificationScope.Groups)
        assertEquals(NotificationScope.Groups, viewModel.state.value.picking)
        viewModel.select(NotificationScope.Groups, 77)
        advanceUntilIdle()

        assertEquals(listOf(NotificationScope.Groups to 77L), settings.sounds)
    }

    @Test
    fun neuspesneNahraniPripomeneStrop() = runTest {
        val tones = FakeTones(addResult = Result.failure(IllegalStateException("RINGTONE_INVALID")))
        val viewModel = NotificationSoundsViewModel(tones, SoundSettings())
        advanceUntilIdle()

        viewModel.upload("/tmp/zvuk.mp3")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/zvuk.mp3"), tones.uploaded)
        assertEquals(UiText(Res.string.notif_sound_upload_failed, 300, 5), viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.uploading)
    }
}

private class FakeTones(private val addResult: Result<NotificationTone> = Result.success(NotificationTone(1, "Zvonek", 2))) : NotificationSoundRepository {
    val uploaded = mutableListOf<String>()
    override val tones: Flow<List<NotificationTone>> = MutableStateFlow(emptyList())
    override suspend fun limits() = ToneLimits(maxBytes = 300 * 1024, maxSeconds = 5, maxCount = 100)
    override suspend fun add(path: String): Result<NotificationTone> {
        uploaded += path
        return addResult
    }
    override suspend fun remove(id: Long) = Result.success(Unit)
    override suspend fun tonePath(id: Long): String? = null
    override suspend fun toneTitle(id: Long): String? = null
}

private class SoundSettings : NotificationSettingsRepository {
    val sounds = mutableListOf<Pair<NotificationScope, Long>>()
    override suspend fun setScopeSound(scope: NotificationScope, soundId: Long): Result<Unit> {
        sounds += scope to soundId
        return Result.success(Unit)
    }
    override fun scopeSettings(scope: NotificationScope): Flow<ScopeSettings?> = flowOf(null)
    override suspend fun setScopeSettings(scope: NotificationScope, settings: ScopeSettings) = Result.success(Unit)
    override suspend fun exceptions(scope: NotificationScope): Result<List<NotificationException>> = Result.success(emptyList())
    override suspend fun resetToDefault(chatId: Long) = Result.success(Unit)
    override suspend fun contactJoinedEnabled() = Result.success(true)
    override suspend fun setContactJoinedEnabled(enabled: Boolean) = Result.success(Unit)
    override fun chatSettings(chatId: Long): Flow<ChatNotificationPrefs?> = flowOf(null)
    override suspend fun setChatOption(chatId: Long, option: ChatNotificationOption, value: Boolean) = Result.success(Unit)
    override fun topicSettings(chatId: Long, topicId: Int): Flow<ChatNotificationPrefs?> = flowOf(null)
    override suspend fun setTopicMute(chatId: Long, topicId: Int, duration: Duration?) = Result.success(Unit)
    override suspend fun setTopicOption(chatId: Long, topicId: Int, option: ChatNotificationOption, value: Boolean) = Result.success(Unit)
    override suspend fun resetTopic(chatId: Long, topicId: Int) = Result.success(Unit)
}
