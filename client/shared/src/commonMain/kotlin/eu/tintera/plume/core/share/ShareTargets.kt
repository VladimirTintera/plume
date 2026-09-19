package eu.tintera.plume.core.share

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chaty v systémové nabídce „Sdílet" (Android Direct Share).
 *
 * Systém si pořadí skládá SÁM, z toho, jak často a jak nedávno aplikace chat nahlásí jako
 * použitý — proto se hlásí každé odeslání ([reportUsed]). Aplikace mu dodává jen kandidáty
 * a signály; „občas nefunguje" je vlastnost Androidu, ne chyba tady.
 */
class ShareTargets(
    private val repository: ConversationRepository,
    private val files: FileRepository,
    private val publisher: ShareTargetPublisher,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Share")

    /**
     * Do chatu se právě něco poslalo. Běží v rozsahu aplikace — hlásí se i po odchodu z chatu
     * hned po odeslání. Název a profilovka se dohledají tady, ať se to nemusí nosit od volajícího.
     */
    fun reportUsed(chatId: Long) {
        scope.launch {
            val info = repository.chatInfo(chatId).getOrElse { failure ->
                logger.w(failure) { "Chat $chatId se kvůli zkratce ke sdílení nepodařilo načíst." }
                return@launch
            }
            // Profilovka bývá stažená (kreslí ji seznam i hlavička). Když není, zkratka ji
            // nedostane — čekat na síť kvůli ikoně ve sdílení nestojí za to.
            val avatarPath = info.avatarFileId?.let { fileId ->
                withTimeoutOrNull(AVATAR_WAIT_MS) {
                    (files.file(fileId).first { it is FileState.Ready || it is FileState.Failed } as? FileState.Ready)?.path
                }
            }
            publisher.publish(chatId, info.title, avatarPath)
        }
    }

    private companion object {
        const val AVATAR_WAIT_MS = 2_000L
    }
}

/** Platformní publikace chatu jako cíle sdílení. */
interface ShareTargetPublisher {
    fun publish(chatId: Long, title: String, avatarPath: String?)
}
