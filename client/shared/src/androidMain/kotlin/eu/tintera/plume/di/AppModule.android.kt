package eu.tintera.plume.di

import eu.tintera.ai.core.AudioClipDecoder
import eu.tintera.ai.core.ModelDownloadNotifications
import eu.tintera.plume.core.ai.AudioClips
import eu.tintera.plume.core.ai.ModelDownloadWorker
import eu.tintera.plume.core.ai.PlumeModelDownloadNotifications
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ai.gemmaIdleUnloadMs
import eu.tintera.ai.core.AiProvider
import eu.tintera.plume.core.ai.GEMMA_IDLE_TEXT_MS
import eu.tintera.ai.core.DeviceLoad
import eu.tintera.ai.core.AndroidDeviceLoad
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.webapp.MINI_APP_STORAGE_FILE_NAME
import eu.tintera.plume.di.MINI_APP_STORAGE
import eu.tintera.plume.core.ui.AndroidStoryVideo
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.calls.AndroidCallPointerDisplay
import eu.tintera.plume.core.calls.CallPointerDisplay
import eu.tintera.plume.core.calls.CallRinger
import eu.tintera.plume.core.calls.CallServiceControls
import eu.tintera.plume.features.videochat.VideoChatServiceControls
import eu.tintera.plume.core.calls.TelecomCallBridge
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.NtgCallMedia
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.calls.NtgLiveStreamMedia
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.speech.SystemSpeechToText
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.translation.MlKitTranslator
import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.ocr.MlKitTextRecognizer
import eu.tintera.plume.core.stickers.AndroidStickerMaker
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.widget.ChatsWidgetUpdater
import eu.tintera.plume.core.widget.AndroidHomeWidgets
import eu.tintera.plume.core.widget.HomeWidgets
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.snooze.AndroidSnoozeReminders
import eu.tintera.plume.core.snooze.SnoozeReminders
import eu.tintera.plume.core.ai.PhotoDescriptionSchedule
import eu.tintera.ai.core.LiteRtOnDeviceAi
import eu.tintera.ai.core.AndroidLiteRtPlatform
import eu.tintera.ai.core.AndroidMeteredNetwork
import eu.tintera.ai.core.MeteredNetwork
import eu.tintera.ai.core.PlayLiteRtRuntime
import eu.tintera.ai.core.SplitInstallConfirmations
import eu.tintera.ai.core.ModelRuntime
import eu.tintera.plume.ai.engine.LITERT_ENGINE_FACTORY
import eu.tintera.plume.ai.engine.LLAMACPP_ENGINE_FACTORY
import eu.tintera.ai.core.MlKitOnDeviceAi
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.RoutingOnDeviceAi
import eu.tintera.plume.core.ui.AndroidProfileVideo
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.AndroidSquarePhoto
import eu.tintera.plume.core.ui.AndroidStoryPhoto
import eu.tintera.plume.core.ui.StoryPhoto
import eu.tintera.plume.core.ui.AndroidPhotoMarkupRenderer
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.audio.AudioEngine
import eu.tintera.plume.core.audio.MusicControls
import eu.tintera.plume.core.location.LiveLocationControls
import eu.tintera.plume.core.audio.EarSensor
import eu.tintera.plume.core.audio.AudioRecorder
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.files.WebFileDownloader
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.share.AndroidShareTargetPublisher
import eu.tintera.plume.core.share.ShareIntake
import eu.tintera.plume.core.share.ShareTargetPublisher
import eu.tintera.plume.core.share.ShareStore
import eu.tintera.plume.core.share.FileShareStore
import eu.tintera.plume.core.ui.OUTGOING_DIRECTORY
import okio.Path.Companion.toPath
import java.io.File
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.secrets.KeystoreSecretStore
import eu.tintera.plume.core.secrets.MiniAppSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.calls.CALL_JOURNAL_DIRECTORY
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.notifications.NotificationPresenter
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.locale.AppLanguage
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.calendar.CalendarEvents
import eu.tintera.plume.core.contacts.AndroidPhoneContacts
import eu.tintera.plume.core.contacts.ContactSaver
import eu.tintera.plume.core.contacts.ContactSyncer
import eu.tintera.plume.core.contacts.PhoneContacts
import eu.tintera.plume.core.wifi.WifiJoiner
import eu.tintera.plume.core.currency.HomeRegion
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.language.AndroidLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named

actual fun Module.platformModule() {
    // Model v zařízení: Gemini Nano v AICore, nebo Gemma a Qwen přes LiteRT-LM, podle volby
    // uživatele (RoutingOnDeviceAi). Obojí jednou na proces — klienti ML Kitu i engine LiteRT-LM
    // jsou drahé.
    single<OnDeviceAi> { RoutingOnDeviceAi(geminiNano = MlKitOnDeviceAi(), liteRt = get<LiteRtOnDeviceAi>()) }
    // Souhlas Play před instalací runtime: runtime ho ohlásí, dialog ukáže MainActivity.
    single { SplitInstallConfirmations() }
    // Notifikace stahování modelu (kanál, ikona, texty Plume) pro úlohu ModelDownloadWorker.
    single<ModelDownloadNotifications> { PlumeModelDownloadNotifications() }
    // Zvlášť, protože ji potřebuje i úloha stahování (ModelDownloadWorker) — stav stahování,
    // který ukazuje Nastavení, musí být TÝŽ, do kterého úloha zapisuje.
    single {
        LiteRtOnDeviceAi(
            // Úloha stahování je podtřída v Plume (jméno si pamatuje WorkManager), zvuk dekóduje
            // systém přes AudioClips z :client:core (používají ho i hodinky).
            AndroidLiteRtPlatform(androidContext(), ModelDownloadWorker::class.java, AudioClipDecoder(AudioClips::wavClips)),
            // Každý runtime je vlastní součást z Google Play (client/litertFeature, client/llamacppFeature).
            mapOf(
                ModelRuntime.LiteRt to PlayLiteRtRuntime(
                    androidContext(),
                    module = "litertFeature",
                    nativeLibrary = "litertlm_jni",
                    factoryClass = LITERT_ENGINE_FACTORY,
                    confirmations = get(),
                ),
                ModelRuntime.LlamaCpp to PlayLiteRtRuntime(
                    androidContext(),
                    module = "llamacppFeature",
                    nativeLibrary = "plume_llama",
                    factoryClass = LLAMACPP_ENGINE_FACTORY,
                    confirmations = get(),
                ),
            )::getValue,
            get(),
            // Až po použití, ne při stavbě: směrovač (OnDeviceAi) tuhle instanci sám potřebuje.
            // Qwen umí jen text, takže drží jako Gemma, která text počítá.
            idleUnloadMs = { provider ->
                if (provider == AiProvider.Gemma) {
                    get<OnDeviceAi>().gemmaIdleUnloadMs(get<AppSettings>().onDeviceAiModels.value)
                } else {
                    GEMMA_IDLE_TEXT_MS
                }
            },
            meteredNetwork = get(),
        )
    }
    // Placená síť: modely se mimo Wi-Fi stahují jen po dotazu (Nastavení → AI v zařízení).
    single<MeteredNetwork> { AndroidMeteredNetwork(androidContext()) }
    // Úsporný režim a teplota: Gemma pod nimi sama od sebe nic nepočítá (`proactiveVariant`).
    single<DeviceLoad> { AndroidDeviceLoad(androidContext()) }
    // Přenos hovorů (ntgcalls) — jen Android, viz docs/poznatky/hovory.md.
    // Zvuk hovoru vede systémový Telecom (most níž), náhradně Plume sám.
    single<CallMedia> { NtgCallMedia(androidContext(), routes = { get<TelecomCallBridge>().routes() }) }
    // Živé příběhy: skupinový hovor jen na příjem, vlastní instance knihovny.
    single<LiveStreamMedia> { NtgLiveStreamMedia(androidContext(), scope = get(named(APP_SCOPE))) }
    // Deník hovorů pro diagnostiku (Nastavení → Pomoc, `docs/poznatky/hovory.md`, „Deník hovorů").
    single { CallJournal(androidContext().filesDir.resolve(CALL_JOURNAL_DIRECTORY).absolutePath, scope = get(named(APP_SCOPE))) }
    // Hovory přihlášené do systémového telefonu: souběh s GSM, auto, hodinky, výstup zvuku.
    single(createdAtStart = true) {
        TelecomCallBridge(context = androidContext(), calls = get(), chats = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    single<AppLockStore> {
        object : AppLockStore, SecretStore by KeystoreSecretStore(
            androidContext(), fileName = "app-lock", keyAlias = "plume_app_lock", logTag = "AppLock",
        ) {}
    }
    single<MiniAppSecretStore> {
        object : MiniAppSecretStore, SecretStore by KeystoreSecretStore(
            androidContext(), fileName = "mini-app-secure-storage", keyAlias = "plume_mini_app_secure", logTag = "WebApp",
        ) {}
    }
    single { NotificationPermission(androidContext()) }
    single { LaunchAtLogin() }
    single { UrlOpener(androidContext(), settings = get()) }
    single { AppLanguage(androidContext()) }
    single { FileOpener(androidContext()) }
    single { WebFileDownloader(androidContext()) }
    single { MediaSaver(androidContext()) }
    single<TextRecognizer> { MlKitTextRecognizer() }
    // Tvorba samolepky z fotky (odstranění pozadí přes ML Kit z Play služeb).
    single<StickerMaker> { AndroidStickerMaker(androidContext()) }
    single<OnDeviceTranslator> { MlKitTranslator(androidContext()) }
    single<SpeechToText> { SystemSpeechToText(androidContext()) }
    single { MediaSharer(androidContext()) }
    single {
        ShareIntake(androidContext(), requests = get(), chatOpenRequests = get(), scope = get(named(APP_SCOPE)))
    }
    single<ShareTargetPublisher> { AndroidShareTargetPublisher(androidContext()) }
    // Manifest sdílení na čipu vedle kopií souborů, které `ShareIntake` dělá do `cache/outgoing`.
    single<ShareStore> {
        FileShareStore(directory = File(androidContext().cacheDir, OUTGOING_DIRECTORY).absolutePath.toPath())
    }
    single { AppUpdates(androidContext()) }
    single { TextClipboard(androidContext()) }
    single { CalendarEvents(androidContext()) }
    single { ContactSaver(androidContext()) }
    single<PhoneContacts> { AndroidPhoneContacts(androidContext()) }
    // Eagerně: synchronizace se má rozběhnout po přihlášení a návratu do popředí sama,
    // ne až s první obrazovkou, která si ji vyžádá.
    single(createdAtStart = true) {
        ContactSyncer(
            settings = get(),
            contacts = get(),
            sync = get(),
            session = get(),
            foreground = get(),
            scope = get(named(APP_SCOPE)),
        )
    }
    single { WifiJoiner(androidContext()) }
    single { HomeRegion(androidContext()) }
    single<LanguageDetector> { AndroidLanguageDetector(androidContext()) }
    single<SquarePhoto> { AndroidSquarePhoto(androidContext()) }
    single<StoryPhoto> { AndroidStoryPhoto(androidContext()) }
    single<HomeWidgets> { AndroidHomeWidgets(androidContext()) }
    single<SnoozeReminders> { AndroidSnoozeReminders(androidContext()) }
    // Eagerně: sleduje volbu a podle ní úlohu zařadí nebo zruší, i když se Nastavení neotevře.
    single(createdAtStart = true) {
        PhotoDescriptionSchedule(androidContext(), settings = get(), scope = get(named(APP_SCOPE)))
    }
    single<PhotoMarkupRenderer> { AndroidPhotoMarkupRenderer(androidContext()) }
    single<StoryVideo> { AndroidStoryVideo(androidContext(), get()) }
    single<ProfileVideo> { AndroidProfileVideo(androidContext()) }
    // Nový na každou konverzaci — nahrávání patří obrazovce, ne aplikaci.
    factory { AudioRecorder(androidContext()) }
    // Nový na každou přehrávanou hlasovku; Context je kvůli audio focus.
    factory { AudioEngine(androidContext()) }
    // Senzory u ucha: jeden na aplikaci, protože hraje nejvýš jedna hlasovka.
    single { EarSensor(androidContext()) }
    // Hudba na pozadí: spustí službu s MediaSession, když začne hrát skladba.
    single(createdAtStart = true) {
        MusicControls(androidContext(), queue = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    // Sdílení živé polohy: spustí službu, když je co posouvat a aplikace je v popředí.
    single(createdAtStart = true) {
        LiveLocationControls(androidContext(), repository = get(), foreground = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }

    // `filesDir`, ne cache: nastavení se nesmí ztratit, když systém uklízí místo.
    single { createSettingsDataStore(androidContext().filesDir.resolve(SETTINGS_FILE_NAME).absolutePath) }
    single(named(MINI_APP_STORAGE)) { createSettingsDataStore(androidContext().filesDir.resolve(MINI_APP_STORAGE_FILE_NAME).absolutePath) }

    // Widget s chaty na ploše: podklady se musí psát od startu procesu, ne až se složí UI —
    // aplikaci často budí jen push a widget má i pak ukázat čerstvý stav.
    single(createdAtStart = true) {
        ChatsWidgetUpdater(
            context = androidContext(),
            chats = get(),
            files = get(),
            authStage = get<TelegramSession>().authStage,
            lockSettings = get<AppLock>().settings,
            scope = get(named(APP_SCOPE)),
        ).also { it.start() }
    }

    single {
        NotificationPresenter(
            context = androidContext(),
            // Vlastní scope: notifikace musí sbírat po celou dobu běhu procesu, ne po dobu
            // života obrazovky.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            settings = get(),
            snoozes = get(),
            callsSupported = get<CallRepository>().supported,
            journal = getOrNull<CallJournal>(),
        )
    }
    // Služba v popředí po dobu hovoru — spouští se z popředí, jakmile hovor vytáčí nebo běží.
    single(createdAtStart = true) {
        CallServiceControls(context = androidContext(), calls = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    // Služba v popředí po dobu videochatu — i se zavřenou obrazovkou a aplikací na pozadí.
    single(createdAtStart = true) {
        VideoChatServiceControls(context = androidContext(), session = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    // Ukazovátko protistrany přes jiné aplikace, když sdílíme obrazovku.
    single<CallPointerDisplay> { AndroidCallPointerDisplay(androidContext()) }
    // Vyzvánění příchozího hovoru a tón protistrany u odchozího.
    single(createdAtStart = true) {
        CallRinger(context = androidContext(), calls = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
}
