package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.InAccount
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.accounts.SharedChats
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_account_switch_current
import eu.tintera.plume.resources.conversation_account_switch_text
import eu.tintera.plume.resources.conversation_account_switch_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Účet, kterým se dá psát do spojené společné skupiny ([rememberChatAccounts]). */
@Immutable
internal data class ChatAccount(val slot: AccountSlot, val info: AccountInfo, val session: AccountSession) {
    /** Fotka účtu: `fileId` z jeho TDLib, kreslí se proto v jeho kontejneru ([InAccount]). */
    @Composable
    fun Photo(size: Dp) {
        InAccount(session) { Avatar(chatId = info.id, title = info.displayName, avatarFileId = info.photoFileId, size = size) }
    }
}

/**
 * Účty, které jsou ve společné skupině nebo kanálu [chatId] (i ten konverzace), v pořadí účtů
 * aplikace. Prázdné, když je tam jen účet konverzace, mimo režim Všechny účty a u chatu, který
 * se přes účty nespojuje ([SharedChats], `isSharedHistoryChat`). [enabled] = `false` u míst,
 * odkud se účet přepnout nedá (bublina konverzace).
 */
@Composable
internal fun rememberChatAccounts(chatId: Long, enabled: Boolean): List<ChatAccount> {
    val account = LocalAccount.current
    val shared = koinInject<SharedChats>()
    val signedIn by koinInject<Accounts>().all.collectAsStateWithLifecycle()
    val allAccounts by koinInject<AppSettings>().chatListAllAccounts.collectAsStateWithLifecycle()
    val accounts by produceState(emptyList<ChatAccount>(), chatId, account.slot, signedIn, allAccounts, enabled) {
        val others = if (enabled) shared.othersIn(account.slot, chatId) else emptyList()
        value = if (others.isEmpty()) {
            emptyList()
        } else {
            // `me()` je dotaz do místní databáze TDLib, ne na síť.
            (listOf(account) + others)
                .sortedBy { session -> signedIn.indexOfFirst { it.slot == session.slot } }
                .mapNotNull { session -> session.session.me().getOrNull()?.let { ChatAccount(session.slot, it, session) } }
        }
    }
    return accounts
}

/**
 * Fotka účtu, kterým se do spojené společné skupiny píše, vlevo u pole; ťuknutí otevře výběr
 * účtu ([AccountSwitchSheet]). Stejné místo i podoba jako výběr odesílatele skupiny
 * (`SendAsButton`), protože jde o totéž rozhodnutí: kým zpráva odejde.
 *
 * **Bez `IconButtonDefaults.shapes()`**, jako `SendAsButton`: obsahem je kulatá fotka a měnící
 * se vlnka by kolem ní trčela.
 */
@Composable
internal fun AccountSwitchButton(
    accounts: List<ChatAccount>,
    current: AccountSlot,
    onSwitch: (AccountSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = accounts.firstOrNull { it.slot == current } ?: return
    // Otevřený sheet je stav tlačítka, ne obrazovky (CLAUDE.md, „Otevřený sheet je stav obrazovky").
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.conversation_account_switch_current, me.info.displayName)
    IconButton(onClick = { open = true }, modifier = modifier.semantics { contentDescription = label }) {
        me.Photo(32.dp)
    }
    if (open) {
        AccountSwitchSheet(
            accounts = accounts,
            current = current,
            onChoose = { slot ->
                open = false
                if (slot != current) onSwitch(slot)
            },
            onDismiss = { open = false },
        )
    }
}

/** Výběr účtu, kterým psát: všechny účty ve skupině, vybraný ten, kterým je konverzace otevřená. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitchSheet(
    accounts: List<ChatAccount>,
    current: AccountSlot,
    onChoose: (AccountSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberRestoringScrollState()).padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(Res.string.conversation_account_switch_title),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            Text(
                stringResource(Res.string.conversation_account_switch_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                accounts.forEachIndexed { index, account ->
                    ChoiceListItem(
                        label = account.info.displayName,
                        selected = account.slot == current,
                        index = index,
                        count = accounts.size,
                        onClick = { onChoose(account.slot) },
                        leading = { account.Photo(40.dp) },
                    )
                }
            }
        }
    }
}
