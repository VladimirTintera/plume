package eu.tintera.plume.core.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import co.touchlab.kermit.Logger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * **Svázání se seznamem otisků je klíč v Keystore** ([KEY_ALIAS]): použít ho jde jen po silné
 * biometrii a systém ho natrvalo zneplatní, jakmile v telefonu přibude otisk nebo tvář
 * (`setInvalidatedByBiometricEnrollment`). Prompt dostane šifru s tím klíčem (`CryptoObject`)
 * a úspěch se počítá, až šifra po ověření opravdu zašifruje — samotné „ověřeno" z promptu by
 * šlo dostat i novým otiskem. Zneplatněný klíč se pozná už při přípravě šifry
 * (`KeyPermanentlyInvalidatedException`), tedy dřív, než se prompt ukáže.
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

    override suspend fun enroll(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val keyStore = keyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    // Každé použití klíče chce nové ověření, a jen silnou biometrií — ne PINem telefonu.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    }
                }
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
            true
        }.onFailure { Logger.withTag("AppLock").w(it) { "Klíč pro biometrii nejde založit." } }
            .getOrDefault(false)
    }

    override suspend fun authenticate(title: String, subtitle: String?): BiometricResult {
        if (!isSupported) return BiometricResult.Failed
        val activity = context.findActivity() ?: return BiometricResult.Failed
        val cipher = when (val prepared = withContext(Dispatchers.IO) { prepareCipher() }) {
            is Prepared.Ready -> prepared.cipher
            Prepared.Invalidated -> return BiometricResult.Invalidated
            Prepared.Unavailable -> return BiometricResult.Failed
        }

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
                    if (continuation.isActive) continuation.resume(BiometricResult.Failed)
                }
                .build()

            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                cancellationSignal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        // Ověřený je klíč, ne jen prompt: šifra bez platného ověření vyhodí výjimku.
                        val unlocked = runCatching {
                            result?.cryptoObject?.cipher?.doFinal(PROBE)
                        }.getOrNull() != null
                        if (continuation.isActive) {
                            continuation.resume(if (unlocked) BiometricResult.Success else BiometricResult.Failed)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (continuation.isActive) continuation.resume(BiometricResult.Failed)
                    }
                },
            )
        }
    }

    /**
     * Šifra s klíčem biometrie. Chybějící klíč (biometrie zapnutá ve verzi před svázáním, nebo
     * smazaný se zálohou dat) je totéž co zneplatněný: o seznamu otisků nic nevíme.
     */
    private fun prepareCipher(): Prepared = try {
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        if (key == null) {
            Prepared.Invalidated
        } else {
            Prepared.Ready(Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) })
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        Prepared.Invalidated
    } catch (e: Exception) {
        Logger.withTag("AppLock").w(e) { "Šifru pro biometrii nejde připravit." }
        Prepared.Unavailable
    }

    private sealed interface Prepared {
        class Ready(val cipher: Cipher) : Prepared
        data object Invalidated : Prepared
        data object Unavailable : Prepared
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "plume_app_lock_biometrics"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val PROBE = byteArrayOf(1)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
