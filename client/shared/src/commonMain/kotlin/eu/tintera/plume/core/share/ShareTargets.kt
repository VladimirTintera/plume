package eu.tintera.plume.core.share

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.telegram.ChatRepository
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
    private val chats: ChatRepository,
    private val files: FileRepository,
    private val publisher: ShareTargetPublisher,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Share")

    init {
        // Uložené zprávy mají být mezi cíli sdílení hned po přihlášení, i když se do nich ještě
        // nic neposlalo — „pošlu si to" je nejčastější sdílení.
        scope.launch {
            chats.savedMessagesChatId.filterNotNull().distinctUntilChanged().collect { reportUsed(it) }
        }
    }

    /**
     * Do chatu se právě něco poslalo. Běží v rozsahu aplikace — hlásí se i po odchodu z chatu
     * hned po odeslání. Název a profilovka se dohledají tady, ať se to nemusí nosit od volajícího.
     */
    fun reportUsed(chatId: Long) {
        scope.launch {
            // Uložené zprávy nesou v TDLib jméno a fotku vlastního účtu; ve sdílení mají mít
            // vlastní název a záložku, jako všude v aplikaci.
            if (chatId == chats.savedMessagesChatId.value) {
                publisher.publish(chatId, readString(Res.string.saved_messages), avatarPath = null, isSavedMessages = true)
                return@launch
            }
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
            publisher.publish(chatId, info.title, avatarPath, isSavedMessages = false)
        }
    }

    private companion object {
        const val AVATAR_WAIT_MS = 2_000L
    }
}

/** Platformní publikace chatu jako cíle sdílení. */
interface ShareTargetPublisher {
    fun publish(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean)
}
