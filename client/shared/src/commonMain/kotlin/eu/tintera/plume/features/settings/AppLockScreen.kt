package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.app_lock_biometrics_revoked
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import eu.tintera.plume.core.ui.ChoiceRow
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import eu.tintera.plume.core.ui.GroupedColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.lock.APP_LOCK_TIMEOUTS
import eu.tintera.plume.core.lock.SystemLockClock
import eu.tintera.plume.core.lock.rememberBiometricAuth
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.PinDots
import eu.tintera.plume.core.ui.PinKeypad
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.reject
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppLockScreen(
    viewModel: AppLockViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val biometricAuth = rememberBiometricAuth()
    val scrollBehavior = settingsScrollBehavior()
    val biometricsPromptTitle = stringResource(Res.string.app_lock_biometrics_prompt)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SettingsTopBar(
                title = stringResource(Res.string.settings_app_lock),
                scrollBehavior = scrollBehavior,
                onBack = {
                    if (uiState.flow !is AppLockFlow.Idle) {
                        viewModel.cancelFlow()
                    } else {
                        onBack()
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                // 1. Zámek je zapnutý, ale ještě nebyl ověřen stávající PIN pro vstup do nastavení
                settings != null && !uiState.isVerified && uiState.flow is AppLockFlow.Idle -> {
                    VerifyCurrentPinContent(
                        viewModel = viewModel,
                        biometricSupported = settings?.biometrics == true && biometricAuth.isSupported,
                        onBiometricAuth = {
                            viewModel.onBiometricsVerify(
                                biometricAuth = biometricAuth,
                                promptTitle = biometricsPromptTitle,
                            )
                        },
                    )
                }

                // 2. Průvodce zadáváním nového PINu (nastavení nebo změna)
                uiState.flow is AppLockFlow.EnterNewPin || uiState.flow is AppLockFlow.ConfirmNewPin -> {
                    PinSetupContent(
                        flow = uiState.flow,
                        enteredPin = uiState.enteredPin,
                        errorTrigger = uiState.errorTrigger,
                        errorMessage = uiState.errorMessage,
                        onDigit = viewModel::onDigit,
                        onDelete = viewModel::onDelete,
                        onCancel = viewModel::cancelFlow,
                    )
                }

                // 3. Zámek je zapnutý a ověřený — správa nastavení
                settings != null && uiState.isVerified -> {
                    AppLockManageContent(
                        settings = settings!!,
                        biometricSupported = biometricAuth.isSupported,
                        onChangePin = viewModel::startChangePin,
                        onSetTimeout = viewModel::setTimeout,
                        onSetBiometrics = { viewModel.setBiometrics(it, biometricAuth) },
                        onDisable = viewModel::disableLock,
                    )
                }

                // 4. Zámek je vypnutý — úvodní obrazovka
                else -> {
                    AppLockIntroContent(onEnable = viewModel::startSetup)
                }
            }
        }
    }
}

/**
 * Úvodní obrazovka při vypnutém zámku.
 */
@Composable
private fun AppLockIntroContent(onEnable: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.lock_24dp),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.settings_app_lock),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(Res.string.settings_app_lock_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(36.dp))

        MediumButton(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.settings_app_lock_enable))
        }
    }
}

/**
 * Zobrazení pro ověření stávajícího PINu před vstupem do správy.
 */
@Composable
private fun VerifyCurrentPinContent(
    viewModel: AppLockViewModel,
    biometricSupported: Boolean,
    onBiometricAuth: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_app_lock_verify_current),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(8.dp))

            val errorText = when {
                isBlocked -> stringResource(Res.string.app_lock_blocked, remainingSeconds)
                uiState.errorMessage == "wrong" -> stringResource(Res.string.app_lock_wrong_pin)
                uiState.errorMessage == BIOMETRICS_REVOKED -> stringResource(Res.string.app_lock_biometrics_revoked)
                else -> null
            }

            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.height(24.dp))

            PinDots(
                filledCount = uiState.enteredPin.length,
                errorTrigger = uiState.errorTrigger,
                isError = isBlocked || uiState.errorMessage != null,
            )
        }

        PinKeypad(
            onDigit = viewModel::onDigit,
            onDelete = viewModel::onDelete,
            onBiometrics = onBiometricAuth,
            isBiometricsEnabled = biometricSupported,
            isEnabled = !isBlocked && !uiState.isBusy,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/**
 * Průvodce zadáváním a potvrzením nového PINu.
 */
@Composable
private fun PinSetupContent(
    flow: AppLockFlow,
    enteredPin: String,
    errorTrigger: Int,
    errorMessage: String?,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val title = when (flow) {
        is AppLockFlow.EnterNewPin -> stringResource(Res.string.settings_app_lock_new_pin)
        is AppLockFlow.ConfirmNewPin -> stringResource(Res.string.settings_app_lock_repeat_pin)
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(8.dp))

            if (errorMessage == "mismatch") {
                Text(
                    text = stringResource(Res.string.settings_app_lock_pins_mismatch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.height(24.dp))

            PinDots(
                filledCount = enteredPin.length,
                errorTrigger = errorTrigger,
                isError = errorMessage != null,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PinKeypad(
                onDigit = onDigit,
                onDelete = onDelete,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    }
}

/**
 * Správa nastavení zapnutého zámku (změna PINu, čas uzamčení, biometrie, vypnutí).
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Stejný blok zaoblených řádků jako skupiny sekcí Nastavení.
        GroupedColumn(Modifier.fillMaxWidth()) {
            // Změnit PIN
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(painterResource(Res.drawable.edit_24dp), contentDescription = null)
                },
                headlineContent = { Text(stringResource(Res.string.settings_app_lock_change_pin)) },
                modifier = Modifier.clickable(onClick = onChangePin),
            )

            // Automaticky zamknout
            ChoiceRow(
                headline = stringResource(Res.string.settings_app_lock_timeout),
                icon = Res.drawable.schedule_24dp,
                options = APP_LOCK_TIMEOUTS,
                selected = settings.timeoutSeconds,
                label = { stringResource(timeoutStringResource(it)) },
                onSelect = onSetTimeout,
            )

            // Biometrie
            if (biometricSupported) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(painterResource(Res.drawable.fingerprint_24dp), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(Res.string.settings_app_lock_biometrics)) },
                    supportingContent = { Text(stringResource(Res.string.settings_app_lock_biometrics_summary)) },
                    trailingContent = {
                        IconSwitch(
                            checked = settings.biometrics,
                            onCheckedChange = onSetBiometrics,
                        )
                    },
                    modifier = Modifier.clickable { onSetBiometrics(!settings.biometrics) },
                )
            }
        }

        // Tlačítko Vypnout zámek
        MediumButton(
            onClick = { showDisableConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(stringResource(Res.string.settings_app_lock_disable))
        }
    }
}

private fun timeoutStringResource(seconds: Int): StringResource = when (seconds) {
    60 -> Res.string.app_lock_timeout_1m
    5 * 60 -> Res.string.app_lock_timeout_5m
    60 * 60 -> Res.string.app_lock_timeout_1h
    5 * 60 * 60 -> Res.string.app_lock_timeout_5h
    else -> Res.string.app_lock_timeout_1m
}
