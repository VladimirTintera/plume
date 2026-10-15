package eu.tintera.plume

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import eu.tintera.plume.core.notifications.IosNotificationCenter
import eu.tintera.plume.core.notifications.IosPush
import eu.tintera.plume.core.navigation.IosLinks
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
 */
fun initKoin() {
    val koin = startKoin { }.koin
    IosNotificationCenter.install(koin)
    IosPush.start(koin)
    IosLinks.start(koin)
}
