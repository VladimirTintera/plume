package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.OnDeviceFix
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.platformLanguageCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val logger = Logger.withTag("OnDeviceTranscription")

/**
 * Přepis hlasovky nebo kolečka v telefonu, když ho server nedá (bez Premium a zkušebních
 * přepisů): Gemma se zvukovým vstupem (`OnDeviceAi.transcribe`), a když není, systém
 * ([SpeechToText]). Výsledek jde do [LocalTranscripts] — zprávu na serveru měnit nejde.
 *
 * Sdílí ho konverzace (`ConversationPlayback`) a přepis na žádost hodinek
 * (`WatchTranscriptionService` v `androidApp`): hodinky Gemmu nemají, telefon jim přepíše
 * a přepis zůstane i v telefonu.
 */
class OnDeviceTranscription(
    private val settings: AppSettings,
    private val files: FileRepository,
    private val local: LocalTranscripts,
    private val onDeviceAi: OnDeviceAi?,
    private val speechToText: SpeechToText?,
) {

    /**
     * Umí telefon přepisovat? Gemma (zapnutá AI, model se zvukem) nebo systém. Když ne, řekne
     * i co by pomohlo ([OnDeviceFix]) — uživatel s Gemini Nano jinak neměl jak poznat, že
     * přepis umí jen Gemma.
     */
    suspend fun check(
        enabled: Boolean = settings.onDeviceAiEnabled.value,
        variant: ModelVariant = settings.onDeviceAiVariant.value,
    ): OnDeviceCheck = if (gemmaTranscribes(enabled, variant) || systemTranscribes()) {
        OnDeviceCheck(available = true)
    } else {
        OnDeviceCheck(available = false, fix = fix(enabled, variant))
    }

    /**
     * Přepíše záznam [fileId] zprávy [messageId] a vrátí výsledek; průběh zapisuje do
     * [LocalTranscripts]. Hotový přepis z dřívějška vrátí hned, rozpracovaný (třeba z konverzace)
     * dočká — znovu se nepřepisuje. Trvá vteřiny až desítky vteřin, volající ho pouští mimo
     * obrazovku (`appScope`), ať ho odchod z chatu nezruší.
     */
    suspend fun transcribe(chatId: Long, messageId: Long, fileId: Int): SpeechTranscript {
        when (val existing = local.get(chatId, messageId)) {
            is SpeechTranscript.Done -> return existing
            is SpeechTranscript.Pending -> return local.state.map { it[chatId to messageId] }.first { it !is SpeechTranscript.Pending }
                ?: SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other)
            else -> Unit
        }
        local.set(chatId, messageId, SpeechTranscript.Pending(""))
        val result = try {
            val file = files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
            val path = (file as? FileState.Ready)?.path ?: throw IllegalStateException("Soubor $fileId se nestáhl")
            var text = ""
            // Gemma má přednost (přepisuje i interpunkci a smysl), systém je záloha pro telefony
            // bez ní — rozhoduje se až teď, stav se mohl od nabídky změnit.
            val variant = settings.onDeviceAiVariant.value
            val ai = onDeviceAi
            val speech = speechToText
            val chunks: Flow<String> = when {
                ai != null && gemmaTranscribes(settings.onDeviceAiEnabled.value, variant) ->
                    ai.transcribe(variant, path, platformLanguageCode).map { it.text }
                speech != null && systemTranscribes() -> speech.transcribe(path, platformLanguageCode)
                else -> throw IllegalStateException("Přepis v telefonu už není k dispozici")
            }
            chunks.collect { chunk ->
                text = chunk
                local.set(chatId, messageId, SpeechTranscript.Pending(text))
            }
            if (text.isBlank()) SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other) else SpeechTranscript.Done(text.trim())
        } catch (e: CancellationException) {
            local.set(chatId, messageId, SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other))
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Přepis zprávy $messageId v telefonu se nepovedl." }
            SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other)
        }
        local.set(chatId, messageId, result)
        return result
    }

    /** Přepíše Gemma (zapnutá AI, model se zvukovým vstupem připravený)? */
    private suspend fun gemmaTranscribes(enabled: Boolean, variant: ModelVariant): Boolean {
        val ai = onDeviceAi ?: return false
        return enabled && runCatching { ai.canTranscribe(variant) }.getOrDefault(false)
    }

    /**
     * Co zapnout, aby přepisovala Gemma; `null`, když ji zařízení neunese (iOS, desktop, slabý
     * telefon) — pak nepomůže nic v telefonu.
     */
    private suspend fun fix(enabled: Boolean, variant: ModelVariant): OnDeviceFix? {
        val ai = onDeviceAi ?: return null
        val gemma = ModelVariant(provider = AiProvider.Gemma, gemma = variant.gemma)
        val supported = runCatching { ai.status(gemma) != OnDeviceAiStatus.Unsupported }.getOrDefault(false)
        return when {
            !supported -> null
            !enabled -> OnDeviceFix.EnableAi
            variant.provider != AiProvider.Gemma -> OnDeviceFix.SwitchToGemma
            else -> OnDeviceFix.DownloadGemma
        }
    }

    /**
     * Přepíše systém telefonu ([SpeechToText])? Nezávisle na volbě AI v zařízení: není to jazykový
     * model, nic se nestahuje a zvuk telefon neopouští — stejná povaha jako rozpoznání textu
     * z fotky, které taky žádnou volbu nemá.
     */
    private suspend fun systemTranscribes(): Boolean {
        val speech = speechToText ?: return false
        return runCatching { speech.canTranscribe(platformLanguageCode) }.getOrDefault(false)
    }
}

/** Výsledek [OnDeviceTranscription.check]: přepíše telefon, a když ne, co by pomohlo. */
data class OnDeviceCheck(val available: Boolean, val fix: OnDeviceFix? = null)
