package eu.tintera.plume.core.lock

import androidx.compose.runtime.Composable

/**
 * Biometrické ověření (otisk prstu, rozpoznání obličeje).
 *
 * Slouží k odemčení aplikace bez nutnosti vyťukávat PIN. Vždy je volitelné
 * a doplňuje PIN, který zůstává hlavní záchrannou sítí.
 */
interface BiometricAuth {
    /** Zda zařízení má biometrický hardware a uživatel má zaregistrovaný alespoň jeden otisk / obličej. */
    val isSupported: Boolean

    /**
     * Zobrazí systémový dialog pro ověření biometrie.
     * Vrátí `true` při úspěšném ověření, `false` při zrušení nebo selhání.
     */
    suspend fun authenticate(title: String, subtitle: String? = null): Boolean
}

@Composable
expect fun rememberBiometricAuth(): BiometricAuth
