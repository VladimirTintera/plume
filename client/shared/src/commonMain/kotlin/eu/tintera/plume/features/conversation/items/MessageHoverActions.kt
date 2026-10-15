package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_message_options
import eu.tintera.plume.resources.menu_reply
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.reply_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pilulka u zprávy pod kurzorem myši: čas, u vlastní zprávy stav doručení, Odpovědět a ⋮
 * (nabídka nad zprávou, totéž co podržení nebo pravé tlačítko). Na desktopu se čas ani odpověď jinak nedaly najít — obojí je tah
 * (odkrývání časů doleva, odpověď doprava), na který myší nikdo nepřijde.
 *
 * - **Jen u zprávy pod kurzorem**, ne časy u všech: konverzace při přejíždění myší nebliká
 *   a nic se neposouvá. Čas u všech zpráv dál ukáže tah doleva.
 * - **Na volné straně bubliny** (u příchozí vpravo, u odchozí vlevo), za značkami efektu
 *   a kontextu. Do layoutu nezasahuje ([outsideBubble]), takže se zpráva při najetí nepřeskládá.
 * - **Odpovědět jen tam, kam se smí psát** ([onReply] `null` = nekreslí se).
 * - **Stav doručení ([delivery]) je tu kvůli desktopu**: na dotyku si odznak „přečteno" u starší
 *   zprávy vyžádá ťuknutí, jenže na desktopu ťuknutí patří výběru textu a odznak pod bublinou
 *   by ji při každém kliknutí posunul (výběr pak ujel).
 * - **Bez minimální dotykové plochy 48 dp**: ukazuje se jen při najetí myší (a perem), takže by
 *   ji jen zbytečně nafoukla nad výšku jednořádkové bubliny.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageHoverActions(
    visible: Boolean,
    timestamp: Int,
    /** Stav doručení vlastní zprávy; `null` u příchozí. */
    delivery: Delivery?,
    onReply: (() -> Unit)?,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            // Kde se na volnou stranu nevejde, překryje kus bubliny — stín ji od ní oddělí.
            shadowElevation = 2.dp,
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 2.dp)) {
                    Text(
                        text = timestamp.asTimeOfDay(),
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    delivery?.let { DeliveryStateIcon(it, Modifier.padding(end = 2.dp)) }
                    if (onReply != null) HoverActionButton(Res.drawable.reply_24dp, stringResource(Res.string.menu_reply), onReply)
                    HoverActionButton(Res.drawable.more_vert_24dp, stringResource(Res.string.conversation_message_options), onOptions)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HoverActionButton(icon: DrawableResource, label: String, onClick: () -> Unit) {
    WithTooltip(label) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
        ) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize))
        }
    }
}

/**
 * Postaví prvek VEDLE bubliny na její volné straně, o [gap] od okraje, bez vlivu na layout:
 * zabere šířku 0 (v `Box` bubliny zarovnaný ke spodnímu rohu volné strany) a obsah se položí
 * ven. Tak to dělají i značky efektu a kontextu (`offset`), jen tady se šířka obsahu nezná
 * předem.
 *
 * **Z řádku [row] nevyjede.** Široká bublina na úzkém displeji nechá volné straně méně místa,
 * než pilulka potřebuje, a ta pak přetekla za okraj obrazovky (ověřeno na emulátoru: z času
 * zbylo „12"). Posune se proto dovnitř a kus bubliny překryje. Souřadnice řádku se čtou až při
 * pokládání, takže posun nic nerekomponuje.
 */
internal fun Modifier.outsideBubble(isOutgoing: Boolean, gap: Dp, row: () -> LayoutCoordinates?): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, placeable.height) {
        val wanted = if (isOutgoing) -placeable.width - gap.roundToPx() else gap.roundToPx()
        val rowCoordinates = row()?.takeIf { it.isAttached }
        val here = coordinates?.takeIf { it.isAttached }
        val x = if (rowCoordinates == null || here == null) {
            wanted
        } else {
            // Kde by pilulka v řádku začínala, a o kolik ji vrátit, aby se vešla.
            val start = rowCoordinates.localPositionOf(here, Offset.Zero).x.roundToInt() + wanted
            val fitted = start.coerceIn(0, (rowCoordinates.size.width - placeable.width).coerceAtLeast(0))
            wanted + (fitted - start)
        }
        placeable.place(x, 0)
    }
}
