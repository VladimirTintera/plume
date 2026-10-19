package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.change_phone_new_title
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.ui.BlockTextField
import eu.tintera.plume.core.ui.CountryPickerSheet
import eu.tintera.plume.core.ui.PhoneField
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.auth.CodeField
import eu.tintera.plume.features.auth.resendLabel
import eu.tintera.plume.features.auth.supportingText
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_change_number
import eu.tintera.plume.resources.auth_headline_code
import eu.tintera.plume.resources.auth_input_code
import eu.tintera.plume.resources.auth_resend_wait
import eu.tintera.plume.resources.change_phone_confirm
import eu.tintera.plume.resources.change_phone_intro
import eu.tintera.plume.resources.change_phone_send
import eu.tintera.plume.resources.change_phone_title
import org.jetbrains.compose.resources.stringResource

/**
 * Změna telefonního čísla. Kroky jsou stav ViewModelu, ne routy — jako u přihlašovacího e-mailu.
 * Po potvrzení kódu se obrazovka zavře; nové číslo ukáže úprava profilu i karta účtu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangePhoneScreen(viewModel: ChangePhoneViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    var pickingCountry by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.sent != null,
        onBackCompleted = { viewModel.back() },
    )

    SettingsPage(
        title = stringResource(Res.string.change_phone_title),
        onBack = { if (!viewModel.back()) onBack() },
        modifier = modifier,
    ) {
        AnimatedContent(targetState = state.sent, label = "change-phone-step") { sent ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (sent == null) {
                    SettingsPageHeader(
                        title = stringResource(Res.string.change_phone_new_title),
                        text = stringResource(Res.string.change_phone_intro),
                    )
                    PhoneField(
                        phone = viewModel.phone,
                        isError = state.error != null,
                        onPhoneChange = viewModel::onPhoneChange,
                        onPickCountry = { pickingCountry = true },
                        onSubmit = viewModel::submitPhone,
                    )
                } else {
                    // Kudy kód přišel, jako při přihlášení; komu, řekne číslo pod tím.
                    SettingsPageHeader(
                        title = stringResource(Res.string.auth_headline_code),
                        text = listOfNotNull(stringResource(sent.code.delivery.supportingText()), state.sentTo).joinToString("\n"),
                    )
                    val digits = sent.code.digits
                    if (digits != null) {
                        CodeField(
                            value = viewModel.code,
                            digits = digits,
                            isError = state.error != null,
                            onValueChange = viewModel::onCodeChange,
                            onSubmit = viewModel::submitCode,
                        )
                        LaunchedEffect(viewModel.code) { if (viewModel.code.length == digits) viewModel.submitCode() }
                    } else {
                        BlockTextField(
                            value = viewModel.code,
                            onValueChange = viewModel::onCodeChange,
                            placeholder = Res.string.auth_input_code,
                            isError = state.error != null,
                            keyboardType = KeyboardType.Text,
                            onSubmit = viewModel::submitCode,
                        )
                    }
                }
                state.error?.let {
                    Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                SettingsPageActions {
                    Button(
                        onClick = if (sent == null) viewModel::submitPhone else viewModel::submitCode,
                        enabled = !state.busy && (sent != null || viewModel.phone.isComplete),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        if (state.busy) {
                            LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(if (sent == null) Res.string.change_phone_send else Res.string.change_phone_confirm))
                        }
                    }
                    if (sent != null) {
                        sent.next?.let { next ->
                            val label = stringResource(next.resendLabel())
                            TextButton(onClick = viewModel::resendCode, enabled = !state.busy && state.resendIn == 0, shapes = ButtonDefaults.shapes()) {
                                Text(
                                    if (state.resendIn > 0) {
                                        stringResource(Res.string.auth_resend_wait, label, formatDuration(state.resendIn))
                                    } else {
                                        label
                                    },
                                    style = LocalTextStyle.current.tabularNumbers(),
                                )
                            }
                        }
                        TextButton(onClick = viewModel::changeNumber, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.auth_change_number))
                        }
                    }
                }
            }
        }
    }

    if (pickingCountry) {
        CountryPickerSheet(
            countries = countries,
            selectedCodes = setOfNotNull(viewModel.phone.country?.code),
            onPick = {
                viewModel.selectCountry(it)
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }
}
