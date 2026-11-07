package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.ai.AiTask
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.reminderTimeOf
import eu.tintera.ai.core.generateAnswer
import eu.tintera.plume.core.ai.ReminderAnswer
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.parseReminderTime
import eu.tintera.plume.core.ai.reminderPrompt
import eu.tintera.plume.core.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime

/**
 * Termín připomínky od modelu v zařízení — doplněk k pravidlům ([reminderTimeFromText]) pro
 * volnější formulace („za týden po obědě", „až skončí víkend").
 *
 * - **Jen se zapnutou AI v zařízení a s modelem, který je PŘIPRAVENÝ.** Kvůli připomínce se nic
 *   nestahuje a model, který se zrovna stahuje, se neptá.
 * - **Ptá se, jen když pravidla nic nenašla** — o tom rozhoduje volající. Pravidla jsou okamžitá
 *   a přesná; model je pomalejší a umí se splést.
 * - **Selhání = žádný návrh**, nikdy chyba v sheetu: termín ze zprávy je jen nabídka navíc.
 *   Strop [TIMEOUT_MS] hlídá, aby „Hledám termín…" nevisel napořád (Gemma na emulátoru načítá
 *   engine i desítky vteřin).
 */
class ReminderTimeModel(private val ai: OnDeviceAi, private val settings: AppSettings) {
    private val logger = Logger.withTag("ReminderTime")

    /** Stojí za to se ptát? Levné: stav modelu, nic se nepočítá. */
    suspend fun available(): Boolean =
        settings.onDeviceAiEnabled.value &&
            runCatching { ai.status(ai.variantFor(AiTask.Dates, settings)) == OnDeviceAiStatus.Ready }.getOrDefault(false)

    suspend fun suggest(text: String, sentAt: LocalDateTime): LocalDateTime? {
        if (text.isBlank() || !available()) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                ai.generateAnswer(
                    ai.variantFor(AiTask.Dates, settings),
                    ReminderAnswer::class,
                    prompt = { structured -> reminderPrompt(text, sentAt, structured) },
                    fromStructured = { reminderTimeOf(it, sentAt) },
                    fromText = { parseReminderTime(it, sentAt) },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Termín od modelu se nepodařilo zjistit" }
            null
        }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
