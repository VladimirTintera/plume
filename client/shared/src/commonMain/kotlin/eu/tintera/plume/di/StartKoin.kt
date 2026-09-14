package eu.tintera.plume.di

import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.NotificationRepository
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
    koin.get<NotificationPresenter>().start(koin.get<NotificationRepository>())
}
