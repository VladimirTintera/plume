package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.telegramAccentColor
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sponsored_bar_ad
import eu.tintera.plume.resources.sponsored_recommended
import org.jetbrains.compose.resources.stringResource

/**
 * Reklama v chatu s botem — pruh nad konverzací, tvarem i sklem jako pruh proti spamu
 * ([ChatBarSurface]). Tak ji předepisuje [specifikace](https://core.telegram.org/api/sponsored-messages)
 * („akin to an action bar"); u kanálu zůstává karta pod zprávami ([SponsoredCard]).
 *
 * - **Před názvem stojí „Reklama"** (u doporučených „Doporučeno") v barvě odkazů — předponu
 *   přidává klient, ve jméně inzerenta není.
 * - **Barva reklamy** (`accent_color_id`) nese název inzerenta a proužek vlevo, jako citace.
 *   Proti sklu pruhu se ověřuje ([accentOn]); kde by zmizela, ustoupí barvě textu.
 * - **Fotka inzerenta jako náhled vpravo.**
 * - **Text celý, bez ořezu.** Zhlédnutí se smí hlásit, až je vidět celý text; reklama u bota
 *   má krátký text, takže pruh nevyroste do nesmyslné výšky.
 * - Ťuknutí kamkoli (i na odkaz v textu) je ťuknutí na reklamu ([onClick]); ⋮ s nahlášením jen
 *   u reklamy, která ho dovolí.
 */
@Composable
internal fun SponsoredBar(
    sponsored: SponsoredMessage,
    onClick: () -> Unit,
    onReport: () -> Unit,
    /** Mléčné sklo nad seznamem, jako u ostatních pruhů ([ChatBarSurface]). */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = accentOn(surface, telegramAccentColor(sponsored.accentColorId), onSurface)
    val prefixColor = accentOn(surface, MaterialTheme.colorScheme.primary, onSurface)
    val prefix = stringResource(if (sponsored.isRecommended) Res.string.sponsored_recommended else Res.string.sponsored_bar_ad)
    val title = buildAnnotatedString {
        withStyle(SpanStyle(color = prefixColor)) { append(prefix) }
        if (sponsored.title.isNotBlank()) {
            append("  ")
            withStyle(SpanStyle(color = accent)) { append(sponsored.title) }
        }
    }

    ChatBarSurface(glass = glass, modifier = modifier) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = if (sponsored.canBeReported) 4.dp else 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    // Proužek přes celou výšku textu kreslí `drawBehind`: `IntrinsicSize` by se
                    // s RichText (SubcomposeLayout u vlastních emoji) nesnesl.
                    .drawBehind {
                        val width = STRIPE_WIDTH.toPx()
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset.Zero,
                            size = Size(width, size.height),
                            cornerRadius = CornerRadius(width / 2),
                        )
                    }
                    .padding(start = STRIPE_WIDTH + 10.dp, end = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sponsored.body.text.isNotBlank()) {
                    RichText(
                        text = sponsored.body.text,
                        entities = sponsored.body.entities,
                        style = MaterialTheme.typography.bodyMedium,
                        linkColor = accentOn(surface, MaterialTheme.colorScheme.primary, onSurface),
                        onLinkClick = { onClick() },
                        onEntityClick = { onClick() },
                    )
                }
            }
            sponsored.photo?.let { photo ->
                RemoteImage(
                    fileId = photo.thumbnailFileId ?: photo.fileId,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(THUMBNAIL)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            if (sponsored.canBeReported) SponsoredMenu(onReport)
        }
    }
}

private val STRIPE_WIDTH = 3.dp
private val THUMBNAIL = 44.dp
