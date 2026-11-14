package eu.tintera.plume.di

import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.logging.configureLogging
import eu.tintera.plume.core.settings.AutoDownloadSync
import eu.tintera.plume.core.storage.StorageCleaner
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.notifications.NotificationPresenter
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.AuthStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

@OptIn(ExperimentalCoroutinesApi::class)
fun startKoin(configure: KoinApplication.() -> Unit = {}): KoinApplication {
    // Writery Kermitu se nastavují dřív, než vznikne graf závislostí. Eager singletony (seznam
    // chatů, FileRepository, nastavení) se vyrábějí ještě uvnitř `startKoin` a jejich logy jsou
    // u pádů při startu to nejzajímavější — s pozdějším nastavením by skončily jen v logcatu.
    configureLogging()

    return startKoin {
        configure()
        modules(appModule())
    }.also { application ->
        val koin = application.koin
        val accounts = koin.get<Accounts>()

        // Zámek patří zařízení, ne účtu: zahodí se až s odhlášením posledního účtu. Odhlášený
        // účet vedle jiných z aplikace jen zmizí (`Accounts`), zámek s ním nemá co dělat.
        koin.get<AppLock>().start(
            foreground = koin.get<AppForeground>().isForeground,
            authStage = accounts.all.flatMapLatest { all -> all.singleOrNull()?.session?.authStage ?: flowOf(AuthStage.Ready) },
        )

        // Klienta TDLib startuje `Accounts` sám, hned s kontejnerem účtu. Tady je, co musí běžet
        // s každým účtem od startu procesu, i s účtem přidaným později.
        accounts.forEachOpened(::startAccount)
    }
}

/**
 * Co běží s účtem od vzniku jeho kontejneru, ne až s první obrazovkou.
 *
 * Presenter se startuje TADY, ne jako `single { get<NotificationPresenter>()… }` v modulu —
 * taková definice by si odvodila vlastní typ a resolvovala sama sebe (StackOverflowError).
 * Musí běžet od startu procesu, aby notifikace chodily i mimo obrazovku konverzace.
 */
private fun startAccount(account: AccountSession) {
    account.get<NotificationPresenter>().start(account.get<NotificationRepository>())

    // Hlášení sítě a popředí. Taky musí běžet od startu procesu, ne od první obrazovky:
    // síť se mění i tehdy, když se žádné UI neskládá, a návrat do popředí je jediná chvíle,
    // kdy se dá poznat spojení, které systém mezitím zabil.
    account.get<ConnectionController>().start()

    // Úklid starých médií podle „Uchovávat média". Od startu procesu ze stejného důvodu:
    // aplikace může běžet dny, aniž by se otevřelo Nastavení.
    account.get<StorageCleaner>().start()

    // Automatické stahování ze serveru po přihlášení. Od startu, protože přihlášení
    // (a odhlášení, po kterém se má příště stáhnout) se nesmí propásnout.
    account.get<AutoDownloadSync>().start()
}
