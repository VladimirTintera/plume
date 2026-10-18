package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.RecommendedChatFolder
import eu.tintera.plume.core.ui.FolderIcon
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.chat_folders_add
import eu.tintera.plume.resources.chat_folders_add_folder
import eu.tintera.plume.resources.chat_folders_create
import eu.tintera.plume.resources.chat_folders_manage
import eu.tintera.plume.resources.chat_folders_suggestions_help
import eu.tintera.plume.resources.tune_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Stav sheetu za „+" v řádku složek ([ChatsViewModel.folderSuggestions]). [adding] je název
 * doporučené složky, která se právě zakládá — jen jedna naráz, ostatní tlačítka zašednou.
 */
data class FolderSuggestions(
    val loading: Boolean = true,
    val recommended: List<RecommendedChatFolder> = emptyList(),
    val adding: String? = null,
)

/**
 * Sheet za „+" na konci řádku složek: doporučené složky od Telegramu (Nepřečtené, Osobní…)
 * na jedno ťuknutí, pod nimi nová složka a správa složek.
 *
 * - **Nahradil filtry v plovoucím toolbaru.** Ty dělaly totéž co složky, jen lokálně a jinou
 *   cestou; dvě úrovně zúžení seznamu nad sebou mátly. Doporučené složky jsou přesně ty filtry,
 *   jen jako skutečná složka Telegramu.
 * - **Věta pod nadpisem říká, že se složka synchronizuje.** Přidaná složka se objeví i v ostatních
 *   klientech — u filtru, který dřív žil jen v Plume, by to nikdo nečekal.
 * - **Doporučené posílá server a nemusí poslat žádné** (při prvním dotazu na emulátoru přišlo
 *   prázdno). Pak zbude nová složka a správa, takže sheet slepá ulička není.
 * - **Celý rozbalený**, jako ostatní sheety s akcemi: napůl rozbalený by schoval správu pod okraj.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderSuggestionsSheet(
    state: FolderSuggestions,
    onAdd: (RecommendedChatFolder) -> Unit,
    onCreate: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                stringResource(Res.string.chat_folders_add_folder),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                stringResource(Res.string.chat_folders_suggestions_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = GROUP_SPACING),
            )

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            state.recommended.forEachIndexed { index, folder ->
                ListItem(
                    headlineContent = { Text(folder.draft.title) },
                    supportingContent = { Text(folder.description) },
                    leadingContent = { FolderIcon(folderIcon(folder.iconName), accent = false) },
                    trailingContent = {
                        if (state.adding == folder.draft.title) {
                            LoadingIndicator(Modifier.size(40.dp))
                        } else {
                            FilledTonalButton(onClick = { onAdd(folder) }, enabled = state.adding == null, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.chat_folders_add))
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.clip(groupItemShape(index, state.recommended.size)),
                )
            }

            // Mezera jako mezi skupinami; `spacedBy` přidá svou spáru z obou stran.
            if (state.loading || state.recommended.isNotEmpty()) {
                Spacer(Modifier.height(GROUP_SPACING - GROUP_ITEM_SPACING * 2))
            }
            ListItem(
                headlineContent = { Text(stringResource(Res.string.chat_folders_create)) },
                leadingContent = { FolderIcon(Res.drawable.add_24dp, accent = true) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.clip(groupItemShape(0, 2)).clickable(onClick = onCreate),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.chat_folders_manage)) },
                leadingContent = { FolderIcon(Res.drawable.tune_24dp, accent = false) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.clip(groupItemShape(1, 2)).clickable(onClick = onManage),
            )
        }
    }
}
