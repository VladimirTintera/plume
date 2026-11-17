package eu.tintera.plume.core.notifications

/**
 * Co o síti aplikace v pozadí řekl systém při pushi (Android, `BackgroundDataWarning`).
 *
 * **`BLOCKED` sám o sobě neříká PROČ.** Na měřené síti ho systém hlásí z kteréhokoli důvodu
 * blokování: vypnutá data na pozadí a spořič dat, ale taky Doze, App Standby, spořič baterie,
 * omezená baterie a od Androidu 15 síť v pozadí (`APP_BACKGROUND`). Ty ostatní ruší dočasná
 * výjimka, kterou systém dává pushi s vysokou prioritou — jenže jen když ji push dostal
 * a když se už propsala do síťových pravidel. Upozornění „Plume nemá data na pozadí" pak
 * vyskakovalo s oběma přepínači zapnutými.
 */
data class BackgroundNetworkSnapshot(
    /** Aplikace v popředí (i se službou v popředí) má síť vždycky — nic se z ní nepozná. */
    val inForeground: Boolean,
    /** Je aktivní síť měřená? `false` i bez sítě. */
    val metered: Boolean,
    /** Hlásí systém aplikaci síť jako zablokovanou (`DetailedState.BLOCKED`)? */
    val blocked: Boolean,
    /** Spořič dat zapnutý a Plume bez výjimky (`RESTRICT_BACKGROUND_STATUS_ENABLED`). */
    val dataSaverBlocks: Boolean,
    /** Dorazil push s vysokou prioritou? Snížený výjimku z Doze a pozadí nedostane. */
    val highPriority: Boolean,
    val batterySaver: Boolean,
    /** „Omezeno" v nastavení baterie aplikace. */
    val backgroundRestricted: Boolean,
    /** Skupina App Standby „omezená" (Android 11+). */
    val restrictedBucket: Boolean,
)

enum class BackgroundDataVerdict {
    /** Síť na měřené síti v pozadí prošla: data na pozadí povolená. */
    Allowed,

    /** Síť zablokovaná a jiný důvod než data na pozadí nebo spořič dat nepřipadá v úvahu. */
    Restricted,

    /** Nejde poznat: popředí, neměřená síť, nebo blokování mohlo mít jinou příčinu. */
    Unknown,
}

fun backgroundDataVerdict(snapshot: BackgroundNetworkSnapshot): BackgroundDataVerdict = with(snapshot) {
    when {
        inForeground || !metered -> BackgroundDataVerdict.Unknown
        !blocked -> BackgroundDataVerdict.Allowed
        // Spořič dat bez výjimky blokuje měřenou síť v pozadí vždycky; to API říká přímo.
        dataSaverBlocks -> BackgroundDataVerdict.Restricted
        !highPriority || batterySaver || backgroundRestricted || restrictedBucket -> BackgroundDataVerdict.Unknown
        else -> BackgroundDataVerdict.Restricted
    }
}
