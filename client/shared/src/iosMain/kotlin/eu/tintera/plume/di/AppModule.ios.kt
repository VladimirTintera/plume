package eu.tintera.plume.di

import eu.tintera.plume.core.ai.NoopOnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ui.IosProfileVideo
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.IosSquarePhoto
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.audio.AudioEngine
import eu.tintera.plume.core.audio.EarSensor
import eu.tintera.plume.core.audio.NowPlayingControls
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
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.secrets.KeychainSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.applicationSupportDirectory
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.language.IosLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.qualifier.named

actual fun Module.platformModule() {
    // Model v zařízení zatím jen na Androidu; Apple Foundation Models by musely přijít ze Swiftu.
    single<OnDeviceAi> { NoopOnDeviceAi }
    single { TelegramPaths() }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by KeychainSecretStore(
            service = "eu.tintera.plume.tdlib", account = "database-key", logTag = "DatabaseKey",
        ) {}
    }
    single<AppLockStore> {
        object : AppLockStore, SecretStore by KeychainSecretStore(
            service = "eu.tintera.plume.applock", account = "pin", logTag = "AppLock",
        ) {}
    }
    single { NotificationPermission() }
    single { NotificationPresenter(chats = get()) }
    single { UrlOpener(settings = get()) }
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
    single<LanguageDetector> { IosLanguageDetector() }
    single<SquarePhoto> { IosSquarePhoto() }
    single<ProfileVideo> { IosProfileVideo() }
    // Nový na každou konverzaci — nahrávání patří obrazovce, ne aplikaci.
    factory { AudioRecorder() }
    // Nový na každou přehrávanou hlasovku.
    factory { AudioEngine() }
    // Senzory u ucha: jeden na aplikaci, protože hraje nejvýš jedna hlasovka.
    single { EarSensor() }
    // Hudba na zamykací obrazovce a v Ovládacím centru. Na hlavním vlákně — z něj chodí povely
    // systému a z něj se ovládá přehrávač.
    single(createdAtStart = true) {
        NowPlayingControls(queue = get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)).also { it.start() }
    }

    // Application Support, stejně jako databáze TDLib: data aplikace, ne dokumenty uživatele.
    single { createSettingsDataStore(applicationSupportDirectory() + "/" + SETTINGS_FILE_NAME) }
}
