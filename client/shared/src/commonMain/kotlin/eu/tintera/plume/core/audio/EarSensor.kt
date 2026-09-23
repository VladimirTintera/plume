package eu.tintera.plume.core.audio

/**
 * Senzory, podle kterých se pozná telefon u ucha — platformní část.
 *
 * Sleduje se jen po dobu, kdy hlasovka hraje ([VoicePlayer]). Trvale běžící senzor přiblížení
 * a zámek zhasínání displeje by braly baterii za funkci, která se používá vteřiny denně.
 *
 * **Zhasnutí displeje dělá platforma, ne aplikace.** Android na to má
 * `PROXIMITY_SCREEN_OFF_WAKE_LOCK`, iOS `isProximityMonitoringEnabled`; obojí se zapíná spolu se
 * sledováním a vypíná s ním. Ručně zhasínat displej podle našeho pravidla by znamenalo tlouct se
 * se systémem o to, kdy se má rozsvítit zpátky.
 *
 * Kde senzor přiblížení není (tablet, emulátor), [start] nic neohlásí a hlasovka hraje
 * z reproduktoru jako dřív.
 */
expect class EarSensor {
    /** Začne sledovat; [onChange] chodí při každé změně a poprvé s tím, co senzory hlásí hned. */
    fun start(onChange: (EarSensors) -> Unit)

    /** Přestane sledovat a pustí zámek zhasínání displeje. Volat se smí i bez [start]. */
    fun stop()
}
