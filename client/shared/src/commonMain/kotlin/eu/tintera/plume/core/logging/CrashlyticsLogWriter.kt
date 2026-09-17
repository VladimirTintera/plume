package eu.tintera.plume.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Přeposílá logy z Kermitu do [CrashReporter]. Díky němu se do hlášení pádu dostane i to, co
 * se v aplikaci dělo předtím, aniž by se kvůli tomu kdekoli volal Crashlytics ručně.
 *
 * Hranice jsou dvě, ne jedna, a to schválně:
 *
 *  - **[breadcrumbFrom] = Info** — co se přiloží k pádu jako kontext. Ladicí výpisy (`d`, `v`)
 *    se zahazují: jejich cena je v živém logcatu, v hlášení pádu by jen zabíraly místo
 *    (Crashlytics si drží posledních 64 řádků).
 *  - **[nonFatalFrom] = Error** — z čeho v konzoli vznikne samostatný problém. `logger.w` je
 *    v projektu běžné hlášení OČEKÁVANÉHO selhání (nenačtená sada samolepek, neuložený koncept,
 *    odkaz, který nemá čím otevřít) a je jich přes dvacet; kdyby zakládaly problémy, seznam by
 *    byl k neužitku a skutečné chyby by v něm zanikly. `logger.e` je proti tomu vzácné.
 *
 * Výjimka se hlásí jen tehdy, když opravdu je — `recordException` staví problém kolem stack
 * trace a bez ní by v konzoli byl záznam bez místa vzniku. Chybová hláška bez výjimky tedy
 * zůstane jen kontextovým řádkem.
 */
internal class CrashlyticsLogWriter(
    private val reporter: CrashReporter,
    private val breadcrumbFrom: Severity = Severity.Info,
    private val nonFatalFrom: Severity = Severity.Error,
) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= breadcrumbFrom

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        reporter.log("${severity.name.first()}/$tag: $message")

        if (severity >= nonFatalFrom && throwable != null) reporter.recordException(throwable)
    }
}
