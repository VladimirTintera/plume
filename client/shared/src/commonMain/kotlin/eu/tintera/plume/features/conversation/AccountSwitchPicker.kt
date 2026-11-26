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
import eu.tintera.plume.core.accounts.ChatCounterpart
import eu.tintera.plume.core.accounts.isSharedHistoryChat
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
import eu.tintera.plume.resources.conversation_account_switch_text_separate
import eu.tintera.plume.resources.conversation_account_switch_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Účet, kterým se dá psát do spojeného chatu ([rememberChatAccounts]), a id toho chatu v něm:
 * Uložené zprávy mají v každém účtu jiné id (vlastní id účtu), ostatní totéž.
 */
@Immutable
internal data class ChatAccount(val slot: AccountSlot, val info: AccountInfo, val session: AccountSession, val chatId: Long) {
    /** Fotka účtu: `fileId` z jeho TDLib, kreslí se proto v jeho kontejneru ([InAccount]). */
    @Composable
    fun Photo(size: Dp) {
        InAccount(session) { Avatar(chatId = info.id, title = info.displayName, avatarFileId = info.photoFileId, size = size) }
    }
}

/**
 * Účty, které chat [chatId] mají taky ([SharedChats.counterparts]: společná skupina nebo kanál,
 * soukromý chat, Uložené zprávy), i ten konverzace, v pořadí účtů aplikace. Prázdné, když ho má
 * jen účet konverzace, a mimo režim Všechny účty. [enabled] = `false` u míst, odkud se účet
 * přepnout nedá (bublina konverzace).
 */
@Composable
internal fun rememberChatAccounts(chatId: Long, enabled: Boolean): List<ChatAccount> {
    val account = LocalAccount.current
    val shared = koinInject<SharedChats>()
    val signedIn by koinInject<Accounts>().all.collectAsStateWithLifecycle()
    val allAccounts by koinInject<AppSettings>().chatListAllAccounts.collectAsStateWithLifecycle()
    val accounts by produceState(emptyList<ChatAccount>(), chatId, account.slot, signedIn, allAccounts, enabled) {
        val others = if (enabled) shared.counterparts(account.slot, chatId) else emptyList()
        value = if (others.isEmpty()) {
            emptyList()
        } else {
            // `me()` je dotaz do místní databáze TDLib, ne na síť.
            (listOf(ChatCounterpart(account, chatId)) + others)
                .sortedBy { counterpart -> signedIn.indexOfFirst { it.slot == counterpart.account.slot } }
                .mapNotNull { (session, id) -> session.session.me().getOrNull()?.let { ChatAccount(session.slot, it, session, id) } }
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
    onSwitch: (ChatAccount) -> Unit,
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
            onChoose = { account ->
                open = false
                if (account.slot != current) onSwitch(account)
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
    onChoose: (ChatAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    // Společná skupina má jednu historii a mění se jen to, kdo píše; soukromý chat a Uložené
    // zprávy jsou v každém účtu jiná konverzace a přepnutí ukáže jiné zprávy.
    val sharedHistory = accounts.firstOrNull()?.chatId?.let(::isSharedHistoryChat) == true
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberRestoringScrollState()).padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(Res.string.conversation_account_switch_title),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            Text(
                stringResource(
                    if (sharedHistory) Res.string.conversation_account_switch_text else Res.string.conversation_account_switch_text_separate,
                ),
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
                        onClick = { onChoose(account) },
                        leading = { account.Photo(40.dp) },
                    )
                }
            }
        }
    }
}
