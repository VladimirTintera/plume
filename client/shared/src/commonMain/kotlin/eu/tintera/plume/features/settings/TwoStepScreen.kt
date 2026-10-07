package eu.tintera.plume.features.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.ResetProgress
import eu.tintera.plume.core.telegram.TwoStepStatus
import eu.tintera.plume.core.telegram.resetProgress
import eu.tintera.plume.core.ui.BlockTextField
import eu.tintera.plume.core.ui.PasswordField
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_headline_recovery
import eu.tintera.plume.resources.auth_input_new_password
import eu.tintera.plume.resources.auth_input_password
import eu.tintera.plume.resources.auth_input_password_hint
import eu.tintera.plume.resources.auth_input_recovery_code
import eu.tintera.plume.resources.auth_recovery_resend
import eu.tintera.plume.resources.auth_support_recovery_sent
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.resources.mail_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.settings_two_step
import eu.tintera.plume.resources.settings_two_step_cancel_email
import eu.tintera.plume.resources.settings_two_step_cancel_email_text
import eu.tintera.plume.resources.settings_two_step_cancel_email_title
import eu.tintera.plume.resources.settings_two_step_cancel_reset_text
import eu.tintera.plume.resources.settings_two_step_cancel_reset_title
import eu.tintera.plume.resources.settings_two_step_change_email
import eu.tintera.plume.resources.settings_two_step_change_password
import eu.tintera.plume.resources.settings_two_step_continue
import eu.tintera.plume.resources.settings_two_step_disable
import eu.tintera.plume.resources.settings_two_step_disable_passport
import eu.tintera.plume.resources.settings_two_step_disable_text
import eu.tintera.plume.resources.settings_two_step_disable_title
import eu.tintera.plume.resources.settings_two_step_email_current
import eu.tintera.plume.resources.settings_two_step_email_none
import eu.tintera.plume.resources.settings_two_step_email_set
import eu.tintera.plume.resources.settings_two_step_enable
import eu.tintera.plume.resources.settings_two_step_enter_code
import eu.tintera.plume.resources.settings_two_step_forgot
import eu.tintera.plume.resources.settings_two_step_hint_row
import eu.tintera.plume.resources.settings_two_step_input_email
import eu.tintera.plume.resources.settings_two_step_input_repeat
import eu.tintera.plume.resources.settings_two_step_keep
import eu.tintera.plume.resources.settings_two_step_load_failed
import eu.tintera.plume.resources.settings_two_step_off
import eu.tintera.plume.resources.settings_two_step_off_text
import eu.tintera.plume.resources.settings_two_step_off_title
import eu.tintera.plume.resources.settings_two_step_on
import eu.tintera.plume.resources.settings_two_step_on_text
import eu.tintera.plume.resources.settings_two_step_on_title
import eu.tintera.plume.resources.settings_two_step_pending
import eu.tintera.plume.resources.settings_two_step_pending_email
import eu.tintera.plume.resources.settings_two_step_reset_cancel
import eu.tintera.plume.resources.settings_two_step_reset_confirm
import eu.tintera.plume.resources.settings_two_step_reset_declined
import eu.tintera.plume.resources.settings_two_step_reset_now
import eu.tintera.plume.resources.settings_two_step_reset_now_text
import eu.tintera.plume.resources.settings_two_step_reset_now_title
import eu.tintera.plume.resources.settings_two_step_reset_ready
import eu.tintera.plume.resources.settings_two_step_reset_text
import eu.tintera.plume.resources.settings_two_step_reset_title
import eu.tintera.plume.resources.settings_two_step_reset_waiting
import eu.tintera.plume.resources.settings_two_step_save
import eu.tintera.plume.resources.settings_two_step_set_email
import eu.tintera.plume.resources.settings_two_step_skip
import eu.tintera.plume.resources.settings_two_step_skip_email_confirm
import eu.tintera.plume.resources.settings_two_step_skip_email_text
import eu.tintera.plume.resources.settings_two_step_skip_email_title
import eu.tintera.plume.resources.settings_two_step_step_current
import eu.tintera.plume.resources.settings_two_step_step_current_text
import eu.tintera.plume.resources.settings_two_step_step_email
import eu.tintera.plume.resources.settings_two_step_step_email_code
import eu.tintera.plume.resources.settings_two_step_step_email_text
import eu.tintera.plume.resources.settings_two_step_step_hint
import eu.tintera.plume.resources.settings_two_step_step_hint_text
import eu.tintera.plume.resources.settings_two_step_step_new
import eu.tintera.plume.resources.settings_two_step_step_new_text
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Řádek „Dvoufázové ověření" ve skupině Soukromí. Stav se čte při každém návratu: po zapnutí
 * nebo vypnutí na obrazovce nad sekcí (a třeba po potvrzení e-mailu v jiném klientovi) by jinak
 * zůstal starý.
 */
@Composable
internal fun TwoStepRow(viewModel: TwoStepViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    val status = state.status
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.lock_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_two_step)) },
        supportingContent = {
            // Dokud stav nedorazí, řádek nic netvrdí — „vypnuto" by mohla být lež.
            when {
                status == null -> Unit
                status.pendingEmail != null -> Text(stringResource(Res.string.settings_two_step_pending))
                status.hasPassword -> Text(stringResource(Res.string.settings_two_step_on))
                else -> Text(stringResource(Res.string.settings_two_step_off))
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/**
 * Dvoufázové ověření: stav a akce, nebo běžící průvodce ([TwoStepFlowState]).
 *
 * **Průvodce je stav ViewModelu, ne routy.** Kroky sbírají hesla a ta do zásobníku (a tím do
 * uloženého stavu, který přežije zabití procesu) nepatří. Zpět proto obsluhuje obrazovka sama
 * (`NavigationBackHandler`, ne `BackHandler` — ten by zabil predictive back): napřed dialog, pak
 * krok, pak průvodce, a teprve potom odejde z obrazovky.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TwoStepScreen(viewModel: TwoStepViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    val snackbarHostState = remember { SnackbarHostState() }
    // Hláška běží mimo efekt: vynulování události mění jeho klíč a zrušilo by ji.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.noticeShown()
        snackbarScope.launch { snackbarHostState.showSnackbar(notice.resolveNow()) }
    }
    val declined = state.resetDeclinedUntil
    val declinedText = declined?.let { stringResource(Res.string.settings_two_step_reset_declined, it.asUpcomingTime()) }
    LaunchedEffect(declined) {
        if (declined == null || declinedText == null) return@LaunchedEffect
        viewModel.resetDeclinedShown()
        snackbarScope.launch { snackbarHostState.showSnackbar(declinedText) }
    }

    val inFlow = state.flow != null || state.dialog != null
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = inFlow,
        onBackCompleted = viewModel::back,
    )

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringResource(Res.string.settings_two_step),
                onBack = { if (inFlow) viewModel.back() else onBack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .settingsContentPadding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = settingsContentBottom(padding, 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val status = state.status
            val flow = state.flow
            when {
                status == null && state.loadFailed -> LoadFailed(viewModel::reload)
                status == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                flow != null -> AnimatedContent(targetState = flow.action to flow.step, label = "two-step-step") { (action, step) ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FlowStep(viewModel, state, flow.copy(action = action, step = step), status)
                    }
                }
                else -> StatusContent(viewModel, state, status)
            }
        }
    }

    state.dialog?.let { TwoStepDialogHost(it, state.status, viewModel) }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Text(stringResource(Res.string.settings_two_step_load_failed), style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.common_try_again)) }
}

/** Stav ověření a co s ním jde dělat. */
@Composable
private fun ColumnScope.StatusContent(viewModel: TwoStepViewModel, state: TwoStepState, status: TwoStepStatus) {
    Header(
        icon = if (status.hasPassword) Res.drawable.lock_24dp else Res.drawable.lock_open_24dp,
        title = if (status.hasPassword) Res.string.settings_two_step_on_title else Res.string.settings_two_step_off_title,
        text = stringResource(if (status.hasPassword) Res.string.settings_two_step_on_text else Res.string.settings_two_step_off_text),
    )

    // Změna, která čeká na kód z e-mailu. Nové heslo s e-mailem platí až po potvrzení, takže
    // tahle karta je u zapínání to jediné, co mezi „vypnuto" a „zapnuto" stojí.
    status.pendingEmail?.let { pending ->
        NoticeCard(
            icon = Res.drawable.mail_24dp,
            text = stringResource(Res.string.settings_two_step_pending_email, pending.pattern),
        ) {
            Button(onClick = { viewModel.start(TwoStepAction.ConfirmEmail) }, enabled = !state.busy) {
                Text(stringResource(Res.string.settings_two_step_enter_code))
            }
            TextButton(onClick = viewModel::resendEmailCode, enabled = !state.busy) {
                Text(stringResource(Res.string.auth_recovery_resend))
            }
            TextButton(onClick = viewModel::askCancelEmail, enabled = !state.busy) {
                Text(stringResource(Res.string.settings_two_step_cancel_email))
            }
        }
    }

    when (val progress = resetProgress(status.resetDate, Clock.System.now().epochSeconds)) {
        ResetProgress.None -> Unit
        is ResetProgress.Waiting -> NoticeCard(
            icon = Res.drawable.schedule_24dp,
            text = stringResource(Res.string.settings_two_step_reset_waiting, progress.at.asUpcomingTime()),
        ) {
            TextButton(onClick = viewModel::askCancelReset, enabled = !state.busy) {
                Text(stringResource(Res.string.settings_two_step_reset_cancel))
            }
        }
        ResetProgress.Ready -> NoticeCard(
            icon = Res.drawable.schedule_24dp,
            text = stringResource(Res.string.settings_two_step_reset_ready),
        ) {
            Button(onClick = viewModel::askResetNow, enabled = !state.busy) {
                Text(stringResource(Res.string.settings_two_step_reset_now))
            }
            TextButton(onClick = viewModel::askCancelReset, enabled = !state.busy) {
                Text(stringResource(Res.string.settings_two_step_reset_cancel))
            }
        }
    }

    if (!status.hasPassword) {
        // S čekajícím potvrzením e-mailu se nezapíná znovu — nový pokus by čekající změnu přepsal.
        if (status.pendingEmail == null) {
            Button(
                onClick = { viewModel.start(TwoStepAction.Enable) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.settings_two_step_enable)) }
        }
        return
    }

    Card(Modifier.fillMaxWidth()) {
        if (status.hint.isNotBlank()) {
            InfoRow(stringResource(Res.string.settings_two_step_hint_row, status.hint))
        }
        InfoRow(
            stringResource(if (status.hasRecoveryEmail) Res.string.settings_two_step_email_set else Res.string.settings_two_step_email_none),
        )
    }
    Card(Modifier.fillMaxWidth()) {
        ActionRow(Res.drawable.edit_24dp, Res.string.settings_two_step_change_password, !state.busy) {
            viewModel.start(TwoStepAction.ChangePassword)
        }
        ActionRow(
            Res.drawable.mail_24dp,
            if (status.hasRecoveryEmail) Res.string.settings_two_step_change_email else Res.string.settings_two_step_set_email,
            !state.busy,
        ) { viewModel.start(TwoStepAction.ChangeEmail) }
        ActionRow(Res.drawable.lock_open_24dp, Res.string.settings_two_step_disable, !state.busy, destructive = true) {
            viewModel.start(TwoStepAction.Disable)
        }
    }
    // Reset už běží — druhý „Zapomenuté heslo" by nic nového nenabídl.
    if (status.resetDate <= 0) {
        TextButton(onClick = { viewModel.start(TwoStepAction.Recover) }, enabled = !state.busy) {
            Text(stringResource(Res.string.settings_two_step_forgot))
        }
    }
}

@Composable
private fun Header(icon: org.jetbrains.compose.resources.DrawableResource, title: StringResource, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoticeCard(
    icon: org.jetbrains.compose.resources.DrawableResource,
    text: String,
    actions: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(icon), contentDescription = null)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            // Tři tlačítka se na úzký telefon do řádku nevejdou.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyMedium) },
    )
}

@Composable
private fun ActionRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    title: StringResource,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = color,
            leadingContentColor = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** Jeden krok průvodce: nadpis, vysvětlení, pole a tlačítka. */
@Composable
private fun ColumnScope.FlowStep(viewModel: TwoStepViewModel, state: TwoStepState, flow: TwoStepFlowState, status: TwoStepStatus) {
    val first = remember(flow.step) { FocusRequester() }
    val second = remember(flow.step) { FocusRequester() }
    // Vyplnit pole je to jediné, co jde v kroku udělat — fokus si bere samo, jako kód při přihlášení.
    LaunchedEffect(flow.step) { runCatching { first.requestFocus() } }
    val isError = state.error != null
    val submit = viewModel::submit

    when (flow.step) {
        TwoStepStep.CurrentPassword -> {
            StepText(Res.string.settings_two_step_step_current, stringResource(Res.string.settings_two_step_step_current_text))
            if (status.hint.isNotBlank()) {
                Text(
                    stringResource(Res.string.settings_two_step_hint_row, status.hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PasswordField(
                value = viewModel.currentPassword,
                isError = isError,
                onValueChange = viewModel::onCurrentPasswordChange,
                onSubmit = submit,
                placeholder = Res.string.auth_input_password,
                fieldModifier = Modifier.focusRequester(first),
            )
        }
        TwoStepStep.RecoveryCode -> {
            StepText(Res.string.auth_headline_recovery, stringResource(Res.string.auth_support_recovery_sent, flow.codePattern))
            CodeInput(viewModel.code, isError, viewModel::onCodeChange, submit, first)
        }
        TwoStepStep.NewPassword -> {
            StepText(Res.string.settings_two_step_step_new, stringResource(Res.string.settings_two_step_step_new_text))
            PasswordField(
                value = viewModel.newPassword,
                isError = isError,
                onValueChange = viewModel::onNewPasswordChange,
                onSubmit = submit,
                placeholder = Res.string.auth_input_new_password,
                contentType = ContentType.NewPassword,
                imeAction = ImeAction.Next,
                // Výchozí „Další" by skočil na oko vedle pole, ne do druhého pole.
                onNext = { runCatching { second.requestFocus() } },
                fieldModifier = Modifier.focusRequester(first),
            )
            PasswordField(
                value = viewModel.repeatPassword,
                isError = isError,
                onValueChange = viewModel::onRepeatPasswordChange,
                onSubmit = submit,
                placeholder = Res.string.settings_two_step_input_repeat,
                contentType = ContentType.NewPassword,
                fieldModifier = Modifier.focusRequester(second),
            )
        }
        TwoStepStep.Hint -> {
            StepText(Res.string.settings_two_step_step_hint, stringResource(Res.string.settings_two_step_step_hint_text))
            BlockTextField(
                value = viewModel.hint,
                onValueChange = viewModel::onHintChange,
                placeholder = Res.string.auth_input_password_hint,
                isError = isError,
                keyboardType = KeyboardType.Text,
                onSubmit = submit,
                fieldModifier = Modifier.focusRequester(first),
            )
        }
        TwoStepStep.Email -> {
            StepText(Res.string.settings_two_step_step_email, stringResource(Res.string.settings_two_step_step_email_text))
            if (flow.currentEmail.isNotBlank()) {
                Text(
                    stringResource(Res.string.settings_two_step_email_current, flow.currentEmail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BlockTextField(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = Res.string.settings_two_step_input_email,
                isError = isError,
                keyboardType = KeyboardType.Email,
                contentType = ContentType.EmailAddress,
                onSubmit = submit,
                fieldModifier = Modifier.focusRequester(first),
            )
        }
        TwoStepStep.EmailCode -> {
            StepText(Res.string.settings_two_step_step_email_code, stringResource(Res.string.auth_support_recovery_sent, flow.codePattern))
            CodeInput(viewModel.code, isError, viewModel::onCodeChange, submit, first)
        }
    }

    state.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = submit, enabled = !state.busy) {
            if (state.busy) {
                LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                // Vypnutí se ještě potvrzuje dialogem, takže heslo jen „pokračuje".
                val last = flow.action != TwoStepAction.Disable &&
                    (flow.action.next(flow.step) == null || flow.action.commitsAt(flow.step))
                Text(stringResource(if (last) Res.string.settings_two_step_save else Res.string.settings_two_step_continue))
            }
        }
        when (flow.step) {
            // Zapnutí jde i bez e-mailu; „Přeskočit" vede na potvrzení s následkem.
            TwoStepStep.Email -> if (flow.action == TwoStepAction.Enable) {
                TextButton(onClick = { viewModel.onEmailChange(""); submit() }, enabled = !state.busy) {
                    Text(stringResource(Res.string.settings_two_step_skip))
                }
            }
            TwoStepStep.EmailCode -> TextButton(onClick = viewModel::resendEmailCode, enabled = !state.busy) {
                Text(stringResource(Res.string.auth_recovery_resend))
            }
            else -> Unit
        }
    }
}

@Composable
private fun StepText(title: StringResource, text: String) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Kód z e-mailu je obyčejné pole, ne políčka: délku TDLib u kódu k obnově neříká vůbec a u kódu
 * k potvrzení adresy jen někdy. `contentType` nemá — `SmsOtpCode` by nabízel kód z SMS.
 */
@Composable
private fun CodeInput(value: String, isError: Boolean, onChange: (String) -> Unit, onSubmit: () -> Unit, focus: FocusRequester) {
    BlockTextField(
        value = value,
        onValueChange = onChange,
        placeholder = Res.string.auth_input_recovery_code,
        isError = isError,
        keyboardType = KeyboardType.Number,
        onSubmit = onSubmit,
        fieldModifier = Modifier.focusRequester(focus),
    )
}

/** Potvrzení s následkem. Text tlačítka říká, CO se stane, ne „OK". */
@Composable
private fun TwoStepDialogHost(dialog: TwoStepDialog, status: TwoStepStatus?, viewModel: TwoStepViewModel) {
    val title: StringResource
    val text: String
    val confirm: StringResource
    var dismiss = Res.string.common_cancel
    var destructive = true
    when (dialog) {
        TwoStepDialog.SkipEmail -> {
            title = Res.string.settings_two_step_skip_email_title
            text = stringResource(Res.string.settings_two_step_skip_email_text)
            confirm = Res.string.settings_two_step_skip_email_confirm
            destructive = false
        }
        TwoStepDialog.Disable -> {
            title = Res.string.settings_two_step_disable_title
            val base = stringResource(Res.string.settings_two_step_disable_text)
            text = if (status?.hasPassportData == true) base + "\n\n" + stringResource(Res.string.settings_two_step_disable_passport) else base
            confirm = Res.string.settings_two_step_disable
        }
        TwoStepDialog.Reset -> {
            title = Res.string.settings_two_step_reset_title
            text = stringResource(Res.string.settings_two_step_reset_text)
            confirm = Res.string.settings_two_step_reset_confirm
        }
        TwoStepDialog.ResetNow -> {
            title = Res.string.settings_two_step_reset_now_title
            text = stringResource(Res.string.settings_two_step_reset_now_text)
            confirm = Res.string.settings_two_step_reset_now
        }
        TwoStepDialog.CancelReset -> {
            title = Res.string.settings_two_step_cancel_reset_title
            text = stringResource(Res.string.settings_two_step_cancel_reset_text)
            confirm = Res.string.settings_two_step_reset_cancel
            // Česky by obě tlačítka byla „Zrušit".
            dismiss = Res.string.settings_two_step_keep
            destructive = false
        }
        TwoStepDialog.CancelEmail -> {
            title = Res.string.settings_two_step_cancel_email_title
            text = stringResource(Res.string.settings_two_step_cancel_email_text)
            confirm = Res.string.settings_two_step_cancel_email
            dismiss = Res.string.settings_two_step_keep
        }
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = viewModel::confirmDialog) {
                Text(
                    stringResource(confirm),
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(dismiss)) } },
    )
}
