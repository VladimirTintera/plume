package eu.tintera.plume.di

import eu.tintera.plume.core.logging.configureLogging
import eu.tintera.plume.core.settings.AutoDownloadSync
import eu.tintera.plume.core.storage.StorageCleaner
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.notifications.NotificationPresenter
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun startKoin(configure: KoinApplication.() -> Unit = {}): KoinApplication {
    // Writery Kermitu se nastavují dřív, než vznikne graf závislostí. Eager singletony (seznam
    // chatů, FileRepository, nastavení) se vyrábějí ještě uvnitř `startKoin` a jejich logy jsou
    // u pádů při startu to nejzajímavější — s pozdějším nastavením by skončily jen v logcatu.
    configureLogging()

    return startKoin {
        configure()
        modules(appModule())
    }.also { application ->
        // Presenter se startuje TADY, ne jako `single { get<NotificationPresenter>()… }` v modulu —
        // taková definice by si odvodila vlastní typ a resolvovala sama sebe (StackOverflowError).
        // Musí běžet od startu procesu, aby notifikace chodily i mimo obrazovku konverzace.
        val koin = application.koin

        // TDLib se MUSÍ nastartovat tady, ne až v AuthViewModel.
        //
        // Když push probudí mrtvý proces, žádné UI se neskládá — poběží jen Application.onCreate
        // a FirebaseMessagingService. Kdyby `setTdlibParameters` viselo na vzniku ViewModelu,
        // zůstal by TDLib ve WaitTdlibParameters a `processPushNotification` by nemělo co
        // rozšifrovat. Push by dorazil a tiše nic neudělal.
        val appScope = koin.get<CoroutineScope>(named(APP_SCOPE))
        appScope.launch { koin.get<TelegramSession>().start() }

        koin.get<NotificationPresenter>().start(koin.get<NotificationRepository>())

        // Hlášení sítě a popředí. Taky musí běžet od startu procesu, ne od první obrazovky:
        // síť se mění i tehdy, když se žádné UI neskládá, a návrat do popředí je jediná chvíle,
        // kdy se dá poznat spojení, které systém mezitím zabil.
        koin.get<ConnectionController>().start()

        // Úklid starých médií podle „Uchovávat média". Od startu procesu ze stejného důvodu:
        // aplikace může běžet dny, aniž by se otevřelo Nastavení.
        koin.get<StorageCleaner>().start()

        // Automatické stahování ze serveru po přihlášení. Od startu, protože přihlášení
        // (a odhlášení, po kterém se má příště stáhnout) se nesmí propásnout.
        koin.get<AutoDownloadSync>().start()
    }
}
