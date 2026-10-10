package eu.tintera.plume.features.lock

import androidx.compose.ui.text.style.TextAlign
import eu.tintera.plume.resources.app_lock_biometrics_revoked
import eu.tintera.plume.core.lock.BiometricResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.lock.APP_LOCK_PIN_LENGTH
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.lock.AppLockState
import eu.tintera.plume.core.lock.PinResult
import eu.tintera.plume.core.lock.SystemLockClock
import eu.tintera.plume.core.lock.rememberBiometricAuth
import eu.tintera.plume.core.ui.PinDots
import eu.tintera.plume.core.ui.PinKeypad
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.app_lock_biometrics_prompt
import eu.tintera.plume.resources.app_lock_blocked
import eu.tintera.plume.resources.app_lock_enter_pin
import eu.tintera.plume.resources.app_lock_wrong_pin
import eu.tintera.plume.resources.lock_24dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Celoobrazovkový zámek aplikace zobrazený při [AppLockState.Locked].
 *
 * Skládá se do vlastního okna ([LockWindow]), které obsluhuje i Zpět.
 */
@Composable
fun LockScreen(
    appLock: AppLock,
    modifier: Modifier = Modifier,
) {
    val state by appLock.state.collectAsStateWithLifecycle()
    val lockedState = state as? AppLockState.Locked ?: return

    val biometricAuth = rememberBiometricAuth()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var enteredPin by rememberSaveable { mutableStateOf("") }
    var errorTrigger by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    // Otisky v telefonu se změnily a biometrie se vypnula: zámek musí říct proč, jinak by
    // ikona otisku prostě zmizela a vypadalo by to jako chyba.
    var biometricsRevoked by rememberSaveable { mutableStateOf(false) }
    val tryBiometrics: suspend () -> Unit = {
        when (biometricAuth.authenticate(title = readString(Res.string.app_lock_biometrics_prompt))) {
            BiometricResult.Success -> {
                haptics.confirm()
                appLock.unlockWithBiometrics()
            }
            BiometricResult.Invalidated -> {
                haptics.reject()
                biometricsRevoked = true
                appLock.revokeBiometrics()
            }
            BiometricResult.Failed -> Unit
        }
    }

    // Automatické spuštění biometrie jednou po zobrazení
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lockedState.biometrics, biometricAuth.isSupported) {
        if (!autoPrompted && lockedState.biometrics && biometricAuth.isSupported) {
            autoPrompted = true
            tryBiometrics()
        }
    }

    // Odpočet brzdy pokusů
    var now by remember { mutableLongStateOf(SystemLockClock.elapsedMillis()) }
    val isBlocked = lockedState.blockedUntil > now
    val remainingSeconds = if (isBlocked) {
        ((lockedState.blockedUntil - now + 999) / 1000).coerceAtLeast(1)
    } else 0

    LaunchedEffect(lockedState.blockedUntil) {
        while (lockedState.blockedUntil > SystemLockClock.elapsedMillis()) {
            now = SystemLockClock.elapsedMillis()
            delay(500L)
        }
        now = SystemLockClock.elapsedMillis()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Hlavička s ikonou a nadpisem
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.lock_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(Res.string.app_lock_enter_pin),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(12.dp))

                val subtitle = when {
                    isBlocked -> stringResource(Res.string.app_lock_blocked, remainingSeconds)
                    errorMessage != null -> errorMessage
                    else -> null
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (biometricsRevoked) {
                    // Není to chyba uživatele, takže ne v chybové barvě.
                    Text(
                        text = stringResource(Res.string.app_lock_biometrics_revoked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }

                Spacer(Modifier.height(24.dp))

                PinDots(
                    filledCount = enteredPin.length,
                    errorTrigger = errorTrigger,
                    isError = isBlocked || errorMessage != null,
                )
            }

            // Klávesnice
            PinKeypad(
                onDigit = { digit ->
                    if (isBlocked || isChecking || enteredPin.length >= APP_LOCK_PIN_LENGTH) return@PinKeypad
                    errorMessage = null
                    val next = enteredPin + digit
                    enteredPin = next

                    if (next.length == APP_LOCK_PIN_LENGTH) {
                        isChecking = true
                        scope.launch {
                            when (val result = appLock.unlock(next)) {
                                PinResult.Success -> {
                                    haptics.confirm()
                                }
                                is PinResult.Wrong -> {
                                    haptics.reject()
                                    errorTrigger++
                                    errorMessage = readString(Res.string.app_lock_wrong_pin)
                                    enteredPin = ""
                                    isChecking = false
                                }
                                is PinResult.Blocked -> {
                                    haptics.reject()
                                    errorTrigger++
                                    enteredPin = ""
                                    isChecking = false
                                }
                            }
                        }
                    }
                },
                onDelete = {
                    if (isBlocked || isChecking) return@PinKeypad
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        errorMessage = null
                    }
                },
                onBiometrics = { scope.launch { tryBiometrics() } },
                isBiometricsEnabled = lockedState.biometrics && biometricAuth.isSupported,
                isEnabled = !isBlocked && !isChecking,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}
