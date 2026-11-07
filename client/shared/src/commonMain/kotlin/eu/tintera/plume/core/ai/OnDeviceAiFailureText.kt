package eu.tintera.plume.core.ai

import eu.tintera.ai.core.AiFailureReason
import eu.tintera.ai.core.AiProvider
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.ai_failure_apple
import eu.tintera.plume.resources.ai_failure_aya
import eu.tintera.plume.resources.ai_failure_busy
import eu.tintera.plume.resources.ai_failure_empty
import eu.tintera.plume.resources.ai_failure_eurollm
import eu.tintera.plume.resources.ai_failure_gemma
import eu.tintera.plume.resources.ai_failure_language
import eu.tintera.plume.resources.ai_failure_nano
import eu.tintera.plume.resources.ai_failure_nano_code
import eu.tintera.plume.resources.ai_failure_needs_update
import eu.tintera.plume.resources.ai_failure_no_space
import eu.tintera.plume.resources.ai_failure_qwen
import eu.tintera.plume.resources.ai_failure_refused
import eu.tintera.plume.resources.ai_failure_too_large
import eu.tintera.plume.resources.ai_failure_unavailable

/*
 * Věty k selháním modelu v zařízení. Důvod ([AiFailureReason]) určuje knihovna (ai-engine :ai-core),
 * texty jsou Plume — knihovna je sdílená s aplikacemi, které mají vlastní prostředky.
 */

/**
 * Věta pro uživatele. U poškozeného modelu říká, KDE se to spravuje — tam je chyba mimo
 * aplikaci a „zkusit znovu" samo nepomůže.
 */
fun OnDeviceAiException.userMessage(): UiText = when (reason) {
    AiFailureReason.Busy -> UiText(Res.string.ai_failure_busy)
    AiFailureReason.TooLarge -> UiText(Res.string.ai_failure_too_large)
    AiFailureReason.NotEnoughSpace -> UiText(Res.string.ai_failure_no_space)
    AiFailureReason.NeedsUpdate -> UiText(Res.string.ai_failure_needs_update)
    AiFailureReason.Unavailable -> UiText(Res.string.ai_failure_unavailable)
    AiFailureReason.UnsupportedLanguage -> UiText(Res.string.ai_failure_language)
    AiFailureReason.Refused -> UiText(Res.string.ai_failure_refused)
    AiFailureReason.ModelError -> when (provider) {
        AiProvider.GeminiNano -> code
            ?.let { UiText(Res.string.ai_failure_nano_code, it.toString()) }
            ?: UiText(Res.string.ai_failure_nano)
        AiProvider.Gemma -> UiText(Res.string.ai_failure_gemma)
        AiProvider.Qwen -> UiText(Res.string.ai_failure_qwen)
        AiProvider.EuroLlm -> UiText(Res.string.ai_failure_eurollm)
        AiProvider.Aya -> UiText(Res.string.ai_failure_aya)
        AiProvider.Apple -> UiText(Res.string.ai_failure_apple)
    }
}

/** Model doběhl, ale nenapsal nic. */
val emptyOutputMessage: UiText get() = UiText(Res.string.ai_failure_empty)
