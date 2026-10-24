package eu.tintera.plume.core.telegram

/**
 * Rozepsaná odpověď bota (TDLib `updatePendingMessage`): text, který bot průběžně generuje
 * a ještě neodeslal. Skutečná zpráva přijde až na konci jako běžná příchozí.
 *
 * Každý update nese CELÝ dosavadní text, ne přírůstek — nový nahradí předchozí se stejným
 * [draftId].
 */
data class PendingBotMessage(
    /** Rozepsaná zpráva v rámci vlákna; jiné id znamená jinou odpověď. */
    val draftId: Long,
    /** Téma, do kterého odpověď půjde; 0 = bez tématu. */
    val forumTopicId: Int,
    val text: String,
    val entities: List<MessageEntity>,
    /** Má se ukázat tlačítko Zastavit ([ConversationRepository.stopPendingMessage]). */
    val canStop: Boolean,
    /** Po zastavení text NEMIZÍ — zůstane, dokud nevyprší nebo ho nenahradí zpráva. */
    val keepOnStop: Boolean,
)
