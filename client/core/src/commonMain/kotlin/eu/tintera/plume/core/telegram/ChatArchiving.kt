package eu.tintera.plume.core.telegram

/**
 * Chat služby Telegramu („Telegram" se značkou ověření): přihlašovací kódy, upozornění na nové
 * přihlášení, oznámení od služby. Id uživatele je v TDLib pevné
 * (`UserManager::get_service_notifications_user_id`) a soukromý chat má v TDLib totéž id jako
 * uživatel, takže platí pro obojí.
 */
const val TELEGRAM_SERVICE_NOTIFICATIONS_ID = 777000L

/**
 * Jde chat PŘESUNOUT do archivu?
 *
 * TDLib to napevno odmítá u dvou chatů (`MessagesManager::add_dialog_to_list`, chyba „Chat
 * can't be archived"): Uložené zprávy a chat služby Telegramu. Příznak v API na to není
 * (`canBeArchived` mají jen příběhy), pravidlo jsou jen tahle dvě id — proto se počítá tady
 * a ne až z chyby. Archivace by jinak skončila obecnou hláškou „Akci se nepodařilo provést"
 * (výběr víc chatů s chatem Telegram).
 *
 * Vrácení z archivu TDLib nebrání; kdo se ptá kvůli vracení, tohle volat nemá.
 */
fun ChatSummary.canBeArchived(savedMessagesChatId: Long?): Boolean =
    id != savedMessagesChatId && id != TELEGRAM_SERVICE_NOTIFICATIONS_ID
