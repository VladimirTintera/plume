package eu.tintera.plume.features.media

import androidx.compose.runtime.compositionLocalOf
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.StoredPhotoDescription
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

/**
 * Popisy fotek z modelu v telefonu („Popsat fotku" v prohlížeči), aby nezmizely se zavřením
 * prohlížeče: fotka v bublině je pak má jako popis pro čtečku obrazovky ([LocalPhotoDescriptions])
 * a prohlížeč je příště ukáže hned, bez modelu.
 *
 * - **Popisy, o které si uživatel řekl**, a s volbou i popisy předem z pozadí na nabíječce
 *   ([BackgroundPhotoDescriber]) — model se nepouští na každou fotku v seznamu za běhu
 *   (baterie, a AICore pustí inferenci jen aplikaci v popředí).
 * - **Nejvýš [MAX_STORED] popisů**, nejstarší pryč — stejně jako přepisy hlasovek
 *   ([eu.tintera.plume.features.conversation.LocalTranscripts]): popis je obsah, ne nastavení.
 * - **Účet je id Uložených zpráv**; dokud není známý, popis se neukládá.
 */
class PhotoDescriptions(private val settings: AppSettings, private val chats: ChatRepository) {

    /** Všechny popisy účtu, nejnovější poslední — pro hledání fotek podle obsahu. */
    val all: Flow<List<StoredPhotoDescription>> =
        combine(settings.storedPhotoDescriptions, chats.savedMessagesChatId) { stored, account ->
            if (account == null) emptyList() else stored.filter { it.accountId == account }
        }

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
        /** S popisy na pozadí jich přibývá víc než z ťuknutí; popis má pár set znaků. */
        const val MAX_STORED = 500
    }
}

/**
 * Popisy fotek otevřeného chatu (id zprávy → popis) pro bubliny: fotka s popisem ho dá čtečce
 * obrazovky místo slova „Fotka". Dodává konverzace; jinde prázdné.
 */
val LocalPhotoDescriptions = compositionLocalOf<Map<Long, String>> { emptyMap() }
