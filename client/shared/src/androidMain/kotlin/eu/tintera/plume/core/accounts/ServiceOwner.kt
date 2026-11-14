package eu.tintera.plume.core.accounts

import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/**
 * Který účet drží službu v popředí (hovor, hudba, poloha, videochat).
 *
 * Služba je v procesu jedna a její závislosti (`by inject()`) se rozřeší líně, často už
 * v `onCreate` — dřív, než dorazí intent, ve kterém by slot mohl být. Proto slot nastaví ten,
 * kdo službu spouští (ovládání v kontejneru účtu), těsně před `startService`, a služba si podle
 * něj vybere kontejner ([koin]).
 *
 * **Dva účty naráz jedna služba neobslouží:** druhé spuštění přepíše vlastníka, ale běžící
 * služba si drží závislosti prvního. Jeden hovor naráz přes všechny účty a jedna fronta hudby
 * jsou v návrhu (`docs/navrh-vice-uctu.md`, „Věci přes všechny účty").
 */
open class ServiceOwner {

    @kotlin.concurrent.Volatile
    var slot: AccountSlot? = null

    /** Kontejner vlastníka; bez vlastníka (služba obnovená systémem) výchozí účet. */
    fun koin(): Koin {
        val global = GlobalContext.get()
        return slot?.let { global.account(it) } ?: global.defaultAccount()
    }
}
