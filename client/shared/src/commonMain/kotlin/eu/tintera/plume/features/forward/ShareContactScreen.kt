package eu.tintera.plume.features.forward

import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.share_contact_send
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSelection
import eu.tintera.plume.core.ui.PickerError
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.share_contact_hidden_phone
import eu.tintera.plume.resources.share_contact_title
import org.jetbrains.compose.resources.stringResource

/**
 * Komu poslat kontakt z profilu: tentýž [ChatPicker] jako přeposílání, víc cílů naráz. Nahoře
 * karta s tím, co odejde — jméno a číslo; kontakt, který číslo skrývá, poslat nejde a karta to
 * řekne (výběr je pak neaktivní).
 */
@Composable
fun ShareContactScreen(
    viewModel: ShareContactViewModel,
    onBack: () -> Unit,
    onSent: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val card = state.card
    val sendable = card != null && card.phone.isNotBlank()

    ChatPicker(
        title = stringResource(Res.string.share_contact_title),
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.forward_search),
        ),
        loading = state.loading,
        sections = listOf(PickerSection(key = "chats", chats = state.chats)),
        enabled = sendable && !state.sending,
        onPick = viewModel::toggle,
        selection = PickerSelection(
            selected = state.selected,
            onConfirm = { viewModel.send(onSent) },
            // Kontakt se neposílá dál, posílá se — „Přeposlat" by lhalo o tom, co se stane.
            confirmLabel = Res.string.share_contact_send,
            confirmIcon = Res.drawable.send_24dp,
        ),
        savedMessagesChatId = state.savedMessagesChatId,
        onBack = onBack,
        error = state.error?.let { PickerError(it, viewModel::consumeError) },
        header = { card?.let { ContactCard(it) } },
        modifier = modifier,
    )
}

/** Co odejde, v roli cizího vloženého obsahu (`tertiaryContainer`), jako karta při sdílení. */
@Composable
private fun ContactCard(contact: MessageMedia.Contact) {
    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(chatId = contact.userId ?: 0L, title = contact.name, avatarFileId = contact.photoFileId, size = 40.dp)
            Column {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    contact.phone.ifBlank { stringResource(Res.string.share_contact_hidden_phone) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
