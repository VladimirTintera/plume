package eu.tintera.plume.core.bookmarks

import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Záložka na zprávu — označení jen pro uživatele, v tomhle zařízení. Na rozdíl od připnutí ji
 * nikdo jiný nevidí a nepotřebuje práva.
 *
 * Nese **otisk zprávy** ([text], [sender], [timestamp]) z doby uložení: seznam záložek se tak
 * ukáže hned, bez načítání zpráv, a záložka smazané zprávy pořád říká, co to bylo. Úprava
 * zprávy se do otisku nepropíše. [accountId] drží záložky od sebe mezi účty.
 */
@Serializable
data class MessageBookmark(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val sender: String?,
    val timestamp: Int,
    val savedAt: Long,
)

/**
 * Záložky zpráv přihlášeného účtu (uložené v [AppSettings]).
 *
 * Účet je id Uložených zpráv ([ChatRepository.savedMessagesChatId]) — Uložené zprávy jsou chat
 * s vlastním účtem a jeho id je id účtu. Dokud není přihlášeno, záložky žádné nejsou.
 */
class MessageBookmarks(private val settings: AppSettings, private val chats: ChatRepository) {

    /** Záložky přihlášeného účtu, nejnověji přidané první. */
    val all: Flow<List<MessageBookmark>> = combine(settings.messageBookmarks, chats.savedMessagesChatId) { list, account ->
        if (account == null) emptyList() else list.filter { it.accountId == account }.sortedByDescending { it.savedAt }
    }

    fun isBookmarked(chatId: Long, messageId: Long): Boolean {
        val account = chats.savedMessagesChatId.value ?: return false
        return settings.messageBookmarks.value.any { it.accountId == account && it.chatId == chatId && it.messageId == messageId }
    }

    /** Přidá záložku; `false`, když účet ještě není znám. */
    fun add(chatId: Long, messageId: Long, text: String, sender: String?, timestamp: Int): Boolean {
        val account = chats.savedMessagesChatId.value ?: return false
        val bookmark = MessageBookmark(account, chatId, messageId, text, sender, timestamp, Clock.System.now().toEpochMilliseconds())
        settings.setMessageBookmarks(settings.messageBookmarks.value.filterNot { it.same(account, chatId, messageId) } + bookmark)
        return true
    }

    fun remove(chatId: Long, messageId: Long) {
        val account = chats.savedMessagesChatId.value ?: return
        settings.setMessageBookmarks(settings.messageBookmarks.value.filterNot { it.same(account, chatId, messageId) })
    }

    private fun MessageBookmark.same(account: Long, chatId: Long, messageId: Long) =
        accountId == account && this.chatId == chatId && this.messageId == messageId
}
