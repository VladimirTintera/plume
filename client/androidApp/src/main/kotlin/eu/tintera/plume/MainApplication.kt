package eu.tintera.plume

import android.app.Application
import android.content.Context
import eu.tintera.plume.core.locale.LegacyAppLanguage
import eu.tintera.plume.di.startKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext

class MainApplication : Application() {

    // Jazyk aplikace do Androidu 12: podle jazyka kontextu aplikace framework obnovuje výchozí
    // `Locale` procesu (viz LegacyAppLanguage). Od 13 to vrátí kontext beze změny.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LegacyAppLanguage.wrapApplication(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Jazyk aplikace do Androidu 12 drží aplikace sama; platit musí dřív, než cokoli
        // přeloží první text (i notifikace z pushe bez aktivity).
        LegacyAppLanguage.applyToProcess(this)
        startKoin {
            androidLogger()
            androidContext(this@MainApplication)
        }

        // Registrace pro push je Android specifická (potřebuje Firebase), takže žije tady,
        // ne ve sdíleném modulu.
        val koin = GlobalContext.get()
        PushRegistrar(session = koin.get(), repository = koin.get()).start()
    }
}
