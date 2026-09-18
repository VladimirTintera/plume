package eu.tintera.plume.di

import eu.tintera.plume.core.audio.AudioRecorder
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.TextClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

actual fun Module.platformModule() {
    single { TelegramPaths(androidContext()) }
    single { NotificationPermission(androidContext()) }
    single { UrlOpener(androidContext()) }
    single { FileOpener(androidContext()) }
    single { MediaSaver(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single { AppForeground(androidContext()) }
    single { TextClipboard(androidContext()) }
    // Nový na každou konverzaci — nahrávání patří obrazovce, ne aplikaci.
    factory { AudioRecorder(androidContext()) }

    // `filesDir`, ne cache: nastavení se nesmí ztratit, když systém uklízí místo.
    single { createSettingsDataStore(androidContext().filesDir.resolve(SETTINGS_FILE_NAME).absolutePath) }

    single {
        NotificationPresenter(
            context = androidContext(),
            // Vlastní scope: notifikace musí sbírat po celou dobu běhu procesu, ne po dobu
            // života obrazovky.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
