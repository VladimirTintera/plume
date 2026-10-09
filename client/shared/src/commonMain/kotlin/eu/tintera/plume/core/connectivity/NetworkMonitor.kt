package eu.tintera.plume.core.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Co platforma ví o síti.
 *
 * Existuje kvůli jediné větě z dokumentace `setNetworkType`: *„Calling this method forces all
 * network connections to reopen … so it must be called whenever the network is changed, even if
 * the network type remains the same."* TDLib se o mrtvém spojení sám nedozví — socket, který
 * systém zabil v dozu nebo při přepnutí Wi-Fi na mobil, vypadá z jeho strany pořád otevřený
 * a mrtvola se pozná až na vlastním timeoutu, jehož odpočet v dozu navíc neběžel. Bez tohohle
 * hlášení sedí aplikace po návratu do popředí ve `Connecting` a čeká minuty.
 *
 * Kromě toho je typ sítě to jediné, podle čeho TDLib rozlišuje statistiku přenesených dat
 * a limity automatického stahování.
 */
expect class NetworkMonitor {

    /**
     * Stav sítě; při odběru se ozve hned aktuální hodnotou, pak na každou změnu.
     *
     * Opakované hodnoty se nefiltrují tady, ale až v [eu.tintera.plume.core.telegram.ConnectionController] —
     * ten je jediné místo, které ví, co se s nimi dělá.
     */
    val status: Flow<NetworkStatus>

    /**
     * Šetří uživatel data? Android: spořič dat (a aplikace nemá výjimku), iOS: režim nízkých dat.
     * Zvlášť od [status], protože se s ním neobnovují spojení — změna by jinak TDLib zbytečně
     * hlásila novou síť. Řídí se jím automatické stahování médií.
     */
    val saveData: Flow<Boolean>
}

/**
 * Pozná platforma roaming? Na iOS ne (`NWPath` ho nehlásí), takže nastavení roaming nenabízí —
 * volba, která se nikdy neuplatní, by lhala.
 */
expect val detectsRoaming: Boolean

/**
 * Stav sítě.
 *
 * [generation] je pořadové číslo SKUTEČNÉ výměny sítě (jiná Wi-Fi, přepnutí na mobil, ztráta
 * spojení). Bez něj by se přechod mezi dvěma stejně pojmenovanými sítěmi — třeba přelezení
 * z jednoho AP na druhé — v porovnání ztratil, protože [kind] by zůstal `WiFi`. Naopak šum,
 * který Android posílá průběžně (změna kvality signálu, dodatečné ověření připojení), číslo
 * nezvedá: vynutit kvůli němu znovuotevření všech spojení by bylo horší než nedělat nic.
 */
data class NetworkStatus(
    val kind: NetworkKind,
    val generation: Long,
)

/** Druh sítě v podobě, kterou TDLib rozlišuje. */
enum class NetworkKind {

    /** Žádná použitelná síť. TDLib při ní spojení ani nezkouší. */
    None,

    WiFi,

    Mobile,

    /**
     * Mobilní data v roamingu. Zvlášť kvůli limitům stahování — na iOS se nerozlišuje
     * (`NWPath` roaming nehlásí) a hlásí se jako [Mobile].
     */
    MobileRoaming,

    /** Ethernet, VPN a všechno ostatní, co nespadá do předchozích. */
    Other,
}
