package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage

/**
 * První nepřečtená zpráva — nejstarší PŘÍCHOZÍ za hranicí nepřečtených ([boundary],
 * `lastReadInboxMessageId` z okamžiku otevření chatu). `null`, když chat nepřečtené nemá, nebo
 * když načtená historie k hranici ještě nesahá: pak by „nejstarší načtená" nebyla první.
 *
 * **Vlastní zprávy nepřečtené nejsou**, i když mají id nad hranicí. Hranice je poslední přečtená
 * PŘÍCHOZÍ zpráva, takže co uživatel odepsal po ní, leží za ní — a dokud se to nebralo v úvahu,
 * předěl „Nepřečtené zprávy" chyběl (sousedem nepřečtené byla vlastní odpověď s vyšším id)
 * a skok po otevření mířil na vlastní zprávu místo na tu, kterou je potřeba přečíst.
 *
 * [messages] jsou od nejnovější, jako v [ConversationState.messages].
 */
internal fun firstUnreadId(messages: List<ChatMessage>, boundary: Long?): Long? {
    if (boundary == null || messages.none { it.id <= boundary }) return null
    return messages.lastOrNull { it.id > boundary && !it.isOutgoing }?.id
}
