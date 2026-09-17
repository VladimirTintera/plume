package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkKind
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.connectivity.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Drží TDLib v obraze o tom, co se děje kolem: jaká je síť a jestli se uživatel dívá.
 *
 * Tohle je ta část, kvůli které celá vrstva vznikla. Bez ní se aplikace po delší době v pozadí
 * často nepřipojila — systém v dozu spojení zabil, TDLib ho považoval za živé a nový pokus
 * odložil na svůj vlastní odpočet, který mezitím neběžel. Killnutí a spuštění pomáhalo proto,
 * že nový klient otevírá čerstvé sokety; tady se totéž udělá bez killnutí.
 *
 * Startuje se ze `startKoin`, ne z UI: síť se mění i tehdy, když se žádná obrazovka neskládá.
 */
class ConnectionController(
    private val connection: ConnectionRepository,
    private val network: NetworkMonitor,
    private val foreground: AppForeground,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Telegram")

    /*
     * Poslední známá síť. Při návratu do popředí se hlásí znovu, i když se nezměnila — proto ji
     * nestačí jen propustit dál. Výchozí `Other` je nejbezpečnější odhad: TDLib při něm spojení
     * zkouší (na rozdíl od `None`) a v nejhorším případě se jen nepřesně počítá statistika.
     */
    private val lastNetwork = MutableStateFlow(NetworkStatus(NetworkKind.Other, generation = 0))

    fun start() {
        scope.launch {
            // `distinctUntilChanged` až tady: monitor hlásí, co ví, filtrování je rozhodnutí
            // toho, kdo s hodnotami něco dělá.
            network.status.distinctUntilChanged().collect { status ->
                lastNetwork.value = status
                connection.networkChanged(status)
            }
        }

        scope.launch {
            foreground.isForeground.distinctUntilChanged().collect { inForeground ->
                connection.setOnline(inForeground)

                /*
                 * Nejdůležitější řádek celé třídy. Síť se nezměnila — změnilo se jen to, že se
                 * uživatel dívá — a přesto se hlásí znovu, protože `setNetworkType` je jediný
                 * způsob, jak TDLib přinutit zahodit spojení, o kterých neví, že jsou mrtvá.
                 */
                if (inForeground) {
                    logger.i { "Návrat do popředí, obnovuji spojení." }
                    connection.networkChanged(lastNetwork.value)
                }
            }
        }
    }
}
