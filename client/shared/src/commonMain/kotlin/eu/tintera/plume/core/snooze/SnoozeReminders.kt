package eu.tintera.plume.core.snooze

import eu.tintera.plume.core.accounts.AccountSlot
import kotlin.time.Instant

/**
 * Upozornění, že se odložený chat vrací — systémová notifikace v čase [ChatSnooze.until].
 *
 * Bez ní by se připomínka ukázala jen v seznamu chatů, tedy až když uživatel aplikaci sám
 * otevře, a „připomeň mi to večer" by nepřipomnělo nic. Plánuje ji platforma (Android
 * WorkManager, iOS lokální notifikace), protože proces aplikace v tu chvíli běžet nemusí.
 */
interface SnoozeReminders {
    /** Naplánuje upozornění na [at]; dřívější téhož chatu nahradí. */
    fun schedule(slot: AccountSlot, chatId: Long, title: String, at: Instant)

    /** Zruší naplánované upozornění i to, které už visí v liště. */
    fun cancel(slot: AccountSlot, chatId: Long)
}
