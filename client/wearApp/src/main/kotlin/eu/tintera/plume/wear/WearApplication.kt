package eu.tintera.plume.wear

import eu.tintera.plume.core.storage.AutoStorageCleaner
import android.app.Application
import eu.tintera.plume.core.accounts.defaultAccount
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.di.coreModule
import eu.tintera.plume.core.logging.configureLogging
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Hodinky dostanou vrstvu kolem TDLib celou z `coreModule()` — klient, přihlášení, repository
 * i s eager odběry. UI telefonu (`appModule()` ve `shared`) sem nejde.
 */
class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Kermit do logcatu a do Crashlytics (release), PŘED grafem závislostí: eager singletony
        // a start TDLib logují už uvnitř `startKoin` (docs/poznatky/release.md, „Hlášení pádů").
        configureLogging()
        val koin = startKoin {
            androidContext(this@WearApplication)
            modules(
                coreModule(
                    accountModules = {
                        listOf(
                            // Presenter notifikací je jeden na proces: kreslí ho kolektor a akce
                            // z notifikace (WearNotificationReceiver) do něj připisují odpověď.
                            module {
                                single { WearNotifications(androidContext(), get(named(APP_SCOPE))) }
                                // Jeden na proces: automatický úklid a tlačítko v Nastavení se nesmí potkat.
                                single {
                                    // Hodinky mají místa málo: 3 dny a 100 MB (dřív WearStorageCleaner, týž soubor voleb).
                                    AutoStorageCleaner(
                                        context = androidContext(),
                                        repository = get(),
                                        scope = get(named(APP_SCOPE)),
                                        prefsName = "wear_storage",
                                        keepSeconds = 3 * 24 * 3600,
                                        maxBytes = 100L * 1024 * 1024,
                                    )
                                }
                            },
                        )
                    },
                ),
            )
        }.koin.defaultAccount()

        // TDLib se startuje s procesem, ne s první obrazovkou — stejně jako na telefonu
        // (`startKoin` ve shared): bez toho by zůstal ve WaitTdlibParameters.
        koin.get<CoroutineScope>(named(APP_SCOPE)).launch { koin.get<TelegramSession>().start() }

        // Hlášení sítě a popředí: spojení, které systém v pozadí zabil, se po návratu obnoví.
        koin.get<ConnectionController>().start()

        // Vlastní push a notifikace hodinek, a zrcadlení z telefonu jen bez přihlášení.
        val appScope = koin.get<CoroutineScope>(named(APP_SCOPE))
        val session = koin.get<TelegramSession>()
        koin.get<WearNotifications>().start(koin.get<NotificationRepository>())
        WearPushRegistrar(session, koin.get<NotificationRepository>(), appScope).start()
        PhoneBridging(this, appScope).start(session)
        // Počet nepřečtených pro dlaždici a komplikaci (kreslí je systém i bez běžícího procesu).
        UnreadCountPublisher(this, appScope).start(session, koin.get<ChatRepository>())
        RecentChatsPublisher(this, appScope, koin.get<FileRepository>()).start(session, koin.get<ChatRepository>())
        // Úklid stažených médií, hodinky mají místa málo.
        koin.get<AutoStorageCleaner>().start(session)
    }
}
