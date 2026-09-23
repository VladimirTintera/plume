package eu.tintera.plume.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger

/**
 * Android: senzor přiblížení + akcelerometr, a k tomu `PROXIMITY_SCREEN_OFF_WAKE_LOCK`.
 *
 * Zámek zhasíná displej, dokud je senzor zakrytý — přesně to, co se má stát, když si uživatel dá
 * telefon k uchu. Zhasíná ho systém podle senzoru samotného, ne podle našeho pravidla
 * ([raisedToEar]), takže telefonu položenému displejem dolů zhasne taky. To nevadí: leží displejem
 * dolů, takže zhasnutý displej nikomu nechybí — a zvuk se do sluchátka nepřepne, což je to, na čem
 * záleží.
 */
actual class EarSensor(context: Context) {

    private val sensors = context.getSystemService<SensorManager>()
    private val power = context.getSystemService<PowerManager>()
    private val logger = Logger.withTag("EarSensor")

    private val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /**
     * Zámek se vyrábí až při [start].
     *
     * `isWakeLockLevelSupported` je jediný způsob, jak se zeptat, jestli zámek na tomhle zařízení
     * existuje; `newWakeLock` s nepodporovanou úrovní vyhodí výjimku až při `acquire`.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    private var latest = EarSensors()
    private var listener: SensorEventListener? = null

    actual fun start(onChange: (EarSensors) -> Unit) {
        if (listener != null) return
        val manager = sensors ?: return
        // Bez senzoru přiblížení se nedá poznat nic; akcelerometr sám by hlásil u ucha i kapsu.
        val near = proximity ?: return

        latest = EarSensors()
        val callback = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val updated = when (event.sensor.type) {
                    // Senzor je většinou jen dvoustavový: 0 = zakryto, `maximumRange` = volno.
                    // Práh v polovině rozsahu proto zvládne obojí, i senzor hlásící centimetry.
                    Sensor.TYPE_PROXIMITY -> latest.copy(near = event.values[0] < near.maximumRange / 2)
                    Sensor.TYPE_ACCELEROMETER -> latest.copy(gravityZ = event.values[2] / EARTH_GRAVITY)
                    else -> latest
                }
                if (updated == latest) return
                latest = updated
                onChange(updated)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = callback

        manager.registerListener(callback, near, SensorManager.SENSOR_DELAY_NORMAL)
        accelerometer?.let { manager.registerListener(callback, it, SensorManager.SENSOR_DELAY_NORMAL) }
        acquireWakeLock()
    }

    actual fun stop() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
        latest = EarSensors()
        releaseWakeLock()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val manager = power ?: return
        if (!manager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        runCatching {
            // Bez časového limitu schválně: zámek drží jen po dobu hlasovky a pouští ho `stop()`,
            // který visí na zastavení přehrávače. Limit by displej rozsvítil uprostřed poslechu.
            manager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
                .also { wakeLock = it }
                .acquire()
        }.onFailure { failure -> logger.w(failure) { "Zámek zhasínání displeje nejde vzít." } }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        runCatching { if (lock.isHeld) lock.release() }
            .onFailure { failure -> logger.w(failure) { "Zámek zhasínání displeje nejde pustit." } }
    }
}

/** Akcelerometr hlásí m/s²; pravidlo počítá v násobcích g. */
private const val EARTH_GRAVITY = 9.81f

/** Jméno v `dumpsys power`, podle kterého se zámek pozná při ověřování. */
private const val WAKE_LOCK_TAG = "plume:voice-at-ear"
