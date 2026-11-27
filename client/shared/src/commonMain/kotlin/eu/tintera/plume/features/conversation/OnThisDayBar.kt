package eu.tintera.plume.features.conversation

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.features.conversation.items.summaryLine
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.history_24dp
import eu.tintera.plume.resources.on_this_day_preview
import eu.tintera.plume.resources.on_this_day_title
import eu.tintera.plume.resources.sender_you
import eu.tintera.plume.resources.statistics_sender_messages
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh „Před rokem" ([OnThisDay]) ve skladbě ostatních pilulek nad konverzací: nadpis „Před
 * rokem" / „Před 3 lety", pod ním „N zpráv · Kdo: první zpráva" (v kanálu bez „Kdo:" — autorem
 * je kanál sám). Ťuknutí skočí na začátek toho dne, křížek pruh zavře do konce dne.
 */
@Composable
internal fun OnThisDayBar(
    memory: OnThisDay,
    /** Kdo píše v soukromém chatu, kde zpráva odesílatele nenese; v kanálu `null`. */
    partnerName: String?,
    /** Kanál: příspěvky píše kanál sám, jméno před ukázkou by jen opakovalo hlavičku. */
    isChannel: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    glass: Modifier,
    modifier: Modifier = Modifier,
) {
    val preview = memory.preview
    val sender = when {
        isChannel -> null
        preview.isOutgoing -> stringResource(Res.string.sender_you)
        else -> preview.senderName ?: partnerName
    }
    val text = preview.summaryLine()
    val line = if (sender.isNullOrBlank()) text else stringResource(Res.string.on_this_day_preview, sender, text)
    val count = if (memory.capped) "${memory.count}$PLUS" else memory.count.toString()
    BarPill(
        title = pluralStringResource(Res.plurals.on_this_day_title, memory.yearsAgo, memory.yearsAgo),
        // Počet napřed: ukázka je dlouhá a na jeden řádek se nevejde, počet by se usekl (viděno na emulátoru).
        subtitle = listOf(pluralStringResource(Res.plurals.statistics_sender_messages, memory.count, count), line)
            .filter { it.isNotBlank() }
            .joinToString(SEPARATOR),
        onClick = onOpen,
        glass = glass,
        modifier = modifier,
        leading = { BarBadge(Res.drawable.history_24dp) },
        trailing = {
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
            }
        },
    )
}

private const val PLUS = "+"
private const val SEPARATOR = " · "
