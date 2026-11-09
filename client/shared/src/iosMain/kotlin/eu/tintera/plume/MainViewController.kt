package eu.tintera.plume

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import eu.tintera.plume.core.notifications.IosNotificationCenter
import eu.tintera.plume.core.notifications.IosPush
import eu.tintera.plume.core.logging.CrashlyticsBridge
import eu.tintera.plume.core.logging.IosCrashReporter
import eu.tintera.plume.core.logging.IosCrashReporting
import eu.tintera.plume.core.navigation.IosLinks
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.di.startKoin

/**
 * Nová kontextová nabídka textu je na iOS ve výchozím stavu VYPNUTÁ (na Androidu zapnutá). Bez
 * ní pole nebere vlastní položky (`appendTextContextMenuComponents`) a „Vložit GIF / obrázek" by
 * se neukázalo — viz `KeyboardContentReceiver`. Nabídku kreslí dál systém (`UIEditMenuInteraction`),
 * vlastní položky se jen přidají jako její akce. Příznak se čte při skládání pole, proto dřív.
 *
 * Compose controller je obalený [StatusBarHostViewController], který řídí ikony stavové lišty.
 */
@OptIn(ExperimentalFoundationApi::class)
fun MainViewController(): UIViewController {
    ComposeFoundationFlags.isNewContextMenuEnabled = true
    val host = StatusBarHostViewController(ComposeUIViewController { App() })
    StatusBarHost.controller = host
    return host
}

/**
 * Volá se z iOSApp.swift při startu — Koin musí běžet dřív, než se složí první obrazovka.
 * Delegát notifikací taky: odpověď na akci z notifikace dostane jen ten, kdo existuje dřív, než
 * aplikace doběhne start.
 *
 * @param crashlytics most do Firebase Crashlytics ze Swiftu; `null`, když build nemá
 *   `GoogleService-Info.plist` (aplikace pak běží bez hlášení pádů). Připojuje se PŘED Koinem:
 *   eager singletony vznikají už v `startKoin` a jejich logy jsou u pádů při startu to
 *   nejzajímavější. Viz docs/poznatky/release.md, „Hlášení pádů na iOS".
 */
fun initKoin(crashlytics: CrashlyticsBridge?) {
    crashlytics?.let { bridge ->
        val reporter = IosCrashReporter(bridge)
        reporter.installUnhandledExceptionHook()
        IosCrashReporting.reporter = reporter
    }
    // Kontejner primárního účtu: co v něm není, Koin hledá v globální části.
    val koin = startKoin { }.koin.primaryAccount()
    IosNotificationCenter.install(koin)
    IosPush.start(koin)
    IosLinks.start(koin)
}
