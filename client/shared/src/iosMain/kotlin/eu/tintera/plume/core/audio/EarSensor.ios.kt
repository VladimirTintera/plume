package eu.tintera.plume.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceProximityStateDidChangeNotification
import platform.darwin.NSObjectProtocol

/**
 * iOS: `UIDevice` pro přiblížení, `CMMotionManager` pro sklon.
 *
 * **Zhasínání displeje dělá systém sám**, jakmile je `isProximityMonitoringEnabled` zapnuté —
 * na rozdíl od Androidu se o zámek starat nemusíme.
 *
 * Sledování se zapíná jen po dobu hlasovky: `isProximityMonitoringEnabled` nechané zapnuté by
 * zhasínalo displej i mimo poslech, kdykoli si uživatel dá telefon k obličeji.
 *
 * **iPad senzor přiblížení nemá.** `isProximityMonitoringEnabled = true` tam zůstane `false`;
 * podle toho se pozná, že se sledovat nedá, a hlasovka hraje z reproduktoru jako dřív.
 */
@OptIn(ExperimentalForeignApi::class)
actual class EarSensor {

    private val motion = CMMotionManager()

    /** Notifikační centrum si pozorovatele drží jen slabě — bez reference by ho nikdo nevolal. */
    private var observer: NSObjectProtocol? = null

    private var latest = EarSensors()

    actual fun start(onChange: (EarSensors) -> Unit) {
        if (observer != null) return

        val device = UIDevice.currentDevice
        device.proximityMonitoringEnabled = true
        // iPad a starší zařízení senzor nemají; zápis se v tu chvíli neprojeví.
        if (!device.proximityMonitoringEnabled) return

        latest = EarSensors()

        fun publish(updated: EarSensors) {
            if (updated == latest) return
            latest = updated
            onChange(updated)
        }

        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceProximityStateDidChangeNotification,
            `object` = device,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            publish(latest.copy(near = UIDevice.currentDevice.proximityState))
        }

        // `gravity` je tíže oddělená od pohybu ruky, takže mávnutí telefonem sklon nerozhodí.
        if (motion.deviceMotionAvailable) {
            motion.deviceMotionUpdateInterval = MOTION_INTERVAL_SECONDS
            motion.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { deviceMotion, _ ->
                val z = deviceMotion?.gravity?.useContents { z } ?: return@startDeviceMotionUpdatesToQueue
                publish(latest.copy(gravityZ = z.toFloat()))
            }
        }

        publish(latest.copy(near = device.proximityState))
    }

    actual fun stop() {
        observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observer = null
        if (motion.deviceMotionActive) motion.stopDeviceMotionUpdates()
        UIDevice.currentDevice.proximityMonitoringEnabled = false
        latest = EarSensors()
    }
}

/** Sklon stačí číst pětkrát za vteřinu — telefon se k uchu nedostane rychleji. */
private const val MOTION_INTERVAL_SECONDS = 0.2
