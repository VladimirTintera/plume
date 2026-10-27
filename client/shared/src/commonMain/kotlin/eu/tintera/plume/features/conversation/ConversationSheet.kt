package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage

/**
 * Sheet nad konverzací ([ConversationState.sheet]) — překlad, informace o zprávě, kdo reagoval,
 * pozvánka, soutěž, naplánované zprávy…; nejvýš jeden.
 *
 * Dřív měl každý vlastní pole stavu (třináct jich bylo) a nic nebránilo tomu, aby jich bylo
 * otevřených víc naráz — `ConversationDialogs` je kreslil přes sebe. Stejná úvaha jako
 * u dialogů ([ConversationDialog]), jen sheet nese DATA, která se do něj dotahují (lidé, kteří
 * reagovali; výsledek překladu; stav soutěže), takže k otevření a zavření přibývá změna.
 *
 * - **Otevírá se `copy(sheet = …)`, čte [sheet], zavírá [closingSheet], mění [updatingSheet].**
 *   Zavření i změna se týkají jen sheetu TOHO druhu: data, která dorazí pozdě, nesmí přepsat
 *   ani zavřít sheet, který mezitím otevřel uživatel jinde.
 * - **Datové třídy sheetů typ implementují samy** (`TranslationSheet`, `ReactionDetails`…), aby
 *   se do nich dalo rovnou sahat. Jen kde byla hodnota holá, je obálka ([PollVotersSheet],
 *   [ScheduledSheet], [SelectTextSheet]).
 * - **Dialog a sheet se nevylučují** — potvrzení může vyskočit nad otevřeným sheetem.
 */
sealed interface ConversationSheet

/** Kdo hlasoval v anketě téhle zprávy. */
data class PollVotersSheet(val messageId: Long) : ConversationSheet

/** Naplánované zprávy chatu; prázdný seznam = načítají se, nebo žádné nejsou. */
data class ScheduledSheet(val messages: List<ChatMessage>) : ConversationSheet

/** „Vybrat text" — text zprávy k výběru části. */
data class SelectTextSheet(val message: ChatMessage) : ConversationSheet

/** Otevřený sheet druhu [T], nebo `null`. */
inline fun <reified T : ConversationSheet> ConversationState.sheet(): T? = sheet as? T

/** Zavře sheet druhu [T]; jiný otevřený sheet nechá být. */
inline fun <reified T : ConversationSheet> ConversationState.closingSheet(): ConversationState =
    if (sheet is T) copy(sheet = null) else this

/**
 * Změní otevřený sheet druhu [T] ([change] vrací nový, nebo `null` = zavřít); když je otevřený
 * jiný nebo žádný, stav nechá být — pozdní data nemají kam patřit.
 */
inline fun <reified T : ConversationSheet> ConversationState.updatingSheet(change: (T) -> ConversationSheet?): ConversationState {
    val open = sheet as? T ?: return this
    return copy(sheet = change(open))
}
