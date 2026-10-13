package eu.tintera.plume.core.platform

/** Mobil aplikaci budí push, spouštět ji po přihlášení není potřeba. */
actual class LaunchAtLogin {
    actual val supported: Boolean = false

    actual fun isEnabled(): Boolean = false

    actual fun setEnabled(enabled: Boolean): Boolean = false
}
