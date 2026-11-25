package eu.tintera.plume.core.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Volby, které patří jednomu účtu, ne celé aplikaci ([AppSettings.forAccount], v kontejneru účtu
 * jako `single`). Jsou to ty, které se týkají serveru nebo dat účtu: zapnutí synchronizace
 * adresáře u jednoho účtu nesmí nahrát kontakty do ostatních a souhlas s mini aplikací dává bot
 * data JEDNOHO účtu.
 *
 * Volby, které už nesou id účtu v záznamu (záložky, odložení, barvy chatů, přepisy…), zůstaly
 * v [AppSettings] — filtruje je jejich třída podle id Uložených zpráv.
 */
interface AccountSettings {

    /**
     * Stav synchronizovaných dat při poslední synchronizaci přes úložiště bota (JSON, viz
     * `CloudSync`) — základ trojcestného slučování. `null` = ještě nikdy.
     */
    suspend fun cloudSyncBase(): String? = null

    suspend fun setCloudSyncBase(value: String) = Unit

    /**
     * Kde stojí automatické stahování vůči serveru Telegramu u tohoto účtu. `null` = ještě nikdy:
     * čerstvě přidaný účet, nebo aktualizace z verze, která nesynchronizovala. Čeká na načtení
     * souboru. Úrovně samotné jsou nastavení zařízení ([AppSettings.autoDownload]).
     */
    suspend fun autoDownloadSync(): SettingsSync?

    /** Zapíše [autoDownloadSync]. Na rozdíl od ostatních zápisů čeká, až je zapsáno. */
    suspend fun setAutoDownloadSync(state: SettingsSync)

    /**
     * Synchronizovat adresář telefonu s tímto účtem (`ContactSyncer`). Nahrává čísla na server,
     * proto výchozí `false` a zapíná se vědomě (s oprávněním ke kontaktům).
     */
    val contactSyncEnabled: StateFlow<Boolean>

    /** Zapne/vypne synchronizaci adresáře. Neblokuje. */
    fun setContactSyncEnabled(enabled: Boolean)

    /**
     * Boti, u jejichž mini aplikace už uživatel potvrdil, že jí posílá data tohoto účtu. TDLib
     * chce to upozornění ukázat pro každého bota jednou (dokumentace `openWebApp`).
     */
    val confirmedWebAppBots: StateFlow<Set<Long>>

    fun confirmWebAppBot(botUserId: Long)

    /**
     * Chaty zamčené v tomhle zařízení (`LockedChatsSession`, `docs/poznatky/zamcene-chaty.md`):
     * mimo seznam zamčených se nikde neukazují a otevřou se až po PINu nebo biometrii. Jen
     * v zařízení, na server se nic neposílá.
     *
     * **`null` = nastavení se ještě čte.** Výchozí prázdná sada by po studeném startu na pár
     * snímků ukázala zamčené chaty v seznamu i v konverzaci; kdo je filtruje, počká na hodnotu.
     */
    val lockedChats: StateFlow<Set<Long>?>

    /** Zamkne / odemkne chat [chatId]. Neblokuje. */
    fun setChatLocked(chatId: Long, locked: Boolean)

    /**
     * Použité jednorázové klávesnice botů: chat → zpráva, ze které klávesnice pochází. Klávesnice
     * po použití v chatu ZŮSTÁVÁ (Bot API ji jen schová) a TDLib si nepamatuje, že byla použitá —
     * bez tohohle by se při každém otevření chatu ukázala zase rozbalená. Tak to drží i oficiální
     * klient (`hidekeyboard_<chat>` v nastavení). Po účtech, protože chat s botem má v každém
     * účtu totéž id, ale jiné zprávy.
     */
    val usedOneTimeKeyboards: StateFlow<Map<Long, Long>>

    fun markOneTimeKeyboardUsed(chatId: Long, messageId: Long)

    /**
     * Kdy naposledy proběhl automatický úklid médií tohoto účtu (sekundy od epochy), `null` =
     * nikdy. Po účtech, protože každý účet má vlastní databázi a úklid jednoho by jinak odložil
     * úklid ostatních. Úklid podle [AppSettings.keepMedia] stačí jednou denně.
     */
    val lastStorageCleanup: StateFlow<Long?>

    /** Zapíše čas posledního úklidu. Neblokuje. */
    fun setLastStorageCleanup(epochSeconds: Long)
}
