package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.ui.UiText

/**
 * Servisní věta se jmény pro řádek v seznamu — seznam chatů i seznam témat: „Pixel 5 opustil(a)
 * skupinu", „Jana uzavřel(a) téma", „Jana připnul(a) „Sraz v 8"". `null` = zpráva není
 * oznámení, nebo jméno dohledat nejde; řádek pak zůstane u bezejmenné věty z [previewText].
 *
 * Jména jdou z lokální databáze (`getUser`, `getChat`), na server se nečeká. Konverzace má
 * vlastní řetěz nad keší odesílatelů (`resolveSender`), proto ji tahle funkce neobsluhuje.
 */
internal suspend fun Message.namedServicePreview(client: TdlClient, myUserId: suspend () -> Long?): UiText? {
    val nameOf: suspend (Long) -> String? = { id ->
        client.getUser(userId = id).toResult().getOrNull()?.fullName()?.ifBlank { null }
    }
    return namedMemberServiceText(myUserId = myUserId, nameOf = nameOf)
        ?: namedChecklistServiceText(
            myUserId = myUserId,
            nameOf = nameOf,
            taskTexts = { checklistMessageId -> client.checklistTaskTexts(chatId, checklistMessageId) },
        )
        ?: namedActorServiceText(myUserId = myUserId, nameOf = nameOf)
        ?: content.namedSharedWithBotText { id -> nameOf(id) ?: client.getChat(chatId = id).toResult().getOrNull()?.title }
        ?: namedPinServiceText(
            myUserId = myUserId,
            nameOf = nameOf,
            pinnedText = { pinnedId -> client.localMessageText(chatId, pinnedId) },
        )
}
