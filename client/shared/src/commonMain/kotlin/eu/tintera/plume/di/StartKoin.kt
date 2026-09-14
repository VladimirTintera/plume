package eu.tintera.plume.di

import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun startKoin(configure: KoinApplication.() -> Unit = {}) = startKoin {
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
}
