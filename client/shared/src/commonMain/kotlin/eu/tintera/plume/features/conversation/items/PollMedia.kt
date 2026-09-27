package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.POLL_EXPLANATION_MEDIA
import eu.tintera.plume.core.telegram.POLL_QUESTION_MEDIA
import eu.tintera.plume.core.telegram.PollAttachment
import eu.tintera.plume.core.telegram.viewable
import eu.tintera.plume.core.ui.LocalPollMediaFlight
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.StaticMap
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/*
 * Přílohy ankety (TDLib 15): u otázky velká, u odpovědi malý náhled v řádku.
 *
 * Fotka, video, GIF, samolepka a soubor jsou tytéž `MessageMedia` jako u zprávy, takže je
 * u otázky kreslí `MessageAttachment` — i s rámečkem podle poměru stran, GIFem, který hraje sám,
 * a letem do prohlížeče; místo je mapa jako u zprávy s polohou ([LocationMap]). Odkaz tvar u zprávy
 * nemá — je to řádek s ikonou, který ťuknutí otevře v prohlížeči.
 */

/**
 * Velká příloha ankety — nad otázkou ([POLL_QUESTION_MEDIA]) a pod vysvětlením kvízu
 * ([POLL_EXPLANATION_MEDIA], [slot]). Klíč letu nese [LocalPollMediaFlight]: pod jedním id zprávy
 * je v anketě obrázků víc (viz `photoKey`).
 */
@Composable
internal fun MessageBodyScope.PollQuestionMedia(
    attachment: PollAttachment,
    slot: Int = POLL_QUESTION_MEDIA,
    modifier: Modifier = Modifier.padding(bottom = 10.dp),
) {
    // Sloupec, ne Box: pod mapou stojí název místa.
    Column(modifier.fillMaxWidth()) {
        when (attachment) {
            is PollAttachment.Media -> CompositionLocalProvider(LocalPollMediaFlight provides slot) {
                val media = attachment.media
                MessageAttachment(
                    media = media,
                    messageId = message.id,
                    onOpen = {
                        when (media) {
                            is MessageMedia.Sticker -> act(MessageAction.OpenStickerSet(media.setId))
                            is MessageMedia.Location -> act(MessageAction.OpenLink(media.mapUrl))
                            else -> act(MessageAction.OpenPollMedia(slot))
                        }
                    },
                    onRequestMenu = ::requestMenu,
                    accent = accent,
                    // Anketa má pevnou šířku; obrázek nad otázkou ji vyplní, jinak by vedle
                    // zúžené fotky zůstalo prázdné místo.
                    fillWidth = true,
                )
                (media as? MessageMedia.Location)?.takeIf { it.hasDetails }?.let {
                    LocationDetails(it, color = onBubbleColor, modifier = Modifier.padding(top = 6.dp))
                }
            }

            is PollAttachment.Link -> AttachmentLine(
                icon = Res.drawable.link_24dp,
                title = attachment.title ?: attachment.url,
                subtitle = attachment.url.takeIf { attachment.title != null },
                onClick = { act(MessageAction.OpenLink(attachment.url)) },
            )
        }
    }
}

/** Odkaz nad otázkou: ikona v tónovaném kolečku, název a pod ním adresa. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBodyScope.AttachmentLine(
    icon: DrawableResource,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ATTACHMENT_CORNER))
            // Dlouhý stisk se opakuje — řádek má vlastní klikání a nabídku zprávy by spolkl.
            .combinedClickable(onClick = onClick, onLongClick = ::requestMenu)
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ANSWER_MEDIA_SIZE)
                .clip(RoundedCornerShape(ATTACHMENT_CORNER))
                .background(onBubbleColor.copy(alpha = TILE_ALPHA)),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = accent)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = onBubbleColor.copy(alpha = SUBTITLE_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Náhled přílohy v řádku odpovědi.
 *
 * **Vlastní cíl ťuknutí.** Řádek odpovědi ťuknutím hlasuje; náhled fotky a videa otevře
 * prohlížeč, odkaz prohlížeč webu, mapa mapy platformy. Bez toho by nešlo odpověď s fotkou prohlédnout,
 * aniž by se pro ni hlasovalo. Co otevřít nejde (soubor, zvuk), ťuknutí propustí řádku.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBodyScope.AnswerMedia(attachment: PollAttachment, index: Int, modifier: Modifier = Modifier) {
    val media = (attachment as? PollAttachment.Media)?.media
    val action: MessageAction? = when {
        attachment.viewable != null -> MessageAction.OpenPollMedia(index)
        attachment is PollAttachment.Link -> MessageAction.OpenLink(attachment.url)
        media is MessageMedia.Location -> MessageAction.OpenLink(media.mapUrl)
        media is MessageMedia.Sticker -> MessageAction.OpenStickerSet(media.setId)
        else -> null
    }
    val shape = RoundedCornerShape(ATTACHMENT_CORNER)
    CompositionLocalProvider(LocalPollMediaFlight provides index) {
        val flight = if (attachment.viewable != null) Modifier.sharedNavElement(photoKey(message.id), shape = shape) else Modifier
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(ANSWER_MEDIA_SIZE)
                .clip(shape)
                .then(flight)
                .background(if (media is MessageMedia.Sticker) Color.Transparent else onBubbleColor.copy(alpha = TILE_ALPHA))
                .then(
                    if (action != null) {
                        Modifier.combinedClickable(onClick = { act(action) }, onLongClick = ::requestMenu)
                    } else {
                        Modifier
                    },
                ),
        ) {
            when {
                media != null -> AnswerMediaPicture(media)
                attachment is PollAttachment.Link -> attachment.photo?.let { photo ->
                    RemoteImage(fileId = photo.thumbnailFileId ?: photo.fileId, modifier = Modifier.fillMaxSize())
                } ?: Icon(painterResource(Res.drawable.link_24dp), contentDescription = null, tint = accent)
                else -> Unit
            }
        }
    }
}

/**
 * Obrázek do čtverečku odpovědi. Bere se nejmenší varianta, která je k dispozici: zobrazovaná
 * fotka má až 1 280 px, do 44 dp by to bylo zbytečné stahování.
 */
@Composable
private fun MessageBodyScope.AnswerMediaPicture(media: MessageMedia) {
    if (media is MessageMedia.Location) {
        // Ve čtverečku o velikosti ikony jsou vidět jen ulice kolem; víc řekne ťuknutí do map.
        StaticMap(media.latitude, media.longitude, pinSize = 18.dp, modifier = Modifier.fillMaxSize())
        return
    }
    val picture: Int? = when (media) {
        is MessageMedia.Photo -> media.fileId
        is MessageMedia.Video -> media.thumbnailFileId
        is MessageMedia.Animation -> media.thumbnailFileId
        is MessageMedia.Sticker -> media.thumbnailFileId ?: media.fileId
        is MessageMedia.Document -> media.thumbnailFileId
        is MessageMedia.Attachment -> media.thumbnailFileId
        else -> null
    }
    val thumbnail = (media as? MessageMedia.Photo)?.thumbnailFileId
    if (picture != null) {
        RemoteImage(
            fileId = picture,
            thumbnailFileId = thumbnail,
            placeholder = (media as? MessageMedia.Photo)?.minithumbnail,
            contentScale = if (media is MessageMedia.Sticker) ContentScale.Fit else ContentScale.Crop,
            background = if (media is MessageMedia.Sticker) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            painter = painterResource(if (media is MessageMedia.Attachment) Res.drawable.music_note_24dp else Res.drawable.description_24dp),
            contentDescription = null,
            tint = accent,
        )
    }
    if (media is MessageMedia.Video || media is MessageMedia.Animation) {
        // Že se jde o pohyblivý obrázek, musí být poznat i v čtverečku o velikosti ikony.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(PLAY_BADGE).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = PLAY_SCRIM)),
        ) {
            Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** Čtvereček přílohy v řádku odpovědi — jako řádek, aby ho nenafukoval. */
internal val ANSWER_MEDIA_SIZE = 44.dp
private val ATTACHMENT_CORNER = 10.dp
private val PLAY_BADGE = 22.dp
private const val TILE_ALPHA = 0.12f
private const val SUBTITLE_ALPHA = 0.7f
private const val PLAY_SCRIM = 0.45f
