package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.DEFAULT_CHECKLIST_TASK_TEXT_MAX
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.LoginLinkKind
import eu.tintera.plume.core.telegram.MessageMedia

/**
 * Potvrzovací nebo rozhodovací dialog nad konverzací ([ConversationState.dialog]); nejvýš jeden.
 *
 * Dřív měl každý dialog vlastní pole stavu (`pendingDelete`, `pendingPin`, `confirmLink`,
 * `botAlert`… osmnáct jich bylo), a nic nebránilo tomu, aby jich bylo otevřených víc naráz —
 * `ConversationDialogs` je kreslil všechny nad sebou. Dialog je modální, takže dva najednou
 * nedávají smysl; jedno pole to říká typem a nový dialog starý nahradí.
 *
 * - **Otevírá se `copy(dialog = …)`, čte [dialog], zavírá [closing].** `closing` zavře jen
 *   dialog TOHO druhu: odpověď, která dorazí se zpožděním (upozornění od bota, přihlášení
 *   z tlačítka), mohla mezitím otevřít jiný, a zavření starého ho smazat nesmí.
 * - **Co sem nepatří:** sheety s vlastními daty a načítáním (překlad, pozvánka, detail reakcí),
 *   nabídka nad zprávou a jednorázové požadavky na navigaci (`openChat`, `openScreen`…) — jiný
 *   druh věci se stejnou úvahou, každý na vlastní převod.
 */
sealed interface ConversationDialog {

    /** Zpráva čekající na potvrzení smazání. */
    data class Delete(val pending: PendingDelete) : ConversationDialog

    /** Zpráva čekající na potvrzení připnutí. */
    data class Pin(val pending: PendingPin) : ConversationDialog

    /** Navrhovaný příspěvek, jehož odmítnutí čeká na komentář. */
    data class DeclineSuggestedPost(val messageId: Long) : ConversationDialog

    /** Zpráva z chatu Odpovědi, u které se potvrzuje zablokování odesílatele. */
    data class BlockFromReplies(val messageId: Long) : ConversationDialog

    /** Tlačítko bota chce sdílet telefonní číslo. */
    data object SharePhone : ConversationDialog

    /** Tlačítko bota chce polohu; po potvrzení ji zjistí obrazovka. */
    data object ShareLocation : ConversationDialog

    /** Odkaz z tlačítka bota, který čeká na potvrzení. */
    data class ConfirmLink(val url: String) : ConversationDialog

    /** Přihlašovací odkaz, který se z ťuknutí neotevírá — jen upozornění. */
    data class LoginWarning(val kind: LoginLinkKind) : ConversationDialog

    /** Webový odkaz, který nabízí přihlášení účtem Telegramu (`getExternalLinkInfo`). */
    data class WebLogin(val login: ExternalLogin) : ConversationDialog

    /** Tlačítko bota chce uživatele přihlásit na web. */
    data class BotLogin(val request: LoginRequest) : ConversationDialog

    /** Upozornění od bota, které se musí potvrdit (`showAlert`). */
    data class BotAlert(val text: String) : ConversationDialog

    /** Odpověď ankety, kterou uživatel chce smazat. */
    data class DeletePollOption(val pending: PendingPollOptionDelete) : ConversationDialog

    /** Anketa, kterou uživatel chce ukončit. */
    data class StopPoll(val messageId: Long) : ConversationDialog

    /** Kontakt, který čeká na potvrzení odeslání (jméno a číslo). */
    data class ShareContact(val contact: MessageMedia.Contact) : ConversationDialog

    /** Upozornění na blízkost u vlastní živé polohy. */
    data class ProximityAlert(val prompt: ProximityAlertPrompt) : ConversationDialog

    /** Vysvětlení neznámého `tg://` odkazu od serveru. */
    data class DeepLink(val info: InternalLink.DeepLinkInfo) : ConversationDialog

    /** Zpráva s anketou, do které se přidává odpověď. */
    data class AddPollOption(val messageId: Long) : ConversationDialog

    /** Seznam úkolů, do kterého se přidává úkol; [maxLength] je strop délky od serveru. */
    data class AddChecklistTask(
        val messageId: Long,
        val maxLength: Int = DEFAULT_CHECKLIST_TASK_TEXT_MAX,
    ) : ConversationDialog
}

/** Otevřený dialog druhu [T], nebo `null`. */
inline fun <reified T : ConversationDialog> ConversationState.dialog(): T? = dialog as? T

/**
 * Zavře dialog druhu [T]; jiný otevřený dialog nechá být (viz [ConversationDialog]).
 */
inline fun <reified T : ConversationDialog> ConversationState.closing(): ConversationState =
    if (dialog is T) copy(dialog = null) else this
