package eu.tintera.plume.features.chatlabels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.chatlabels.ChatLabel
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.labels_filter
import org.jetbrains.compose.resources.stringResource

/**
 * Filtr seznamu chatů podle vlastního štítku: čipy štítků, které nějaký chat má, nad chaty.
 * Jeden vybraný naráz; ťuknutí na vybraný filtr zruší (řádek „Vše" by jen zabíral místo).
 * Vodorovně roluje, když se štítky nevejdou.
 */
@Composable
internal fun LabelFilterRow(labels: List<ChatLabel>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.labels_filter)
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            FilterChip(
                selected = label.id == selected,
                onClick = { onSelect(label.id) },
                label = { Text(label.name) },
                leadingIcon = { LabelDot(label.color) },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}
