package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.resources.conversation_contact_me
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.GROUP_CORNER
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_contact_confirm_body
import eu.tintera.plume.resources.conversation_contact_confirm_title
import eu.tintera.plume.resources.conversation_contact_none
import eu.tintera.plume.resources.conversation_contact_search
import eu.tintera.plume.resources.conversation_contact_send
import eu.tintera.plume.resources.conversation_contact_title
import eu.tintera.plume.resources.conversation_search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * Sponka → Kontakt: kontakty účtu s hledáním podle jména. Ťuknutí kontakt vybere, odeslání ale
 * potvrdí až [ContactConfirmDialog] — odchází cizí telefonní číslo.
 *
 * Sheet, ne obrazovka, jako výběr místa: kontaktů bývají desítky a pole hledání nad nimi stačí.
 * Pevná výška seznamu, aby se sheet při psaní nenatahoval a nesmršťoval.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactPickerSheet(
    picker: ContactPicker,
    onQueryChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // Jen celý: napůl rozbalený sheet schoval seznam pod spodní okraj (viz „Výběr samolepek").
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTitle(stringResource(Res.string.conversation_contact_title), modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        SearchPill(
            initialQuery = picker.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(Res.string.conversation_contact_search),
            clearDescription = stringResource(Res.string.conversation_search_clear),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
            val shown = picker.shown
            when {
                picker.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                shown.isEmpty() && !picker.showsMe -> Text(
                    text = stringResource(Res.string.conversation_contact_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                ) {
                    // Vlastní číslo je nejčastější, co se posílá — samostatná karta nad
                    // kontakty, jako Uložené zprávy ve výběru chatu, ne první řádek bloku.
                    picker.me?.takeIf { picker.showsMe }?.let { me ->
                        item(key = ME_KEY) {
                            val onCard = MaterialTheme.colorScheme.onPrimaryContainer
                            ListItem(
                                headlineContent = { Text(stringResource(Res.string.conversation_contact_me)) },
                                supportingContent = { Text(me.phone) },
                                leadingContent = {
                                    Avatar(
                                        chatId = me.userId ?: 0L,
                                        title = me.name,
                                        avatarFileId = me.photoFileId,
                                        size = 40.dp,
                                        backdrop = MaterialTheme.colorScheme.primaryContainer,
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = onCard,
                                    supportingContentColor = onCard,
                                ),
                                modifier = Modifier
                                    .animateItem()
                                    .padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING)
                                    .clip(RoundedCornerShape(GROUP_CORNER))
                                    .clickable { me.userId?.let(onPick) },
                            )
                        }
                    }
                    itemsIndexed(shown, key = { _, contact -> contact.id }) { index, contact ->
                        ListItem(
                            headlineContent = { Text(contact.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Avatar(chatId = contact.id, title = contact.title, avatarFileId = contact.avatarFileId, size = 40.dp)
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            modifier = Modifier
                                .animateItem()
                                .clip(groupItemShape(index, shown.size))
                                // V soukromém chatu je id chatu id uživatele.
                                .clickable { onPick(contact.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Potvrzení kontaktu: jméno a číslo, přesně jak odejdou. Bez něj by ťuknutí vedle v seznamu
 * poslalo do chatu číslo někoho jiného.
 */
@Composable
internal fun ContactConfirmDialog(contact: MessageMedia.Contact, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.conversation_contact_confirm_title),
        text = stringResource(Res.string.conversation_contact_confirm_body, contact.name, contact.phone),
        confirmLabel = stringResource(Res.string.conversation_contact_send),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        iconContent = { Avatar(chatId = contact.userId ?: 0L, title = contact.name, avatarFileId = contact.photoFileId, size = 48.dp) },
    )
}

private val LIST_HEIGHT = 420.dp

/** Klíč karty vlastního kontaktu; id chatů jsou nenulová, záporný klíč se s nimi nesrazí. */
private const val ME_KEY = -1L
