package eu.tintera.plume

import android.app.Application
import eu.tintera.plume.di.startKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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
