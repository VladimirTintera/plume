package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.features.conversation.photoFrame
import eu.tintera.plume.features.conversation.showsLargePhoto
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bolt_filled_24dp
import eu.tintera.plume.resources.instant_view_mark
import eu.tintera.plume.resources.apps_24dp
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.motion_photos_on_24dp
import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import eu.tintera.plume.resources.link_preview_open_app
import eu.tintera.plume.resources.link_preview_open_bot
import eu.tintera.plume.resources.link_preview_send_message
import eu.tintera.plume.resources.link_preview_view_channel
import eu.tintera.plume.resources.link_preview_view_group
import eu.tintera.plume.resources.link_preview_view_message
import eu.tintera.plume.resources.link_preview_view_stickers
import eu.tintera.plume.resources.link_preview_view_story
import eu.tintera.plume.core.telegram.LinkPreviewTarget
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.features.conversation.StickerPreview
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
 *   z barev bublin. Kde by s bublinou přesto splynula, se posune — viz [MessageBodyScope.insetColor].
 * - **Šířka.** S velkým obrázkem ji určuje obrázek ([photoFrame]); bez něj obsah. Řádek karta
 *   nikdy nezabere celý — protější strana zůstává volná jako u každé zprávy
 *   ([MESSAGE_MAX_WIDTH_FRACTION]). Obě části mají stejnou šířku (`IntrinsicSize.Max` — uvnitř
 *   není nic se `SubcomposeLayout`, viz „Gotchas").
 * - **Barvu z obrázku nevytahujeme.** TDLib u náhledu žádnou barvu neposílá a kvantizace z bitmapy
 *   by znamenala keš na `fileId` a počítání mimo hlavní vlákno.
 */
@Immutable
internal data class LinkPreviewMessageItem(
    override val frame: MessageFrameData,
    val preview: LinkPreview,
) : MessageItem {

    @Composable
    override fun MessageBodyScope.Body() {
        Column(
            // Karta se srovnává ke straně odesílatele, stejně jako holá fotka s popiskem.
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            val photo = preview.photo
            if (preview.showsLargePhoto && photo != null) {
                BoxWithConstraints {
                    // Text zprávy i popis webu mají šířku obrázku; s ním využije celou nabízenou šířku.
                    val frameSize = photoFrame(
                        maxWidth,
                        photo.width.toFloat() / photo.height.toFloat(),
                        fillWidth = true,
                    )
                    Column(Modifier.width(frameSize.width).clip(shape)) {
                        CardParts(
                            largePhoto = { LargePhoto(photo, frameSize) },
                            thumbnail = null,
                        )
                    }
                }
            } else {
                Column(Modifier.width(IntrinsicSize.Max).clip(shape)) {
                    CardParts(largePhoto = null, thumbnail = photo)
                }
            }
        }
    }

    /**
     * Díly karty v pořadí, které si přál odesílatel.
     *
     * `showAboveText` znamená náhled NAD textem zprávy — a s ním se otáčí i to, který díl je
     * poslední (spodní odsazení nese ten, pod kterým už nic není; počítá se s místem na čipy
     * reakcí). **Jméno odesílatele a značka přeposlání zůstávají nahoře vždycky**: jsou to údaje
     * o zprávě, ne o odkazu, a dole by vypadaly jako popisek náhledu. Jméno je uvnitř karty jako
     * v bublině — dřív stálo nad kartou na ploše a blok začínající odkazem vypadal jinak než blok
     * začínající textem.
     */
    @Composable
    private fun MessageBodyScope.CardParts(
        largePhoto: (@Composable () -> Unit)?,
        thumbnail: MessageMedia.Photo?,
    ) {
        if (preview.showAboveText) {
            CardHeader(withName = true)
            largePhoto?.invoke()
            InfoPart(thumbnail = thumbnail, last = false)
            MessagePart(withForward = false, last = true)
        } else {
            MessagePart(withForward = true, last = false)
            largePhoto?.invoke()
            InfoPart(thumbnail = thumbnail, last = true)
        }
    }

    @Composable
    private fun MessageBodyScope.LargePhoto(photo: MessageMedia.Photo, frameSize: DpSize) {
        LinkPreviewImage(
            photo = photo,
            messageId = message.id,
            onLongClick = ::requestMenu,
            modifier = Modifier.size(frameSize),
            imageModifier = openLink(),
        )
    }

    /** Text zprávy v barvě bubliny, se jménem a značkou přeposlání, když je díl první. */
    @Composable
    private fun MessageBodyScope.MessagePart(withForward: Boolean, last: Boolean) {
        val forward = withForward && message.hasOriginLabel
        val name = withForward && showsSenderName
        if (message.text.isBlank() && !message.isEdited && !forward && !name) return
        BubbleSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(
                    start = MESSAGE_PADDING_H,
                    end = MESSAGE_PADDING_H,
                    top = MESSAGE_PADDING_V,
                    bottom = if (last) contentBottom else MESSAGE_PADDING_V,
                ),
            ) {
                if (name) SenderName(background = bubbleColor)
                if (forward) OriginLabel()
                if (message.text.isNotBlank()) MessageText(linkColor = accent)
                EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
            }
        }
    }

    /** Web, titulek a popis v tónované ploše, případně malý obrázek vpravo. */
    @Composable
    private fun MessageBodyScope.InfoPart(thumbnail: MessageMedia.Photo?, last: Boolean) {
        val hasInfo = preview.siteName != null || preview.title != null || preview.description != null
        if (!hasInfo && thumbnail == null) return
        val colors = MaterialTheme.colorScheme
        Surface(
            color = insetColor,
            contentColor = colors.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth().then(openLink()),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(
                        start = MESSAGE_PADDING_H,
                        end = MESSAGE_PADDING_H,
                        top = MESSAGE_PADDING_V + 2.dp,
                        bottom = if (last) contentBottom + 2.dp else MESSAGE_PADDING_V + 2.dp,
                    ),
            ) {
                PreviewInfo(
                    preview = preview,
                    accent = accentOn(insetColor, colors.tertiary, colors.onTertiaryContainer),
                    modifier = Modifier.weight(1f, fill = false),
                )
                thumbnail?.let { photo ->
                    LinkPreviewImage(
                        photo = photo,
                        messageId = message.id,
                        label = false,
                        onLongClick = ::requestMenu,
                        modifier = Modifier.size(LINK_PREVIEW_THUMBNAIL).clip(preview.thumbnailShape(ShapeDefaults.Medium)),
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
    private fun MessageBodyScope.openLink(): Modifier = Modifier.contextClickable(
        onClick = { act(preview.openAction()) },
        onLongClick = ::requestMenu,
    )
}

/**
 * Web, titulek a popis. Název webu nese [accent] té plochy, na které leží (`tertiary` na
 * `tertiaryContainer`), titulek a popis její barvu popředí.
 *
 * Akcent se podává zvenku, protože plochu zná volající — a protože se na ní `tertiary` nemusí
 * uhlídat: v paletě se zvýšeným kontrastem je v tmavém režimu `tertiaryContainer` světlý a název
 * webu na něm zmizel. Rozhoduje o tom [accentOn], tatáž past jako u citace.
 */
@Composable
internal fun PreviewInfo(preview: LinkPreview, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        preview.siteName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
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
        if (preview.stickers.isNotEmpty()) PreviewStickers(preview.stickers, Modifier.padding(top = 4.dp))
        val target = preview.target?.mark()
        when {
            target != null -> PreviewMark(target.first, stringResource(target.second), accent, Modifier.padding(top = 4.dp))
            preview.instantView -> InstantViewMark(accent, Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Ukázka sady samolepek pod popisem náhledu — z odkazu na sadu se jinak nedalo poznat, jak
 * samolepky vypadají. Statické náhledy ([StickerPreview] bez přehrávání): animace by v každé
 * zprávě s odkazem na sadu běžely naráz.
 */
@Composable
private fun PreviewStickers(stickers: List<MessageMedia.Sticker>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stickers.forEach { sticker ->
            StickerPreview(sticker = sticker, playing = false, modifier = Modifier.size(LINK_PREVIEW_STICKER))
        }
    }
}

/**
 * Co se po ťuknutí na náhled odkazu na Telegram otevře: ikona a popisek značky; `null`, kde
 * akci slíbit nejde. Boost (`t.me/boost/…`) Plume v aplikaci neotevře, takže značku nemá.
 */
internal fun LinkPreviewTarget.mark(): Pair<DrawableResource, StringResource>? = when (this) {
    LinkPreviewTarget.Channel -> Res.drawable.campaign_24dp to Res.string.link_preview_view_channel
    LinkPreviewTarget.Group -> Res.drawable.group_24dp to Res.string.link_preview_view_group
    LinkPreviewTarget.User -> Res.drawable.chat_24dp to Res.string.link_preview_send_message
    LinkPreviewTarget.Bot -> Res.drawable.smart_toy_24dp to Res.string.link_preview_open_bot
    LinkPreviewTarget.Stickers -> Res.drawable.sticky_note_2_24dp to Res.string.link_preview_view_stickers
    LinkPreviewTarget.App -> Res.drawable.apps_24dp to Res.string.link_preview_open_app
    LinkPreviewTarget.Message -> Res.drawable.chat_bubble_24dp to Res.string.link_preview_view_message
    LinkPreviewTarget.Story -> Res.drawable.motion_photos_on_24dp to Res.string.link_preview_view_story
    LinkPreviewTarget.Boost -> null
}

/** Tvar malého obrázku náhledu: profilovka v kruhu, jinak [default]. */
internal fun LinkPreview.thumbnailShape(default: Shape): Shape = if (roundPhoto) CircleShape else default

/**
 * Ťuknutí na náhled: stránka s Instant View se otevře ve čtečce v aplikaci, ostatní odkazem
 * (prohlížeč nebo, u `t.me`, přímo v Plume).
 */
internal fun LinkPreview.openAction(): MessageAction =
    if (instantView) MessageAction.OpenInstantView(url) else MessageAction.OpenLink(url)

/**
 * Značka „⚡ Rychlé zobrazení" — že se stránka otevře v aplikaci, je vidět předem, jako tlačítko
 * Instant View v oficiálních klientech. Značka, ne tlačítko: otevírá ťuknutí na celý náhled.
 */
@Composable
internal fun InstantViewMark(accent: Color, modifier: Modifier = Modifier) {
    PreviewMark(Res.drawable.bolt_filled_24dp, stringResource(Res.string.instant_view_mark), accent, modifier)
}

/**
 * Značka pod popisem náhledu: co ťuknutí udělá. Značka, ne tlačítko — otevírá ťuknutí na celý
 * náhled, jako u Instant View ([InstantViewMark]) a odkazu na Telegram ([LinkPreviewTarget]).
 */
@Composable
private fun PreviewMark(icon: DrawableResource, label: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

/** Samolepka v ukázce sady pod popisem náhledu. */
private val LINK_PREVIEW_STICKER = 44.dp
