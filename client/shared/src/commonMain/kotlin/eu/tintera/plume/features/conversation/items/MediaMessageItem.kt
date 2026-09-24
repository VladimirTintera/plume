package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage

/**
 * Fotka, video nebo album přímo na ploše konverzace, bez bubliny (pravidlo: [showsBareMedia]).
 *
 * Bublina kolem snímku nemá co ohraničovat — je to obrázek sám o sobě. **Popisek ale na plochu
 * nepatří**: text bez pozadí by se ztratil, takže dostane kontejner v barvě bubliny.
 *
 * **Snímek s popiskem je JEDNA KARTA**, jako v Google Zprávách a jako náhled odkazu: tvar
 * bubliny nese celá karta, uvnitř nejsou spáry ani vlastní zaoblení a **popisek má přesně šířku
 * snímku** — zalomí se do ní. Dřív byly snímek a popisek dva kusy se spárou a popisek byl široký
 * jako jeho text: u krátkého čouhalo zaoblené nároží a vypadal jako další, useknutá zpráva;
 * u dlouhého se roztáhl vedle zúžené fotky.
 *
 * **Řádek snímek nezabere celý** (výchozí [MESSAGE_MAX_WIDTH_FRACTION]). Bez bubliny nemá barvu,
 * podle které by se poznal odesílatel, takže volný pruh na protější straně je tu jediné vodítko —
 * na fotku přes celou šířku se obě strany konverzace dívaly stejně.
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
        // Přeposlaná fotka nese značku v hlavičce téže karty — patří k obsahu, ne vedle něj.
        val forwarded = message.forwardOrigin != null
        val isCard = hasCaption || forwarded
        // Snímek leží na ploše, takže jeho akcent (průběh, ikony) patří k ní, ne k bublině.
        val surfaceAccent = if (message.isOutgoing) LocalContentColor.current else MaterialTheme.colorScheme.primary
        // S popiskem nebo hlavičkou ořezává karta celá, snímek sám zaoblený není; jinak nese tvar snímek.
        val mediaShape = if (isCard) RectangleShape else shape

        Column(
            // Karta se srovnává ke straně odesílatele.
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            // Nad holým snímkem si jméno musí odsazení přinést samo.
            SenderName(Modifier.padding(bottom = 2.dp))

            MediaWithCaption(
                modifier = if (isCard) Modifier.clip(shape) else Modifier,
                header = { if (forwarded) ForwardHeader() },
                media = {
                    if (album != null) {
                        Album(members = album, accent = surfaceAccent, shape = mediaShape)
                    } else {
                        message.media?.let {
                            Attachment(
                                media = it,
                                accent = surfaceAccent,
                                mediaShape = mediaShape,
                                // Popisek má šířku fotky; s ním využije celou nabízenou šířku.
                                fillWidth = hasCaption,
                            )
                        }
                    }
                },
                caption = {
                    if (hasCaption) {
                        BubbleSurface {
                            PaddedColumn {
                                if (message.text.isNotBlank()) MessageText(linkColor = accent)
                                EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Hlavička snímku, pod ní snímek a pod ním popisek — hlavička i popisek s PŘESNĚ jeho šířkou.
 *
 * Vlastní layout, ne `IntrinsicSize`: snímek se kreslí v `BoxWithConstraints` (SubcomposeLayout),
 * a ten intrinsické míry neumí — aplikace by spadla (viz „Gotchas" v CLAUDE.md). Tady se snímek
 * změří normálně a popisek dostane jeho šířku jako pevnou.
 */
@Composable
private fun MediaWithCaption(
    header: @Composable () -> Unit,
    media: @Composable () -> Unit,
    caption: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(header, media, caption), modifier = modifier) { (headerMeasurables, mediaMeasurables, captionMeasurables), constraints ->
        val mediaPlaceables = mediaMeasurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = mediaPlaceables.maxOfOrNull { it.width } ?: 0
        val fixed = Constraints(minWidth = width, maxWidth = width)
        val headerPlaceables = headerMeasurables.map { it.measure(fixed) }
        val captionPlaceables = captionMeasurables.map { it.measure(fixed) }
        val height = (headerPlaceables + mediaPlaceables + captionPlaceables).sumOf { it.height }
        layout(width, height) {
            var y = 0
            (headerPlaceables + mediaPlaceables + captionPlaceables).forEach { it.place(0, y); y += it.height }
        }
    }
}
