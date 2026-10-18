package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sponsored_open
import eu.tintera.plume.resources.sponsored_recommended
import eu.tintera.plume.resources.sponsored_sponsored
import org.jetbrains.compose.resources.stringResource

/**
 * Sponzorovaná zpráva v kanálu.
 *
 * **Podle [Telegram API ToS](https://core.telegram.org/api/terms) je zobrazení povinné** a klient
 * do reklam nesmí zasahovat. Proto se nedá skrýt ani přeskočit a popisek tlačítka se bere
 * doslova z Telegramu, nepřekládá se.
 *
 * Vizuálně se drží stranou od bublin: je to vlastní karta přes celou šířku se štítkem
 * „Sponzorováno", aby si ji uživatel nespletl se zprávou od někoho z kanálu.
 */
@Composable
fun SponsoredCard(
    sponsored: SponsoredMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // `surfaceContainerLow` drží kartu o stupeň pod bublinami, takže nekonkuruje obsahu
        // konverzace, ale zůstává čitelná.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                // Telegram odlišuje placené od doporučených; uživatel má vědět, co vidí.
                text = stringResource(if (sponsored.isRecommended) Res.string.sponsored_recommended else Res.string.sponsored_sponsored),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (sponsored.title.isNotBlank()) {
                Text(
                    text = sponsored.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (sponsored.text.isNotBlank()) {
                Text(text = sponsored.text, style = MaterialTheme.typography.bodyMedium)
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
