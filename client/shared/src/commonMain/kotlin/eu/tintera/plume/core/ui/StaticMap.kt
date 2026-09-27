package eu.tintera.plume.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.MapRequest
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.location_on_filled_24dp
import eu.tintera.plume.resources.navigation_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Statická mapa kolem polohy se špendlíkem uprostřed.
 *
 * Obrázek kreslí Telegram (`FileRepository.mapThumbnail`): bez klíče k mapovému API a bez
 * dotazu na cizí službu. Je to obrázek, ne mapa — neposouvá se ani nepřibližuje; kdo chce víc,
 * ťukne a otevře systémové mapy.
 *
 * **Velikost se měří, ne zadává.** Mapa se žádá přesně na změřenou plochu (v bodech, se
 * zaokrouhlením na [SIZE_STEP], ať se kvůli pár bodům rozdílu nežádá nový soubor) a v hustotě
 * displeje. Větší než 1 024 bodů TDLib nevydá; větší plochu obrázek vyplní ořezem.
 *
 * **Mapa je vždycky světlá**, i v tmavém motivu — tmavé dlaždice Telegram nemá. Špendlík proto
 * nebere `primary` (v tmavém motivu světlá a na světlé mapě by zanikla), ale tmavou z dvojice
 * `primary` / `inversePrimary`.
 *
 * **Se známým směrem pohybu ([heading], živá poloha) je místo špendlíku šipka** otočená po
 * směru, na bílém kolečku — tak to kreslí i navigace. Špendlík ukazuje MÍSTO hrotem dole,
 * šipka ho ukazuje středem; na kolečku proto, aby byla vidět i nad tmavou plochou (park, voda).
 */
@Composable
fun StaticMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Int = MAP_ZOOM,
    chatId: Long = 0,
    pinSize: Dp = 32.dp,
    heading: Int = 0,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current.density
        val request = MapRequest(
            latitude = latitude,
            longitude = longitude,
            zoom = zoom.coerceIn(13, 20),
            width = maxWidth.mapSide(),
            height = (if (constraints.hasBoundedHeight) maxHeight else maxWidth).mapSide(),
            scale = density.roundToInt().coerceIn(1, 3),
            chatId = chatId,
        )
        val repository = koinInject<FileRepository>()
        val fileId by produceState<Int?>(initialValue = null, request) {
            value = repository.mapThumbnail(request)
        }
        fileId?.let { id ->
            RemoteImage(
                fileId = id,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val pin = if (colors.primary.luminance() < colors.inversePrimary.luminance()) colors.primary else colors.inversePrimary
        if (heading in 1..360) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 2.dp, modifier = Modifier.size(pinSize)) {
                Icon(
                    painterResource(Res.drawable.navigation_filled_24dp),
                    contentDescription = null,
                    tint = pin,
                    modifier = Modifier.padding(pinSize * ARROW_INSET).rotate(heading.toFloat()),
                )
            }
        } else {
            Icon(
                painterResource(Res.drawable.location_on_filled_24dp),
                contentDescription = null,
                tint = pin,
                // Hrot špendlíku je v 11/12 výšky ikony; posadí se na střed mapy, ne střed ikony.
                modifier = Modifier.size(pinSize).offset(y = -pinSize * PIN_TIP_FROM_CENTER),
            )
        }
    }
}

/** Strana mapy v bodech: zaokrouhlená na [SIZE_STEP] a v mezích TDLib. */
private fun Dp.mapSide(): Int =
    ((value / SIZE_STEP).roundToInt() * SIZE_STEP).coerceIn(MIN_MAP_SIDE, MAX_MAP_SIDE)

/** Ulice a jejich jména, ale pořád dost okolí, aby se dalo poznat, kde to je. */
const val MAP_ZOOM = 15

private const val SIZE_STEP = 8
private const val MIN_MAP_SIDE = 16
private const val MAX_MAP_SIDE = 1024

/** Hrot špendlíku (y = 880 z 960) leží 5/12 výšky ikony pod jejím středem. */
private const val PIN_TIP_FROM_CENTER = 5f / 12f

/** Okraj kolečka kolem šipky, jako podíl jeho průměru. */
private const val ARROW_INSET = 0.18f
