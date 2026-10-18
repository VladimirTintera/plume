package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.StaticMap
import eu.tintera.plume.features.conversation.items.DetailsAction
import eu.tintera.plume.features.conversation.items.liveSharingText
import eu.tintera.plume.features.conversation.items.proximityAlertLabel
import eu.tintera.plume.features.conversation.items.rememberLiveSharing
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.expand_more_24dp
import eu.tintera.plume.resources.location_dock_collapse
import eu.tintera.plume.resources.location_dock_expand
import eu.tintera.plume.resources.location_dock_many_sharing
import eu.tintera.plume.resources.location_dock_other_sharing
import eu.tintera.plume.resources.location_dock_show_message
import eu.tintera.plume.resources.location_dock_you
import eu.tintera.plume.resources.location_dock_you_sharing
import eu.tintera.plume.resources.location_live_stop
import eu.tintera.plume.resources.location_off_24dp
import eu.tintera.plume.resources.location_on_filled_24dp
import eu.tintera.plume.resources.media_map
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.chat_bubble_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dock živé polohy: řádek ve sloupci pruhů nad konverzací, do kterého se zpráva se živou polohou
 * „přestěhuje", když odjede z obrazovky — mapa i ovládání (zastavit, upozornit na blízkost) tak
 * zůstanou po ruce, i když se v chatu mezitím píše.
 *
 * - **Vysouvá ho scroll, ne čas.** [reveal] je podíl zprávy schovaný pod okrajem
 *   ([dockProgress]); řádek se o tolik vysune zpod pruhů nad ním a zprůhlední se. Zpráva tak
 *   pod okraj zajíždí a řádek z něj vyjíždí jedním tahem prstu, oběma směry.
 * - **Rozbalí se na místě**: pilulka naroste v kartu s mapou a ovládáním každé polohy. Zaoblení
 *   28 dp je u 56dp řádku právě pilulka, takže tvar se mezi stavy nemění, jen roste.
 * - **Odznak je SoftBurst** v terciární barvě: SoftBurst v aplikaci znamená živý signál
 *   (`ShapeMorphs`) a terciární role nese „živé" věci, jako u videochatu.
 */
@Composable
internal fun LiveLocationDockBar(
    locations: List<ChatMessage>,
    /** 0 = schovaný, 1 = celý vysunutý; čte se až při rozmístění. */
    reveal: () -> Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    /** Jméno protějšku v soukromém chatu — cizí zpráva tam jméno odesílatele nenese. */
    chatTitle: String,
    onOpenMap: (ChatMessage) -> Unit,
    onShowMessage: (ChatMessage) -> Unit,
    onStop: (ChatMessage) -> Unit,
    onProximityAlert: (ChatMessage) -> Unit,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val first = locations.firstOrNull() ?: return
    val you = stringResource(Res.string.location_dock_you)
    fun nameOf(message: ChatMessage): String = if (message.isOutgoing) you else message.senderName ?: chatTitle

    val title = when {
        locations.size > 1 -> pluralStringResource(Res.plurals.location_dock_many_sharing, locations.size, locations.size)
        first.isOutgoing -> stringResource(Res.string.location_dock_you_sharing)
        else -> stringResource(Res.string.location_dock_other_sharing, nameOf(first))
    }
    val firstLocation = first.media as? MessageMedia.Location ?: return
    val subtitle = if (locations.size > 1) {
        locations.joinToString(", ") { nameOf(it) }
    } else {
        liveSharingText(firstLocation, rememberLiveSharing(firstLocation))
    }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dock-chevron")
    val toggleLabel = stringResource(if (expanded) Res.string.location_dock_collapse else Res.string.location_dock_expand)

    Box(modifier.dockReveal(reveal)) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = DOCK_SHAPE,
            // Stín jen u sbaleného řádku: pod průsvitným sklem prosvítá DOVNITŘ plochy a u vysoké
            // rozbalené karty z něj byl ztmavlý pruh vpravo a dole (ověřeno vypnutím stínu).
            shadowElevation = if (expanded) 0.dp else DOCK_ELEVATION,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(Modifier.clip(DOCK_SHAPE).then(glass)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .semantics { contentDescription = toggleLabel }
                        .heightIn(min = DOCK_ROW_HEIGHT)
                        .padding(start = DOCK_BADGE_INSET, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveBadge()
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        painterResource(Res.drawable.expand_more_24dp),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = chevron },
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    // Víc poloh (skupina) by kartu vytáhlo přes celou obrazovku; posouvá se uvnitř.
                    Column(
                        Modifier.heightIn(max = DOCK_EXPANDED_MAX).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        locations.forEach { message ->
                            DockedLocation(
                                message = message,
                                name = nameOf(message),
                                showName = locations.size > 1,
                                onOpenMap = { onOpenMap(message) },
                                onShowMessage = { onShowMessage(message) },
                                onStop = { onStop(message) },
                                onProximityAlert = { onProximityAlert(message) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Odznak docku: špendlík v SoftBurstu, terciárně. */
@Composable
private fun LiveBadge() {
    val shape = MaterialShapes.SoftBurst.toShape()
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = shape, modifier = Modifier.size(DOCK_BADGE_SIZE)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(Res.drawable.location_on_filled_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Jedna poloha v rozbaleném docku: mapa (ťuknutí otevře mapy platformy), u víc poloh jméno
 * a do kdy, a ovládání. Zastavit a upozornit jde jen VLASTNÍ polohu — cizí nanejvýš ukázat ve
 * zprávě, stejně jako pod mapou v konverzaci.
 */
@Composable
private fun DockedLocation(
    message: ChatMessage,
    name: String,
    showName: Boolean,
    onOpenMap: () -> Unit,
    onShowMessage: () -> Unit,
    onStop: () -> Unit,
    onProximityAlert: () -> Unit,
) {
    val location = message.media as? MessageMedia.Location ?: return
    val sharing = rememberLiveSharing(location)
    val color = MaterialTheme.colorScheme.onSurface
    Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp)) {
        StaticMap(
            latitude = location.latitude,
            longitude = location.longitude,
            heading = if (sharing) location.heading else 0,
            contentDescription = stringResource(Res.string.media_map),
            modifier = Modifier
                .fillMaxWidth()
                .height(DOCK_MAP_HEIGHT)
                .clip(RoundedCornerShape(DOCK_MAP_CORNER))
                .clickable(onClick = onOpenMap),
        )
        if (showName) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Text(
                liveSharingText(location, sharing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        // Tři tlačítka se česky na šířku telefonu vedle sebe nevejdou; přebytečné přejde pod.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            if (message.isOutgoing && sharing) {
                location.proximityAlertRadius?.let { radius ->
                    DetailsAction(Res.drawable.notifications_24dp, proximityAlertLabel(radius), color, onProximityAlert)
                }
                DetailsAction(Res.drawable.location_off_24dp, stringResource(Res.string.location_live_stop), color, onStop)
            }
            DetailsAction(Res.drawable.chat_bubble_24dp, stringResource(Res.string.location_dock_show_message), color, onShowMessage)
        }
    }
}

private val DOCK_MAP_HEIGHT = 160.dp
private val DOCK_MAP_CORNER = 20.dp
private val DOCK_EXPANDED_MAX = 440.dp
