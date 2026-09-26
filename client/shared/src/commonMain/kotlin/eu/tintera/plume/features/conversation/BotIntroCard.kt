package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.BotIntro
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.items.AnimationBubble
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_bot_intro_title
import org.jetbrains.compose.resources.stringResource

/**
 * „Co tento bot umí?" — karta v prázdném chatu s botem: obrázek nebo GIF od bota, nadpis
 * a popis. Nahrazuje holé „Zatím tu nic není": u nespuštěného bota to jediné vysvětluje, co
 * Start udělá.
 *
 * Obrázek i GIF mají šířku karty a poměr stran od serveru; GIF hraje sám jako v konverzaci.
 * Obrázek vyplní horní část karty bez vlastního zaoblení — tvar nese karta.
 */
@Composable
internal fun BotIntroCard(intro: BotIntro, modifier: Modifier = Modifier) {
    Surface(
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH).padding(horizontal = 24.dp),
    ) {
        Column {
            intro.animation?.let { animation ->
                AnimationBubble(
                    animation = animation,
                    shape = RoundedCornerShape(0.dp),
                    onRequestMenu = {},
                    modifier = Modifier.fillMaxWidth().aspectRatio(animation.ratio()),
                )
            } ?: intro.photo?.let { photo ->
                RemoteImage(
                    fileId = photo.fileId,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio(photo.width, photo.height)),
                )
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(Res.string.conversation_bot_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (intro.description.isNotBlank()) {
                    Text(
                        text = intro.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private fun eu.tintera.plume.core.telegram.MessageMedia.Animation.ratio(): Float = ratio(width, height)

/** Neznámé rozměry (nula) by dělaly nekonečný poměr; pak obrázek jako 16 : 9. */
private fun ratio(width: Int, height: Int): Float =
    if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(0.5f, 3f) else 16f / 9f

private val CARD_SHAPE = RoundedCornerShape(24.dp)
private val CARD_MAX_WIDTH = 400.dp
