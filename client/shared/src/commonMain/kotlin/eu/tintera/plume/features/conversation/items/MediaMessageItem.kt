package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage

/**
 * Fotka, video nebo album přímo na ploše konverzace, bez bubliny (pravidlo: [showsBareMedia]).
 *
 * Bublina kolem snímku nemá co ohraničovat — je to obrázek sám o sobě. **Popisek ale na plochu
 * nepatří**: text bez pozadí by se ztratil, takže dostane vlastní kontejner v barvě bubliny.
 *
 * Snímek a popisek jsou SKUPINA, ne dva nezávislé kusy: nároží mezi nimi se stahuje a spára je
 * úzká, stejně jako u bloků v seznamu chatů. Bez toho by nebylo poznat, že popisek patří k tomu
 * snímku nad ním.
 */
@Immutable
internal data class MediaMessageItem(
    override val frame: MessageFrameData,
    /** Zprávy alba od nejstarší; u samostatné fotky nebo videa `null`. */
    val album: List<ChatMessage>?,
) : MessageItem {

    override fun containsMessage(messageId: Long): Boolean =
        frame.message.id == messageId || album?.any { it.id == messageId } == true

    @Composable
    override fun MessageBodyScope.Body() {
        val hasCaption = message.text.isNotBlank() || message.isEdited
        // Snímek leží na ploše, takže jeho akcent (průběh, ikony) patří k ní, ne k bublině.
        val surfaceAccent = if (message.isOutgoing) LocalContentColor.current else MaterialTheme.colorScheme.primary
        val tight = CornerSize(BUBBLE_TIGHT)
        // Zaoblení snímku přebírá tvar bubliny, aby navázal na sousedy v bloku i na popisek.
        val mediaShape = if (hasCaption) shape.copy(bottomStart = tight, bottomEnd = tight) else shape

        Column(
            /*
             * Snímek a popisek drží u sebe jen zarovnáním a každý je jinak široký. Musí se proto
             * srovnat ke STRANĚ ODESÍLATELE — jinak zůstane odchozí fotka u levého okraje bloku,
             * jakmile je popisek širší než ona.
             */
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            // Nad holým snímkem si jméno musí odsazení přinést samo.
            SenderName(Modifier.padding(bottom = 2.dp))

            if (album != null) {
                Album(members = album, accent = surfaceAccent, shape = mediaShape)
            } else {
                message.media?.let { Attachment(media = it, accent = surfaceAccent, mediaShape = mediaShape) }
            }

            if (hasCaption) {
                Surface(
                    color = bubbleColor,
                    contentColor = onBubbleColor,
                    shape = shape.copy(topStart = tight, topEnd = tight),
                    // Úzká spára, ne mezera: popisek patří ke snímku nad sebou.
                    modifier = Modifier.padding(top = MEDIA_CAPTION_GAP),
                ) {
                    PaddedColumn {
                        if (message.text.isNotBlank()) MessageText(linkColor = accent)
                        EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
    }
}
