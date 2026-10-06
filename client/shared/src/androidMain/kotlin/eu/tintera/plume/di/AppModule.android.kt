package eu.tintera.plume.di

import eu.tintera.plume.core.ai.LiteRtOnDeviceAi
import eu.tintera.plume.core.ai.MlKitOnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.RoutingOnDeviceAi
import eu.tintera.plume.core.ui.AndroidProfileVideo
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.AndroidSquarePhoto
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
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.KeystoreDatabaseKeyStore
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.TextClipboard
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
    single<DatabaseKeyStore> { KeystoreDatabaseKeyStore(androidContext()) }
    single { NotificationPermission(androidContext()) }
    single { UrlOpener(androidContext()) }
    single { FileOpener(androidContext()) }
    single { MediaSaver(androidContext()) }
    single { MediaSharer(androidContext()) }
    single {
        ShareIntake(androidContext(), requests = get(), chatOpenRequests = get(), scope = get(named(APP_SCOPE)))
    }
    single<ShareTargetPublisher> { AndroidShareTargetPublisher(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single { AppForeground(androidContext()) }
    single { AppUpdates(androidContext()) }
    single { TextClipboard(androidContext()) }
    single<LanguageDetector> { AndroidLanguageDetector(androidContext()) }
    single<SquarePhoto> { AndroidSquarePhoto(androidContext()) }
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

    single {
        NotificationPresenter(
            context = androidContext(),
            // Vlastní scope: notifikace musí sbírat po celou dobu běhu procesu, ne po dobu
            // života obrazovky.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
