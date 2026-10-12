package eu.tintera.plume.features.conversation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
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
import eu.tintera.plume.resources.conversation_switch_chat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Šipka zpět v hlavičce konverzace, která po podržení nabídne rychlý přepínač chatů
 * ([ChatSwitcher]). Ťuknutí je dál obyčejné „zpět".
 *
 * - **Podržení se pozná z interakcí tlačítka, ne vlastním gestem.** `IconButton` dlouhý stisk
 *   neumí a obalit ho detektorem gest by mu vzalo stisk — a s ním změnu tvaru, kterou mají
 *   všechna tlačítka v aplikaci. Tlačítko tak dál hlásí `Press` / `Release` a podržení je jen
 *   stisk, který trval déle než `longPressTimeoutMillis`; puštění po něm už „zpět" neudělá.
 * - **Haptiku tu volá aplikace sama** (`longPress()`), protože gesto detekuje sama — vestavěná
 *   ji dělá jen `combinedClickable`.
 * - **Čtečce obrazovky** nese podržení `onLongClick` se jménem akce („Přepnout chat").
 * - Bez chatů, na které přepnout (čerstvé přihlášení), podržení nic nedělá.
 *
 * [onSwitch] `null` = přepínač se nenabízí (bublina konverzace), šipka je jen šipka.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatSwitcherBackButton(
    chatId: Long,
    onBack: () -> Unit,
    onSwitch: ((ChatSummary) -> Unit)?,
) {
    val switcher = koinInject<ChatSwitcher>()
    // Konverzace na obrazovce je „naposledy otevřená" — i když se do ní vrátilo zpět zezhora.
    LaunchedEffect(chatId) { switcher.opened(chatId) }

    val candidates by remember(chatId, onSwitch != null) {
        if (onSwitch != null) switcher.candidates(chatId) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    var expanded by remember { mutableStateOf(false) }
    // Puštění po podržení je konec gesta, ne ťuknutí — `onClick` ho musí přejít.
    var heldDown by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val canSwitch = onSwitch != null && candidates.isNotEmpty()

    LaunchedEffect(interactions, canSwitch) {
        if (!canSwitch) return@LaunchedEffect
        // `collectLatest`: puštění nebo zrušení stisku čekání přeruší.
        interactions.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                heldDown = false
                delay(longPressTimeout)
                heldDown = true
                haptics.longPress()
                expanded = true
            }
        }
    }

    val switchLabel = stringResource(Res.string.conversation_switch_chat)
    Box {
        IconButton(
            onClick = { if (heldDown) heldDown = false else onBack() },
            shapes = IconButtonDefaults.shapes(),
            interactionSource = interactions,
            modifier = if (canSwitch) {
                Modifier.semantics { onLongClick(label = switchLabel) { expanded = true; true } }
            } else {
                Modifier
            },
        ) {
            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
        }

        DropdownMenuPopup(
            expanded = expanded && canSwitch,
            onDismissRequest = { expanded = false },
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below),
            properties = MenuPopupProperties,
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                candidates.forEachIndexed { index, chat ->
                    SwitcherItem(
                        chat = chat,
                        index = index,
                        count = candidates.size,
                        onClick = {
                            expanded = false
                            onSwitch?.invoke(chat)
                        },
                    )
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
        trailingIcon = if (chat.unreadCount > 0) {
            {
                // Ztlumený chat neutrálně, jako v seznamu chatů — počet tam je, ale nevolá.
                val colors = MaterialTheme.colorScheme
                Badge(
                    containerColor = if (chat.isMuted) colors.surfaceContainerHighest else colors.primary,
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
