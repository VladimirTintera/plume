package eu.tintera.plume.features.chats

import androidx.compose.foundation.background
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.lightbulb_24dp
import eu.tintera.plume.resources.shield_person_24dp
import eu.tintera.plume.resources.system_update_24dp
import eu.tintera.plume.resources.chats_pending_freeze
import eu.tintera.plume.resources.chats_pending_login
import eu.tintera.plume.resources.chats_pending_birthdays
import eu.tintera.plume.resources.chats_pending_suggestion
import eu.tintera.plume.resources.chats_pending_folder_chats
import eu.tintera.plume.resources.chats_pending_update
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Seznam chatů sbalený táhlem na úzký sloupec (`ListPaneAnchor.Collapsed`): jen avatary
 * s počtem nepřečtených, nahoře účet a hledání. Jako sbalený seznam v Telegram Desktop —
 * konverzace dostane skoro celou plochu a přepínat chaty jde dál jedním ťuknutím.
 *
 * - **Pořadí a skupiny jsou tytéž jako v plném seznamu** ([chatGroups]), jen bez předělů:
 *   rozbalením se nic nepřeskládá.
 * - **Hledání seznam napřed rozbalí** ([onSearch]) — pilulka hledání se do sloupce nevejde
 *   a výsledky by v něm byly nečitelné.
 * - **Jméno chatu ukáže bublina po najetí myší** ([WithTooltip], ne holý `TooltipBox`; na dotykové
 *   ploše podržení); čtečka ho dostane jako popis.
 * - **Karty nad seznamem** (aktualizace, nové přihlášení, narozeniny…) se do sloupce nevejdou.
 *   Že nějaká čeká, ukazuje tlačítko s tečkou pod lupou ([pendingCard]); ťuknutí seznam
 *   rozbalí a odjede nahoru na ni. Bez něj by se o nepotvrzeném přihlášení člověk se sbaleným
 *   seznamem nedozvěděl, dokud ho nerozbalí.
 * - FAB a výběr víc chatů tu nejsou: potřebují šířku a po rozbalení jsou zpátky.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsedChatList(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    onForumClick: (Long) -> Unit,
    onSearch: () -> Unit,
    account: @Composable () -> Unit,
    /** Nejdůležitější karta, která v plném seznamu nahoře čeká; `null` = žádná. */
    pendingCard: PendingCard?,
    /** Rozbalit seznam a ukázat karty nahoře. */
    onShowCards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()
    val hiddenSavedMessagesChatId by viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle()
    val snoozes by viewModel.snoozeState.collectAsStateWithLifecycle()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            account()
            IconButton(onClick = onSearch, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.search_24dp), contentDescription = stringResource(Res.string.chats_search))
            }
            pendingCard?.let { PendingCardButton(it, onClick = onShowCards) }
        }

        when (val current = state) {
            is ChatListState.Ready -> {
                val chats = remember(current.chats, separateUnread, hiddenSavedMessagesChatId, snoozes.due) {
                    chatGroups(current.chats, separateUnread, hiddenSavedMessagesChatId, snoozes.due).flatten()
                }
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                ) {
                    items(chats, key = { it.id }) { chat ->
                        CollapsedChat(
                            chat = chat,
                            selected = chat.id == selectedChatId,
                            onClick = { if (chat.isForum) onForumClick(chat.id) else onChatClick(chat.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            ChatListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            // Chybu i s tlačítkem „Zkusit znovu" ukáže plný seznam; do sloupce se nevejde.
            is ChatListState.Failed -> Unit
        }
    }
}

/**
 * Avatar chatu ve sbaleném seznamu. Vybraný chat má pod avatarem plochu `secondaryContainer`,
 * stejnou roli jako vybraný řádek plného seznamu (navigační stav, ne zvýraznění).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsedChat(chat: ChatSummary, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = chatTitle(chat.id, chat.title)
    // Ne holý TooltipBox: tah táhlem přepne sloupec na plný seznam uprostřed měření a bublina,
    // která nad avatarem ještě visí, by na desktopu shodila vykreslování (viz WithTooltip).
    WithTooltip(label = title, position = TooltipAnchorPosition.Right, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(COLLAPSED_ITEM_SIZE)
                .clip(COLLAPSED_ITEM_SHAPE)
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                .contextClickable(onClick = onClick)
                .semantics {
                    contentDescription = title
                    this.selected = selected
                },
        ) {
            Box {
                Avatar(
                    chatId = chat.avatarColorId,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    size = COLLAPSED_AVATAR_SIZE,
                    isSavedMessages = isSavedMessages(chat.id),
                    shape = chat.kind.avatarShape(),
                )
                if (chat.unreadCount > 0) {
                    val muted = chat.isMuted
                    Badge(
                        containerColor = if (muted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                        contentColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
                    ) {
                        RollingNumber(chat.unreadCount, style = LocalTextStyle.current.tabularNumbers())
                    }
                }
            }
        }
    }
}

/**
 * Karta nahoře v plném seznamu chatů, kterou sbalený sloupec nemá kam dát. Pořadí je pořadí
 * karet v seznamu — první čekající je ta nejdůležitější. [urgent] karty (zmrazený účet, cizí
 * přihlášení) jsou v chybové barvě: týkají se bezpečnosti účtu, ne pohodlí.
 */
internal enum class PendingCard(val icon: DrawableResource, val label: StringResource, val urgent: Boolean) {
    Freeze(Res.drawable.error_24dp, Res.string.chats_pending_freeze, urgent = true),
    Login(Res.drawable.shield_person_24dp, Res.string.chats_pending_login, urgent = true),
    Birthdays(Res.drawable.cake_24dp, Res.string.chats_pending_birthdays, urgent = false),
    Suggestion(Res.drawable.lightbulb_24dp, Res.string.chats_pending_suggestion, urgent = false),
    FolderChats(Res.drawable.folder_24dp, Res.string.chats_pending_folder_chats, urgent = false),
    Update(Res.drawable.system_update_24dp, Res.string.chats_pending_update, urgent = false),
}

/**
 * Tlačítko s tečkou: že nahoře v seznamu čeká [card]. Ikona a bublina říkají která, tečka
 * jen to, že něco — stejný odznak jako nepřečtené. Bublina přes [WithTooltip] (viz výš).
 */
@Composable
private fun PendingCardButton(card: PendingCard, onClick: () -> Unit) {
    val label = stringResource(card.label)
    val colors = if (card.urgent) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }
    WithTooltip(label = label, position = TooltipAnchorPosition.Right) {
        BadgedBox(
            badge = {
                Badge(containerColor = if (card.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            },
        ) {
            FilledTonalIconButton(onClick = onClick, colors = colors, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(card.icon), contentDescription = label)
            }
        }
    }
}

private val COLLAPSED_AVATAR_SIZE = 52.dp
private val COLLAPSED_ITEM_SIZE = 72.dp
private val COLLAPSED_ITEM_SHAPE = RoundedCornerShape(20.dp)
