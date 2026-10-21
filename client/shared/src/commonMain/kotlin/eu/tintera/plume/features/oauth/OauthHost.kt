package eu.tintera.plume.features.oauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.OauthRequest
import eu.tintera.plume.core.ui.ActionButtonGroup
import eu.tintera.plume.core.ui.DialogCheckRow
import eu.tintera.plume.core.ui.GroupAction
import eu.tintera.plume.core.ui.GroupActionText
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.computer_24dp
import eu.tintera.plume.resources.language_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.oauth_accept
import eu.tintera.plume.resources.oauth_app_unverified
import eu.tintera.plume.resources.oauth_close
import eu.tintera.plume.resources.oauth_decline
import eu.tintera.plume.resources.oauth_done
import eu.tintera.plume.resources.oauth_ip
import eu.tintera.plume.resources.oauth_pick_code
import eu.tintera.plume.resources.oauth_phone_access
import eu.tintera.plume.resources.oauth_title
import eu.tintera.plume.resources.oauth_warning
import eu.tintera.plume.resources.oauth_write_access
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Žádost webu o přihlášení účtem Telegramu nad celou aplikací — přijít může kdykoli, jako
 * oznámení od služby (`AccountNoticesHost`), a platí jen minutu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OauthHost(viewModel: OauthViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    ModalBottomSheet(onDismissRequest = viewModel::close, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(stringResource(Res.string.oauth_title), modifier = Modifier.padding(start = 8.dp))
            val request = current.request
            when (val stage = current.stage) {
                OauthStage.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                is OauthStage.Failed -> {
                    Text(stage.message.resolve(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    CloseButton(viewModel::close)
                }
                OauthStage.Done -> {
                    Text(
                        stringResource(Res.string.oauth_done, request?.domain.orEmpty()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    CloseButton(viewModel::close)
                }
                OauthStage.MatchCode -> if (request != null) {
                    RequestDetails(request)
                    CodePicker(request.matchCodes, selected = null, enabled = !current.busy, onPick = viewModel::pickCode)
                    TextButton(onClick = viewModel::decline, shapes = ButtonDefaults.shapes(), modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(Res.string.oauth_decline))
                    }
                }
                OauthStage.Confirm -> if (request != null) {
                    RequestDetails(request)
                    // Bez ověření předem se číslo vybírá tady a jde s přihlášením.
                    val needsCode = !request.matchCodeFirst && request.matchCodes.isNotEmpty()
                    if (needsCode) CodePicker(request.matchCodes, selected = current.matchCode, enabled = !current.busy, onPick = viewModel::pickCode)
                    var allowWrite by rememberSaveable(current.url) { mutableStateOf(true) }
                    var allowPhone by rememberSaveable(current.url) { mutableStateOf(false) }
                    if (request.requestWriteAccess) {
                        DialogCheckRow(label = stringResource(Res.string.oauth_write_access, request.botName), checked = allowWrite, onChange = { allowWrite = it })
                    }
                    if (request.requestPhoneNumber) {
                        DialogCheckRow(label = stringResource(Res.string.oauth_phone_access), checked = allowPhone, onChange = { allowPhone = it })
                    }
                    Text(
                        stringResource(Res.string.oauth_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    val decline = stringResource(Res.string.oauth_decline)
                    val accept = stringResource(Res.string.oauth_accept)
                    val canAccept = !current.busy && (!needsCode || current.matchCode != null)
                    ActionButtonGroup(
                        listOf(
                            GroupAction(decline, viewModel::decline, weight = 1f) { modifier, source ->
                                FilledTonalButton(onClick = viewModel::decline, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                                    GroupActionText(decline)
                                }
                            },
                            GroupAction(accept, { viewModel.accept(allowWrite, allowPhone) }, enabled = canAccept, weight = 1f) { modifier, source ->
                                Button(
                                    onClick = { viewModel.accept(allowWrite, allowPhone) },
                                    enabled = canAccept,
                                    modifier = modifier,
                                    interactionSource = source,
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    GroupActionText(accept)
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Kdo a odkud: doména s aplikací, prohlížeč, IP adresa a místo — blok zaoblených řádků. */
@Composable
private fun RequestDetails(request: OauthRequest) {
    val rows = listOfNotNull(
        Triple(Res.drawable.language_24dp, request.domain, request.appName ?: stringResource(Res.string.oauth_app_unverified)),
        listOf(request.browser, request.platform).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
            ?.let { Triple(Res.drawable.computer_24dp, it, null) },
        request.ipAddress.takeIf { it.isNotBlank() }?.let { Triple(Res.drawable.computer_24dp, stringResource(Res.string.oauth_ip, it), null) },
        request.location.takeIf { it.isNotBlank() }?.let { Triple(Res.drawable.location_on_24dp, it, null) },
    )
    Column {
        rows.forEachIndexed { index, (icon, headline, supporting) ->
            DetailRow(icon, headline, supporting, index, rows.size)
        }
    }
}

@Composable
private fun DetailRow(icon: DrawableResource, headline: String, supporting: String?, index: Int, count: Int) {
    val container = MaterialTheme.colorScheme.surfaceContainerLowest
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = Modifier.groupedItem(index, count, container, edge = 0.dp, pressable = false),
    )
}

/** Čísla na výběr — správné ukazuje web. Vybrané je plné tlačítko, ostatní tónová. */
@Composable
private fun CodePicker(codes: List<String>, selected: String?, enabled: Boolean, onPick: (String) -> Unit) {
    Text(
        stringResource(Res.string.oauth_pick_code),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    ActionButtonGroup(
        codes.map { code ->
            GroupAction(code, { onPick(code) }, enabled = enabled, weight = 1f) { modifier, source ->
                if (code == selected) {
                    Button(onClick = { onPick(code) }, enabled = enabled, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                        GroupActionText(code)
                    }
                } else {
                    FilledTonalButton(onClick = { onPick(code) }, enabled = enabled, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                        GroupActionText(code)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.oauth_close), textAlign = TextAlign.End) }
    }
}
