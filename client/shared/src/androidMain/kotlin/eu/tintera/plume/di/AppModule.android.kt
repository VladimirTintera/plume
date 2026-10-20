package eu.tintera.plume.di

import eu.tintera.plume.core.calls.AndroidCallPointerDisplay
import eu.tintera.plume.core.calls.CallPointerDisplay
import eu.tintera.plume.core.calls.CallRinger
import eu.tintera.plume.core.calls.CallServiceControls
import eu.tintera.plume.core.calls.TelecomCallBridge
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.NtgCallMedia
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.speech.SystemSpeechToText
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.translation.MlKitTranslator
import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.ocr.MlKitTextRecognizer
import eu.tintera.plume.core.widget.ChatsWidgetUpdater
import eu.tintera.plume.core.widget.AndroidHomeWidgets
import eu.tintera.plume.core.widget.HomeWidgets
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.snooze.AndroidSnoozeReminders
import eu.tintera.plume.core.snooze.SnoozeReminders
import eu.tintera.plume.core.ai.PhotoDescriptionSchedule
import eu.tintera.plume.core.ai.LiteRtOnDeviceAi
import eu.tintera.plume.core.ai.MlKitOnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.RoutingOnDeviceAi
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
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.share.AndroidShareTargetPublisher
import eu.tintera.plume.core.share.ShareIntake
import eu.tintera.plume.core.share.ShareTargetPublisher
import eu.tintera.plume.core.share.ShareStore
import eu.tintera.plume.core.share.FileShareStore
import eu.tintera.plume.core.ui.OUTGOING_DIRECTORY
import okio.Path.Companion.toPath
import java.io.File
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.secrets.KeystoreSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.calls.CALL_JOURNAL_DIRECTORY
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
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
    // Model v zařízení: Gemini Nano v AICore, nebo Gemma přes LiteRT-LM, podle volby uživatele
    // (RoutingOnDeviceAi). Obojí jednou na proces — klienti ML Kitu i engine LiteRT-LM jsou drahé.
    single<OnDeviceAi> { RoutingOnDeviceAi(geminiNano = MlKitOnDeviceAi(), gemma = get<LiteRtOnDeviceAi>()) }
    // Zvlášť, protože ji potřebuje i úloha stahování (ModelDownloadWorker) — stav stahování,
    // který ukazuje Nastavení, musí být TÝŽ, do kterého úloha zapisuje.
    single { LiteRtOnDeviceAi(androidContext(), get()) }
    single { TelegramPaths(androidContext()) }
    // Přenos hovorů (ntgcalls) — jen Android, viz docs/poznatky/hovory.md.
    // Zvuk hovoru vede systémový Telecom (most níž), náhradně Plume sám.
    single<CallMedia> { NtgCallMedia(androidContext(), routes = { get<TelecomCallBridge>().routes() }) }
    // Deník hovorů pro diagnostiku (Nastavení → Pomoc, `docs/poznatky/hovory.md`, „Deník hovorů").
    single { CallJournal(androidContext().filesDir.resolve(CALL_JOURNAL_DIRECTORY).absolutePath, scope = get(named(APP_SCOPE))) }
    // Hovory přihlášené do systémového telefonu: souběh s GSM, auto, hodinky, výstup zvuku.
    single(createdAtStart = true) {
        TelecomCallBridge(context = androidContext(), calls = get(), chats = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by KeystoreSecretStore(
            androidContext(), fileName = "tdlib-key", keyAlias = "plume_tdlib_database", logTag = "DatabaseKey",
        ) {}
    }
    single<AppLockStore> {
        object : AppLockStore, SecretStore by KeystoreSecretStore(
            androidContext(), fileName = "app-lock", keyAlias = "plume_app_lock", logTag = "AppLock",
        ) {}
    }
    single { NotificationPermission(androidContext()) }
    single { LaunchAtLogin() }
    single { UrlOpener(androidContext(), settings = get()) }
    single { AppLanguage(androidContext()) }
    single { FileOpener(androidContext()) }
    single { MediaSaver(androidContext()) }
    single<TextRecognizer> { MlKitTextRecognizer() }
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
    single { NetworkMonitor(androidContext()) }
    single { AppForeground(androidContext()) }
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
    // Ukazovátko protistrany přes jiné aplikace, když sdílíme obrazovku.
    single<CallPointerDisplay> { AndroidCallPointerDisplay(androidContext()) }
    // Vyzvánění příchozího hovoru a tón protistrany u odchozího.
    single(createdAtStart = true) {
        CallRinger(context = androidContext(), calls = get(), scope = get(named(APP_SCOPE))).also { it.start() }
    }
}
