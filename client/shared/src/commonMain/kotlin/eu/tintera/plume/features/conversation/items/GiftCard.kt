package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.GiftKind
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.premiumPeriod
import eu.tintera.plume.core.telegram.tonAmount
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.content_gift
import eu.tintera.plume.resources.gift_backdrop
import eu.tintera.plume.resources.gift_code_hint
import eu.tintera.plume.resources.gift_code_title
import eu.tintera.plume.resources.gift_code_unclaimed
import eu.tintera.plume.resources.gift_converted
import eu.tintera.plume.resources.gift_from
import eu.tintera.plume.resources.gift_model
import eu.tintera.plume.resources.gift_premium_title
import eu.tintera.plume.resources.gift_refunded
import eu.tintera.plume.resources.gift_stars_giveaway
import eu.tintera.plume.resources.gift_symbol
import eu.tintera.plume.resources.gift_to
import eu.tintera.plume.resources.gift_ton_title
import eu.tintera.plume.resources.gift_transferred
import eu.tintera.plume.resources.gift_upgrade_refunded
import eu.tintera.plume.resources.gift_upgraded
import eu.tintera.plume.resources.gift_value_stars
import eu.tintera.plume.resources.giveaway_code_copied
import eu.tintera.plume.resources.giveaway_copy_code
import eu.tintera.plume.resources.giveaway_prize_stars
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dárek jako karta uprostřed konverzace. Je to servisní oznámení (jako u oficiálních klientů),
 * ne zpráva od někoho, takže nemá bublinu ani stranu — jen samolepku dárku, co se darovalo,
 * kdo komu a vzkaz.
 *
 * Proměnit, vylepšit ani předat dárek Plume neumí, takže karta nic z toho nenabízí. Jediná akce
 * je zkopírovat dárkový kód Premium; uplatnit ho jde v oficiální aplikaci a věta pod ním to říká.
 */
@Composable
internal fun GiftCard(gift: MessageMedia.Gift, onAction: (MessageAction) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        // Všechny karty stejně široké: se šířkou podle obsahu byla karta „Dárek" užší než ta vedle.
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH).fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GiftSticker(gift)
            Text(
                text = giftTitle(gift.kind),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            val parties = listOfNotNull(
                gift.senderName?.let { stringResource(Res.string.gift_from, it) },
                gift.receiverName?.let { stringResource(Res.string.gift_to, it) },
            )
            if (parties.isNotEmpty()) {
                Text(
                    text = parties.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
            if (gift.text.isNotBlank()) {
                Text(
                    text = gift.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Details(gift.kind, muted)
            (gift.kind as? GiftKind.PremiumCode)?.takeIf { it.code.isNotBlank() }?.let { code ->
                GiftCode(code.code, onAction)
            }
        }
    }
}

/**
 * Samolepka dárku. Sběratelský dárek má pod ní pozadí ve svých barvách — kruhový přechod od
 * středu k okraji, jako u oficiálních klientů; bez něj by se od běžného dárku lišil jen jménem.
 */
@Composable
private fun GiftSticker(gift: MessageMedia.Gift) {
    val sticker = gift.sticker
    val upgraded = gift.kind as? GiftKind.Upgraded
    if (upgraded != null) {
        val center = Color(OPAQUE or upgraded.centerColor)
        val edge = Color(OPAQUE or upgraded.edgeColor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BACKDROP_HEIGHT)
                .clip(MaterialTheme.shapes.large)
                .background(Brush.radialGradient(listOf(center, edge))),
            contentAlignment = Alignment.Center,
        ) {
            if (sticker != null) StickerPreview(sticker = sticker, playing = true, modifier = Modifier.size(STICKER_SIZE))
        }
    } else if (sticker != null) {
        StickerPreview(sticker = sticker, playing = true, modifier = Modifier.size(STICKER_SIZE))
    }
}

@Composable
private fun giftTitle(kind: GiftKind): String = when (kind) {
    is GiftKind.Regular, GiftKind.RefundedUpgrade -> stringResource(Res.string.content_gift)
    is GiftKind.Upgraded -> "${kind.title} #${kind.number}"
    is GiftKind.Premium -> stringResource(Res.string.gift_premium_title, premiumPeriod(kind.months, kind.days).resolve())
    is GiftKind.PremiumCode -> stringResource(Res.string.gift_code_title)
    is GiftKind.Stars -> pluralStringResource(
        Res.plurals.giveaway_prize_stars,
        kind.count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        kind.count.grouped(),
    )
    is GiftKind.Ton -> stringResource(Res.string.gift_ton_title, tonAmount(kind.nanoAmount).resolve())
}

/** Co se s dárkem stalo a co ho dělá jedinečným — jedna ztlumená řádka na údaj. */
@Composable
private fun Details(kind: GiftKind, muted: Color) {
    val lines = buildList {
        when (kind) {
            is GiftKind.Regular -> {
                if (kind.starCount > 0) add(starLine(Res.plurals.gift_value_stars, kind.starCount))
                when {
                    kind.wasRefunded -> add(stringResource(Res.string.gift_refunded))
                    kind.wasUpgraded -> add(stringResource(Res.string.gift_upgraded))
                    kind.wasConverted && kind.sellStarCount > 0 ->
                        add(starLine(Res.plurals.gift_converted, kind.sellStarCount))
                }
            }
            is GiftKind.Upgraded -> {
                if (kind.modelName.isNotBlank()) add(stringResource(Res.string.gift_model, kind.modelName))
                if (kind.symbolName.isNotBlank()) add(stringResource(Res.string.gift_symbol, kind.symbolName))
                if (kind.backdropName.isNotBlank()) add(stringResource(Res.string.gift_backdrop, kind.backdropName))
                if (kind.wasTransferred) add(stringResource(Res.string.gift_transferred))
            }
            GiftKind.RefundedUpgrade -> add(stringResource(Res.string.gift_upgrade_refunded))
            is GiftKind.PremiumCode -> {
                add(stringResource(Res.string.gift_premium_title, premiumPeriod(kind.months, kind.days).resolve()))
                if (kind.isUnclaimed) add(stringResource(Res.string.gift_code_unclaimed))
            }
            is GiftKind.Stars -> if (kind.fromGiveaway) add(stringResource(Res.string.gift_stars_giveaway))
            is GiftKind.Premium, is GiftKind.Ton -> Unit
        }
    }
    if (lines.isEmpty()) return
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun starLine(resource: org.jetbrains.compose.resources.PluralStringResource, count: Long): String =
    pluralStringResource(resource, count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), count.grouped())

/**
 * Dárkový kód Premium: dá se vybrat i zkopírovat. Potvrzení je fajfka v tlačítku — snackbar
 * by u servisního oznámení nebylo kde čekat a schránku na Androidu pod 13 nic neohlásí.
 */
@Composable
private fun GiftCode(code: String, onAction: (MessageAction) -> Unit) {
    var copied by remember(code) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        SelectionContainer {
            Text(
                text = code,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
    FilledTonalButton(
        onClick = {
            onAction(MessageAction.CopyText(code))
            copied = true
        },
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(if (copied) Res.drawable.check_24dp else Res.drawable.content_copy_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(if (copied) Res.string.giveaway_code_copied else Res.string.giveaway_copy_code))
        }
    }
    Text(
        text = stringResource(Res.string.gift_code_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private val CARD_MAX_WIDTH = 280.dp
private val STICKER_SIZE = 112.dp
private val BACKDROP_HEIGHT = 140.dp

/** Barvy pozadí sběratelského dárku chodí jako RGB bez alfy. */
private const val OPAQUE = 0xFF000000.toInt()
