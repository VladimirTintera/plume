package eu.tintera.plume.core.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricAuth(): BiometricAuth = remember { IosBiometricAuth() }

/**
 * **Svázání se seznamem otisků je `evaluatedPolicyDomainState`**: neprůhledná data, která se
 * změní, kdykoli v telefonu přibude nebo ubude otisk či tvář. Při zapnutí se uloží, před
 * každým ověřením se porovnají. Leží v `NSUserDefaults` — kdo zná PIN telefonu, je bez
 * jailbreaku nepřepíše, a proti jailbreaku zámek nechrání tak jako tak (viz `docs/poznatky/zamek-aplikace.md`).
 */
// `canEvaluatePolicy(error = null)` je cinterop s ukazatelem na NSError; bez opt-inu se iOS nepřeloží.
@OptIn(ExperimentalForeignApi::class)
private class IosBiometricAuth : BiometricAuth {

    override val isSupported: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)

    override suspend fun enroll(): Boolean {
        val state = currentDomainState() ?: return false
        NSUserDefaults.standardUserDefaults.setObject(state, forKey = DOMAIN_STATE_KEY)
        return true
    }

    override suspend fun authenticate(title: String, subtitle: String?): BiometricResult {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) {
            return BiometricResult.Failed
        }
        // Stav je známý až po `canEvaluatePolicy` téhož kontextu.
        val enrolled = NSUserDefaults.standardUserDefaults.dataForKey(DOMAIN_STATE_KEY)
        val current = context.evaluatedPolicyDomainState
        if (enrolled == null || current == null || enrolled != current) {
            return BiometricResult.Invalidated
        }

        return suspendCancellableCoroutine { continuation ->
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = title,
            ) { success, _ ->
                if (continuation.isActive) {
                    continuation.resume(if (success) BiometricResult.Success else BiometricResult.Failed)
                }
            }
        }
    }

    private fun currentDomainState(): NSData? {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) return null
        return context.evaluatedPolicyDomainState
    }

    private companion object {
        const val DOMAIN_STATE_KEY = "app_lock_biometrics_domain_state"
    }
}
