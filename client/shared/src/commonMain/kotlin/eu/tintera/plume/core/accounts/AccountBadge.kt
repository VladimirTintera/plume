package eu.tintera.plume.core.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.Avatar
import org.koin.compose.koinInject
import eu.tintera.plume.core.adaptive.WindowControlsSpace
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace

/**
 * Účet téhle části obrazovky ([LocalAccount]), když je v aplikaci víc účtů; s jedním `null`.
 * Kde se ukazuje, komu chat patří (řádek společného seznamu, výsledek hledání, lišta obrazovky
 * účtu), má s jedním účtem zůstat všechno, jak bylo. Konverzace se ptá užší
 * [rememberConversationAccount].
 */
@Composable
fun rememberShownAccount(): AccountInfo? {
    val account = LocalAccount.current
    val signedIn by koinInject<Accounts>().all.collectAsStateWithLifecycle()
    val several = signedIn.size > 1
    // `me()` je dotaz do místní databáze TDLib, ne na síť.
    val info by produceState<AccountInfo?>(null, account, several) {
        value = if (several) account.session.me().getOrNull() else null
    }
    return info?.takeIf { several }
}

/**
 * Kde konverzace ukáže, kterým účtem se v ní píše: [inHeader] = odznak na avataru v hlavičce,
 * vstupní pole „Zpráva jako …" pokaždé, když je tahle hodnota vůbec.
 */
@Immutable
data class ConversationAccount(val info: AccountInfo, val inHeader: Boolean)

/**
 * Účet konverzace [chatId] ([LocalAccount]), jen když ho má smysl ukázat; s jedním účtem `null`.
 *
 * - **Režim Všechny účty, nebo chat jiného než výchozího účtu**: odznak v hlavičce i pole.
 *   Seznam tu míchá účty, nebo konverzace patří účtu, jehož chaty seznam neukazuje.
 * - **Chat výchozího účtu mimo režim Všechny účty**: kdo jsem, říká fotka v pilulce seznamu,
 *   takže odznak v hlavičce nic nepřidá. Pole účet jmenuje jen u chatu, do kterého se dá psát
 *   i z jiného přihlášeného účtu (společná skupina, tentýž člověk): tam se dá splést.
 *
 * Přepnutí výchozího účtu z notifikace nebo zástupce ohlásí konverzace zvlášť
 * ([AccountSwitchSnackbar]), takže pole kvůli němu účet nést nemusí.
 */
@Composable
fun rememberConversationAccount(chatId: Long): ConversationAccount? {
    val info = rememberShownAccount() ?: return null
    val account = LocalAccount.current
    val accounts = koinInject<Accounts>()
    val signedIn by accounts.all.collectAsStateWithLifecycle()
    val default by accounts.default.collectAsStateWithLifecycle()
    val allAccounts by koinInject<AppSettings>().chatListAllAccounts.collectAsStateWithLifecycle()
    if (allAccounts || default.slot != account.slot) return ConversationAccount(info, inHeader = true)
    val others = signedIn.filter { it.slot != account.slot }
    // Uložené zprávy mají id vlastního účtu; ve druhém účtu je to chat s tímhle, ne „totéž".
    val shared by produceState(false, chatId, others, info.id) {
        value = chatId != info.id && others.any { other -> other.koin.getOrNull<ChatRepository>()?.takesPartIn(chatId) == true }
    }
    return ConversationAccount(info, inHeader = false).takeIf { shared }
}

/**
 * Malá fotka účtu na avataru chatu: komu chat patří, když je účtů víc. Fotka, ne barva — barvy
 * nesou chaty (`ChatColors`) a pletly by se. Obroučka barvou plochy pod avatarem ([rim]),
 * stejně jako u tečky dostupnosti naproti.
 */
@Composable
fun AccountBadge(account: AccountInfo, rim: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Avatar(
        chatId = account.id,
        title = account.displayName,
        avatarFileId = account.photoFileId,
        size = size,
        modifier = modifier
            .offset(x = (-4).dp, y = 4.dp)
            .background(rim, CircleShape)
            .padding(2.dp),
    )
}

/**
 * Lišta obrazovky, která patří jednomu účtu (Moje příběhy, Hovory a Stahování mimo režim Všechny
 * účty): s víc účty nese pod nadpisem jméno účtu, s jedním je to obyčejná [TopAppBar]. Bez toho
 * nebylo poznat, čí historie hovorů nebo čí příběhy jsou. [showAccount] = `false` u společného
 * seznamu víc účtů, kde účet nese řádek.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    showAccount: Boolean = true,
) {
    val account = rememberShownAccount()?.takeIf { showAccount }
    if (account == null) {
        TopAppBar(title = title, navigationIcon = { WindowControlsSpace(navigationIcon = navigationIcon) }, actions = {
                actions()
                WindowControlsEndSpace()
            })
    } else {
        TopAppBar(title = title, subtitle = { Text(account.displayName) }, navigationIcon = { WindowControlsSpace(navigationIcon = navigationIcon) }, actions = {
                actions()
                WindowControlsEndSpace()
            })
    }
}

/**
 * Jméno účtu [slot] do notifikace, když je v aplikaci víc účtů; s jedním `null`. Bez něj není
 * z notifikace poznat, komu zpráva přišla.
 */
suspend fun Accounts.notificationName(slot: AccountSlot): String? {
    if (all.value.size < 2) return null
    return find(slot)?.session?.me()?.getOrNull()?.displayName?.takeIf { it.isNotBlank() }
}
