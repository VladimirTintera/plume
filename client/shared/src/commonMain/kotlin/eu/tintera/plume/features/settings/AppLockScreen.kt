package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.settings_app_lock_on_text
import eu.tintera.plume.resources.settings_app_lock_on_title
import eu.tintera.plume.resources.settings_app_lock_off_title
import androidx.compose.ui.text.style.TextAlign
import eu.tintera.plume.core.ui.GroupBreak
import eu.tintera.plume.resources.app_lock_biometrics_revoked
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import eu.tintera.plume.core.ui.ChoiceRow
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import eu.tintera.plume.core.ui.GroupedColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.lock.APP_LOCK_TIMEOUTS
import eu.tintera.plume.core.lock.SystemLockClock
import eu.tintera.plume.core.lock.rememberBiometricAuth
import eu.tintera.plume.core.ui.PinDots
import eu.tintera.plume.core.ui.PinKeypad
import eu.tintera.plume.core.ui.PinLayout
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.app_lock_biometrics_prompt
import eu.tintera.plume.resources.app_lock_blocked
import eu.tintera.plume.resources.app_lock_timeout_1h
import eu.tintera.plume.resources.app_lock_timeout_1m
import eu.tintera.plume.resources.app_lock_timeout_5h
import eu.tintera.plume.resources.app_lock_timeout_5m
import eu.tintera.plume.resources.app_lock_wrong_pin
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.fingerprint_24dp
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.settings_app_lock
import eu.tintera.plume.resources.settings_app_lock_biometrics
import eu.tintera.plume.resources.settings_app_lock_biometrics_summary
import eu.tintera.plume.resources.settings_app_lock_change_pin
import eu.tintera.plume.resources.settings_app_lock_description
import eu.tintera.plume.resources.settings_app_lock_disable
import eu.tintera.plume.resources.settings_app_lock_disable_confirm_text
import eu.tintera.plume.resources.settings_app_lock_disable_confirm_title
import eu.tintera.plume.resources.settings_app_lock_enable
import eu.tintera.plume.resources.settings_app_lock_new_pin
import eu.tintera.plume.resources.settings_app_lock_pins_mismatch
import eu.tintera.plume.resources.settings_app_lock_repeat_pin
import eu.tintera.plume.resources.settings_app_lock_timeout
import eu.tintera.plume.resources.settings_app_lock_verify_current
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.SwitchListItem

@Composable
fun AppLockScreen(
    viewModel: AppLockViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val biometricAuth = rememberBiometricAuth()
    val biometricsPromptTitle = stringResource(Res.string.app_lock_biometrics_prompt)

    val verifying = settings != null && !uiState.isVerified && uiState.flow is AppLockFlow.Idle
    val settingUp = uiState.flow is AppLockFlow.EnterNewPin || uiState.flow is AppLockFlow.ConfirmNewPin

    // Číselník má vyplnit výšku, správa a úvod scrollují jako každá jiná stránka Nastavení.
    SettingsPage(
        title = stringResource(Res.string.settings_app_lock),
        onBack = {
            if (uiState.flow !is AppLockFlow.Idle) {
                viewModel.cancelFlow()
            } else {
                onBack()
            }
        },
        modifier = modifier,
        scrollable = !verifying && !settingUp,
    ) {
        when {
            // 1. Zámek je zapnutý, ale ještě nebyl ověřen stávající PIN pro vstup do nastavení
            verifying -> VerifyCurrentPinContent(
                viewModel = viewModel,
                biometricSupported = settings?.biometrics == true && biometricAuth.isSupported,
                onBiometricAuth = {
                    viewModel.onBiometricsVerify(
                        biometricAuth = biometricAuth,
                        promptTitle = biometricsPromptTitle,
                    )
                },
                modifier = Modifier.weight(1f),
            )

            // 2. Průvodce zadáváním nového PINu (nastavení nebo změna)
            settingUp -> PinStep(
                title = stringResource(
                    if (uiState.flow is AppLockFlow.EnterNewPin) Res.string.settings_app_lock_new_pin else Res.string.settings_app_lock_repeat_pin,
                ),
                errorText = if (uiState.errorMessage == "mismatch") stringResource(Res.string.settings_app_lock_pins_mismatch) else null,
                filledCount = uiState.enteredPin.length,
                errorTrigger = uiState.errorTrigger,
                isError = uiState.errorMessage != null,
                onDigit = viewModel::onDigit,
                onDelete = viewModel::onDelete,
                onCancel = viewModel::cancelFlow,
                modifier = Modifier.weight(1f),
            )

            // 3. Zámek je zapnutý a ověřený — správa nastavení
            settings != null && uiState.isVerified -> AppLockManageContent(
                settings = settings!!,
                biometricSupported = biometricAuth.isSupported,
                onChangePin = viewModel::startChangePin,
                onSetTimeout = viewModel::setTimeout,
                onSetBiometrics = { viewModel.setBiometrics(it, biometricAuth) },
                onDisable = viewModel::disableLock,
            )

            // 4. Zámek je vypnutý — úvod se stejnou skladbou jako vypnuté dvoufázové ověření
            else -> AppLockIntroContent(onEnable = viewModel::startSetup)
        }
    }
}

/** Zámek je vypnutý: stav, k čemu je, a jeho zapnutí. */
@Composable
private fun AppLockIntroContent(onEnable: () -> Unit) {
    SettingsPageHeader(
        icon = Res.drawable.lock_open_24dp,
        title = stringResource(Res.string.settings_app_lock_off_title),
        text = stringResource(Res.string.settings_app_lock_description),
    )
    MediumButton(
        onClick = onEnable,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.settings_app_lock_enable))
    }
}

/** Ověření stávajícího PINu před vstupem do správy, s brzdou po opakovaných chybách. */
@Composable
private fun VerifyCurrentPinContent(
    viewModel: AppLockViewModel,
    biometricSupported: Boolean,
    onBiometricAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(SystemLockClock.elapsedMillis()) }
    val isBlocked = uiState.blockedUntil > now
    val remainingSeconds = if (isBlocked) {
        ((uiState.blockedUntil - now + 999) / 1000).coerceAtLeast(1)
    } else 0

    LaunchedEffect(uiState.blockedUntil) {
        while (uiState.blockedUntil > SystemLockClock.elapsedMillis()) {
            now = SystemLockClock.elapsedMillis()
            delay(500L)
        }
        now = SystemLockClock.elapsedMillis()
    }

    PinStep(
        title = stringResource(Res.string.settings_app_lock_verify_current),
        errorText = when {
            isBlocked -> stringResource(Res.string.app_lock_blocked, remainingSeconds)
            uiState.errorMessage == "wrong" -> stringResource(Res.string.app_lock_wrong_pin)
            uiState.errorMessage == BIOMETRICS_REVOKED -> stringResource(Res.string.app_lock_biometrics_revoked)
            else -> null
        },
        filledCount = uiState.enteredPin.length,
        errorTrigger = uiState.errorTrigger,
        isError = isBlocked || uiState.errorMessage != null,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onBiometrics = onBiometricAuth.takeIf { biometricSupported },
        enabled = !isBlocked && !uiState.isBusy,
        modifier = modifier,
    )
}

/**
 * Krok s číselníkem: nadpis, chyba, puntíky nahoře, číselník dole. Ověření i nový PIN mají
 * tutéž skladbu jako obrazovka zámku (nadpis `headlineSmall`, chyba na místě, které zůstává
 * vyhrazené, ať puntíky neposkočí).
 */
@Composable
internal fun PinStep(
    title: String,
    errorText: String?,
    filledCount: Int,
    errorTrigger: Int,
    isError: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometrics: (() -> Unit)? = null,
    enabled: Boolean = true,
    onCancel: (() -> Unit)? = null,
) {
    // Naležato vedle sebe a se zmenšenou klávesnicí — viz [PinLayout].
    PinLayout(
        modifier = modifier.fillMaxWidth(),
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

                Spacer(Modifier.height(8.dp))

                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }

                Spacer(Modifier.height(24.dp))

                PinDots(filledCount = filledCount, errorTrigger = errorTrigger, isError = isError)
            }
        },
        keypad = { keySize ->
            PinKeypad(
                onDigit = onDigit,
                onDelete = onDelete,
                onBiometrics = onBiometrics,
                isBiometricsEnabled = onBiometrics != null,
                isEnabled = enabled,
                keySize = keySize,
            )
        },
        footer = onCancel?.let { cancel ->
            {
                TextButton(onClick = cancel, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        },
    )
}

/**
 * Zapnutý zámek: stav, blok voleb a vypnutí jako vlastní blok — tatáž skladba jako zapnuté
 * dvoufázové ověření.
 */
@Composable
private fun AppLockManageContent(
    settings: eu.tintera.plume.core.lock.AppLockSettings,
    biometricSupported: Boolean,
    onChangePin: () -> Unit,
    onSetTimeout: (Int) -> Unit,
    onSetBiometrics: (Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    var showDisableConfirm by rememberSaveable { mutableStateOf(false) }

    if (showDisableConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_app_lock_disable_confirm_title),
            text = stringResource(Res.string.settings_app_lock_disable_confirm_text),
            confirmLabel = stringResource(Res.string.settings_app_lock_disable),
            onConfirm = {
                showDisableConfirm = false
                onDisable()
            },
            onDismiss = { showDisableConfirm = false },
            icon = Res.drawable.lock_open_24dp,
            destructive = true,
        )
    }

    SettingsPageHeader(
        icon = Res.drawable.lock_24dp,
        title = stringResource(Res.string.settings_app_lock_on_title),
        text = stringResource(Res.string.settings_app_lock_on_text),
    )

    // Vypnutí je nevratné, má blok vlastní — jako vypnutí dvoufázového ověření.
    GroupedColumn(Modifier.fillMaxWidth()) {
        SettingsActionRow(
            icon = Res.drawable.edit_24dp,
            title = stringResource(Res.string.settings_app_lock_change_pin),
            onClick = onChangePin,
        )

        ChoiceRow(
            headline = stringResource(Res.string.settings_app_lock_timeout),
            icon = Res.drawable.schedule_24dp,
            options = APP_LOCK_TIMEOUTS,
            selected = settings.timeoutSeconds,
            label = { stringResource(timeoutStringResource(it)) },
            onSelect = onSetTimeout,
        )

        if (biometricSupported) {
            SwitchListItem(
                headline = stringResource(Res.string.settings_app_lock_biometrics),
                checked = settings.biometrics,
                supporting = stringResource(Res.string.settings_app_lock_biometrics_summary),
                icon = Res.drawable.fingerprint_24dp,
                onCheckedChange = onSetBiometrics,
            )
        }

        GroupBreak()

        SettingsActionRow(
            icon = Res.drawable.lock_open_24dp,
            title = stringResource(Res.string.settings_app_lock_disable),
            destructive = true,
            onClick = { showDisableConfirm = true },
        )
    }
}

private fun timeoutStringResource(seconds: Int): StringResource = when (seconds) {
    60 -> Res.string.app_lock_timeout_1m
    5 * 60 -> Res.string.app_lock_timeout_5m
    60 * 60 -> Res.string.app_lock_timeout_1h
    5 * 60 * 60 -> Res.string.app_lock_timeout_5h
    else -> Res.string.app_lock_timeout_1m
}
