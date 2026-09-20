package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.secrets.DatabaseKeyStore

/** Databáze bez dodatečného šifrování. Tak vznikaly všechny do verze, která klíč zavedla. */
internal val EMPTY_DATABASE_KEY = ByteArray(0)

/**
 * Otevře databázi TDLib a postará se o to, aby byla nakonec šifrovaná. Vrací chybu, kvůli které
 * se to nepovedlo, nebo `null`.
 *
 * **Uložený klíč je vždycky ten, který databáze mít MÁ** — ukládá se DŘÍV, než se jím databáze
 * zašifruje. Obrácené pořadí („nejdřív zašifrovat, pak uložit") má okno, ve kterém zabitý proces
 * nechá zašifrovanou databázi bez klíče, a to je nenávratné odhlášení. Takhle je nejhorší možný
 * stav ten, že databáze za klíčem zaostává — a ten se pozná a dožene.
 *
 * Kroky jsou tři a každý pojmenovává jeden stav světa:
 * 1. **Otevřít uloženým klíčem.** Běžný start; jedno volání jako dřív.
 * 2. **Nejde to? Otevřít prázdným a zašifrovat.** Sem spadne instalace z verze, která šifrovat
 *    neuměla, i migrace přerušená v půlce. První spuštění taky, jen tam krok 1 uspěje rovnou,
 *    protože databáze ještě není a TDLib ji založí hned zašifrovanou.
 * 3. **Nejde ani to?** Databáze je zašifrovaná klíčem, který nemáme (obnova zálohy na jiné
 *    zařízení, ztracený Keychain). Je nenávratně nečitelná, takže se zahodí a zakládá se nová.
 *    Uživatel skončí na přihlašovací obrazovce — po přenosu na nový telefon to stejně je to,
 *    co od Telegramu čeká, takže se to nevysvětluje vlastní hláškou.
 *
 * **Krok 3 se smí spustit JEN na chybu o klíči** ([isWrongKey]). Kdyby se mazalo při každém
 * selhání, odhlásil by uživatele plný disk nebo překlep v `api_id` — proto na to sedí test.
 *
 * Je to samostatná funkce s podstrčenými operacemi, ne metoda session: pořadí kroků je to jediné,
 * co se na tom dá rozbít, a uvnitř třídy s živým TDLib klientem by se to neotestovalo.
 *
 * @param encrypt zašifrování otevřené databáze. Vrací se z něj `Unit`: když selže, aplikace běží
 *   dál (jen nešifrovaně) a příští start to zkusí znovu krokem 2, takže to není důvod nepustit
 *   uživatele dovnitř. Hlásit si to musí volající.
 */
internal suspend fun unlockDatabase(
    keys: DatabaseKeyStore,
    isWrongKey: (Throwable) -> Boolean,
    open: suspend (key: ByteArray) -> Throwable?,
    encrypt: suspend (key: ByteArray) -> Unit,
    wipe: suspend (cause: Throwable) -> Unit,
): Throwable? {
    val key = keys.load() ?: keys.generate().also { keys.store(it) }

    val withKey = open(key) ?: return null
    if (!isWrongKey(withKey)) return withKey

    val withoutKey = open(EMPTY_DATABASE_KEY)
    if (withoutKey == null) {
        encrypt(key)
        return null
    }
    if (!isWrongKey(withoutKey)) return withoutKey

    wipe(withKey)
    return open(key)
}
