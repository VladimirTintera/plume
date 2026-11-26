package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.Price
import eu.tintera.plume.core.telegram.parts
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.photoFrame
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.product_paid
import eu.tintera.plume.resources.product_price
import eu.tintera.plume.resources.product_price_stars
import eu.tintera.plume.resources.product_test
import eu.tintera.plume.resources.star_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Faktura nebo hra od bota: obrázek, název, popis a u faktury cena.
 *
 * Karta sama na ťuknutí nereaguje. Zaplatit a hrát se dá jen tlačítkem bota pod zprávou
 * („Zaplatit", „Hrát"), které Telegram ke zprávě přikládá vždycky — a to už říká, že to Plume
 * zatím neumí. Ťuknutí i dlouhý stisk tak dostane bublina jako u textu. Obrázek do prohlížeče
 * nevede: prohlížeč otevírá fotku ZPRÁVY a tahle zpráva žádnou fotku nemá.
 *
 * Obrázek zabírá celou šířku bubliny (jako fotka s popiskem, `fillWidth`), text se zalomí na ni.
 */
@Composable
internal fun ProductCard(product: MessageMedia.Product, accent: Color) {
    val contentAccent = accent.orContentColor()
    val photo = product.photo
    if (photo == null) {
        ProductText(product, contentAccent, width = null)
        return
    }
    BoxWithConstraints {
        val ratio = if (photo.width > 0 && photo.height > 0) photo.width.toFloat() / photo.height else 1f
        val frame = photoFrame(maxWidth, ratio, fillWidth = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteImage(
                fileId = photo.fileId,
                thumbnailFileId = photo.thumbnailFileId,
                placeholder = photo.minithumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(frame).clip(ShapeDefaults.Medium),
            )
            ProductText(product, contentAccent, width = frame.width)
        }
    }
}

@Composable
private fun ProductText(product: MessageMedia.Product, accent: Color, width: Dp?) {
    Column(
        modifier = if (width != null) Modifier.width(width) else Modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = product.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (product.description.isNotBlank()) {
            Text(
                text = product.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        product.price?.let { price ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PriceLabel(price, accent)
                // Zaplaceno a testovací platba jsou údaje navíc; ztlumené, ať nekonkurují ceně.
                val notes = listOfNotNull(
                    stringResource(Res.string.product_paid).takeIf { product.isPaid },
                    stringResource(Res.string.product_test).takeIf { product.isTest },
                )
                if (notes.isNotEmpty()) {
                    Text(
                        text = notes.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = NOTE_ALPHA),
                    )
                }
            }
        }
    }
}

/**
 * Cena: hvězdy ikonou (jako u placeného média), jiné měny kódem za částkou. Desetinný oddělovač
 * patří jazyku (`decimal_number`), stejně jako u velikosti souboru.
 */
@Composable
private fun PriceLabel(price: Price, accent: Color) {
    val parts = price.parts()
    val number = parts.fraction?.let { stringResource(Res.string.decimal_number, parts.whole.grouped(), it) }
        ?: parts.whole.grouped()
    val style = MaterialTheme.typography.titleMedium.tabularNumbers()
    if (!price.isStars) {
        Text(
            text = stringResource(Res.string.product_price, number, price.currency),
            style = style,
            color = accent,
        )
        return
    }
    val description = pluralStringResource(
        Res.plurals.product_price_stars,
        parts.whole.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        number,
    )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(Res.drawable.star_filled_24dp),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(text = number, style = style, color = accent)
    }
}

private const val DESCRIPTION_MAX_LINES = 6
private const val NOTE_ALPHA = 0.7f
