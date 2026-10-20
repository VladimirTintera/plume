package eu.tintera.plume.di

import eu.tintera.plume.core.ui.IosStoryVideo
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.ocr.VisionTextRecognizer
import eu.tintera.plume.core.snooze.IosSnoozeReminders
import eu.tintera.plume.core.snooze.SnoozeReminders
import eu.tintera.plume.core.ai.AppleOnDeviceAi
import eu.tintera.plume.core.ai.IosOnDeviceModel
import eu.tintera.plume.core.ai.NoopOnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ui.IosProfileVideo
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.IosSquarePhoto
import eu.tintera.plume.core.ui.IosStoryPhoto
import eu.tintera.plume.core.ui.StoryPhoto
import eu.tintera.plume.core.ui.IosPhotoMarkupRenderer
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
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
import eu.tintera.plume.core.locale.AppLanguage
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.calendar.CalendarEvents
import eu.tintera.plume.core.contacts.ContactSaver
import eu.tintera.plume.core.wifi.WifiJoiner
import eu.tintera.plume.core.currency.HomeRegion
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.language.IosLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.qualifier.named

actual fun Module.platformModule() {
    // Apple Intelligence (Foundation Models), kterou Swift zaregistruje před `initKoin`; bez ní
    // (iOS pod 26) model v zařízení na iOS není.
    single<OnDeviceAi> { IosOnDeviceModel.model?.let(::AppleOnDeviceAi) ?: NoopOnDeviceAi }
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
    single { LaunchAtLogin() }
    single { NotificationPresenter(chats = get()) }
    single { UrlOpener(settings = get()) }
    single { AppLanguage() }
    single { FileOpener() }
    single { MediaSaver() }
    single<TextRecognizer> { VisionTextRecognizer() }
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
    single { CalendarEvents() }
    single { ContactSaver() }
    single { WifiJoiner() }
    single { HomeRegion() }
    single<LanguageDetector> { IosLanguageDetector() }
    single<SquarePhoto> { IosSquarePhoto() }
    single<StoryPhoto> { IosStoryPhoto() }
    single<SnoozeReminders> { IosSnoozeReminders() }
    single<PhotoMarkupRenderer> { IosPhotoMarkupRenderer() }
    single<StoryVideo> { IosStoryVideo(get()) }
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
