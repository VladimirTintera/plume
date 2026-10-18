package eu.tintera.plume.features.media

import androidx.compose.runtime.compositionLocalOf
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** Hotový popis fotky z modelu v telefonu, uložený v [AppSettings]. [accountId] drží účty od sebe. */
@Serializable
data class StoredPhotoDescription(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val savedAt: Long,
)

/**
 * Popisy fotek z modelu v telefonu („Popsat fotku" v prohlížeči), aby nezmizely se zavřením
 * prohlížeče: fotka v bublině je pak má jako popis pro čtečku obrazovky ([LocalPhotoDescriptions])
 * a prohlížeč je příště ukáže hned, bez modelu.
 *
 * - **Jen popisy, o které si uživatel řekl** — model se nepouští na každou fotku v seznamu
 *   (baterie, a AICore pustí inferenci jen aplikaci v popředí). Popis předem v pozadí je jiný
 *   bod roadmapy (hledání fotek podle obsahu).
 * - **Nejvýš [MAX_STORED] popisů**, nejstarší pryč — stejně jako přepisy hlasovek
 *   ([eu.tintera.plume.features.conversation.LocalTranscripts]): popis je obsah, ne nastavení.
 * - **Účet je id Uložených zpráv**; dokud není známý, popis se neukládá.
 */
class PhotoDescriptions(private val settings: AppSettings, private val chats: ChatRepository) {

    /** Popisy fotek v chatu [chatId] podle id zprávy. */
    fun forChat(chatId: Long): Flow<Map<Long, String>> =
        combine(settings.storedPhotoDescriptions, chats.savedMessagesChatId) { stored, account ->
            if (account == null) emptyMap() else stored.filter { it.accountId == account && it.chatId == chatId }.associate { it.messageId to it.text }
        }

    fun get(chatId: Long, messageId: Long): String? {
        val account = chats.savedMessagesChatId.value ?: return null
        return settings.storedPhotoDescriptions.value
            .firstOrNull { it.accountId == account && it.chatId == chatId && it.messageId == messageId }?.text
    }

    fun set(chatId: Long, messageId: Long, text: String) {
        val account = chats.savedMessagesChatId.value ?: return
        val saved = StoredPhotoDescription(account, chatId, messageId, text, Clock.System.now().toEpochMilliseconds())
        settings.setStoredPhotoDescriptions(
            (settings.storedPhotoDescriptions.value.filterNot { it.accountId == account && it.chatId == chatId && it.messageId == messageId } + saved)
                .sortedBy { it.savedAt }
                .takeLast(MAX_STORED),
        )
    }

    private companion object {
        const val MAX_STORED = 300
    }
}

/**
 * Popisy fotek otevřeného chatu (id zprávy → popis) pro bubliny: fotka s popisem ho dá čtečce
 * obrazovky místo slova „Fotka". Dodává konverzace; jinde prázdné.
 */
val LocalPhotoDescriptions = compositionLocalOf<Map<Long, String>> { emptyMap() }
