package eu.tintera.plume.di

import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.ui.UrlOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

actual fun Module.platformModule() {
    single { TelegramPaths(androidContext()) }
    single { NotificationPermission(androidContext()) }
    single { UrlOpener(androidContext()) }

    single {
        NotificationPresenter(
            context = androidContext(),
            // Vlastní scope: notifikace musí sbírat po celou dobu běhu procesu, ne po dobu
            // života obrazovky.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
