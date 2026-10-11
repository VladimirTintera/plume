package eu.tintera.plume.core.lock

import androidx.compose.runtime.Composable

/**
 * `nanoTime` ve spánku počítače na macOS stojí, takže prodleva zámku se o dobu spánku zkrátí
 * — zamkne se spíš později. Lepší monotónní hodiny JVM nemá; viz docs/poznatky/zamek-aplikace.md.
 */
internal actual fun bootElapsedMillis(): Long = System.nanoTime() / 1_000_000

private object NoBiometrics : BiometricAuth {
    override val isSupported: Boolean = false

    override suspend fun enroll(): Boolean = false

    override suspend fun authenticate(title: String, subtitle: String?): BiometricResult = BiometricResult.Failed
}

/** Touch ID / Windows Hello by chtěly nativní most; zámek na desktopu zatím jen s PINem. */
@Composable
actual fun rememberBiometricAuth(): BiometricAuth = NoBiometrics
