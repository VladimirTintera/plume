package eu.tintera.plume.core.logging

/**
 * Desktop nemá Crashlytics: log a stack trace zachycených chyb jdou do souboru v datech aplikace
 * ([DesktopLogs]). Kermit píše dál i na standardní výstup.
 */
internal actual fun createCrashReporter(): CrashReporter = DesktopLogs.reporter
