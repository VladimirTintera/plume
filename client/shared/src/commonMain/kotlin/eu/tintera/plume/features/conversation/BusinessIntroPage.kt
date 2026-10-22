package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.BusinessIntro
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.OnlineDot
import eu.tintera.plume.features.conversation.items.MessageAttachment

/**
 * Prázdný chat s firemním účtem: úvodní stránka, kterou si firma nastavila v Telegram Business
 * (`startPage`) — uvítací samolepka, nadpis a text. Skladba jako u bota ([BotIntroPage]): tvář
 * a jméno nahoře (hlavička svůj avatar schová), pod tím obsah stránky.
 *
 * **Samolepka je NAD nadpisem**, jako ji kreslí oficiální klienti: je to pozdrav, nadpis a text
 * ho rozvádí. Ťuknutí na ni nic nedělá — není to zpráva, kterou by šlo přeposlat nebo uložit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BusinessIntroPage(
    intro: BusinessIntro,
    chatId: Long,
    title: String,
    avatarFileId: Int?,
    /** Tečka „online". Bez spojení ji volající nedává — dostupnost je pak stará hodnota. */
    online: Boolean,
    modifier: Modifier = Modifier,
    /** Výška pruhů nad konverzací — odsazuje se uvnitř posuvné plochy, jako u bota. */
    topPadding: Dp = 0.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding)
            .padding(horizontal = 32.dp, vertical = 32.dp),
    ) {
        Box {
            Avatar(chatId = chatId, title = title, avatarFileId = avatarFileId, size = AVATAR_SIZE)
            if (online) {
                OnlineDot(
                    rim = MaterialTheme.colorScheme.surface,
                    size = ONLINE_DOT,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

        intro.sticker?.let { sticker ->
            Box(Modifier.padding(top = 20.dp).size(STICKER_SIZE), contentAlignment = Alignment.Center) {
                MessageAttachment(media = sticker, messageId = 0L, onOpen = {}, onRequestMenu = {})
            }
        }
        if (intro.title.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = intro.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
            )
        }
        if (intro.message.isNotBlank()) {
            Text(
                text = intro.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
            )
        }
    }
}

private val AVATAR_SIZE = 112.dp
private val ONLINE_DOT = 24.dp
private val STICKER_SIZE = 160.dp
private val TEXT_MAX_WIDTH = 480.dp
