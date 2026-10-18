package eu.tintera.plume.features.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_members_confirm_count
import eu.tintera.plume.resources.add_members_bot_admin_confirm
import eu.tintera.plume.resources.add_members_bot_admin_text
import eu.tintera.plume.resources.add_members_bot_admin_title
import eu.tintera.plume.resources.add_members_refused_invite
import eu.tintera.plume.resources.add_members_refused_text
import eu.tintera.plume.resources.add_members_refused_text_no_link
import eu.tintera.plume.resources.add_members_refused_title
import eu.tintera.plume.resources.add_members_search
import eu.tintera.plume.resources.add_members_title
import eu.tintera.plume.resources.add_subscribers_title
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_empty
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.search_off_24dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr lidí, které přidat do skupiny nebo kanálu: tentýž [ChatPicker] jako kontakty
 * a přeposílání, víc lidí naráz a přidává se tlačítkem s počtem.
 */
@Composable
fun AddMembersScreen(
    viewModel: AddMembersViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blankQuery = state.query.isBlank()
    ChatPicker(
        title = stringResource(if (state.isChannel) Res.string.add_subscribers_title else Res.string.add_members_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.add_members_search),
        loading = state.loading && state.contacts.isEmpty(),
        sections = listOf(
            PickerSection(key = "contacts", chats = state.contacts, title = stringResource(Res.string.new_chat_contacts)),
            PickerSection(key = "others", chats = state.others, title = stringResource(Res.string.chats_search_section_chats)),
        ),
        enabled = !state.adding,
        onPick = viewModel::toggle,
        selected = state.selected,
        onConfirm = { viewModel.add(onDone) },
        confirmLabel = Res.string.add_members_confirm_count,
        confirmIcon = Res.drawable.person_add_24dp,
        showPresence = true,
        onBack = onBack,
        emptyIcon = if (blankQuery) Res.drawable.person_24dp else Res.drawable.search_off_24dp,
        emptyTitle = if (blankQuery) stringResource(Res.string.new_chat_empty) else null,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )

    // Boti do kanálu jen jako správci — zeptat se dřív, než se ukáže, koho přidat nešlo.
    if (state.pendingBots.isNotEmpty()) {
        val names = state.pendingBots.joinToString(", ") { it.title }
        AlertDialog(
            onDismissRequest = { if (!state.adding) viewModel.skipBots(onDone) },
            title = { Text(stringResource(Res.string.add_members_bot_admin_title)) },
            text = { Text(stringResource(Res.string.add_members_bot_admin_text, names)) },
            confirmButton = {
                TextButton(onClick = { viewModel.addBotsAsAdmins(onDone) }, enabled = !state.adding, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.add_members_bot_admin_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipBots(onDone) }, enabled = !state.adding, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    } else if (state.refused.isNotEmpty()) {
        val names = state.refused.joinToString(", ") { it.title }
        val link = state.inviteLink
        AlertDialog(
            // Přidaní ostatních už proběhlo, takže zavření dialogu = hotovo.
            onDismissRequest = onDone,
            title = { Text(pluralStringResource(Res.plurals.add_members_refused_title, state.refused.size, state.refused.size)) },
            text = {
                Text(
                    if (link != null) {
                        stringResource(Res.string.add_members_refused_text, names)
                    } else {
                        stringResource(Res.string.add_members_refused_text_no_link, names)
                    },
                )
            },
            confirmButton = {
                if (link != null) {
                    TextButton(onClick = { viewModel.sendInvites(onDone) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.add_members_refused_invite))
                    }
                } else {
                    TextButton(onClick = onDone, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_close)) }
                }
            },
            dismissButton = if (link != null) {
                { TextButton(onClick = onDone, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_close)) } }
            } else {
                null
            },
        )
    }
}
