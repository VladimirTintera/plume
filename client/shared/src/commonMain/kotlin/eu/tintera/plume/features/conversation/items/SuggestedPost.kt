package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SuggestedPost
import eu.tintera.plume.core.telegram.SuggestedPostState
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.suggested_post_approve
import eu.tintera.plume.resources.suggested_post_approved
import eu.tintera.plume.resources.suggested_post_decline
import eu.tintera.plume.resources.suggested_post_declined
import eu.tintera.plume.resources.suggested_post_pending
import eu.tintera.plume.resources.suggested_post_price_stars
import eu.tintera.plume.resources.suggested_post_price_ton
import eu.tintera.plume.resources.suggested_post_publish_at
import eu.tintera.plume.resources.suggested_post_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Navrhovaný příspěvek pod zprávou v přímých zprávách kanálu: co to je, v jakém je stavu, cena
 * a termín, a správci Schválit / Odmítnout.
 *
 * **Samostatná karta pod zprávou** jako kontrola faktů ([FactCheckCard]) — návrh může být text,
 * fotka i album a karta má vypadat všude stejně. Sekundární role: je to stav zprávy, ne cizí
 * vložený obsah (ten má terciární).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SuggestedPostCard(
    post: SuggestedPost,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer
    val accent = accentOn(background, MaterialTheme.colorScheme.secondary, content)
    Surface(
        shape = RoundedCornerShape(SUGGESTED_POST_CORNER),
        color = background,
        contentColor = content,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(Res.drawable.campaign_24dp), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(Res.string.suggested_post_title),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = accent,
                )
            }
            Text(
                text = stringResource(
                    when (post.state) {
                        SuggestedPostState.Pending -> Res.string.suggested_post_pending
                        SuggestedPostState.Approved -> Res.string.suggested_post_approved
                        SuggestedPostState.Declined -> Res.string.suggested_post_declined
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            val price = post.stars?.let { pluralStringResource(Res.plurals.suggested_post_price_stars, it.toInt(), it) }
                ?: post.tonCents?.let { stringResource(Res.string.suggested_post_price_ton, formatTon(it)) }
            price?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            if (post.sendDate > 0) {
                Text(
                    stringResource(Res.string.suggested_post_publish_at, post.sendDate.asRelativeTime()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (post.canApprove || post.canDecline) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (post.canDecline) {
                        OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.suggested_post_decline))
                        }
                    }
                    if (post.canApprove) {
                        // Plné tlačítko, ne tónové: tónové má barvu `secondaryContainer` — tutéž
                        // jako karta — a splynulo s ní (ověřeno na emulátoru).
                        Button(onClick = onApprove, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.suggested_post_approve))
                        }
                    }
                }
            }
        }
    }
}

/** Setiny TON jako číslo s nejvýš dvěma desetinnými místy („12,5"). */
internal fun formatTon(cents: Long): String {
    val whole = cents / 100
    val rest = cents % 100
    return when {
        rest == 0L -> whole.toString()
        rest % 10 == 0L -> "$whole,${rest / 10}"
        else -> "$whole,${rest.toString().padStart(2, '0')}"
    }
}

private val SUGGESTED_POST_CORNER = 16.dp
