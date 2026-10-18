package eu.tintera.plume.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.cloud_off_24dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/**
 * Stav spojení, jak se má ukázat: `null`, dokud je všechno v pořádku.
 *
 * **Výpadek se ukáže až po [CONNECTION_SHOW_DELAY_MS], návrat hned.** Po každém návratu
 * z pozadí TDLib na zlomek vteřiny hlásí `Updating` a stav by při každém otevření aplikace
 * probliknul. Změna mezi dvěma stavy výpadku (čekání na síť → připojování) je už vidět, takže
 * se nečeká.
 */
@Composable
fun rememberShownConnection(state: ConnectionState): ConnectionState? {
    val shown by produceState<ConnectionState?>(initialValue = null, state) {
        if (state == ConnectionState.Ready) {
            value = null
            return@produceState
        }
        if (value == null) delay(CONNECTION_SHOW_DELAY_MS)
        value = state
    }
    return shown
}

/**
 * Znak výpadku spojení, který stojí vždycky vedle jeho popisku ([label]): v pilulce hledání
 * místo lupy, v hlavičce konverzace na začátku podtitulu. Jeden znak pro „nejsem připojený"
 * napříč aplikací.
 *
 * - **Připojování a aktualizace: `LoadingIndicator`** (měnící se tvar) — něco se děje, stačí
 *   počkat.
 * - **Čekání na síť: ikona bez sítě, bez pohybu.** Na nic se nečeká, dokud se síť nevrátí;
 *   pohyb by lhal. Stojící vlnka na avataru působila jako zamrzlá, ikona ne — čte se jako stav,
 *   ne jako čekání, které se zaseklo. Klid proti pohybu rozliší oba stavy i bez barvy.
 *
 * **Avatar stav nenese.** Dřív se avatar zvlnil a točil (`ConnectionIndicator`), jenže avatar
 * je identita: roztočená tvář protějšku v hlavičce konverzace vypadala, jako by se něco dělo
 * s ním, a ne s naším spojením. A přetahovala se s tím, co avatar nese sám (tvar podle druhu
 * chatu, tečka online, tečka archivu).
 *
 * Výměna mezi tvarem a ikonou se prolne; mezi připojováním a aktualizací se nic nemění.
 */
@Composable
fun ConnectionStatusIcon(
    connection: ConnectionState,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = connection == ConnectionState.WaitingForNetwork,
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
        label = "connection-icon",
    ) { waiting ->
        if (waiting) {
            Icon(painterResource(Res.drawable.cloud_off_24dp), contentDescription = null, modifier = Modifier.size(size))
        } else {
            LoadingIndicator(Modifier.size(size))
        }
    }
}

private const val CONNECTION_SHOW_DELAY_MS = 600L
