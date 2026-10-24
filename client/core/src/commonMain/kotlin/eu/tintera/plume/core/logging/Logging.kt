package eu.tintera.plume.core.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter

/**
 * Nastaví, kam Kermit píše: do systémového logu (logcat / NSLog) a zároveň do hlásiče pádů.
 *
 * - **Systémový log zůstává i v release buildu.** Release APK se v tomhle projektu ověřuje na
 *   zařízení (přepodepsané debug klíčem, viz docs/poznatky/release.md) a `adb logcat` je při tom jediné, co
 *   je vidět hned. Do logů se nepíše obsah zpráv, jen průběh volání.
 * - **Sběr hlášení se nepřepíná tady, ale v manifestu** (`firebase_crashlytics_collection_enabled`
 *   podle build typu). Crashlytics ten příznak čte při inicializaci procesu, tedy dřív, než se
 *   k tomuhle volání vůbec dojde — a v debug buildu je pak tenhle writer prázdná trubka.
 *
 * Volá se jako PRVNÍ věc při startu, dřív než vznikne graf závislostí: eager singletony
 * (seznam chatů, `FileRepository`, nastavení, start TDLib) se vyrábějí ještě uvnitř `startKoin`
 * a právě jejich logy jsou u pádů při startu to nejzajímavější.
 */
fun configureLogging() {
    Logger.setLogWriters(platformLogWriter(), CrashlyticsLogWriter(createCrashReporter()))
}
