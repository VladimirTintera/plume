package eu.tintera.plume.features.notices

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.accounts.LocalAccount
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ServiceNotice
import eu.tintera.plume.core.telegram.TermsOfServiceUpdate
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.navigation.isTelegramLink
import androidx.compose.runtime.remember
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.notice_service_log_out
import eu.tintera.plume.resources.notice_service_ok
import eu.tintera.plume.resources.notice_service_title
import eu.tintera.plume.resources.notice_terms_accept
import eu.tintera.plume.resources.notice_terms_later
import eu.tintera.plume.resources.notice_terms_min_age
import eu.tintera.plume.resources.notice_terms_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Dialogy, které Telegram předepisuje ukázat nad čímkoli: oznámení od služby a nové podmínky.
 * Patří do přihlášené části aplikace nad obsah (`App`), ne do jedné obrazovky — přijít můžou
 * kdykoli a kdekoli.
 */
@Composable
fun AccountNoticesHost(viewModel: AccountNoticesViewModel = koinViewModel()) {
    val notice by viewModel.serviceNotice.collectAsStateWithLifecycle()
    val terms by viewModel.terms.collectAsStateWithLifecycle()
    val accepting by viewModel.accepting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val openLink = rememberNoticeLinkOpener()

    val shownNotice = notice
    val shownTerms = terms
    when {
        shownNotice != null -> ServiceNoticeDialog(
            notice = shownNotice,
            onDismiss = { viewModel.dismiss(shownNotice) },
            onLogOut = { viewModel.logOut(shownNotice) },
            onLink = openLink,
        )
        shownTerms != null -> TermsDialog(
            terms = shownTerms,
            accepting = accepting,
            error = error,
            onAccept = viewModel::acceptTerms,
            onLater = viewModel::postponeTerms,
            onLink = openLink,
        )
    }
}

/**
 * Oznámení od služby. U neplatného klíče relace (`AUTH_KEY_DROP_…`) TDLib předepisuje „Zrušit"
 * a „Odhlásit"; jinak je to jen sdělení, které stačí potvrdit.
 */
@Composable
private fun ServiceNoticeDialog(
    notice: ServiceNotice,
    onDismiss: () -> Unit,
    onLogOut: () -> Unit,
    onLink: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.notice_service_title)) },
        text = {
            if (notice.text.isNotBlank()) {
                ScrollableRichText(text = notice.text, notice.entities, onLink = onLink)
            }
        },
        confirmButton = {
            if (notice.offersLogOut) {
                TextButton(onClick = onLogOut, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.notice_service_log_out)) }
            } else {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.notice_service_ok)) }
            }
        },
        dismissButton = if (notice.offersLogOut) {
            { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } }
        } else {
            null
        },
    )
}

/**
 * Nové podmínky služby. Telegram chce blokující popup, takže ťuknutí mimo ani zpět ho nezavřou.
 *
 * **Odmítnout tu nejde, jen odložit.** Odmítnutí podle TDLib znamená smazat účet
 * (`deleteAccount` s důvodem „Decline ToS update") — nevratný krok, který do dialogu s dlouhým
 * právním textem nepatří. „Teď ne" podmínky schová do příštího startu, kdy je TDLib pošle znovu.
 */
@Composable
private fun TermsDialog(
    terms: TermsOfServiceUpdate,
    accepting: Boolean,
    error: UiText?,
    onAccept: () -> Unit,
    onLater: () -> Unit,
    onLink: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.notice_terms_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (terms.minUserAge > 0) {
                    Text(
                        stringResource(Res.string.notice_terms_min_age, terms.minUserAge),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                error?.let {
                    Text(it.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                ScrollableRichText(text = terms.text, terms.entities, onLink = onLink)
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = !accepting, shapes = ButtonDefaults.shapes()) {
                if (accepting) LoadingIndicator() else Text(stringResource(Res.string.notice_terms_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !accepting, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.notice_terms_later)) }
        },
    )
}

/**
 * Odkaz z oznámení nebo z karty zmrazení. Na Telegram (typicky odvolání u `@SpamBot`) ho otevře
 * aplikace sama, přes tutéž cestu jako odkaz z jiné aplikace ([LinkOpenRequests]); jinak by šel do
 * prohlížeče a z něj teprve zpátky — nebo vůbec ne (ověřeno: otevřel se Chrome). Ostatní do
 * prohlížeče.
 */
@Composable
fun rememberNoticeLinkOpener(): (String) -> Unit {
    val urlOpener = koinInject<UrlOpener>()
    val linkRequests = koinInject<LinkOpenRequests>()
    // Odkaz z oznámení účtu se otevře v tom účtu, ne ve výchozím.
    val slot = LocalAccount.current.slot
    return remember(urlOpener, linkRequests, slot) {
        { url: String -> if (isTelegramLink(url)) linkRequests.request(url, slot) else urlOpener.open(url) }
    }
}

/** Text s odkazy; dlouhý (podmínky) se v dialogu posouvá, tlačítka zůstanou vidět. */
@Composable
private fun ScrollableRichText(
    text: String,
    entities: List<eu.tintera.plume.core.telegram.MessageEntity>,
    onLink: (String) -> Unit,
) {
    RichText(
        text = text,
        entities = entities,
        style = MaterialTheme.typography.bodyMedium,
        onLinkClick = onLink,
        modifier = Modifier.verticalScroll(rememberRestoringScrollState()),
    )
}
