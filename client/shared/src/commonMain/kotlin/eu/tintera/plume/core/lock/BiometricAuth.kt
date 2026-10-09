package eu.tintera.plume.core.lock

import androidx.compose.runtime.Composable

/**
 * Biometrické ověření (otisk prstu, rozpoznání obličeje).
 *
 * Slouží k odemčení aplikace bez nutnosti vyťukávat PIN. Vždy je volitelné
 * a doplňuje PIN, který zůstává hlavní záchrannou sítí.
 *
 * **Biometrie je svázaná se seznamem otisků a tváří v telefonu** ([enroll]). Kdo zná PIN
 * telefonu, přidá si v systému vlastní otisk — a bez svázání by jím odemkl i Plume. Po změně
 * seznamu proto [authenticate] vrátí [BiometricResult.Invalidated] a biometrie se musí znovu
 * zapnout v Nastavení, tedy po odemčení PINem aplikace.
 */
interface BiometricAuth {
    /** Zda zařízení má biometrický hardware a uživatel má zaregistrovaný alespoň jeden otisk / obličej. */
    val isSupported: Boolean

    /**
     * Zapamatuje si současný seznam otisků a tváří; volá se při zapnutí biometrie v Nastavení.
     * Vrací `false`, když to platforma neumí (pak se biometrie nezapne).
     */
    suspend fun enroll(): Boolean

    /** Zobrazí systémový dialog pro ověření biometrie. */
    suspend fun authenticate(title: String, subtitle: String? = null): BiometricResult
}

enum class BiometricResult {
    Success,

    /** Zrušeno nebo neověřeno — zkusit znovu jde, PIN funguje dál. */
    Failed,

    /**
     * Seznam otisků nebo tváří se od zapnutí změnil (nebo svázání chybí, protože biometrie byla
     * zapnutá ve starší verzi). Biometrie se vypne ([AppLock.revokeBiometrics]).
     */
    Invalidated,
}

@Composable
expect fun rememberBiometricAuth(): BiometricAuth
