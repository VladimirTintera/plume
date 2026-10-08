package eu.tintera.plume.core.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricAuth(): BiometricAuth {
    val context = LocalContext.current
    return remember(context) { AndroidBiometricAuth(context) }
}

/**
 * Jen SILNÁ biometrie (třída 3). Slabá (třída 2) je typicky odemykání obličejem z přední kamery,
 * které na některých telefonech oklame fotka — a zámek je právě proti někomu, kdo má telefon
 * v ruce a majitele zná. Systémový zámek obrazovky (`DEVICE_CREDENTIAL`) se nenabízí: zámek
 * aplikace má být DRUHÁ bariéra, ne kopie té první.
 *
 * Android 10 `canAuthenticate(Int)` ani `setAllowedAuthenticators` nemá; tam `BiometricPrompt`
 * podle CDD bere jen silnou biometrii sám. Pod Androidem 10 `BiometricManager` není vůbec.
 */
private class AndroidBiometricAuth(private val context: Context) : BiometricAuth {

    override val isSupported: Boolean
        get() = runCatching {
            val manager = context.getSystemService(BiometricManager::class.java)
            val result = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                    @Suppress("DEPRECATION") manager?.canAuthenticate()
                else -> null
            }
            result == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)

    override suspend fun authenticate(title: String, subtitle: String?): Boolean {
        if (!isSupported) return false
        val activity = context.findActivity() ?: return false

        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .apply { if (subtitle != null) setSubtitle(subtitle) }
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    }
                }
                .setNegativeButton(
                    activity.getString(android.R.string.cancel),
                    activity.mainExecutor,
                ) { _, _ ->
                    if (continuation.isActive) continuation.resume(false)
                }
                .build()

            prompt.authenticate(
                cancellationSignal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
