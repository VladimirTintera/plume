package eu.tintera.plume.core.ai

import eu.tintera.ai.core.AiFailureReason
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.ai.core.isReportable
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.ai_failure_busy
import eu.tintera.plume.resources.ai_failure_gemma
import eu.tintera.plume.resources.ai_failure_nano
import eu.tintera.plume.resources.ai_failure_nano_code
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Selhání modelu musí říct, co se stalo — v aplikaci i v Crashlytics. Dřív bylo všechno jedna
 * věta „Shrnutí se nepovedlo" a rozbitý Gemini Nano v AICore se nedal od vytíženého poznat.
 */
class OnDeviceAiFailureTest {

    @Test
    fun rozbityNanoRikaKodIKdeToSpravit() {
        val e = OnDeviceAiException(AiFailureReason.ModelError, ModelVariant.GeminiNano(), code = 5)
        assertEquals(UiText(Res.string.ai_failure_nano_code, "5"), e.userMessage())
    }

    @Test
    fun rozbityNanoBezKoduMaVetuBezCisla() {
        val e = OnDeviceAiException(AiFailureReason.ModelError, ModelVariant.GeminiNano())
        assertEquals(UiText(Res.string.ai_failure_nano), e.userMessage())
    }

    @Test
    fun rozbitaGemmaRadiSmazatModelVAplikaci() {
        // Gemmu stahuje aplikace, takže náprava je u nás, ne v AICore.
        val e = OnDeviceAiException(AiFailureReason.ModelError, LiteRtModel.E2B.variant)
        assertEquals(UiText(Res.string.ai_failure_gemma), e.userMessage())
    }

    @Test
    fun vytizenyModelMaVlastniVetuBezOhleduNaPoskytovatele() {
        val e = OnDeviceAiException(AiFailureReason.Busy, ModelVariant.GeminiNano(), code = 9)
        assertEquals(UiText(Res.string.ai_failure_busy), e.userMessage())
    }

    @Test
    fun doCrashlyticsJenRozbityModelNeOcekavaneStavy() {
        assertTrue(OnDeviceAiException(AiFailureReason.ModelError, ModelVariant.GeminiNano()).isReportable)
        assertTrue(OnDeviceAiException(AiFailureReason.NeedsUpdate, ModelVariant.GeminiNano()).isReportable)
        assertTrue(OnDeviceAiException(AiFailureReason.Unavailable, ModelVariant.GeminiNano()).isReportable)
        assertFalse(OnDeviceAiException(AiFailureReason.Busy, ModelVariant.GeminiNano()).isReportable)
        assertFalse(OnDeviceAiException(AiFailureReason.TooLarge, ModelVariant.GeminiNano()).isReportable)
        assertFalse(OnDeviceAiException(AiFailureReason.NotEnoughSpace, LiteRtModel.E2B.variant).isReportable)
    }

    @Test
    fun zpravaVyjimkyJePodleCehoSeDaHledatVCrashlytics() {
        val e = OnDeviceAiException(AiFailureReason.ModelError, ModelVariant.GeminiNano(), code = 5)
        assertEquals("GeminiNano failed: ModelError (code 5)", e.message)
    }
}
