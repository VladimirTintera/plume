package eu.tintera.plume.di

import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.ai.NoopOnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAi
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
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.contacts.ContactSaver
import eu.tintera.plume.core.currency.HomeRegion
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.locale.AppLanguage
import eu.tintera.plume.core.notifications.DesktopNotifier
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.notifications.desktopNotifier
import eu.tintera.plume.core.ocr.RecognizedText
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopDebugBuild
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.secrets.desktopSecretStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.calls.CALL_JOURNAL_DIRECTORY
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.share.ShareTargetPublisher
import eu.tintera.plume.core.snooze.SnoozeReminders
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.DesktopSquarePhoto
import eu.tintera.plume.core.ui.MarkedPhoto
import eu.tintera.plume.core.ui.PhotoMarkup
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
    single<OnDeviceAi> { NoopOnDeviceAi }
    // Hovory jen se zvukem přes ntgcalls; bez přibalené knihovny (Intel Mac, build bez sítě)
    // se CallMedia neregistruje a hovory se nenabídnou (`CallRepository.supported`).
    if (DesktopCallMedia.available()) {
        single { DesktopCallMedia() }
        single<CallMedia> { get<DesktopCallMedia>() }
        // Deník hovorů pro diagnostiku (Nastavení → Pomoc); přenos tu čísla zatím nehlásí, jen události.
        single { CallJournal(File(desktopDataDirectory, CALL_JOURNAL_DIRECTORY).path, scope = get(named(APP_SCOPE))) }
        // Ukončení aplikace uprostřed hovoru: zavěsit a zastavit knihovnu dřív, než JVM skončí.
        single(createdAtStart = true) { DesktopCallShutdown(calls = get(), media = get()).also { it.install() } }
        // Vyzvánění příchozího hovoru a tón protistrany u odchozího.
        single(createdAtStart = true) { DesktopCallRinger(calls = get(), scope = get(named(APP_SCOPE))).also { it.start() } }
    }
    single { TelegramPaths() }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by desktopSecretStore(
            service = secretService("eu.tintera.plume.tdlib"), account = "database-key", logTag = "DatabaseKey",
        ) {}
    }
    single<AppLockStore> {
        object : AppLockStore, SecretStore by desktopSecretStore(
            service = secretService("eu.tintera.plume.applock"), account = "pin", logTag = "AppLock",
        ) {}
    }
    single<DesktopNotifier> { desktopNotifier() }
    single { NotificationPermission(notifier = get()) }
    single { LaunchAtLogin() }
    single {
        NotificationPresenter(
            chats = get(),
            settings = get(),
            chatOpenRequests = get(),
            notifier = get(),
            // Vlastní scope: oznámení se sbírají po celou dobu běhu procesu.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            snoozes = get(),
            actions = get(),
            callRequests = get(),
            callsSupported = get<CallRepository>().supported,
        )
    }
    single { UrlOpener(settings = get()) }
    single { AppLanguage() }
    single { FileOpener() }
    single { MediaSaver() }
    single<TextRecognizer> {
        object : TextRecognizer {
            override suspend fun recognize(path: String): RecognizedText? = null
        }
    }
    single { MediaSharer() }
    single<ShareTargetPublisher> {
        object : ShareTargetPublisher {
            override fun publish(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean) = Unit
        }
    }
    single { NetworkMonitor() }
    single { AppForeground() }
    single { AppUpdates() }
    single { TextClipboard() }
    single { CalendarEvents() }
    single { ContactSaver() }
    single { WifiJoiner() }
    single { HomeRegion() }
    single<LanguageDetector> { LanguageDetector { null } }
    single<SquarePhoto> { DesktopSquarePhoto() }
    single<SnoozeReminders> {
        object : SnoozeReminders {
            override fun schedule(chatId: Long, title: String, at: Instant) = Unit

            override fun cancel(chatId: Long) = Unit
        }
    }
    single<PhotoMarkupRenderer> {
        object : PhotoMarkupRenderer {
            override suspend fun render(path: String, markup: PhotoMarkup): MarkedPhoto =
                throw UnsupportedOperationException("Úprava fotky na desktopu zatím není.")
        }
    }
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
}

/**
 * Ladicí běh má vlastní adresář dat (`plume.dataDir`), a proto i vlastní položky v Klíčence.
 * Se společnou položkou by odhlášení v jednom smazalo klíč k databázi i druhému a ten by o ni
 * přišel.
 */
private fun secretService(name: String): String = if (desktopDebugBuild) "$name.debug" else name
