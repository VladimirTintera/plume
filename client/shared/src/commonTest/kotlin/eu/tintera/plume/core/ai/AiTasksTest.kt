package eu.tintera.plume.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Který model počítá kterou úlohu ([chooseProvider], [variantFor]). */
class AiTasksTest {

    private val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma))

    private fun statuses(vararg pairs: Pair<AiProvider, OnDeviceAiStatus>): suspend (AiProvider) -> OnDeviceAiStatus {
        val map = pairs.toMap()
        return { map[it] ?: OnDeviceAiStatus.Unsupported }
    }

    private val bothReady = statuses(AiProvider.GeminiNano to OnDeviceAiStatus.Ready, AiProvider.Gemma to OnDeviceAiStatus.Ready)

    /** Hlavní důvod celé věci: Nano na text, Gemma jen na to, co Nano neumí. */
    @Test
    fun automatickyTextNanoZvukAObrazGemma() = runTest {
        assertEquals(AiProvider.GeminiNano, chooseProvider(AiTask.Summary, both, bothReady))
        assertEquals(AiProvider.GeminiNano, chooseProvider(AiTask.Replies, both, bothReady))
        assertEquals(AiProvider.Gemma, chooseProvider(AiTask.Transcription, both, bothReady))
        assertEquals(AiProvider.Gemma, chooseProvider(AiTask.PhotoDescription, both, bothReady))
    }

    /** Nano, které se ještě stahuje: text mezitím počítá připravená Gemma. */
    @Test
    fun automatickyBerePripravenyModel() = runTest {
        val nanoDownloading = statuses(AiProvider.GeminiNano to OnDeviceAiStatus.Downloading, AiProvider.Gemma to OnDeviceAiStatus.Ready)
        assertEquals(AiProvider.Gemma, chooseProvider(AiTask.Summary, both, nanoDownloading))
    }

    /** Žádný připravený: první, který zařízení umí — funkce pak nabídne stažení. */
    @Test
    fun bezPripravenehoPrvniDostupny() = runTest {
        val nothingReady = statuses(AiProvider.GeminiNano to OnDeviceAiStatus.Unsupported, AiProvider.Gemma to OnDeviceAiStatus.Downloadable)
        assertEquals(AiProvider.Gemma, chooseProvider(AiTask.Summary, both, nothingReady))
    }

    /** Ruční volba platí, i když model zrovna není připravený — nic se tiše nemění. */
    @Test
    fun rucniPrirazeniPlati() = runTest {
        val models = both.copy(assigned = mapOf(AiTask.Summary to AiProvider.Gemma))
        val gemmaDownloading = statuses(AiProvider.GeminiNano to OnDeviceAiStatus.Ready, AiProvider.Gemma to OnDeviceAiStatus.Downloading)
        assertEquals(AiProvider.Gemma, chooseProvider(AiTask.Summary, models, gemmaDownloading))
        assertEquals(AiProvider.GeminiNano, chooseProvider(AiTask.Replies, models, gemmaDownloading))
    }

    /** Přiřazení k vypnutému modelu, nebo k modelu, který úlohu neumí, se nepočítá. */
    @Test
    fun neplatnePrirazeniJedeAutomaticky() = runTest {
        val gemmaOff = AiModels(enabled = setOf(AiProvider.GeminiNano), assigned = mapOf(AiTask.Summary to AiProvider.Gemma))
        assertEquals(AiProvider.GeminiNano, chooseProvider(AiTask.Summary, gemmaOff, bothReady))
        val nanoForAudio = both.copy(assigned = mapOf(AiTask.Transcription to AiProvider.GeminiNano))
        assertEquals(AiProvider.Gemma, chooseProvider(AiTask.Transcription, nanoForAudio, bothReady))
    }

    /**
     * Zvuk jen s Nano: kandidát není, vrátí se zapnutý model. Ten zvuk neumí (`canTranscribe`
     * dá `false`), takže se přepis nenabídne — volající nic zvláštního řešit nemusí.
     */
    @Test
    fun bezKandidataZapnutyModel() = runTest {
        val onlyNano = AiModels(enabled = setOf(AiProvider.GeminiNano))
        assertEquals(AiProvider.GeminiNano, chooseProvider(AiTask.Transcription, onlyNano, bothReady))
    }

    /** Varianta nese volby modelu, ať ji dostane kterýkoli runtime. */
    @Test
    fun variantaNeseVolby() = runTest {
        val ai = object : OnDeviceAi by NoopOnDeviceAi {
            override suspend fun status(variant: ModelVariant) = OnDeviceAiStatus.Ready
        }
        val models = both.copy(fast = true, gemma = GemmaModel.E4B)
        assertEquals(
            ModelVariant(provider = AiProvider.GeminiNano, fast = true, gemma = GemmaModel.E4B),
            ai.variantFor(AiTask.Summary, models),
        )
        assertEquals(
            ModelVariant(provider = AiProvider.Gemma, fast = true, gemma = GemmaModel.E4B),
            ai.variantFor(AiTask.Transcription, models),
        )
    }
}
