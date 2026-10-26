package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_greeting_hint
import eu.tintera.plume.resources.conversation_greeting_send
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Samolepka na pozdrav v prázdném chatu s člověkem ([showsGreetingSticker]): jedna náhodná
 * z pozdravné sady Telegramu (`getGreetingStickers`, emoji „👋⭐️" — tutéž bere oficiální
 * klient), ťuknutí ji pošle jako běžnou samolepku. Pod ní věta, co ťuknutí udělá.
 *
 * Sadu si čte sama a jen po dobu, co je vidět — konverzace ji jinak nepotřebuje. Místo pro
 * samolepku drží od začátku, ať prázdný stav po načtení neposkočí; když sada nepřijde (chyba,
 * prázdná), nekreslí nic.
 */
@Composable
internal fun GreetingSticker(onSend: (StickerItem) -> Unit, modifier: Modifier = Modifier) {
    val stickers = koinInject<StickerRepository>()
    // `null` = načítá se, prázdný seznam = sada nepřišla.
    val set by produceState<List<StickerItem>?>(initialValue = null) { value = stickers.greetingStickers() }
    // Náhodná jednou za otevření, ne s každou rekompozicí.
    val sticker = remember(set) { set?.randomOrNull() }
    if (set != null && sticker == null) return
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = stringResource(Res.string.conversation_greeting_send)
        Box(
            Modifier
                .size(STICKER_SIZE)
                .clickable(enabled = sticker != null, onClickLabel = label, role = Role.Button) { sticker?.let(onSend) },
            contentAlignment = Alignment.Center,
        ) {
            sticker?.let { StickerPreview(it.preview, playing = true, modifier = Modifier.size(STICKER_SIZE)) }
        }
        Text(
            stringResource(Res.string.conversation_greeting_hint),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val STICKER_SIZE = 128.dp
