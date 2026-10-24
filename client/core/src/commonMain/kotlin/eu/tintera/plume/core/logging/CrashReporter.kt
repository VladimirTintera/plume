package eu.tintera.plume.core.logging

/**
 * Hranice kolem hlásiče pádů — stejný princip jako u TDLib: `com.google.firebase.crashlytics`
 * smí být importovaný jen tady v `core/logging`, zbytek aplikace o něm neví.
 *
 * Aplikace do něj nehlásí přímo. Píše se do Kermitu (`Logger.withTag(…)`) a odtud to přebírá
 * [CrashlyticsLogWriter] — jedno místo, kde se loguje, ne dvě. Konkrétní implementaci vybírá
 * [createCrashReporter] podle platformy.
 */
interface CrashReporter {

    /**
     * Řádek kontextu k nejbližšímu hlášení pádu (v konzoli „logs"). Sám o sobě nikam neodejde —
     * odešle se až s pádem nebo zachycenou chybou, takže je vidět, co se dělo těsně předtím.
     */
    fun log(message: String)

    /**
     * Zachycená chyba, po které aplikace běží dál. V konzoli z ní vznikne samostatný problém
     * (non-fatal) se stack tracem místa, kde výjimka vznikla.
     */
    fun recordException(throwable: Throwable)
}

/**
 * Hlásič, který nic nehlásí: iOS (Firebase v Xcode projektu zatím není) a Android build bez
 * `google-services.json`. Není to chybový stav — aplikace bez Firebase normálně funguje,
 * stejně jako funguje bez push notifikací.
 */
object NoopCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun recordException(throwable: Throwable) = Unit
}

/** Hlásič pádů pro danou platformu. Volá se jednou při startu, viz [configureLogging]. */
internal expect fun createCrashReporter(): CrashReporter
