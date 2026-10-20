package eu.tintera.plume.features.channellimit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.navigation.ChannelLimitRequests
import eu.tintera.plume.core.ui.CheckableChatRows
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.MediumButtonHeight
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.channel_limit_chats
import eu.tintera.plume.resources.channel_limit_empty
import eu.tintera.plume.resources.channel_limit_leave
import eu.tintera.plume.resources.channel_limit_premium
import eu.tintera.plume.resources.channel_limit_text
import eu.tintera.plume.resources.channel_limit_title
import eu.tintera.plume.resources.logout_24dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Limit skupin a kanálů ([ChannelLimitRequests]) nad celým shellem: proč vstup nebo založení
 * nešlo, a neaktivní skupiny a kanály k opuštění. Bez něj by uživatel dostal jen hlášku a sám
 * hledal, co opustit.
 */
@Composable
fun ChannelLimitHost(viewModel: ChannelLimitViewModel = koinViewModel()) {
    val requests = koinInject<ChannelLimitRequests>()
    val shown by requests.shown.collectAsStateWithLifecycle()
    if (!shown) return
    LaunchedEffect(Unit) { viewModel.load() }
    ChannelLimitSheet(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelLimitSheet(viewModel: ChannelLimitViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val premium by viewModel.isPremium.collectAsStateWithLifecycle()
    // Seznam bývá dlouhý; napůl rozbalený sheet by tlačítko schoval.
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = viewModel::dismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            SheetTitle(stringResource(Res.string.channel_limit_title))
            Text(
                text = stringResource(Res.string.channel_limit_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Premium limit zdvojnásobí; koupit ho Plume neumí, ale vědět se to má.
            if (!premium) {
                Text(
                    text = stringResource(Res.string.channel_limit_premium),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.channel_limit_chats),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp),
            )
            val chats = state.chats
            when {
                chats == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                chats.isEmpty() -> GroupEmptyRow(stringResource(Res.string.channel_limit_empty))
                else -> CheckableChatRows(
                    chats = chats,
                    selected = state.selected,
                    onToggle = viewModel::toggle,
                    enabled = !state.leaving,
                )
            }
            state.error?.let { error ->
                Text(
                    text = error.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!chats.isNullOrEmpty()) {
                // Opustit je nevratné (soukromá skupina zpátky nepustí), proto chybová barva.
                MediumButton(
                    onClick = viewModel::leaveSelected,
                    enabled = state.selected.isNotEmpty() && !state.leaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(MediumButtonHeight),
                ) {
                    if (state.leaving) {
                        LoadingIndicator(Modifier.size(ButtonDefaults.iconSizeFor(MediumButtonHeight)), color = LocalContentColor.current)
                    } else {
                        MediumButtonIcon(Res.drawable.logout_24dp)
                        Text(pluralStringResource(Res.plurals.channel_limit_leave, state.selected.size, state.selected.size))
                    }
                }
            }
        }
    }
}
