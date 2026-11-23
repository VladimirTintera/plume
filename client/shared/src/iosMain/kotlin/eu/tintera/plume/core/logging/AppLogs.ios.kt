package eu.tintera.plume.core.logging

/** Pády jdou do Crashlytics, vlastní soubory se záznamy tu nejsou. */
actual val appLogsAvailable: Boolean = false

actual fun revealAppLogs(): Boolean = false
