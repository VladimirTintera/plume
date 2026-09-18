package eu.tintera.plume.features.conversation.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.LinkPreview

/**
 * Zpráva s náhledem odkazu s velkým obrázkem, jako skupina na ploše: text v kontejneru, obrázek
 * a popis webu pod ním (pravidlo: [showsBareLinkPreview], kreslení: [BareLinkPreview]).
 *
 * Obrázek náhledu je obrázek jako každý jiný, takže jde ven z bubliny ze stejného důvodu jako
 * fotka. Malý náhled zůstává kartou v bublině ([BubbleMessageItem]).
 */
@Immutable
internal data class LinkPreviewMessageItem(
    override val frame: MessageFrameData,
    val preview: LinkPreview,
) : MessageItem {

    @Composable
    override fun MessageBodyScope.Body() {
        PaddedColumn(
            // Skupina se srovnává ke straně odesílatele, stejně jako holá fotka s popiskem.
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            SenderName()
            BareLinkPreview(
                preview = preview,
                containerColor = bubbleColor,
                contentColor = onBubbleColor,
                accent = accent,
                bubbleShape = shape,
                contentBottom = contentBottom,
                // Ťuknutí jde stejnou cestou jako odkaz v textu, tedy přes ViewModel.
                onOpen = { act(MessageAction.OpenLink(preview.url)) },
                onLongClick = ::requestMenu,
            ) {
                if (message.text.isNotBlank()) MessageText(linkColor = accent)
                EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
