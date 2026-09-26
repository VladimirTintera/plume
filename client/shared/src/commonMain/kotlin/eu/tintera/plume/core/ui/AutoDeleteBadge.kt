package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_delete_summary
import eu.tintera.plume.resources.timer_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Odznak automatického mazání za názvem chatu: hodiny a zkratka doby („1 d").
 *
 * Za názvem, jako ověření a emoji status — je to vlastnost chatu, kterou má uživatel vidět, když
 * píše: co pošle, samo zmizí. Tlumená barva popředí, ne akcent: je to informace, ne výstraha.
 * Čtečka obrazovky dostane celou větu místo „hodiny, 1 d".
 */
@Composable
fun AutoDeleteBadge(seconds: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.auto_delete_summary, durationText(seconds))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .padding(start = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            painterResource(Res.drawable.timer_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = durationShortText(seconds),
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
