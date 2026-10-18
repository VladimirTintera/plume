package eu.tintera.plume.features.media

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiException
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.platformLanguageCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Popisy fotek předem, na pozadí — aby šly v hledání najít podle toho, co na nich je
 * („pes na pláži"), ne jen podle popisku. Spouští ho platforma, jen na nabíječce (Android
 * `PhotoDescriptionWorker`); tady je jen jedna dávka.
 *
 * - **Jen s volbou** ([AppSettings.backgroundPhotoDescriptions]), se zapnutou AI v zařízení
 *   a s modelem, který obraz bere ([OnDeviceAi.canDescribeImage], dnes Gemma). Gemini Nano
 *   obraz nebere a AICore by inferenci na pozadí stejně nepustil.
 * - **Nejnovější fotky napříč chaty** ([ChatRepository.recentPhotos]), už popsané se přeskočí.
 *   Dávka je malá ([BATCH]): jeden popis trvá i desítky sekund a úloha má běžet, dokud
 *   telefon nabíjí, ne do vybití.
 * - **Popis jde do téhož úložiště jako „Popsat fotku"** ([PhotoDescriptions]), takže ho pak
 *   prohlížeč ukáže hned a čtečka obrazovky ho přečte u fotky v bublině.
 * - Fotka telefon neopouští; stahuje se jako při otevření (TDLib ji drží v keši).
 */
class BackgroundPhotoDescriber(
    private val settings: AppSettings,
    private val chats: ChatRepository,
    private val files: FileRepository,
    private val ai: OnDeviceAi,
    private val descriptions: PhotoDescriptions,
) {
    private val logger = Logger.withTag("PhotoDescriptions")

    /** Popíše nejvýš [limit] dosud nepopsaných fotek; vrátí, kolik popsal. */
    suspend fun describeBatch(limit: Int = BATCH): Int {
        if (!settings.backgroundPhotoDescriptions.value || !settings.onDeviceAiEnabled.value) return 0
        val variant = settings.onDeviceAiVariant.value
        if (!runCatching { ai.canDescribeImage(variant) }.getOrDefault(false)) {
            logger.d { "Popisy na pozadí: model obraz nebere, nic se nedělá." }
            return 0
        }
        val photos = chats.recentPhotos(SCAN).getOrElse {
            logger.w(it) { "Popisy na pozadí: fotky se nenačetly." }
            return 0
        }
        var described = 0
        for (photo in photos) {
            if (described >= limit) break
            if (descriptions.get(photo.chatId, photo.messageId) != null) continue
            val file = withTimeoutOrNull(DOWNLOAD_TIMEOUT) {
                files.file(photo.fileId).first { it is FileState.Ready || it is FileState.Failed }
            } as? FileState.Ready ?: continue
            val text = try {
                var last = ""
                ai.describeImage(variant, file.path, platformLanguageCode).collect { last = it.text }
                last.trim()
            } catch (e: OnDeviceAiException) {
                // Model selhal (paměť, přerušení): zbytek dávky by dopadl stejně.
                logger.w(e) { "Popisy na pozadí: model selhal, dávka končí." }
                break
            }
            if (text.isNotBlank()) {
                descriptions.set(photo.chatId, photo.messageId, text)
                described++
            }
        }
        logger.i { "Popisy na pozadí: popsáno $described fotek." }
        return described
    }

    private companion object {
        /** Kolik fotek se popíše za jedno spuštění. */
        const val BATCH = 10

        /** Kolik nejnovějších fotek se prohlíží, než se najdou nepopsané. */
        const val SCAN = 100

        val DOWNLOAD_TIMEOUT = 2.minutes
    }
}
