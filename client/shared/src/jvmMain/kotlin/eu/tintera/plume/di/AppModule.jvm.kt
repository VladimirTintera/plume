package eu.tintera.plume.di

import eu.tintera.plume.core.accounts.notificationName
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.ai.core.NoDeviceLoad
import eu.tintera.ai.core.MeteredNetwork
import eu.tintera.ai.core.NoMeteredNetwork
import eu.tintera.ai.core.DeviceLoad
import eu.tintera.plume.core.ai.DesktopAudioClips
import eu.tintera.plume.core.ai.aiModelsDirectory
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.webapp.MINI_APP_STORAGE_FILE_NAME
import eu.tintera.plume.di.MINI_APP_STORAGE
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.ui.UnsupportedStoryVideo
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.ai.core.NoopOnDeviceAi
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.provider
import eu.tintera.ai.engine.LITERT_ENGINE_FACTORY
import eu.tintera.ai.engine.LLAMACPP_ENGINE_FACTORY
import eu.tintera.ai.core.BundledLiteRtRuntime
import eu.tintera.ai.core.ModelRuntime
import eu.tintera.ai.core.DesktopLiteRtPlatform
import eu.tintera.plume.core.ai.GEMMA_IDLE_TEXT_MS
import eu.tintera.ai.core.LiteRtOnDeviceAi
import eu.tintera.ai.core.RoutingOnDeviceAi
import eu.tintera.plume.core.ai.gemmaIdleUnloadMs
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.plume.core.audio.AudioEngine
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.DesktopCallMedia
import eu.tintera.plume.core.calls.DesktopCallRinger
import eu.tintera.plume.core.calls.DesktopCallShutdown
import eu.tintera.plume.core.telegram.CallRepository
import org.koin.core.qualifier.named
import eu.tintera.plume.core.audio.AudioRecorder
import eu.tintera.plume.core.audio.EarSensor
import eu.tintera.plume.core.calendar.CalendarEvents
import eu.tintera.plume.core.contacts.ContactSaver
import eu.tintera.plume.core.currency.HomeRegion
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.files.WebFileDownloader
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.locale.AppLanguage
import eu.tintera.plume.core.notifications.DesktopNotifier
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.notifications.desktopNotifier
import eu.tintera.plume.core.ocr.RecognizedText
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.secrets.MiniAppSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.secrets.desktopSecretStore
import eu.tintera.plume.core.secrets.secretService
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.calls.CALL_JOURNAL_DIRECTORY
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.share.ShareTargetPublisher
import eu.tintera.plume.core.snooze.SnoozeReminders
import eu.tintera.plume.core.notifications.NotificationPresenter
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.DesktopSquarePhoto
import eu.tintera.plume.core.ui.DesktopStoryPhoto
import eu.tintera.plume.core.ui.StoryPhoto
import eu.tintera.plume.core.ui.DesktopPhotoMarkupRenderer
import eu.tintera.plume.core.stickers.DesktopStickerMaker
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.wifi.WifiJoiner
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Instant
import org.koin.core.module.Module

/**
 * Desktop (JVM). Co platforma zatím neumí, dostává prázdnou implementaci — rozhraní volající
 * kód vede tak, aby „neumí" uměl ohlásit (viz docs/roadmap.md, sekce Desktop).
 */
actual fun Module.platformModule() {
    // Model v zařízení: Gemma a Qwen přes LiteRT-LM jako na Androidu; Gemini Nano (AICore) tu
    // není, směrovač na jeho místo dostane prázdný model a „Automaticky" ho přeskočí.
    single<OnDeviceAi> { RoutingOnDeviceAi(geminiNano = NoopOnDeviceAi, liteRt = get<LiteRtOnDeviceAi>()) }
    single {
        LiteRtOnDeviceAi(
            DesktopLiteRtPlatform(aiModelsDirectory, DesktopAudioClips),
            // Oba runtime jsou v balíčku (ai-engine :ai-litert a :ai-llamacpp-desktop).
            mapOf(
                ModelRuntime.LiteRt to BundledLiteRtRuntime(LITERT_ENGINE_FACTORY),
                ModelRuntime.LlamaCpp to BundledLiteRtRuntime(LLAMACPP_ENGINE_FACTORY),
            )::getValue,
            get(),
            // Až po použití, ne při stavbě: směrovač (OnDeviceAi) tuhle instanci sám potřebuje.
            idleUnloadMs = { model ->
                if (model.provider == AiProvider.Gemma) {
                    get<OnDeviceAi>().gemmaIdleUnloadMs(get<AppSettings>().onDeviceAiModels.value)
                } else {
                    GEMMA_IDLE_TEXT_MS
                }
            },
        )
    }
    // Úsporný režim a teplotu počítače pro model v zařízení neřešíme.
    single<DeviceLoad> { NoDeviceLoad }
    // Druh sítě se tu nepozná, volba „jen přes Wi-Fi" se nenabízí.
    single<MeteredNetwork> { NoMeteredNetwork }
    // Hovory jen se zvukem přes ntgcalls; bez přibalené knihovny (Intel Mac, build bez sítě)
    // se CallMedia neregistruje a hovory se nenabídnou (`CallRepository.supported`).
    if (DesktopCallMedia.available()) {
        single { DesktopCallMedia() }
        single<CallMedia> { get<DesktopCallMedia>() }
        // Deník hovorů pro diagnostiku (Nastavení → Pomoc); přenos tu čísla zatím nehlásí, jen události.
        single { CallJournal(File(desktopDataDirectory, CALL_JOURNAL_DIRECTORY).path, scope = get(named(APP_SCOPE))) }
    }
    single<AppLockStore> {
        object : AppLockStore, SecretStore by desktopSecretStore(
            service = secretService("eu.tintera.plume.applock"), account = "pin", logTag = "AppLock",
        ) {}
    }
    single<MiniAppSecretStore> {
        object : MiniAppSecretStore, SecretStore by desktopSecretStore(
            service = secretService("eu.tintera.plume.miniapps"), account = "secure-storage", logTag = "WebApp",
        ) {}
    }
    single<DesktopNotifier> { desktopNotifier() }
    single { NotificationPermission(notifier = get()) }
    single { LaunchAtLogin() }
    single { UrlOpener(settings = get()) }
    single { AppLanguage() }
    single { FileOpener() }
    single { WebFileDownloader(get()) }
    single { MediaSaver() }
    single<TextRecognizer> {
        object : TextRecognizer {
            override suspend fun recognize(path: String): RecognizedText? = null
        }
    }
    single { MediaSharer() }
    single<ShareTargetPublisher> {
        object : ShareTargetPublisher {
            override fun publish(slot: AccountSlot, chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean) = Unit
        }
    }
    single { AppUpdates() }
    single { TextClipboard() }
    single { CalendarEvents() }
    single { ContactSaver() }
    single { WifiJoiner() }
    single { HomeRegion() }
    single<LanguageDetector> { LanguageDetector { null } }
    single<SquarePhoto> { DesktopSquarePhoto() }
    single<StoryPhoto> { DesktopStoryPhoto() }
    single<SnoozeReminders> {
        object : SnoozeReminders {
            override fun schedule(slot: AccountSlot, chatId: Long, title: String, at: Instant) = Unit

            override fun cancel(slot: AccountSlot, chatId: Long) = Unit
        }
    }
    single<PhotoMarkupRenderer> { DesktopPhotoMarkupRenderer() }
    single<StickerMaker> { DesktopStickerMaker() }
    single<StoryVideo> { UnsupportedStoryVideo }
    single<ProfileVideo> {
        object : ProfileVideo {
            override suspend fun durationMs(path: String): Long =
                throw UnsupportedOperationException("Profilové video na desktopu zatím není.")

            override suspend fun frame(path: String, atMs: Long, edge: Int): String =
                throw UnsupportedOperationException("Profilové video na desktopu zatím není.")

            override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String =
                throw UnsupportedOperationException("Profilové video na desktopu zatím není.")
        }
    }
    factory { AudioRecorder() }
    factory { AudioEngine() }
    single { EarSensor() }

    single { createSettingsDataStore(File(desktopDataDirectory, SETTINGS_FILE_NAME).path) }
    single(named(MINI_APP_STORAGE)) { createSettingsDataStore(File(desktopDataDirectory, MINI_APP_STORAGE_FILE_NAME).path) }
}

actual fun Module.platformAccountModule() {
    if (DesktopCallMedia.available()) {
        // Ukončení aplikace uprostřed hovoru: zavěsit a zastavit knihovnu dřív, než JVM skončí.
        single(createdAtStart = true) { DesktopCallShutdown(calls = get(), media = get()).also { it.install() } }
        // Vyzvánění příchozího hovoru a tón protistrany u odchozího.
        single(createdAtStart = true) { DesktopCallRinger(calls = get(), scope = get(named(APP_SCOPE))).also { it.start() } }
    }
    single {
        NotificationPresenter(
            chats = get(),
            settings = get(),
            slot = get(),
            chatOpenRequests = get(),
            notifier = get(),
            // Vlastní scope: oznámení se sbírají po celou dobu běhu procesu.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            snoozes = get(),
            actions = get(),
            callRequests = get(),
            callsSupported = get<CallRepository>().supported,
            accountName = { get<Accounts>().notificationName(get()) },
            otherAccounts = { get<Accounts>().all.value.size - 1 },
        )
    }
}
