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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia

/**
 * Fotka, video, album, mapa polohy nebo obrázek poslaný jako soubor přímo na ploše konverzace, bez
 * bubliny (pravidlo: [showsBareMedia]).
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
    /** Citovaná zpráva — horní díl karty, nad značkou přeposlání. */
    val quoted: ChatMessage? = null,
    /** Koho citace uvede v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val partnerName: String? = null,
) : MessageItem {

    override fun containsMessage(messageId: Long): Boolean =
        frame.message.id == messageId || album?.any { it.id == messageId } == true

    @Composable
    override fun MessageBodyScope.Body() {
        val location = (message.media as? MessageMedia.Location)?.takeIf { it.hasDetails }
        // Příběh má pod snímkem vždycky, čí je — bez toho by vypadal jako fotka od odesílatele.
        val story = message.media as? MessageMedia.Story
        // Soubor s velkým náhledem má pod snímkem vždycky název a velikost — bez nich by vypadal
        // jako fotka a nebylo by poznat, že ťuknutí otevře soubor.
        val document = if (album == null) message.media as? MessageMedia.Document else null
        // Živou polohu posouvá úprava zprávy; „upraveno" by u ní svítilo pořád.
        val edited = message.isEdited && !message.isLiveLocation
        val hasCaption = message.text.isNotBlank() || edited || location != null || document != null || story != null
        // Přeposlaná fotka nese značku v hlavičce téže karty — patří k obsahu, ne vedle něj.
        // Citace nad ní, stejně jako u odpovědi v bublině.
        val forwarded = message.hasOriginLabel
        val hasHeader = forwarded || quoted != null
        val isCard = hasCaption || hasHeader
        // Snímek leží na ploše, takže jeho akcent (průběh, ikony) patří k ní, ne k bublině.
        val surfaceAccent = if (message.isOutgoing) LocalContentColor.current else MaterialTheme.colorScheme.primary
        // S popiskem nebo hlavičkou ořezává karta celá; snímek si přesto nese nároží karty, do
        // kterých sahá — kvůli letu do prohlížeče (viz [cardMediaShape]). Jinak nese tvar snímek.
        val mediaShape = if (isCard) cardMediaShape(shape, hasHeader = hasHeader, hasCaption = hasCaption) else shape

        Column(
            // Karta se srovnává ke straně odesílatele.
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            // Nad holým snímkem si jméno musí odsazení přinést samo — i do strany: zpráva je
            // ořezaná tvarem bubliny a zaoblené nároží by jinak ukouslo první písmeno.
            SenderName(Modifier.padding(horizontal = MESSAGE_PADDING_H).padding(bottom = 2.dp))

            MediaWithCaption(
                modifier = if (isCard) Modifier.clip(shape) else Modifier,
                header = {
                    if (hasHeader) {
                        Column {
                            quoted?.let { QuotePart(it, partnerName) }
                            if (forwarded) OriginHeader()
                        }
                    }
                },
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
                                // Mapa má šířku stálou — s názvem místa i bez něj stejně velká.
                                // Soubor jen s textem zprávy: řádek s názvem má každý, a kvůli
                                // němu by se roztahoval každý náhled.
                                fillWidth = when (it) {
                                    is MessageMedia.Location -> false
                                    // Příběh je na výšku; přes celou šířku by z něj zbyl ořezaný pruh.
                                    is MessageMedia.Story -> false
                                    is MessageMedia.Document -> message.text.isNotBlank()
                                    else -> hasCaption
                                },
                            )
                        }
                    }
                },
                caption = {
                    if (hasCaption) {
                        BubbleSurface {
                            PaddedColumn {
                                location?.let { LocationDetails(it, color = onBubbleColor, onStop = stopLiveLocation(), onProximityAlert = setProximityAlert()) }
                                story?.let { StoryDetails(it, color = onBubbleColor) }
                                document?.let { DocumentCaption(it, color = onBubbleColor) }
                                if (message.text.isNotBlank()) MessageText(linkColor = accent)
                                if (edited) EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
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

/**
 * Tvar snímku uvnitř karty s popiskem nebo hlavičkou: nároží karty tam, kam snímek sahá (nahoře
 * bez hlavičky, dole bez popisku), jinak rovné hrany.
 *
 * V klidu ořezává karta celá a snímek by tvar nepotřeboval. **Sdílený prvek se ale během letu
 * do prohlížeče a zpátky kreslí v překryvu, mimo kartu** (viz `sharedNavElement`), takže s
 * hranatým snímkem fotka, video i obrázek poslaný jako soubor letěly a dosedaly s ostrými rohy
 * a zaoblily se až po dosednutí. Tatáž úvaha jako u dlaždic alba ([albumTileShape]).
 */
internal fun cardMediaShape(card: Shape, hasHeader: Boolean, hasCaption: Boolean): Shape =
    albumTileShape(card, top = !hasHeader, bottom = !hasCaption, start = true, end = true)
