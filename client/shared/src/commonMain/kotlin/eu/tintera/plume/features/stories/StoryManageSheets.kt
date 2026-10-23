package eu.tintera.plume.features.stories

import eu.tintera.plume.resources.story_select_people
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.story_audience_close_friends
import eu.tintera.plume.resources.story_audience_contacts
import eu.tintera.plume.resources.story_audience_everyone
import eu.tintera.plume.resources.story_audience_selected
import eu.tintera.plume.resources.story_audience_selected_count
import eu.tintera.plume.resources.story_audience_title
import eu.tintera.plume.resources.story_caption
import eu.tintera.plume.resources.story_caption_save
import eu.tintera.plume.resources.story_close_friends_none
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kdo zveřejněný příběh uvidí — tytéž volby jako u nového příběhu.
 *
 * - **Blízcí přátelé jdou vybrat, jen když na seznamu někdo je** ([StoryAudienceSheet.closeFriends]);
 *   s prázdným seznamem by příběh neviděl nikdo. Seznam je nastavení účtu a upravuje se u nového
 *   příběhu.
 * - **„Vybraní lidé" otevře výběr lidí** ([onPickPeople]) — přes celou plochu místo příběhu,
 *   stejný jako u nového příběhu. Současný výběr je v něm předvybraný.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryAudiencePickerSheet(
    sheet: StoryAudienceSheet,
    onChoose: (StoryAudience) -> Unit,
    onDismiss: () -> Unit,
    onPickPeople: () -> Unit,
) {
    val selected = sheet.audience as? StoryAudience.Selected
    val friends = sheet.closeFriends
    val options = buildList {
        add(Triple(StoryAudience.Everyone, stringResource(Res.string.story_audience_everyone), null))
        add(Triple(StoryAudience.Contacts, stringResource(Res.string.story_audience_contacts), null))
        if (friends == null || friends > 0 || sheet.audience == StoryAudience.CloseFriends) {
            add(
                Triple(
                    StoryAudience.CloseFriends,
                    stringResource(Res.string.story_audience_close_friends),
                    friends?.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.story_audience_selected_count, it, it) },
                ),
            )
        }
        add(
            Triple(
                selected ?: StoryAudience.Selected(emptyList()),
                stringResource(Res.string.story_audience_selected),
                selected?.let { pluralStringResource(Res.plurals.story_audience_selected_count, it.userIds.size, it.userIds.size) }
                    ?: stringResource(Res.string.story_select_people),
            ),
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(stringResource(Res.string.story_audience_title), modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, (audience, label, supporting) ->
                    ChoiceListItem(
                        label = label,
                        supporting = supporting,
                        selected = sheet.audience == audience,
                        index = index,
                        count = options.size,
                        // Vybraní lidé vždycky přes výběr: ťuknutí na vybranou volbu výběr upraví.
                        onClick = { if (audience is StoryAudience.Selected) onPickPeople() else onChoose(audience) },
                    )
                }
            }
            if (friends == 0 && sheet.audience != StoryAudience.CloseFriends) {
                Text(
                    stringResource(Res.string.story_close_friends_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Nový popisek zveřejněného příběhu; prázdný popisek odebere. */
@Composable
internal fun StoryCaptionDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var caption by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.story_caption)) },
        text = {
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                maxLines = CAPTION_MAX_LINES,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(caption) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.story_caption_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

private const val CAPTION_MAX_LINES = 6
