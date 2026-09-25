package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import eu.tintera.plume.core.location.mapsUrl
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.StaticMap
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_live_location
import eu.tintera.plume.resources.location_live_ended
import eu.tintera.plume.resources.location_live_until
import eu.tintera.plume.resources.media_map
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

/**
 * Poloha jako obrázek mapy — u zprávy s polohou, místem i živou polohou a u místa v anketě.
 *
 * Mapa je obrázek sám o sobě, takže jde na plochu bez bubliny jako fotka ([showsBareMedia]);
 * název místa pod ní nese karta v barvě bubliny ([LocationDetails]). Poměr stran je pevný
 * ([MAP_ASPECT]), mapa nemá vlastní rozměry a obrázek se vyžádá přesně na plochu.
 *
 * **Živá poloha je tady taky obrázek.** Každé posunutí přijde jako úprava zprávy
 * (`editedMessages`), takže se mapa přestaví na novou polohu sama; plynulý pohyb po mapě by
 * chtěl mapové SDK, které aplikace nemá.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocationMap(
    location: MessageMedia.Location,
    shape: Shape,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    fillWidth: Boolean = false,
) {
    BoxWithConstraints {
        val width = if (fillWidth) maxWidth else min(maxWidth, MAP_WIDTH)
        StaticMap(
            latitude = location.latitude,
            longitude = location.longitude,
            contentDescription = stringResource(Res.string.media_map),
            modifier = Modifier
                .size(width, width * MAP_ASPECT)
                .clip(shape)
                // Dlouhý stisk se opakuje — mapa má vlastní klikání a nabídku zprávy by spolkla.
                .combinedClickable(onClick = onOpen, onLongClick = onRequestMenu),
        )
    }
}

/** Ťuknutí na mapu otevře polohu v mapách platformy. */
internal val MessageMedia.Location.mapUrl: String get() = mapsUrl(latitude, longitude)

/** Živá poloha — její posuny chodí jako úpravy zprávy, takže „upraveno" u ní nic neříká. */
internal val ChatMessage.isLiveLocation: Boolean
    get() = (media as? MessageMedia.Location)?.isLive == true

/** Má poloha co napsat pod mapu — název a adresu místa, nebo stav živé polohy? */
internal val MessageMedia.Location.hasDetails: Boolean get() = title != null || address != null || isLive

/**
 * Název a adresa místa pod mapou; u živé polohy do kdy se sdílí.
 *
 * Konec sdílení se počítá při složení, ne průběžně: skončené sdílení se ukáže při dalším
 * překreslení zprávy. Odpočet po vteřinách by kvůli jednomu řádku překresloval konverzaci.
 */
@Composable
internal fun LocationDetails(location: MessageMedia.Location, color: Color, modifier: Modifier = Modifier) {
    val title = location.title ?: if (location.isLive) stringResource(Res.string.content_live_location) else null
    val subtitle = when {
        !location.isLive -> location.address
        location.liveUntil == Long.MAX_VALUE -> null
        location.liveUntil <= remember(location) { Clock.System.now().epochSeconds } ->
            stringResource(Res.string.location_live_ended)
        else -> stringResource(Res.string.location_live_until, location.liveUntil.asUpcomingTime())
    }
    Column(modifier) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = DETAILS_SUBTITLE_ALPHA),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Šířka mapy ve zprávě — zhruba jako fotka na šířku; řádek ji dál omezí na 82 %. */
private val MAP_WIDTH: Dp = 280.dp

/** Výška mapy k šířce. Širší než vyšší: okolí vlevo a vpravo řekne víc než nad a pod. */
private const val MAP_ASPECT = 0.6f

private const val DETAILS_SUBTITLE_ALPHA = 0.7f
