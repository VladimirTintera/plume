package eu.tintera.plume.core.connectivity

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Popředí podle oznámení `UIApplication`. */
@OptIn(ExperimentalForeignApi::class)
actual class AppForeground {

    actual val isForeground: Flow<Boolean> = callbackFlow {
        val center = NSNotificationCenter.defaultCenter

        // Pozorovatele je potřeba si podržet: `removeObserver` chce tentýž objekt, který vrátila
        // registrace.
        val observers = listOf(
            UIApplicationDidBecomeActiveNotification to true,
            UIApplicationDidEnterBackgroundNotification to false,
        ).map { (name, foreground) ->
            center.addObserverForName(
                name = name,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
                usingBlock = { trySend(foreground) },
            )
        }

        /*
         * Stav při odběru: aplikace mohla nastartovat na pozadí (push) a stejně tak mohla být
         * na obrazovce dávno předtím, než se sem někdo přihlásil — v tom případě už oznámení
         * o aktivaci proběhlo a nic dalšího by nepřišlo.
         *
         * `UIApplication` se smí číst jen z hlavního vlákna; odběr běží na Dispatchers.Default.
         */
        dispatch_async(dispatch_get_main_queue()) {
            trySend(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
        }

        awaitClose { observers.forEach { center.removeObserver(it) } }
    }
        .distinctUntilChanged()
}
