package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.scam_warning_dismiss
import eu.tintera.plume.resources.scam_warning_money
import eu.tintera.plume.resources.scam_warning_note
import eu.tintera.plume.resources.scam_warning_phishing
import eu.tintera.plume.resources.scam_warning_prize
import eu.tintera.plume.resources.scam_warning_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Varování pod zprávou, která vypadá jako podvod (posouzení modelem v zařízení, `ScamWarnings`).
 *
 * - **Karta pod zprávou jako kontrola faktů** ([FactCheckCard], [WithBelowMessage]), ne díl
 *   bubliny: je to slovo aplikace, ne odesílatele, a musí to být vidět na první pohled.
 * - **Chybová role (`errorContainer`)** — jediná, která říká „pozor". Tvar a sazba jsou jinak
 *   tytéž jako u kontroly faktů, aby varování nevypadalo jako cizí prvek.
 * - **Věta je naše podle druhu podvodu**, ne text od modelu, a pod ní stojí, že jde o odhad.
 *   Model se umí splést a karta to nesmí zamlčet.
 * - **Skrýt jde jedním ťuknutím.** Varování u legitimní zprávy otravuje, tak ať je otrava krátká.
 */
@Composable
internal fun ScamWarningCard(
    kind: ScamKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.errorContainer
    val content = MaterialTheme.colorScheme.onErrorContainer
    val accent = accentOn(background, MaterialTheme.colorScheme.error, content)
    Surface(
        shape = RoundedCornerShape(SCAM_WARNING_CORNER),
        color = background,
        contentColor = content,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = MESSAGE_PADDING_H, end = 4.dp, top = MESSAGE_PADDING_V),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.error_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(Res.string.scam_warning_title),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = accent,
                )
            }
            Text(
                text = stringResource(
                    when (kind) {
                        ScamKind.Phishing -> Res.string.scam_warning_phishing
                        ScamKind.Prize -> Res.string.scam_warning_prize
                        ScamKind.Money -> Res.string.scam_warning_money
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = MESSAGE_PADDING_H - 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.scam_warning_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = SCAM_WARNING_NOTE_ALPHA),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = accent),
                ) {
                    Text(stringResource(Res.string.scam_warning_dismiss))
                }
            }
        }
    }
}

private val SCAM_WARNING_CORNER = 16.dp

private const val SCAM_WARNING_NOTE_ALPHA = 0.75f
