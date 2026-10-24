package eu.tintera.plume.core.logging

/** Hlásič pádů desktop zatím nemá; Kermit píše dál na standardní výstup. */
internal actual fun createCrashReporter(): CrashReporter = NoopCrashReporter
