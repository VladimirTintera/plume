package eu.tintera.plume.features.forward

import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSelection
import eu.tintera.plume.core.ui.PickerError
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_share_chat_admin
import eu.tintera.plume.resources.bot_share_chat_info
import eu.tintera.plume.resources.bot_share_chat_member
import eu.tintera.plume.resources.bot_share_channel_info
import eu.tintera.plume.resources.bot_share_confirm
import eu.tintera.plume.resources.bot_share_confirm_count
import eu.tintera.plume.resources.bot_share_confirm_title
import eu.tintera.plume.resources.bot_share_pick_channel
import eu.tintera.plume.resources.bot_share_pick_group
import eu.tintera.plume.resources.bot_share_pick_users
import eu.tintera.plume.resources.bot_share_users_info
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.send_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr pro tlačítko bota „vyber lidi" / „vyber chat": tentýž [ChatPicker] jako přeposílání
 * a sdílení kontaktu. Nahoře karta s tím, co bot dostane — o cizích lidech a o chatu, takže to
 * musí být řečeno předem, ne až po odeslání.
 */
@Composable
fun BotShareScreen(
    viewModel: BotShareViewModel,
    users: BotUsersRequest?,
    chat: BotChatRequest?,
    onBack: () -> Unit,
    onShared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = when {
        users != null -> stringResource(Res.string.bot_share_pick_users)
        chat?.channel == true -> stringResource(Res.string.bot_share_pick_channel)
        else -> stringResource(Res.string.bot_share_pick_group)
    }

    ChatPicker(
        title = title,
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.forward_search),
        ),
        loading = state.loading,
        sections = listOf(PickerSection(key = "candidates", chats = state.chats)),
        enabled = !state.busy,
        onPick = viewModel::pick,
        // Lidé se zaškrtávají, chat se potvrzuje dialogem.
        selection = PickerSelection(
            selected = state.selected,
            onConfirm = { viewModel.share(onShared) },
            confirmLabel = Res.string.bot_share_confirm_count,
            confirmIcon = Res.drawable.send_24dp,
        ).takeIf { users != null },
        showPresence = users != null,
        onBack = onBack,
        error = state.error?.let { PickerError(it, viewModel::consumeError) },
        header = { BotShareCard(users, chat) },
        modifier = modifier,
    )

    val pending = state.pendingChat
    if (pending != null && chat != null) {
        ConfirmDialog(
            title = stringResource(Res.string.bot_share_confirm_title, pending.title),
            text = chatConsequences(chat),
            confirmLabel = stringResource(Res.string.bot_share_confirm),
            onConfirm = { viewModel.share(onShared) },
            onDismiss = viewModel::dismissChat,
            icon = Res.drawable.share_24dp,
        )
    }
}

/** Co bot dostane, v roli cizího vloženého obsahu (`tertiaryContainer`), jako u sdílení kontaktu. */
@Composable
private fun BotShareCard(users: BotUsersRequest?, chat: BotChatRequest?) {
    val text = when {
        users != null -> stringResource(Res.string.bot_share_users_info)
        chat != null -> chatConsequences(chat)
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

/**
 * Co bot uvidí a co se s chatem stane. Že se bot do chatu PŘIDÁ a dostane práva správce, je
 * následek, který uživatel z tlačítka „vyber skupinu" nečeká — proto výslovně.
 *
 * **Název, uživatelské jméno a fotku věta zmiňuje vždycky**: jestli o ně bot žádá, klient
 * nezjistí (viz [BotUsersRequest]), takže se říká, co bot dostat MŮŽE.
 */
@Composable
private fun chatConsequences(chat: BotChatRequest): String = listOfNotNull(
    stringResource(if (chat.channel) Res.string.bot_share_channel_info else Res.string.bot_share_chat_info),
    stringResource(Res.string.bot_share_chat_member).takeIf { chat.botIsMember },
    stringResource(Res.string.bot_share_chat_admin).takeIf { chat.botAdministrator },
).joinToString(" ")
