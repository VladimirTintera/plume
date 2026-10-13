package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SendFailure
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_not_sent_details
import eu.tintera.plume.resources.conversation_not_sent_retry
import eu.tintera.plume.resources.menu_resend
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.conversation_delivery_failed
import eu.tintera.plume.resources.conversation_delivery_read
import eu.tintera.plume.resources.conversation_delivery_sending
import eu.tintera.plume.resources.conversation_delivery_sent
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.schedule_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Stav doručení tak, jak se kreslí — čtyři stavy, každý s vlastní ikonou, popisem a barvou. */
/** Stav doručení odchozí zprávy, jak ho ukazuje odznak pod bublinou a hlavička nabídky. */
internal enum class Delivery { Sending, Sent, Read, Failed }

/**
 * Odznak se stavem doručení POD bublinou.
 *
 * Čtyři stavy ve čtyřech ikonách, jak je zvykem: hodiny (odesílá se), fajfka (na serveru),
 * dvojitá fajfka (protějšek přečetl), vykřičník (selhalo). Který z nich se vůbec ukáže,
 * rozhoduje bublina — `null` znamená, že tahle zpráva odznak nemá.
 *
 * **Je mimo bublinu, takže barva patří PLOŠE, ne bublině.** Dokud byl uvnitř, bral
 * `contentColor` bubliny; pod ní by `onPrimary` na tónovaném pozadí nebyl vidět. Stejná past
 * jako u `QuotedMessage.accent` a u zpráv bez bubliny.
 */
@Composable
internal fun DeliveryBadge(
    delivery: Delivery?,
    /** Proč zpráva neodešla; u [Delivery.Failed] rozhoduje o větě vedle vykřičníku. */
    failure: SendFailure? = null,
    /** Ťuknutí na neodeslanou zprávu: poslat znovu, nebo říct, proč to nejde. */
    onFailedClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    /*
     * Při odjezdu se musí kreslit POSLEDNÍ známý stav.
     *
     * V okamžiku skrytí je `delivery` už `null` (protějšek přečetl a fajfka u starší zprávy
     * odchází), takže by `AnimatedVisibility` dobíhala s prázdným obsahem. Stejně to řeší pruh
     * připnutých zpráv.
     */
    var lastShown by remember { mutableStateOf(delivery) }
    if (delivery != null && delivery != lastShown) lastShown = delivery

    /*
     * Mizí i MÍSTO odznaku, ne jen jeho krytí.
     *
     * Se samotným `fadeOut` drží slot v layoutu celou animaci a bublina by pak doskočila
     * nahoru až na konci — tatáž past jako u skrývání spodního baru.
     */
    AnimatedVisibility(
        visible = delivery != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        modifier = modifier.padding(top = DELIVERY_BADGE_GAP, end = DELIVERY_BADGE_INSET),
    ) {
        lastShown?.let { shown ->
            if (shown == Delivery.Failed) {
                FailedNote(failure = failure, onClick = onFailedClick)
            } else {
                DeliveryIcon(shown)
            }
        }
    }
}

/**
 * Neodeslaná zpráva: vykřičník a věta, co s tím. Samotný vykřičník byl slepá ulička — bylo vidět,
 * že zpráva neodešla, ale ne co dál. Jako v Google Messages je pod bublinou rovnou výzva
 * („Neodesláno. Ťukni a zkus to znovu."); u zprávy, kterou znovu poslat nejde, ťuknutí řekne
 * proč. Ťuká se na celý řádek, ne jen na ikonu — 14 dp by se netrefil nikdo.
 */
@Composable
private fun FailedNote(failure: SendFailure?, onClick: () -> Unit) {
    val canRetry = failure?.canRetry == true
    val label = stringResource(if (canRetry) Res.string.menu_resend else Res.string.conversation_delivery_failed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FAILED_NOTE_GAP),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = FAILED_NOTE_PADDING, vertical = FAILED_NOTE_PADDING),
    ) {
        DeliveryIcon(Delivery.Failed)
        Text(
            text = stringResource(
                if (canRetry) Res.string.conversation_not_sent_retry else Res.string.conversation_not_sent_details,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Samotná ikona. Oddělená od [DeliveryBadge], aby se animace příjezdu a výměny nepraly. */
@Composable
private fun DeliveryIcon(delivery: Delivery) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    /*
     * Nová ikona naskočí zvětšením, stará jen rychle zmizí. NE `Crossfade`.
     *
     * Crossfade prolíná obě ikony současně, takže v půlce přechodu jsou obě zhruba napůl
     * průhledné přes sebe. Fajfka a dvojitá fajfka se kryjí, a společná část tak na okamžik
     * zeslábne — to je ten problik. Navíc jel na pevném `tween` mimo `MotionScheme` a barvu
     * i popis bral zvenku, takže odcházející hodiny při selhání zčervenaly dřív, než zmizely.
     *
     * `using null`: ikony mají stejnou velikost, není co animovat, a ořez přechodu by uřízl
     * pružný přeběh zvětšení.
     */
    AnimatedContent(
        targetState = delivery,
        transitionSpec = {
            (fadeIn(effects) + scaleIn(spatial, initialScale = DELIVERY_ICON_INITIAL_SCALE)) togetherWith
                fadeOut(fastEffects) using null
        },
        contentAlignment = Alignment.Center,
        label = "delivery",
    ) { current ->
        val (icon, description) = when (current) {
            Delivery.Sending -> Res.drawable.schedule_24dp to Res.string.conversation_delivery_sending
            Delivery.Sent -> Res.drawable.check_24dp to Res.string.conversation_delivery_sent
            Delivery.Read -> Res.drawable.done_all_24dp to Res.string.conversation_delivery_read
            Delivery.Failed -> Res.drawable.error_24dp to Res.string.conversation_delivery_failed
        }

        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            // Selhání je jediný stav, který uživatel musí zaregistrovat — dostane chybovou barvu.
            // Počítá se z `current`, ne z cílového stavu: odcházející ikona si barvu drží.
            tint = if (current == Delivery.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(DELIVERY_ICON_SIZE),
        )
    }
}

/** Mezera mezi vykřičníkem a větou u neodeslané zprávy. */
private val FAILED_NOTE_GAP = 4.dp

/** Vnitřní okraj ťukací plochy „Neodesláno" — aby se dala trefit, a přitom nezvedla řádek moc. */
private val FAILED_NOTE_PADDING = 4.dp

/** Jak malá nová ikona doručení naskočí. Dost na to, aby změna šla postřehnout koutkem oka. */
private const val DELIVERY_ICON_INITIAL_SCALE = 0.5f

/** Ikona odznaku. Menší než text, aby vedle bubliny nekřičela. */
private val DELIVERY_ICON_SIZE = 14.dp

/** Mezera mezi bublinou a odznakem pod ní. */
private val DELIVERY_BADGE_GAP = 2.dp

/** Odsazení odznaku od hrany bubliny, aby neseděl přesně na jejím okraji. */
private val DELIVERY_BADGE_INSET = 4.dp
