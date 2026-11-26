package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FactCheck
import eu.tintera.plume.core.telegram.flagEmoji
import eu.tintera.plume.core.telegram.localizedCountryName
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.fact_check_24dp
import eu.tintera.plume.resources.fact_check_country
import eu.tintera.plume.resources.fact_check_source
import eu.tintera.plume.resources.fact_check_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kontrola faktů pod příspěvkem: nadpis s ikonou, poznámka ověřovatelů a odkud pochází.
 *
 * **Samostatná karta pod zprávou, ne díl bubliny** — visí zespodu jako tlačítka bota
 * ([WithBelowMessage]), takže vypadá stejně pod textem, pod holou fotkou i pod albem. Barva je
 * terciární role: je to cizí vložený obsah (jako popis webu u náhledu odkazu), ne slovo autora,
 * a musí se od zprávy odlišit na první pohled.
 */
@Composable
internal fun FactCheckCard(
    check: FactCheck,
    onLinkClick: (String) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.tertiaryContainer
    val content = MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        shape = RoundedCornerShape(BUBBLE_CORNER),
        color = background,
        contentColor = content,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.fact_check_24dp),
                    contentDescription = null,
                    tint = accentOn(background, MaterialTheme.colorScheme.tertiary, content),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(Res.string.fact_check_title),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = accentOn(background, MaterialTheme.colorScheme.tertiary, content),
                )
            }
            RichText(
                text = check.text,
                entities = check.entities,
                style = MaterialTheme.typography.bodyMedium,
                linkColor = accentOn(background, MaterialTheme.colorScheme.tertiary, content),
                onLinkClick = onLinkClick,
                onLongClick = onLongClick,
            )
            // Poznámku píší nezávislí ověřovatelé, ne autor — bez téhle věty by se dala číst jako
            // dodatek od kanálu. Země říká, pro koho platí.
            val country = check.countryCode?.let { code ->
                listOfNotNull(flagEmoji(code), localizedCountryName(code) ?: code).joinToString(" ")
            }
            Text(
                text = if (country != null) {
                    stringResource(Res.string.fact_check_country, country)
                } else {
                    stringResource(Res.string.fact_check_source)
                },
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = FACT_CHECK_NOTE_ALPHA),
            )
        }
    }
}

/** Mezera mezi kartou kontroly faktů a tlačítky bota pod ní. */
internal val FACT_CHECK_GAP = 4.dp


private const val FACT_CHECK_NOTE_ALPHA = 0.75f
