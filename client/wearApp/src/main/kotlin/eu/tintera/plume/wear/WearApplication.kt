package eu.tintera.plume.wear

import android.app.Application
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.di.coreModule
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

/**
 * Hodinky dostanou vrstvu kolem TDLib celou z `coreModule()` — klient, přihlášení, repository
 * i s eager odběry. UI telefonu (`appModule()` ve `shared`) sem nejde.
 */
class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@WearApplication)
            modules(coreModule())
        }.koin

        // TDLib se startuje s procesem, ne s první obrazovkou — stejně jako na telefonu
        // (`startKoin` ve shared): bez toho by zůstal ve WaitTdlibParameters.
        koin.get<CoroutineScope>(named(APP_SCOPE)).launch { koin.get<TelegramSession>().start() }

        // Hlášení sítě a popředí: spojení, které systém v pozadí zabil, se po návratu obnoví.
        koin.get<ConnectionController>().start()

        // Vlastní push a notifikace hodinek, a zrcadlení z telefonu jen bez přihlášení.
        val appScope = koin.get<CoroutineScope>(named(APP_SCOPE))
        val session = koin.get<TelegramSession>()
        WearNotifications(this, appScope).start(koin.get<NotificationRepository>())
        WearPushRegistrar(session, koin.get<NotificationRepository>(), appScope).start()
        PhoneBridging(this, appScope).start(session)
    }
}
