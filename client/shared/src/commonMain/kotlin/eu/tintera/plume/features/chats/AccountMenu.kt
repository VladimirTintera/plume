package eu.tintera.plume.features.chats

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.canAddAccounts
import eu.tintera.plume.resources.accounts_add
import eu.tintera.plume.resources.accounts_shown
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.person_add_24dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import eu.tintera.plume.resources.history_toggle_off_24dp
import eu.tintera.plume.resources.my_stories_title
import eu.tintera.plume.resources.chats_bookmarks_count
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.profile_bookmarks
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.bookmark_24dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.download_24dp
import eu.tintera.plume.resources.downloads_title
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.calls_title
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_archive_unread
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_drafts
import eu.tintera.plume.resources.chats_drafts_count
import eu.tintera.plume.resources.edit_note_24dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.resources.chats_account
import eu.tintera.plume.resources.my_qr_title
import eu.tintera.plume.resources.qr_code_24dp
import eu.tintera.plume.resources.nav_settings
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.settings_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Fotka účtu na konci vyhledávací pilulky a pod ní menu: Uložené zprávy, Archiv, Nastavení.
 *
 * Vzor Gmailu a Google Messages. Nahradil ⋮ vedle pilulky, a hlavně spodní lištu: Nastavení
 * jsou na telefonu jen tady. Pilulka tím dostala celou šířku.
 *
 * Než se účet načte (nebo když se načíst nepodaří), je místo fotky ikona člověka — tlačítko
 * se nesmí objevit až později, menu by do té doby nebylo kde otevřít.
 *
 * [archiveUnread] se ukazuje dvakrát, protože samotné menu je zavřené: číslem u položky Archiv
 * a tečkou na tlačítku menu, jinak by se o nepřečtených v archivu nikdo nedozvěděl. Tečka je
 * ale jen od NEZTLUMENÝCH chatů — do archivu se ztlumené chaty ukládají, takže tečka od nich
 * by byla signál, který nikdy nezhasne. Číslo u položky počítá všechny; kdo menu otevřel, chce
 * vědět, kolik toho v archivu čeká.
 *
 * Stav spojení fotka NENESE — ten je v pilulce, znakem místo lupy a nápovědou
 * (`ChatSearchBar`). Fotka je identita účtu, ne kontrolka.
 */
@Composable
internal fun AccountMenu(
    account: AccountInfo?,
    archiveUnread: ArchiveUnread,
    onSavedMessages: () -> Unit,
    onArchive: () -> Unit,
    /** Historie hovorů; `null` = bez položky (platforma bez hovorů). */
    onCalls: (() -> Unit)?,
    onDownloads: () -> Unit,
    onMyStories: () -> Unit,
    /** Kolik chatů má rozepsanou zprávu; bez konceptů položka v menu není. */
    draftCount: Int,
    onDrafts: () -> Unit,
    /** Kolik je záložek zpráv; bez nich položka v menu není (jako u konceptů). */
    bookmarkCount: Int,
    onBookmarks: () -> Unit,
    onMyQrCode: () -> Unit,
    onSettings: () -> Unit,
    /** Přihlášené účty, přepnutí a přidání účtu. */
    switcher: AccountSwitcher,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.chats_account)
    // Odznak je jen číslo — čtečka obrazovky potřebuje větu, ne „3".
    val unreadLabel = pluralStringResource(Res.plurals.chats_archive_unread, archiveUnread.chats, archiveUnread.chats)
    /*
     * Ztlumený archiv nese odznak v neutrální barvě: chaty tam nepřečtené jsou, ale nic si
     * nežádá pozornost — obarvit je akcentem by bylo falešné volání.
     */
    val accented = archiveUnread.unmutedChats > 0
    val archiveBadge: (@Composable () -> Unit)? = if (archiveUnread.chats == 0) null else {
        {
            Badge(
                containerColor = if (accented) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (accented) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                RollingNumber(
                    value = archiveUnread.chats,
                    style = LocalTextStyle.current.tabularNumbers(),
                    modifier = Modifier.semantics { contentDescription = unreadLabel },
                )
            }
        }
    }
    // Tečka archivu je mimo `IconButton`, který obsah ořezává na 40dp kruh, v rohu fotky.
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = label },
            // Bez tvaru při stisku: obsahem je kulatá fotka, hranatá vlnka by kolem ní trčela.
        ) {
            if (account != null) {
                Avatar(
                    chatId = account.id,
                    title = account.displayName,
                    avatarFileId = account.photoFileId,
                    size = ACCOUNT_AVATAR_SIZE,
                )
            } else {
                Icon(painterResource(Res.drawable.person_24dp), contentDescription = null)
            }
        }
        if (archiveUnread.unmutedChats > 0) {
            Box(Modifier.size(ACCOUNT_AVATAR_SIZE)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        // Obroučka barvou pilulky — bez ní je tečka na tmavé fotce jen skvrna,
                        // stejně jako u zelené tečky dostupnosti v řádku chatu.
                        .size(ACCOUNT_DOT)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        // Kam se odtud jde: místa v chatech, pak vlastní účet (QR kód) a Nastavení — jiný druh
        // cíle, vlastní skupina.
        // Koncepty jen když nějaké jsou: prázdný přehled by byl položka, která nikam nevede.
        val draftsLabel = pluralStringResource(Res.plurals.chats_drafts_count, draftCount, draftCount)
        val bookmarksLabel = pluralStringResource(Res.plurals.chats_bookmarks_count, bookmarkCount, bookmarkCount)
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                // Účty nahoře, jako v Telegramu, a jen když je jich víc: s jedním účtem by tu byl
                // řádek, který nikam nevede.
                if (switcher.accounts.size > 1) {
                    switcher.accounts.map { entry ->
                        MenuAction(
                            label = entry.title,
                            leading = {
                                AccountScope(entry.account) {
                                    Avatar(
                                        chatId = entry.info?.id ?: 0L,
                                        title = entry.title,
                                        avatarFileId = entry.info?.photoFileId,
                                        size = ACCOUNT_ROW_AVATAR_SIZE,
                                    )
                                }
                            },
                            trailing = if (entry.shown) {
                                { Icon(painterResource(Res.drawable.check_24dp), contentDescription = stringResource(Res.string.accounts_shown)) }
                            } else {
                                null
                            },
                            onClick = { switcher.onSelect(entry.account.slot) },
                        )
                    }
                } else {
                    emptyList()
                },
                listOfNotNull(
                    MenuAction(stringResource(Res.string.saved_messages), Res.drawable.bookmark_24dp, onClick = onSavedMessages),
                    MenuAction(
                        label = stringResource(Res.string.chats_archive),
                        icon = Res.drawable.archive_24dp,
                        trailing = archiveBadge,
                        onClick = onArchive,
                    ),
                    onCalls?.let { MenuAction(stringResource(Res.string.calls_title), Res.drawable.call_24dp, onClick = it) },
                    MenuAction(stringResource(Res.string.downloads_title), Res.drawable.download_24dp, onClick = onDownloads),
                    MenuAction(stringResource(Res.string.my_stories_title), Res.drawable.history_toggle_off_24dp, onClick = onMyStories),
                    // Počet v neutrální barvě: koncept je náš rozdělaný text, ne nic, co by
                    // si žádalo pozornost jako nepřečtené.
                    if (draftCount > 0) {
                        MenuAction(
                            label = stringResource(Res.string.chats_drafts),
                            icon = Res.drawable.edit_note_24dp,
                            trailing = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    RollingNumber(
                                        value = draftCount,
                                        style = LocalTextStyle.current.tabularNumbers(),
                                        modifier = Modifier.semantics { contentDescription = draftsLabel },
                                    )
                                }
                            },
                            onClick = onDrafts,
                        )
                    } else {
                        null
                    },
                    if (bookmarkCount > 0) {
                        MenuAction(
                            label = stringResource(Res.string.profile_bookmarks),
                            icon = Res.drawable.bookmark_filled_24dp,
                            trailing = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    RollingNumber(
                                        value = bookmarkCount,
                                        style = LocalTextStyle.current.tabularNumbers(),
                                        modifier = Modifier.semantics { contentDescription = bookmarksLabel },
                                    )
                                }
                            },
                            onClick = onBookmarks,
                        )
                    } else {
                        null
                    },
                ),
                listOfNotNull(
                    MenuAction(stringResource(Res.string.my_qr_title), Res.drawable.qr_code_24dp, onClick = onMyQrCode),
                    // Přidat účet patří k vlastnímu účtu, ne k místům v chatech; u limitu zmizí.
                    if (switcher.canAdd) MenuAction(stringResource(Res.string.accounts_add), Res.drawable.person_add_24dp, onClick = switcher.onAdd) else null,
                    MenuAction(stringResource(Res.string.nav_settings), Res.drawable.settings_24dp, onClick = onSettings),
                ),
            ),
        )
    }
}

/**
 * Účty v menu účtu ([AccountMenu]). [accounts] jsou v pořadí, ve kterém přibyly; [canAdd] je
 * `false` u limitu účtů.
 */
@Immutable
internal data class AccountSwitcher(
    val accounts: List<AccountEntry>,
    val canAdd: Boolean,
    val onSelect: (AccountSlot) -> Unit,
    val onAdd: () -> Unit,
)

/** Účet v menu: [info] se dočte z jeho klienta, do té doby je [title] prázdný. */
@Immutable
internal data class AccountEntry(
    val account: AccountSession,
    val info: AccountInfo?,
    /** Jeho chaty ukazuje seznam (výchozí účet). */
    val shown: Boolean,
) {
    val title: String get() = info?.displayName.orEmpty()
}

/**
 * Účty pro [AccountMenu] z `Accounts`: jména a fotky z klienta každého účtu, výběr nastaví
 * výchozí účet, přidání otevře přihlášení dalšího účtu.
 */
@Composable
internal fun rememberAccountSwitcher(): AccountSwitcher {
    val accounts = koinInject<Accounts>()
    val all by accounts.all.collectAsStateWithLifecycle()
    val default by accounts.default.collectAsStateWithLifecycle()
    // `me()` je dotaz do místní databáze TDLib, ne na síť; účtů jsou nejvýš tři.
    val infos by produceState(emptyMap<AccountSlot, AccountInfo>(), all) {
        value = all.mapNotNull { account -> account.session.me().getOrNull()?.let { account.slot to it } }.toMap()
    }
    val scope = rememberCoroutineScope()
    return AccountSwitcher(
        accounts = all.map { AccountEntry(it, infos[it.slot], shown = it === default) },
        canAdd = canAddAccounts && all.size < Accounts.MAX_ACCOUNTS,
        onSelect = accounts::setDefault,
        onAdd = { scope.launch { accounts.add() } },
    )
}

/** Fotka účtu v řádku menu — velikost ikony položky. */
private val ACCOUNT_ROW_AVATAR_SIZE = 24.dp

/** Fotka účtu v pilulce — o kus menší než ikona, ať pilulku nevyboulí. */
private val ACCOUNT_AVATAR_SIZE = 30.dp

/** Tečka „v archivu něco je" na fotce účtu, i s obroučkou. Menší než tečka dostupnosti — je jen signál, ne stav. */
private val ACCOUNT_DOT = 10.dp
