package eu.tintera.plume.di

import eu.tintera.ai.core.NoDeviceLoad
import eu.tintera.ai.core.MeteredNetwork
import eu.tintera.ai.core.NoMeteredNetwork
import eu.tintera.ai.core.DeviceLoad
import eu.tintera.plume.core.stickers.IosStickerMaker
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.webapp.MINI_APP_STORAGE_FILE_NAME
import eu.tintera.plume.di.MINI_APP_STORAGE
import eu.tintera.plume.core.ui.IosStoryVideo
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.ocr.VisionTextRecognizer
import eu.tintera.plume.core.snooze.IosSnoozeReminders
import eu.tintera.plume.core.snooze.SnoozeReminders
import eu.tintera.ai.core.AppleOnDeviceAi
import eu.tintera.ai.core.IosOnDeviceModel
import eu.tintera.ai.core.NoopOnDeviceAi
import eu.tintera.ai.core.OnDeviceAi
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
import eu.tintera.plume.core.files.WebFileDownloader
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.share.IosShareInbox
import eu.tintera.plume.core.share.IosShareSnapshot
import eu.tintera.plume.core.share.IosShareTargetPublisher
import eu.tintera.plume.core.share.ShareTargetPublisher
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.secrets.MiniAppSecretStore
import eu.tintera.plume.core.secrets.KeychainSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.applicationSupportDirectory
import eu.tintera.plume.core.notifications.NotificationPresenter
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
    // Hudba na zamykací obrazovce a v Ovládacím centru, jedna přes všechny účty jako fronta. Na
    // hlavním vlákně — z něj chodí povely systému a z něj se ovládá přehrávač.
    single(createdAtStart = true) {
        NowPlayingControls(queue = get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)).also { it.start() }
    }
    // Apple Intelligence (Foundation Models), kterou Swift zaregistruje před `initKoin`; bez ní
    // (iOS pod 26) model v zařízení na iOS není.
    single<OnDeviceAi> { IosOnDeviceModel.model?.let(::AppleOnDeviceAi) ?: NoopOnDeviceAi }
    // Gemma tu není, zátěž telefonu pro model v zařízení neřešíme.
    single<DeviceLoad> { NoDeviceLoad }
    // Druh sítě se tu nepozná, volba „jen přes Wi-Fi" se nenabízí.
    single<MeteredNetwork> { NoMeteredNetwork }
    single<AppLockStore> {
        object : AppLockStore, SecretStore by KeychainSecretStore(
            service = "eu.tintera.plume.applock", account = "pin", logTag = "AppLock",
        ) {}
    }
    single<MiniAppSecretStore> {
        object : MiniAppSecretStore, SecretStore by KeychainSecretStore(
            service = "eu.tintera.plume.miniapps", account = "secure-storage", logTag = "WebApp",
        ) {}
    }
    single { NotificationPermission() }
    single { LaunchAtLogin() }
    single { UrlOpener(settings = get()) }
    single { AppLanguage() }
    single { FileOpener() }
    single { WebFileDownloader(get()) }
    single { MediaSaver() }
    single<TextRecognizer> { VisionTextRecognizer() }
    single { MediaSharer() }
    single(createdAtStart = true) {
        IosShareInbox(requests = get(), chatOpenRequests = get(), scope = get(named(APP_SCOPE)))
            .also { it.start(get<AppForeground>().isForeground) }
    }
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
    single<StickerMaker> { IosStickerMaker() }
    single<StoryVideo> { IosStoryVideo(get()) }
    single<ProfileVideo> { IosProfileVideo() }
    // Nový na každou konverzaci — nahrávání patří obrazovce, ne aplikaci.
    factory { AudioRecorder() }
    // Nový na každou přehrávanou hlasovku.
    factory { AudioEngine() }
    // Senzory u ucha: jeden na aplikaci, protože hraje nejvýš jedna hlasovka.
    single { EarSensor() }
    // Application Support, stejně jako databáze TDLib: data aplikace, ne dokumenty uživatele.
    single { createSettingsDataStore(applicationSupportDirectory() + "/" + SETTINGS_FILE_NAME) }
    single(named(MINI_APP_STORAGE)) { createSettingsDataStore(applicationSupportDirectory() + "/" + MINI_APP_STORAGE_FILE_NAME) }
}

actual fun Module.platformAccountModule() {
    single { NotificationPresenter(chats = get()) }
    // Sdílení přes rozšíření: snímek chatů pro výběr v něm a vyzvednutí toho, co v něm uživatel sdílel.
    single(createdAtStart = true) {
        IosShareSnapshot(chats = get(), files = get(), holder = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    single<ShareTargetPublisher> { IosShareTargetPublisher(get()) }
}
