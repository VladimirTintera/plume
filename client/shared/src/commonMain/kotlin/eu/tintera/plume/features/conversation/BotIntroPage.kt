package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.BotIntro
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.items.AnimationBubble
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_bot
import org.jetbrains.compose.resources.stringResource

/**
 * Prázdný chat s botem jako jeho profil: velká tvář nahoře uprostřed, jméno, „Bot" a pod tím
 * popis. Obrázek nebo GIF, který si bot k popisu nastavil, je až pod textem — představí ho
 * tvář a jméno, obrázek je doplněk.
 *
 * Kreslí se místo holého „Zatím tu nic není" i u bota BEZ popisu: tvář a jméno řeknou, s kým
 * chat je, a tlačítko Start pod tím dává smysl. Stránka se posouvá — popis bývá dlouhý a pod
 * ní stojí Start, který se nesmí dostat přes text.
 */
@Composable
internal fun BotIntroPage(
    intro: BotIntro,
    chatId: Long,
    title: String,
    avatarFileId: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
    ) {
        Avatar(chatId = chatId, title = title, avatarFileId = avatarFileId, size = AVATAR_SIZE)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.profile_bot),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (intro.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = intro.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
            )
        }
        val media = Modifier.padding(top = 20.dp).widthIn(max = MEDIA_MAX_WIDTH).fillMaxWidth().clip(MEDIA_SHAPE)
        intro.animation?.let { animation ->
            AnimationBubble(
                animation = animation,
                shape = MEDIA_SHAPE,
                onRequestMenu = {},
                modifier = media.aspectRatio(animation.ratio()),
            )
        } ?: intro.photo?.let { photo ->
            RemoteImage(
                fileId = photo.fileId,
                contentDescription = null,
                modifier = media.aspectRatio(ratio(photo.width, photo.height)),
            )
        }
    }
}

private fun MessageMedia.Animation.ratio(): Float = ratio(width, height)

/** Neznámé rozměry (nula) by dělaly nekonečný poměr; pak obrázek jako 16 : 9. */
private fun ratio(width: Int, height: Int): Float =
    if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(0.5f, 3f) else 16f / 9f

private val AVATAR_SIZE = 112.dp
private val TEXT_MAX_WIDTH = 480.dp
private val MEDIA_MAX_WIDTH = 360.dp
private val MEDIA_SHAPE = RoundedCornerShape(24.dp)
