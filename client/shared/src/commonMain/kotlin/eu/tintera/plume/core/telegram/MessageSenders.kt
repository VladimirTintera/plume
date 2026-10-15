package eu.tintera.plume.core.telegram

/**
 * Kým jde do skupiny psát (`getChatAvailableMessageSenders`): vlastním účtem, kanálem, který
 * uživatel spravuje, nebo skupinou samotnou — to když je v ní anonymní správce. Kdo je zrovna
 * vybraný, nese chat (`Chat.messageSenderId`, živě [ConversationRepository.messageSender]).
 */
data class MessageSenderOption(
    /** Id uživatele (kladné), nebo chatu (záporné) — konvence [SenderInfo.id]. */
    val id: Long,
    val title: String,
    val avatarFileId: Int?,
    /** [ChatKind.Private] = vlastní účet, jinak kanál nebo skupina. */
    val kind: ChatKind,
    /**
     * Volba chce Telegram Premium, které účet NEMÁ (`ChatMessageSender.needsPremium`; s Premium
     * se nebere v potaz). Nabízí se, ale nejde vybrat — jako u oficiálních klientů.
     */
    val locked: Boolean = false,
    /** Odesílatel je tentýž chat, do kterého se píše: anonymní správce píše jménem skupiny. */
    val isChat: Boolean = false,
)

/**
 * Výběr odesílatele u vstupního pole: [current] je vybraný, [options] všechny nabízené.
 *
 * Vzniká jen tam, kde je z čeho vybírat ([messageSenderChoice]). TDLib dá `messageSenderId`
 * jen tam, kde odesílatele měnit jde, ale výběr s jedinou volbou by byl tlačítko, které nic
 * nedělá.
 */
data class MessageSenderChoice(
    val current: MessageSenderOption,
    val options: List<MessageSenderOption>,
)

/**
 * Složí výběr z vybraného id a nabídky; `null` = nic neukazovat: odesílatele měnit nejde
 * ([currentId] `null`), není z čeho vybírat, nebo vybraný v nabídce chybí — avatar u pole by
 * pak ukázal někoho jiného, než kým zpráva odejde.
 */
internal fun messageSenderChoice(currentId: Long?, options: List<MessageSenderOption>): MessageSenderChoice? {
    if (currentId == null || options.size < 2) return null
    val current = options.firstOrNull { it.id == currentId } ?: return null
    return MessageSenderChoice(current = current, options = options)
}
