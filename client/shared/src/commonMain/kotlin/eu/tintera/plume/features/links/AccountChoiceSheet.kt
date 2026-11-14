package eu.tintera.plume.features.links

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.features.chats.rememberAccountSwitcher
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.link_account_text
import eu.tintera.plume.resources.link_account_title
import org.jetbrains.compose.resources.stringResource

/**
 * Kterým účtem otevřít odkaz zvenčí, který jedná za účet (pozvánka, sdílená složka, mini
 * aplikace; viz [actsAsAccount]). Předvybraný je účet, který odkaz dostal ([current]) — výchozí.
 * Ťuknutí rovnou volí: výběr jedné hodnoty bez potvrzovacího tlačítka, jako výběr jazyka.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountChoiceSheet(
    current: AccountSlot,
    onChoose: (AccountSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    val accounts = rememberAccountSwitcher().accounts
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(Res.string.link_account_title),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Text(
                stringResource(Res.string.link_account_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                accounts.forEachIndexed { index, entry ->
                    val slot = entry.account.slot
                    ChoiceListItem(
                        label = entry.title,
                        selected = slot == current,
                        index = index,
                        count = accounts.size,
                        onClick = { onChoose(slot) },
                        leading = {
                            // Fotka účtu ze souborů jeho kontejneru.
                            AccountScope(entry.account) {
                                Avatar(
                                    chatId = entry.info?.id ?: 0L,
                                    title = entry.title,
                                    avatarFileId = entry.info?.photoFileId,
                                    size = ACCOUNT_AVATAR_SIZE,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private val ACCOUNT_AVATAR_SIZE = 40.dp
