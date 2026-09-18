package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.photoFrame

/**
 * Zpráva s náhledem odkazu s velkým obrázkem (pravidlo: [showsBareLinkPreview]): **jedna
 * souvislá karta** — text zprávy, pod ním obrázek přes celou šířku a pod ním popis webu.
 *
 * Malý náhled zůstává kartou v bublině ([BubbleMessageItem]); o velikosti rozhoduje odesílatel.
 *
 * - **Bez spár.** Text, obrázek a popis jsou jedna věc — sdílený odkaz. Dřív to byly tři kusy
 *   se spárami a staženými nárožími (jazyk fotky s popiskem), a vypadalo to jako tři zprávy.
 *   Tvar bubliny nese celá karta, části uvnitř jsou rovné.
 * - **Text zprávy má barvu bubliny.** Je to zpráva sama a odchozí zprávy se od příchozích liší
 *   právě barvou (`primary`) — v jiné roli by se strany rozlišovaly jen zarovnáním.
 * - **Popis webu je `tertiaryContainer`**, na obou stranách. Je to cizí vložený obsah, takže se
 *   od textu zprávy musí lišit; terciární role je v M3 na doplňkové prvky, které vyvažují primary,
 *   a nesplyne s žádnou z barev bublin.
 * - **Šířku určuje obrázek** ([photoFrame]): text i popis se na ni natáhnou, takže karta nemá
 *   roztřepený okraj.
 * - **Barvu z obrázku nevytahujeme.** TDLib u náhledu žádnou barvu neposílá a kvantizace z bitmapy
 *   by znamenala keš na `fileId` a počítání mimo hlavní vlákno.
 */
@Immutable
internal data class LinkPreviewMessageItem(
    override val frame: MessageFrameData,
    val preview: LinkPreview,
) : MessageItem {

    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun MessageBodyScope.Body() {
        val photo = preview.photo ?: return
        val hasText = message.text.isNotBlank() || message.isEdited
        val hasInfo = preview.siteName != null || preview.title != null || preview.description != null
        val open = { act(MessageAction.OpenLink(preview.url)) }

        Column(
            // Karta se srovnává ke straně odesílatele, stejně jako holá fotka s popiskem.
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            // Karta jde až k hraně řádku jako ostatní bubliny, takže jméno si odsazení nese samo.
            SenderName(Modifier.padding(bottom = 2.dp))

            BoxWithConstraints {
                val frameSize = photoFrame(maxWidth, photo.width.toFloat() / photo.height.toFloat())

                Column(Modifier.width(frameSize.width).clip(shape)) {
                    if (hasText) {
                        Surface(color = bubbleColor, contentColor = onBubbleColor, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                if (message.text.isNotBlank()) MessageText(linkColor = accent)
                                EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }

                    RemoteImage(
                        fileId = photo.fileId,
                        thumbnailFileId = photo.thumbnailFileId,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(frameSize)
                            // Ťuknutí otevře odkaz; dlouhý stisk se opakuje, jinak by ho obrázek
                            // spolkl a nad zprávou by nešla otevřít nabídka.
                            .combinedClickable(onClick = open, onLongClick = ::requestMenu),
                    )

                    if (hasInfo) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = open, onLongClick = ::requestMenu),
                        ) {
                            PreviewInfo(preview, bottom = contentBottom)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Web, titulek a popis. Název webu nese akcent té plochy, na které leží (`tertiary` na
 * `tertiaryContainer`), titulek a popis její barvu popředí.
 *
 * [bottom] je místo na čipy reakcí — leží přes dolní nároží, tedy na popisu.
 */
@Composable
private fun PreviewInfo(preview: LinkPreview, bottom: Dp) {
    Column(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        preview.siteName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        preview.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        preview.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = LINK_PREVIEW_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
