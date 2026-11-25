package eu.tintera.plume.core.video

import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Rozkoukaná videa přihlášeného účtu (menu účtu → Rozkoukaná videa): pamatované pozice
 * ([VideoPositions]), které vědí, ze které zprávy video hrálo ([VideoSource]).
 *
 * Pozice jsou společné pro celou aplikaci (klíč je soubor), seznam ale patří účtu: zpráva se
 * otevírá v jeho chatu. Účet je id Uložených zpráv, jako u záložek ([eu.tintera.plume.core.bookmarks.MessageBookmarks]).
 */
class ContinueWatching(
    private val positions: VideoPositions,
    private val chats: ChatRepository,
    /** Zamčené chaty: jejich videa seznam neukáže. `null` = sada se ještě čte. */
    locked: Flow<Set<Long>?> = flowOf(emptySet()),
) {

    /** Nejnověji sledované první; jen videa ze zpráv tohoto účtu, bez zamčených chatů. */
    val all: Flow<List<VideoPosition>> = combine(positions.all, chats.savedMessagesChatId, locked) { list, account, hidden ->
        if (account == null || hidden == null) {
            emptyList()
        } else {
            list.filter { position ->
                val source = position.source
                source != null && source.accountId == account && source.chatId !in hidden
            }
        }
    }

    /** Zdroj pro zápis pozice; `null`, dokud účet není znám (pozice se zapíše i tak, jen bez něj). */
    fun sourceFor(chatId: Long, messageId: Long): VideoSource? =
        chats.savedMessagesChatId.value?.let { account -> VideoSource(account, chatId, messageId) }

    fun remove(key: String): RemovedPosition? = positions.remove(key)

    fun restore(removed: RemovedPosition) = positions.restore(removed)
}
