package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.features.conversation.showsLargePhoto
import eu.tintera.plume.core.telegram.MessageMedia
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.CAPTIONED_MIN_WIDTH_FRACTION
import eu.tintera.plume.features.conversation.photoFrame

/**
 * Zpráva s náhledem odkazu (pravidlo: [showsLinkPreviewCard]): **jedna karta ze dvou částí** —
 * nahoře text zprávy v barvě bubliny, dole popis webu v tónované ploše. S velkým obrázkem stojí
 * obrázek mezi nimi přes celou šířku, malý obrázek je čtvereček vpravo v popisu.
 *
 * - **Dvě plochy, ne proužek.** Dřív byl malý náhled uvnitř bubliny oddělený proužkem akcentu
 *   vlevo, převzatým z citace — jazyk M2. Odlišná plocha dělá totéž bez čáry navíc a karta
 *   vypadá stejně s velkým obrázkem i bez něj.
 * - **Bez spár.** Text, obrázek a popis jsou jedna věc — sdílený odkaz. Tvar bubliny nese celá
 *   karta (`clip(shape)`), části uvnitř jsou rovné.
 * - **Text zprávy má barvu bubliny.** Je to zpráva sama a odchozí zprávy se od příchozích liší
 *   právě barvou (`primary`) — v jiné roli by se strany rozlišovaly jen zarovnáním.
 * - **Popis webu je `tertiaryContainer`**, na obou stranách. Je to cizí vložený obsah, takže se
 *   od textu zprávy musí lišit; terciární role je v M3 na doplňkové prvky a nesplyne s žádnou
 *   z barev bublin. Výjimka je zvýrazněná příchozí zpráva: ta sama přejde do `tertiaryContainer`,
 *   takže popis ztmavne o kus k `tertiary`, jinak by obě části splynuly.
 * - **Šířka.** S velkým obrázkem ji určuje obrázek ([photoFrame]); bez něj obsah, nejvýš jako
 *   textová bublina, a obě části mají stejnou šířku (`IntrinsicSize.Max` — uvnitř není nic se
 *   `SubcomposeLayout`, viz „Gotchas").
 * - **Barvu z obrázku nevytahujeme.** TDLib u náhledu žádnou barvu neposílá a kvantizace z bitmapy
 *   by znamenala keš na `fileId` a počítání mimo hlavní vlákno.
 */
@Immutable
internal data class LinkPreviewMessageItem(
    override val frame: MessageFrameData,
    val preview: LinkPreview,
) : MessageItem {

    override val maxWidthFraction: Float
        get() = if (preview.showsLargePhoto) 1f else TEXT_MAX_WIDTH_FRACTION

    @Composable
    override fun MessageBodyScope.Body() {
        Column(
            // Karta se srovnává ke straně odesílatele, stejně jako holá fotka s popiskem.
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            // Karta jde až k hraně řádku jako ostatní bubliny, takže jméno si odsazení nese samo.
            SenderName(Modifier.padding(bottom = 2.dp))

            val photo = preview.photo
            if (preview.showsLargePhoto && photo != null) {
                BoxWithConstraints {
                    // Text zprávy i popis webu mají šířku obrázku — úzký obrázek je nesmí lámat po slovech.
                    val frameSize = photoFrame(
                        maxWidth,
                        photo.width.toFloat() / photo.height.toFloat(),
                        CAPTIONED_MIN_WIDTH_FRACTION,
                    )
                    Column(Modifier.width(frameSize.width).clip(shape)) {
                        MessagePart()
                        RemoteImage(
                            fileId = photo.fileId,
                            thumbnailFileId = photo.thumbnailFileId,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(frameSize).then(openLink()),
                        )
                        InfoPart(thumbnail = null)
                    }
                }
            } else {
                Column(Modifier.width(IntrinsicSize.Max).clip(shape)) {
                    MessagePart()
                    InfoPart(thumbnail = photo)
                }
            }
        }
    }

    /** Horní část: text zprávy v barvě bubliny. */
    @Composable
    private fun MessageBodyScope.MessagePart() {
        if (message.text.isBlank() && !message.isEdited) return
        Surface(color = bubbleColor, contentColor = onBubbleColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (message.text.isNotBlank()) MessageText(linkColor = accent)
                EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
            }
        }
    }

    /** Spodní část: web, titulek a popis v tónované ploše, případně malý obrázek vpravo. */
    @Composable
    private fun MessageBodyScope.InfoPart(thumbnail: MessageMedia.Photo?) {
        val hasInfo = preview.siteName != null || preview.title != null || preview.description != null
        if (!hasInfo && thumbnail == null) return
        val colors = MaterialTheme.colorScheme
        val background = if (frame.emphasized && !message.isOutgoing) {
            lerp(colors.tertiaryContainer, colors.tertiary, EMPHASIZED_INFO_SHIFT)
        } else {
            colors.tertiaryContainer
        }
        Surface(
            color = background,
            contentColor = colors.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth().then(openLink()),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = contentBottom + 2.dp),
            ) {
                PreviewInfo(preview, Modifier.weight(1f, fill = false))
                thumbnail?.let { photo ->
                    RemoteImage(
                        fileId = photo.fileId,
                        thumbnailFileId = photo.thumbnailFileId,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(LINK_PREVIEW_THUMBNAIL).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }

    /**
     * Ťuknutí otevře odkaz stejnou cestou jako odkaz v textu. Dlouhý stisk se opakuje, jinak by
     * ho plocha spolkla a nad zprávou by nešla otevřít nabídka.
     */
    @OptIn(ExperimentalFoundationApi::class)
    private fun MessageBodyScope.openLink(): Modifier = Modifier.combinedClickable(
        onClick = { act(MessageAction.OpenLink(preview.url)) },
        onLongClick = ::requestMenu,
    )
}

/** O kolik popis zvýrazněné příchozí zprávy ztmavne k `tertiary`, aby nesplynul s textem nad ním. */
private const val EMPHASIZED_INFO_SHIFT = 0.22f

/**
 * Web, titulek a popis. Název webu nese akcent té plochy, na které leží (`tertiary` na
 * `tertiaryContainer`), titulek a popis její barvu popředí.
 */
@Composable
internal fun PreviewInfo(preview: LinkPreview, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
