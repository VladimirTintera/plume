package eu.tintera.plume.core.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import eu.tintera.plume.core.ui.chatAccountBadgeKey
import eu.tintera.plume.core.ui.sharedNavElement
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
 *
 * S [chatId] odznak letí spolu s avatarem mezi seznamem a hlavičkou konverzace
 * ([chatAccountBadgeKey]); nad avatarem v překryvu, jinak by ho přikryl. Předává se jen tam,
 * kde letí i avatar. [flight] je další let, který odznak nese s avatarem (sbalení seznamu do
 * sloupce, `collapseFlight`); patří na odznak sám, ne na [modifier] se zarovnáním v rodiči.
 */
@Composable
fun AccountBadge(
    account: AccountInfo,
    rim: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    chatId: Long? = null,
    flight: Modifier = Modifier,
) {
    Avatar(
        chatId = account.id,
        title = account.displayName,
        avatarFileId = account.photoFileId,
        size = size,
        modifier = modifier
            .offset(x = (-4).dp, y = 4.dp)
            .then(flight)
            .badgeFlight(chatId, account)
            .background(rim, CircleShape)
            .padding(2.dp),
    )
}

/**
 * Účet odznaku: jméno a fotka, a [session], ve kterém se fotka kreslí. Fotka je `fileId`
 * z TDLib JEHO účtu — v kontejneru jiného účtu znamená totéž číslo jiný soubor (na Foldu se
 * místo fotky VT ukázala samolepka dárku). `null` = kreslí se v účtu kolem ([LocalAccount]),
 * u odznaku řádku, který patří témuž účtu.
 */
@Immutable
data class BadgeAccount(val info: AccountInfo, val session: AccountSession? = null)

/**
 * Odznaky víc účtů přes sebe ([AccountBadge] pro každý): řádek spojené společné skupiny
 * ve společném seznamu. Každý další leží o kus vpravo nad předchozím, s obroučkou [rim], takže
 * se od sebe odliší i stejně barevné fotky. S jedním účtem je to [AccountBadge], s žádným nic.
 * [chatId] jako u [AccountBadge]: do hlavičky konverzace přiletí odznak účtu, kterým se otevřela.
 * [flight] dostane účet odznaku, ať má každý z odznaků přes sebe vlastní let.
 */
@Composable
fun AccountBadges(
    accounts: List<BadgeAccount>,
    rim: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    chatId: Long? = null,
    flight: @Composable (AccountInfo) -> Modifier = { Modifier },
) {
    when (accounts.size) {
        0 -> Unit
        1 -> InAccount(accounts.single().session) {
            AccountBadge(accounts.single().info, rim, modifier, size, chatId, flight(accounts.single().info))
        }
        else -> Box(modifier.offset(x = (-4).dp, y = 4.dp)) {
            accounts.take(MAX_STACKED_BADGES).forEachIndexed { index, account ->
                InAccount(account.session) {
                    Avatar(
                        chatId = account.info.id,
                        title = account.info.displayName,
                        avatarFileId = account.info.photoFileId,
                        size = size,
                        modifier = Modifier
                            .padding(start = size * STACKED_BADGE_STEP * index)
                            .then(flight(account.info))
                            .badgeFlight(chatId, account.info)
                            .background(rim, CircleShape)
                            .padding(2.dp),
                    )
                }
            }
        }
    }
}

/** Let odznaku [account] s avatarem chatu [chatId] ([chatAccountBadgeKey]); bez [chatId] nic. */
@Composable
private fun Modifier.badgeFlight(chatId: Long?, account: AccountInfo): Modifier =
    if (chatId == null) this else sharedNavElement(chatAccountBadgeKey(chatId, account.id), zIndexInOverlay = 1f)

/** [content] v účtu [session] ([AccountScope]), nebo v tom kolem, když je `null`. */
@Composable
fun InAccount(session: AccountSession?, content: @Composable () -> Unit) {
    if (session == null || session == LocalAccount.current) content() else AccountScope(session, content)
}

/** Víc odznaků by přes avatar chatu přerostlo; tolik účtů ve spojené skupině skoro nebývá. */
private const val MAX_STACKED_BADGES = 3

/** O kolik velikosti odznaku leží další vpravo (zbytek překrývá předchozí). */
private const val STACKED_BADGE_STEP = 0.6f

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
