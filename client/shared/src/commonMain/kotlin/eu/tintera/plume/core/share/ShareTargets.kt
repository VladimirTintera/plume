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
            val chat = shortcutChat(chatId, AVATAR_WAIT_MS) ?: return@launch
            publisher.publish(chat.chatId, chat.title, chat.avatarPath, chat.isSavedMessages)
        }
    }

    /** Umí platforma připnout chat na plochu (Android s launcherem, který připnutí dovolí). */
    val canPinToHomeScreen: Boolean get() = publisher.canPin

    /**
     * „Přidat na plochu": požádá launcher o zástupce chatu — tutéž zkratku konverzace, kterou
     * znají notifikace i sdílení, takže má fotku chatu a otevře ho. Potvrzení (dialog launcheru)
     * je věc systému; jestli ho uživatel přijal, se aplikace nedozví.
     */
    fun pinToHomeScreen(chatId: Long) {
        scope.launch {
            // Na plochu jde fotka napořád a uživatel o ni výslovně požádal: na stažení se
            // počká déle než u zkratky ke sdílení.
            val chat = shortcutChat(chatId, PIN_AVATAR_WAIT_MS) ?: return@launch
            if (!publisher.pin(chat.chatId, chat.title, chat.avatarPath, chat.isSavedMessages)) {
                logger.w { "Launcher zástupce chatu $chatId nepřijal." }
            }
        }
    }

    /** Název a stažená profilovka chatu pro jeho zkratku; `null`, když chat nejde načíst. */
    private suspend fun shortcutChat(chatId: Long, avatarWaitMs: Long): ShortcutChat? {
        // Uložené zprávy nesou v TDLib jméno a fotku vlastního účtu; ve sdílení i na ploše mají
        // mít vlastní název a záložku, jako všude v aplikaci.
        if (chatId == chats.savedMessagesChatId.value) {
            return ShortcutChat(chatId, readString(Res.string.saved_messages), avatarPath = null, isSavedMessages = true)
        }
        val info = repository.chatInfo(chatId).getOrElse { failure ->
            logger.w(failure) { "Chat $chatId se kvůli zkratce nepodařilo načíst." }
            return null
        }
        // Profilovka bývá stažená (kreslí ji seznam i hlavička). Když není, zkratka ji
        // nedostane — čekat na síť kvůli ikoně ve sdílení nestojí za to.
        val avatarPath = info.avatarFileId?.let { fileId ->
            withTimeoutOrNull(avatarWaitMs) {
                (files.file(fileId).first { it is FileState.Ready || it is FileState.Failed } as? FileState.Ready)?.path
            }
        }
        return ShortcutChat(chatId, info.title, avatarPath, isSavedMessages = false)
    }

    private class ShortcutChat(val chatId: Long, val title: String, val avatarPath: String?, val isSavedMessages: Boolean)

    private companion object {
        const val AVATAR_WAIT_MS = 2_000L
        const val PIN_AVATAR_WAIT_MS = 5_000L
    }
}

/** Platformní publikace chatu jako cíle sdílení a jeho zástupce na ploše. */
interface ShareTargetPublisher {
    fun publish(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean)

    /** Jde chat připnout na plochu ([pin]); jinde než na Androidu ne. */
    val canPin: Boolean get() = false

    /** Požádá launcher o připnutí zástupce chatu; `false`, když žádost neodešla. */
    fun pin(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean): Boolean = false
}
