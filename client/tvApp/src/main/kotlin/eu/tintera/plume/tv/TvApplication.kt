package eu.tintera.plume.tv

import android.app.Application
import eu.tintera.plume.core.accounts.defaultAccount
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.di.coreModule
import eu.tintera.plume.core.logging.configureLogging
import eu.tintera.plume.core.settings.sync.BotCloudStore
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Televize dostane vrstvu kolem TDLib celou z `coreModule()` — klient, přihlášení, repository
 * i s eager odběry — stejně jako hodinky. UI telefonu (`appModule()` ve `shared`) sem nejde.
 */
class TvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Kermit do logcatu a do Crashlytics (release), PŘED grafem závislostí: eager singletony
        // a start TDLib logují už uvnitř `startKoin` (docs/poznatky/release.md, „Hlášení pádů").
        configureLogging()
        val koin = startKoin {
            androidContext(this@TvApplication)
            modules(coreModule(), module { single { TvVideoPositions(androidContext()) } })
        }.koin.defaultAccount()

        // TDLib se startuje s procesem, ne s první obrazovkou — jako na telefonu a hodinkách:
        // bez toho by zůstal ve WaitTdlibParameters.
        koin.get<CoroutineScope>(named(APP_SCOPE)).launch { koin.get<TelegramSession>().start() }

        // Hlášení sítě a popředí: spojení, které systém v pozadí zabil, se po návratu obnoví.
        koin.get<ConnectionController>().start()

        // Rozkoukaná videa s telefonem přes úložiště bota (jako `CloudSync` na telefonu).
        TvPositionSync(
            positions = koin.get(),
            store = BotCloudStore(webApps = koin.get(), chats = koin.get()),
            accountId = koin.get<ChatRepository>().savedMessagesChatId,
            authStage = koin.get<TelegramSession>().authStage,
            foreground = koin.getOrNull<AppForeground>()?.isForeground,
            scope = koin.get(named(APP_SCOPE)),
        ).start()
    }
}
