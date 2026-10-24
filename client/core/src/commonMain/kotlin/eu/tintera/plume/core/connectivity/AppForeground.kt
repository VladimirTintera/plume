package eu.tintera.plume.core.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Je aplikace na obrazovce?
 *
 * Dvě různé věci na tom visí:
 *
 * 1. **Návrat do popředí je okamžik, kdy se musí ohlásit síť** (viz [NetworkMonitor]). Systém
 *    během spánku spojení zabil a TDLib o tom neví; tohle je jediná chvíle, kdy se to dá poznat
 *    bez čekání na jeho vlastní timeout.
 * 2. **Volba `online`.** Dokud se nenastaví, server nás nikdy nevidí jako online — ostatním se
 *    nezobrazuje zelená tečka a updaty chodí líněji.
 *
 * Schválně **není** vázané na kompozici: `LocalLifecycleOwner` by fungoval taky, ale žil by jen
 * po dobu, kdy se skládá UI. Tohle běží od startu procesu, stejně jako `NotificationPresenter`.
 */
expect class AppForeground {

    /** Při odběru se ozve hned aktuálním stavem, pak na každou změnu. */
    val isForeground: Flow<Boolean>
}
