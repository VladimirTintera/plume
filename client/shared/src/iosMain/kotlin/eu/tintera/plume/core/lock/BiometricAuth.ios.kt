package eu.tintera.plume.core.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricAuth(): BiometricAuth = remember { IosBiometricAuth() }

// `canEvaluatePolicy(error = null)` je cinterop s ukazatelem na NSError; bez opt-inu se iOS nepřeloží.
@OptIn(ExperimentalForeignApi::class)
private class IosBiometricAuth : BiometricAuth {

    override val isSupported: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)

    override suspend fun authenticate(title: String, subtitle: String?): Boolean {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) {
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = title,
            ) { success, _ ->
                if (continuation.isActive) {
                    continuation.resume(success)
                }
            }
        }
    }
}
