package eu.tintera.plume.di

import eu.tintera.plume.core.ui.IosSquarePhoto
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.audio.AudioRecorder
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.share.IosShareInbox
import eu.tintera.plume.core.share.IosShareSnapshot
import eu.tintera.plume.core.share.IosShareTargetPublisher
import eu.tintera.plume.core.share.ShareTargetPublisher
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.KeychainDatabaseKeyStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.applicationSupportDirectory
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.TextClipboard
import org.koin.core.module.Module
import org.koin.core.qualifier.named

actual fun Module.platformModule() {
    single { TelegramPaths() }
    single<DatabaseKeyStore> { KeychainDatabaseKeyStore() }
    single { NotificationPermission() }
    single { NotificationPresenter(chats = get()) }
    single { UrlOpener() }
    single { FileOpener() }
    single { MediaSaver() }
    single { MediaSharer() }
    // Sdílení přes rozšíření: snímek chatů pro výběr v něm a vyzvednutí toho, co v něm uživatel sdílel.
    single(createdAtStart = true) {
        IosShareSnapshot(chats = get(), files = get(), holder = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    single<ShareTargetPublisher> { IosShareTargetPublisher(get()) }
    single(createdAtStart = true) {
        IosShareInbox(requests = get(), chatOpenRequests = get(), scope = get(named(APP_SCOPE)))
            .also { it.start(get<AppForeground>().isForeground) }
    }
    single { NetworkMonitor() }
    single { AppForeground() }
    single { AppUpdates() }
    single { TextClipboard() }
    single<SquarePhoto> { IosSquarePhoto() }
    // Nový na každou konverzaci — nahrávání patří obrazovce, ne aplikaci.
    factory { AudioRecorder() }

    // Application Support, stejně jako databáze TDLib: data aplikace, ne dokumenty uživatele.
    single { createSettingsDataStore(applicationSupportDirectory() + "/" + SETTINGS_FILE_NAME) }
}
