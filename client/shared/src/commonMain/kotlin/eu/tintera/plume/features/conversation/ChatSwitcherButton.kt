package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.menuGroupShape
import eu.tintera.plume.core.ui.menuScroll
import eu.tintera.plume.core.ui.MenuColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.LocalPaneNeighbours
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.onSecondaryClick
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.MenuPopupProperties
import eu.tintera.plume.core.ui.longPress
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.conversation_back_unread
import eu.tintera.plume.resources.conversation_switch_chat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Rychlý přepínač chatů v hlavičce konverzace ([ChatSwitcher]): co nabídnout, jestli je nabídka
 * otevřená a kolik nepřečtených chatů čeká jinde. Sdílí ho šipka zpět ([ChatSwitcherBackButton],
 * pod ní se nabídka otevírá) a položka „Přepnout chat" v ⋮ (`ConversationMenu`), která otevře
 * TUTÉŽ nabídku pod šipkou — tak se uživatel dozví, kde ji najde příště.
 *
 * `onSwitch` `null` = přepínač se nenabízí (bublina konverzace): bez nabídky i bez odznaku.
 */
@Stable
internal class ChatSwitcherState(
    candidates: State<List<ChatSummary>>,
    unreadElsewhere: State<Int>,
    onSwitch: State<((ChatSummary) -> Unit)?>,
) {
    val candidates by candidates

    /** Odznak na šipce zpět, viz [eu.tintera.plume.features.conversation.unreadElsewhere]. */
    val unreadElsewhere by unreadElsewhere
    private val onSwitch by onSwitch

    var expanded by mutableStateOf(false)
        private set

    /** Bez chatů, na které přepnout (čerstvé přihlášení), se nabídka neotevře. */
    val canSwitch: Boolean get() = onSwitch != null && candidates.isNotEmpty()

    fun open() {
        if (canSwitch) expanded = true
    }

    fun dismiss() {
        expanded = false
    }

    fun switchTo(chat: ChatSummary) {
        expanded = false
        onSwitch?.invoke(chat)
    }
}

@Composable
internal fun rememberChatSwitcherState(chatId: Long, onSwitch: ((ChatSummary) -> Unit)?): ChatSwitcherState {
    val switcher = koinInject<ChatSwitcher>()
    // Konverzace na obrazovce je „naposledy otevřená" — i když se do ní vrátilo zpět zezhora.
    LaunchedEffect(chatId) { switcher.opened(chatId) }

    val enabled = onSwitch != null
    val candidates = remember(chatId, enabled) {
        if (enabled) switcher.candidates(chatId) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val unread = remember(chatId, enabled) {
        if (enabled) switcher.unreadElsewhere(chatId) else flowOf(0)
    }.collectAsStateWithLifecycle(0)
    val currentOnSwitch = rememberUpdatedState(onSwitch)
    return remember(chatId) { ChatSwitcherState(candidates, unread, currentOnSwitch) }
}

/**
 * Šipka zpět v hlavičce konverzace, která po podržení nabídne rychlý přepínač chatů
 * ([ChatSwitcherState]). Ťuknutí je dál obyčejné „zpět".
 *
 * - **Podržení se pozná z interakcí tlačítka, ne vlastním gestem.** `IconButton` dlouhý stisk
 *   neumí a obalit ho detektorem gest by mu vzalo stisk — a s ním změnu tvaru, kterou mají
 *   všechna tlačítka v aplikaci. Tlačítko tak dál hlásí `Press` / `Release` a podržení je jen
 *   stisk, který trval déle než `longPressTimeoutMillis`; puštění po něm už „zpět" neudělá.
 * - **Haptiku tu volá aplikace sama** (`longPress()`), protože gesto detekuje sama — vestavěná
 *   ji dělá jen `combinedClickable`.
 * - **Čtečce obrazovky** nese podržení `onLongClick` se jménem akce („Přepnout chat").
 * - **Odznak nese počet nepřečtených chatů jinde.** Podržení samo vidět není; odznak je trvalý
 *   znak, že za šipkou něco čeká, a ukáže se právě tehdy, kdy se přepnout vyplatí. Když je seznam
 *   vidět vedle konverzace (list-detail), odznak by jen opakoval jeho odznaky.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatSwitcherBackButton(
    state: ChatSwitcherState,
    onBack: () -> Unit,
) {
    // Puštění po podržení je konec gesta, ne ťuknutí — `onClick` ho musí přejít.
    var heldDown by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val canSwitch = state.canSwitch

    LaunchedEffect(interactions, canSwitch) {
        if (!canSwitch) return@LaunchedEffect
        // `collectLatest`: puštění nebo zrušení stisku čekání přeruší.
        interactions.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                heldDown = false
                delay(longPressTimeout)
                heldDown = true
                haptics.longPress()
                state.open()
            }
        }
    }

    val unread = state.unreadElsewhere.takeIf { !LocalPaneNeighbours.current.listBeside } ?: 0
    val switchLabel = stringResource(Res.string.conversation_switch_chat)
    val backLabel = if (unread > 0) {
        pluralStringResource(Res.plurals.conversation_back_unread, unread, unread)
    } else {
        stringResource(Res.string.common_back)
    }
    Box {
        IconButton(
            onClick = { if (heldDown) heldDown = false else onBack() },
            shapes = IconButtonDefaults.shapes(),
            interactionSource = interactions,
            modifier = if (canSwitch) {
                Modifier
                    .semantics { onLongClick(label = switchLabel) { state.open(); true } }
                    // Pravé tlačítko myši = podržení; tlačítko samo bere jen levé.
                    .onSecondaryClick { state.open() }
            } else {
                Modifier
            },
        ) {
            BadgedBox(
                badge = {
                    if (unread > 0) {
                        // Barvy jako odznak nepřečtených v seznamu a v nabídce pod šipkou: je to
                        // týž počet. Výchozí chybová barva by vedle nich hlásila poruchu.
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            RollingNumber(unread, format = { it.grouped() }, style = LocalTextStyle.current.tabularNumbers())
                        }
                    }
                },
            ) {
                Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = backLabel)
            }
        }

        DropdownMenuPopup(
            modifier = Modifier.menuScroll(),
            expanded = state.expanded && canSwitch,
            onDismissRequest = state::dismiss,
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below),
            properties = MenuPopupProperties,
        ) {
            MenuColors {
                val candidates = state.candidates
                DropdownMenuGroup(shapes = menuGroupShape(0, 1)) {
                    candidates.forEachIndexed { index, chat ->
                        SwitcherItem(
                            chat = chat,
                            index = index,
                            count = candidates.size,
                            onClick = { state.switchTo(chat) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitcherItem(chat: ChatSummary, index: Int, count: Int, onClick: () -> Unit) {
    val saved = isSavedMessages(chat.id)
    DropdownMenuItem(
        onClick = onClick,
        text = {
            Text(
                chatTitle(chat.id, chat.title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        },
        shape = MenuDefaults.itemShape(index, count).shape,
        leadingIcon = {
            Avatar(
                chatId = chat.avatarColorId,
                title = chat.title,
                avatarFileId = chat.avatarFileId,
                size = 32.dp,
                isSavedMessages = saved,
                shape = chat.kind.avatarShape(),
                backdrop = MenuDefaults.groupStandardContainerColor,
            )
        },
        trailingContent = if (chat.unreadCount > 0) {
            {
                // Ztlumený chat neutrálně, jako v seznamu chatů — počet tam je, ale nevolá.
                val colors = MaterialTheme.colorScheme
                Badge(
                    containerColor = if (chat.isMuted) colors.outlineVariant else colors.primary,
                    contentColor = if (chat.isMuted) colors.onSurfaceVariant else colors.onPrimary,
                ) {
                    RollingNumber(chat.unreadCount, style = LocalTextStyle.current.tabularNumbers())
                }
            }
        } else {
            null
        },
    )
}
