package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import eu.tintera.plume.resources.location_live_forever
import eu.tintera.plume.resources.location_live_stop
import eu.tintera.plume.resources.location_off_24dp
import eu.tintera.plume.resources.location_live_until
import eu.tintera.plume.resources.media_map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
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
    // Směr má smysl jen u polohy, která se ještě sdílí; u skončené by ukazoval, kam se kdo
    // kdysi hýbal, a vypadal by jako živý.
    val sharing = rememberLiveSharing(location)
    BoxWithConstraints {
        val width = if (fillWidth) maxWidth else min(maxWidth, MAP_WIDTH)
        StaticMap(
            latitude = location.latitude,
            longitude = location.longitude,
            heading = if (sharing) location.heading else 0,
            contentDescription = stringResource(Res.string.media_map),
            modifier = Modifier
                .size(width, width * MAP_ASPECT)
                .clip(shape)
                // Dlouhý stisk se opakuje — mapa má vlastní klikání a nabídku zprávy by spolkla.
                .combinedClickable(onClick = onOpen, onLongClick = onRequestMenu),
        )
    }
}

/** „Zastavit sdílení" jen u vlastní živé polohy — cizí zastavit nejde. */
internal fun MessageBodyScope.stopLiveLocation(): (() -> Unit)? =
    if (message.isOutgoing && message.isLiveLocation) { { act(MessageAction.StopLiveLocation) } } else null

/** Ťuknutí na mapu otevře polohu v mapách platformy. */
internal val MessageMedia.Location.mapUrl: String get() = mapsUrl(latitude, longitude)

/** Živá poloha — její posuny chodí jako úpravy zprávy, takže „upraveno" u ní nic neříká. */
internal val ChatMessage.isLiveLocation: Boolean
    get() = (media as? MessageMedia.Location)?.isLive == true

/** Má poloha co napsat pod mapu — název a adresu místa, nebo stav živé polohy? */
internal val MessageMedia.Location.hasDetails: Boolean get() = title != null || address != null || isLive

/**
 * Sdílí se živá poloha ještě? Nepočítá se po vteřinách, jen se jednou počká na konec — odpočet
 * by kvůli jednomu řádku překresloval konverzaci.
 *
 * **Jednorázové porovnání při složení NESTAČÍ.** Zastavené sdílení přijde úpravou zprávy
 * s dobou zkrácenou právě na okamžik zastavení, takže konec leží ve stejné vteřině jako
 * složení a porovnání „už skončilo" vyšlo naprázdno: pod mapou zůstalo „Sdílí se do 14:08"
 * a nad ní šipka směru, jako by se pořád sdílelo.
 */
@Composable
internal fun rememberLiveSharing(location: MessageMedia.Location): Boolean {
    if (!location.isLive) return false
    val until = location.liveUntil
    val sharing by produceState(initialValue = until > Clock.System.now().epochSeconds, until) {
        val left = until - Clock.System.now().epochSeconds
        if (left > 0 && until != Long.MAX_VALUE) {
            delay(left.seconds)
            value = false
        } else {
            value = left > 0
        }
    }
    return sharing
}

/**
 * Název a adresa místa pod mapou; u živé polohy do kdy se sdílí. U VLASTNÍ živé polohy, která
 * ještě běží, i „Zastavit sdílení" ([onStop]) — jinak by šlo sdílení ukončit jen z notifikace,
 * a ta zastaví všechno naráz.
 */
@Composable
internal fun LocationDetails(
    location: MessageMedia.Location,
    color: Color,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
) {
    val title = location.title ?: if (location.isLive) stringResource(Res.string.content_live_location) else null
    val sharing = rememberLiveSharing(location)
    val subtitle = when {
        !location.isLive -> location.address
        !sharing -> stringResource(Res.string.location_live_ended)
        location.liveUntil == Long.MAX_VALUE -> stringResource(Res.string.location_live_forever)
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
        if (onStop != null && sharing) {
            TextButton(
                onClick = onStop,
                colors = ButtonDefaults.textButtonColors(contentColor = color),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Icon(painterResource(Res.drawable.location_off_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.location_live_stop), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Šířka mapy ve zprávě — zhruba jako fotka na šířku; řádek ji dál omezí na 82 %. */
private val MAP_WIDTH: Dp = 280.dp

/** Výška mapy k šířce. Širší než vyšší: okolí vlevo a vpravo řekne víc než nad a pod. */
private const val MAP_ASPECT = 0.6f

private const val DETAILS_SUBTITLE_ALPHA = 0.7f
