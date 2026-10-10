package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.PhotoMarkup

/**
 * Připravená příloha přes celou obrazovku ([StagedAttachmentPreview]) a nad ní úprava fotky
 * ([PhotoMarkupEditor]).
 *
 * **Vlastní funkce, ne kus `ConversationScreen`**: ta je v dexu na hraně počtu registrů (viz
 * „`ConversationScreen` je v dexu na hraně" v CLAUDE.md) a editor by ji přes ni přetlačil.
 *
 * [index] je pořadí přílohy nad polem, `null` = náhled zavřený. Při zavírání se dokresluje
 * příloha, která byla otevřená naposledy — jinak by náhled při mizení zmizel naráz.
 */
@Composable
internal fun StagedAttachmentPreviewHost(
    state: ConversationState,
    viewModel: ConversationViewModel,
    index: Int?,
    onIndexChange: (Int?) -> Unit,
) {
    val lastPreviewed = remember { arrayOf<Pair<Int, OutgoingAttachment>?>(null) }
    if (index != null) {
        state.attachments.getOrNull(index)?.let { lastPreviewed[0] = index to it }
    }
    val currentTarget = if (index != null) {
        state.attachments.getOrNull(index)?.let { index to it }
    } else {
        lastPreviewed[0]
    }

    // Upravovaná fotka podle cesty; přežije otočení, tahy rozpracované v editoru ne.
    var markupPath by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedVisibility(
        visible = index != null && currentTarget != null,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        currentTarget?.let { (shownIndex, attachment) ->
            StagedAttachmentPreview(
                attachment = attachment,
                isOpen = index != null,
                onDismiss = { onIndexChange(null) },
                onDelete = {
                    viewModel.removeAttachment(shownIndex)
                    onIndexChange(null)
                },
                onSetMode = { mode -> viewModel.setAttachmentMode(shownIndex, mode) },
                selfDestructAllowed = state.canSelfDestruct(),
                // Kreslit jde jen do fotky: video by se muselo překódovat.
                onEdit = if (attachment is OutgoingAttachment.Photo && viewModel.canMarkupPhotos) {
                    { markupPath = attachment.path }
                } else {
                    null
                },
                busy = state.markupBusy,
            )
        }
    }

    val editing = markupPath?.let { path ->
        state.attachments.firstOrNull { it.path == path } as? OutgoingAttachment.Photo
    }
    AnimatedVisibility(
        visible = editing != null,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        // Při mizení se dokresluje poslední stav; příloha mezitím dostala novou cestu.
        val shown = remember { arrayOf<OutgoingAttachment.Photo?>(null) }
        editing?.let { shown[0] = it }
        shown[0]?.let { photo ->
            val edit = state.photoEdits[photo.path]
            PhotoMarkupEditor(
                // Upravuje se vždycky originál: cokoli z minulé úpravy jde vzít zpátky.
                path = edit?.originalPath ?: photo.path,
                width = edit?.originalWidth ?: photo.width,
                height = edit?.originalHeight ?: photo.height,
                initial = edit?.markup ?: PhotoMarkup(),
                onCancel = { markupPath = null },
                onDone = { markup ->
                    viewModel.applyPhotoMarkup(photo.path, markup)
                    markupPath = null
                },
            )
        }
    }
}
