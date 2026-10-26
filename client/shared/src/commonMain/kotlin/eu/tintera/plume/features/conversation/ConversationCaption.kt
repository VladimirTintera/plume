package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.cleanCaption
import eu.tintera.plume.core.ai.photoCaptionPrompt
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.platformLanguageCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Návrh popisku fotky nad vstupním polem ([ConversationState.captionSuggestion]). */
enum class CaptionSuggestion {
    /** Nenabízí se: žádná fotka, vypnutá AI, nebo model, který obraz nebere (Nano). */
    Hidden,

    /** „Navrhnout popisek" jde ťuknout. */
    Available,

    /** Model píše návrh. */
    Working,
}

/**
 * Návrh popisku k fotce, která čeká nad polem na odeslání: model se obrazem (dnes Gemma)
 * napíše krátkou větu a ta se VLOŽÍ do pole ([InputFill]) — uživatel ji vidí, upraví nebo smaže
 * dřív, než něco odejde. Psaní textu k odeslání je nejslabší kategorie modelu; tady ho ale
 * uživatel schvaluje, takže chyba je levná.
 *
 * - **Jen na ťuknutí** (`AiTask.PhotoDescription` přes `variantFor`, ne `proactiveVariant`):
 *   o návrh si uživatel řekl, takže jede i pod úsporným režimem.
 * - **Popisuje se PRVNÍ fotka** — u alba patří popisek první položce (viz „Posílání příloh
 *   a alb"); video ani soubor model nečte.
 * - **Pole se nepřepisuje** (`onlyIfEmpty`): když uživatel mezitím začal psát, návrh zahodí.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ConversationCaption(
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val ai: OnDeviceAi?,
    private val logger: Logger,
) {
    private var job: Job? = null

    init {
        val ai = ai
        if (ai != null) {
            scope.launch {
                combine(
                    state.map { firstPhoto(it.attachments) }.distinctUntilChanged(),
                    settings.onDeviceAiEnabled,
                    settings.onDeviceAiModels,
                ) { photo, enabled, models -> Triple(photo, enabled, models) }
                    .mapLatest { (photo, enabled, models) ->
                        photo != null && enabled &&
                            runCatching { ai.canDescribeImage(ai.variantFor(AiTask.PhotoDescription, models)) }.getOrDefault(false)
                    }
                    .collect { available ->
                        // Rozběhnutý návrh nechá doběhnout; nová fotka ho zruší v [suggest].
                        state.update {
                            if (it.captionSuggestion == CaptionSuggestion.Working && available) it
                            else it.copy(captionSuggestion = if (available) CaptionSuggestion.Available else CaptionSuggestion.Hidden)
                        }
                        if (!available) job?.cancel()
                    }
            }
        }
    }

    fun suggest() {
        val ai = ai ?: return
        val path = firstPhoto(state.value.attachments) ?: return
        if (job?.isActive == true) return
        state.update { it.copy(captionSuggestion = CaptionSuggestion.Working) }
        job = scope.launch {
            val caption = try {
                val variant = ai.variantFor(AiTask.PhotoDescription, settings)
                ai.describeImage(variant, path, platformLanguageCode, photoCaptionPrompt(platformLanguageCode))
                    .lastOrNull()?.text?.let(::cleanCaption)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Návrh je doplněk: nepovedený prostě není, a pole zůstane, jak bylo.
                logger.w(e) { "Návrh popisku fotky se nepovedl" }
                null
            }
            state.update {
                it.copy(
                    captionSuggestion = if (firstPhoto(it.attachments) != null) CaptionSuggestion.Available else CaptionSuggestion.Hidden,
                    // Jen když je fotka pořád tatáž — mezitím odebraná nebo vyměněná návrh nedostane.
                    inputFill = caption?.takeIf { _ -> firstPhoto(it.attachments) == path }
                        ?.let { text -> InputFill(text, onlyIfEmpty = true) } ?: it.inputFill,
                )
            }
        }
    }

    private fun firstPhoto(attachments: List<OutgoingAttachment>): String? =
        (attachments.firstOrNull() as? OutgoingAttachment.Photo)?.path
}
