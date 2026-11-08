package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.provider
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.NoopOnDeviceAi
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.speech.SpeechDownload
import eu.tintera.plume.core.speech.SpeechLanguage
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.core.telegram.OnDeviceFix
import eu.tintera.plume.core.telegram.UnimplementedFileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Co poradí přepis v telefonu, když nejde ([OnDeviceTranscription.check]). */
class OnDeviceTranscriptionTest {

    private class FakeSpeech(val language: SpeechLanguage) : SpeechToText {
        override suspend fun language(languageCode: String) = language
        override suspend fun downloadLanguage(languageCode: String) = SpeechDownload.Done
        override fun transcribe(path: String, languageCode: String): Flow<String> = emptyFlow()
    }

    /** Zařízení, které Gemmu unese, ale stažená není. */
    private object GemmaDownloadable : OnDeviceAi by NoopOnDeviceAi {
        override suspend fun status(variant: ModelVariant) =
            if (variant.provider == AiProvider.Gemma) OnDeviceAiStatus.Downloadable else OnDeviceAiStatus.Ready
    }

    private fun check(
        speech: SpeechLanguage?,
        ai: OnDeviceAi = NoopOnDeviceAi,
        enabled: Boolean = true,
        models: Set<AiProvider> = setOf(AiProvider.GeminiNano),
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-ondevice-${Random.nextLong()}.preferences_pb").toString()
            val settings = DataStoreAppSettings(createSettingsDataStore(path), scope)
            OnDeviceTranscription(
                settings = settings,
                files = UnimplementedFileRepository(),
                local = LocalTranscripts(settings, FakeChatRepository()),
                onDeviceAi = ai,
                speechToText = speech?.let(::FakeSpeech),
            ).check(enabled, AiModels(enabled = models))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stazenyJazykPrepise() {
        assertEquals(OnDeviceCheck(available = true), check(SpeechLanguage.Installed))
    }

    /** Jazyk pro rozpoznávač má desítky MB, Gemma gigabajty — radí se napřed on. */
    @Test
    fun jazykKeStazeniMaPrednostPredGemmou() {
        assertEquals(OnDeviceFix.DownloadSpeechLanguage, check(SpeechLanguage.Downloadable, GemmaDownloadable).fix)
        assertEquals(OnDeviceFix.SpeechLanguageDownloading, check(SpeechLanguage.Downloading, GemmaDownloadable).fix)
    }

    @Test
    fun bezJazykaZbyvaGemma() {
        assertEquals(OnDeviceFix.SwitchToGemma, check(SpeechLanguage.Unsupported, GemmaDownloadable).fix)
        assertEquals(OnDeviceFix.EnableAi, check(null, GemmaDownloadable, enabled = false).fix)
        assertEquals(OnDeviceFix.DownloadGemma, check(SpeechLanguage.Unsupported, GemmaDownloadable, models = setOf(AiProvider.Gemma)).fix)
        // Gemma vedle Nano: přepis je její, i když text počítá Nano.
        assertEquals(
            OnDeviceFix.DownloadGemma,
            check(SpeechLanguage.Unsupported, GemmaDownloadable, models = setOf(AiProvider.GeminiNano, AiProvider.Gemma)).fix,
        )
    }

    /** Bez rozpoznávače i bez Gemmy nepomůže nic v telefonu — jen Premium. */
    @Test
    fun nicNepomuze() {
        assertEquals(OnDeviceCheck(available = false, fix = null), check(SpeechLanguage.Unsupported))
    }
}
