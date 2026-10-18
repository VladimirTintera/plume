package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.edit_chat_sticker_set
import eu.tintera.plume.resources.edit_chat_sticker_set_empty
import eu.tintera.plume.resources.edit_chat_sticker_set_explanation
import eu.tintera.plume.resources.edit_chat_sticker_set_none
import eu.tintera.plume.resources.stickers_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr sady samolepek skupiny: „Žádná sada" a nainstalované sady uživatele. Tvarem jako výběr
 * diskusní skupiny ([DiscussionSheet]) — kolečko u vybrané volby, řádek nese klikání celý.
 *
 * **Bez potvrzení.** Na rozdíl od diskuse nic nezveřejní: sada je jen nabídka ve výběru samolepek
 * členů a jde vrátit stejnou cestou.
 *
 * Nabízí se jen sady, které má uživatel přidané — jako u oficiálních klientů. Cizí sadu si
 * napřed přidá (z detailu sady nebo hledání) a pak ji tu vybere.
 */
@Composable
internal fun GroupStickerSetSheet(
    current: StickerSetSummary?,
    candidates: List<StickerSetSummary>?,
    onSelect: (StickerSetSummary?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.edit_chat_sticker_set),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(Res.string.edit_chat_sticker_set_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (candidates == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@ModalBottomSheet
        }
        // Současná sada mezi nainstalovanými být nemusí (nastavil ji jiný správce) — ukázat ji jde vždycky.
        val sets = (listOfNotNull(current) + candidates).distinctBy { it.id }
        Column(
            Modifier
                .selectableGroup()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            StickerSetChoice(
                selected = current == null,
                set = null,
                onClick = { if (current != null) onSelect(null) },
            )
            sets.forEach { set ->
                StickerSetChoice(
                    selected = set.id == current?.id,
                    set = set,
                    onClick = { if (set.id != current?.id) onSelect(set) },
                )
            }
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(Res.string.edit_chat_sticker_set_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StickerSetChoice(selected: Boolean, set: StickerSetSummary?, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = {
            Text(
                set?.title ?: stringResource(Res.string.edit_chat_sticker_set_none),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = set?.takeIf { it.size > 0 }?.let {
            { Text(pluralStringResource(Res.plurals.stickers_count, it.size, it.size)) }
        },
        // Obálka nehraje: v seznamu sad by se hýbalo všechno naráz (jako záložky výběru).
        trailingContent = set?.cover?.let { cover ->
            { StickerPreview(cover, playing = false, modifier = Modifier.size(COVER_SIZE)) }
        },
        // Řádek nese klikání celý, kolečko má `onClick = null` — viz „Obrazovka nastavení".
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

private val COVER_SIZE = 40.dp
