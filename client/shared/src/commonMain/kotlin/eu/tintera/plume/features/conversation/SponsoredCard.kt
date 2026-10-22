package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.sponsored_menu
import eu.tintera.plume.resources.sponsored_report
import eu.tintera.plume.resources.sponsored_open
import eu.tintera.plume.resources.sponsored_recommended
import eu.tintera.plume.resources.sponsored_sponsored
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sponzorovaná zpráva v kanálu. Reklama u bota je pruh nad konverzací ([SponsoredBar]).
 *
 * **Podle [Telegram API ToS](https://core.telegram.org/api/terms) je zobrazení povinné** a klient
 * do reklam nesmí zasahovat. Proto se nedá skrýt ani přeskočit a popisek tlačítka se bere
 * doslova z Telegramu, nepřekládá se.
 *
 * Vizuálně se drží stranou od bublin: je to vlastní karta přes celou šířku se štítkem
 * „Sponzorováno", aby si ji uživatel nespletl se zprávou od někoho z kanálu.
 *
 * ⋮ vedle štítku nabízí nahlášení ([onReport]), jen u reklamy, která ho dovolí
 * (`canBeReported`). Skrýt reklamu klient nesmí, nahlásit ji ano —
 * a mezi důvody od serveru bývá i skrytí reklam, které server sám podmíní Premium.
 */
@Composable
fun SponsoredCard(
    sponsored: SponsoredMessage,
    onClick: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeDefaults.Large,
        // `surfaceContainerLow` drží kartu o stupeň pod bublinami, takže nekonkuruje obsahu
        // konverzace, ale zůstává čitelná.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // Telegram odlišuje placené od doporučených; uživatel má vědět, co vidí.
                    text = stringResource(if (sponsored.isRecommended) Res.string.sponsored_recommended else Res.string.sponsored_sponsored),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (sponsored.canBeReported) SponsoredMenu(onReport)
            }

            if (sponsored.title.isNotBlank()) {
                Text(
                    text = sponsored.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (sponsored.text.isNotBlank()) {
                // Odkaz v textu je taky ťuknutí na reklamu: kliknutí se musí nahlásit a cíl
                // určuje reklama, ne entita.
                RichText(
                    text = sponsored.body.text,
                    entities = sponsored.body.entities,
                    style = MaterialTheme.typography.bodyMedium,
                    onLinkClick = { onClick() },
                    onEntityClick = { onClick() },
                )
            }

            sponsored.additionalInfo?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                // Popisek dodává Telegram — vlastní text by byl zásah do reklamy.
                Text(sponsored.buttonText.ifBlank { stringResource(Res.string.sponsored_open) })
            }
        }
    }
}

/**
 * ⋮ s nahlášením. Malé tlačítko (bez minimální dotykové plochy by štítek nad ním odskočil
 * o celých 48 dp), zarovnané do pravého rohu karty.
 */
@Composable
internal fun SponsoredMenu(onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.sponsored_menu)
    Box {
        WithTooltip(label) {
            IconButton(
                onClick = { open = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
            ) {
                Icon(
                    painterResource(Res.drawable.more_vert_24dp),
                    contentDescription = label,
                    modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
                )
            }
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOf(
                    MenuAction(label = stringResource(Res.string.sponsored_report), icon = Res.drawable.flag_24dp) {
                        open = false
                        onReport()
                    },
                ),
            ),
        )
    }
}
