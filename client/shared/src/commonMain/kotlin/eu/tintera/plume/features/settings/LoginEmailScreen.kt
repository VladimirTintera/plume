package eu.tintera.plume.features.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.ui.BlockTextField
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.auth_email_change
import eu.tintera.plume.resources.auth_headline_email_code
import eu.tintera.plume.resources.auth_input_email
import eu.tintera.plume.resources.auth_input_recovery_code
import eu.tintera.plume.resources.auth_recovery_resend
import eu.tintera.plume.resources.auth_support_email_code
import eu.tintera.plume.resources.login_email_add_title
import eu.tintera.plume.resources.login_email_change_title
import eu.tintera.plume.resources.login_email_confirm
import eu.tintera.plume.resources.login_email_current
import eu.tintera.plume.resources.login_email_intro
import eu.tintera.plume.resources.login_email_send
import eu.tintera.plume.resources.settings_login_email
import eu.tintera.plume.resources.settings_login_email_none
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Řádek „Přihlašovací e-mail" v Soukromí. **Jen tam, kde ho jde nastavit nebo změnit**: účet
 * e-mail už má (maska v `PasswordState`), nebo ho Telegram chce (doporučená akce). Jinak server
 * nastavení odmítne (`EMAIL_NOT_SETUP`, dokumentace `setLoginEmailAddress`) a řádek by sliboval
 * cestu, která není.
 */
@Composable
internal fun LoginEmailRow(twoStep: TwoStepViewModel, onOpen: () -> Unit) {
    val state by twoStep.state.collectAsStateWithLifecycle()
    val notices = koinInject<AccountNotices>()
    val suggestions by notices.suggestedActions.collectAsStateWithLifecycle()
    val pattern = state.status?.loginEmailPattern
    val wanted = suggestions.any { it is SuggestedAction.SetLoginEmail }
    if (pattern == null && !wanted) return
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.alternate_email_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_login_email)) },
        supportingContent = { Text(pattern ?: stringResource(Res.string.settings_login_email_none)) },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/**
 * Přihlašovací e-mail: adresa, pak kód z ní. Po potvrzení se obrazovka zavře a řádek v Soukromí
 * ukáže novou masku. Kroky jsou stav ViewModelu, ne routy — jako průvodce dvoufázového ověření.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginEmailScreen(viewModel: LoginEmailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onBack() }

    // Zpět z kódu vede k adrese, teprve potom pryč (NavigationBackHandler kvůli predictive back).
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.pendingCode != null,
        onBackCompleted = { viewModel.back() },
    )

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        topBar = {
            SettingsTopBar(
                title = stringResource(Res.string.settings_login_email),
                onBack = { if (!viewModel.back()) onBack() },
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
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }
            val focus = remember { FocusRequester() }
            AnimatedContent(targetState = state.pendingCode, label = "login-email-step") { pending ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (pending == null) {
                        Text(
                            stringResource(if (state.currentPattern == null) Res.string.login_email_add_title else Res.string.login_email_change_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            buildString {
                                append(stringResource(Res.string.login_email_intro))
                                state.currentPattern?.let { append(' ').append(stringResource(Res.string.login_email_current, it)) }
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BlockTextField(
                            value = viewModel.email,
                            onValueChange = viewModel::onEmailChange,
                            placeholder = Res.string.auth_input_email,
                            isError = state.error != null,
                            keyboardType = KeyboardType.Email,
                            contentType = ContentType.EmailAddress,
                            onSubmit = viewModel::submitEmail,
                            fieldModifier = Modifier.focusRequester(focus),
                        )
                    } else {
                        Text(stringResource(Res.string.auth_headline_email_code), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            stringResource(Res.string.auth_support_email_code, pending.pattern),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Obyčejné pole, ne políčka: délku kódu TDLib u e-mailu říká jen někdy.
                        // Bez `contentType`: `SmsOtpCode` by nabízel kód z SMS.
                        BlockTextField(
                            value = viewModel.code,
                            onValueChange = viewModel::onCodeChange,
                            placeholder = Res.string.auth_input_recovery_code,
                            isError = state.error != null,
                            keyboardType = KeyboardType.Number,
                            onSubmit = viewModel::submitCode,
                            fieldModifier = Modifier.focusRequester(focus),
                        )
                    }
                    state.error?.let {
                        Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = if (pending == null) viewModel::submitEmail else viewModel::submitCode, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                            if (state.busy) {
                                LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(if (pending == null) Res.string.login_email_send else Res.string.login_email_confirm))
                            }
                        }
                        if (pending != null) {
                            TextButton(onClick = viewModel::resendCode, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.auth_recovery_resend))
                            }
                            TextButton(onClick = viewModel::changeEmail, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.auth_email_change))
                            }
                        }
                    }
                    // Pole se má psát hned — je to jediná věc, kterou tu jde dělat.
                    LaunchedEffect(pending) { runCatching { focus.requestFocus() } }
                }
            }
        }
    }
}
