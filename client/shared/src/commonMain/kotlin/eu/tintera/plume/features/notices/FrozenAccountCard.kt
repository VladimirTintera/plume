package eu.tintera.plume.features.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.AccountFreeze
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.notice_frozen_appeal
import eu.tintera.plume.resources.notice_frozen_text
import eu.tintera.plume.resources.notice_frozen_text_no_date
import eu.tintera.plume.resources.notice_frozen_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zmrazený účet — nahoře v seznamu chatů, nad ostatními kartami. Stejný tvar jako karta
 * s aktualizací a s novým přihlášením, ale v chybové roli: účet je omezený a bez odvolání
 * zanikne, to je jediná karta, která právem straší.
 *
 * Nedá se zavřít. Zmizí, až TDLib ohlásí, že účet zmrazený není — dokud je, je to to
 * nejdůležitější, co aplikace o účtu ví (psát ani zakládat chaty zmrazený účet nemůže).
 *
 * Akcent jen přes [accentOn]: v paletě se zvýšeným kontrastem by `error` na `errorContainer`
 * splynul (tatáž past jako u karty s aktualizací).
 */
@Composable
fun FrozenAccountCard(
    freeze: AccountFreeze,
    onAppeal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = accentOn(colors.errorContainer, colors.error, colors.onErrorContainer)
    val onAccent = if (accent == colors.error) colors.onError else colors.errorContainer

    Surface(
        shape = RoundedCornerShape(CARD_CORNER),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.block_24dp), contentDescription = null, tint = onAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(Res.string.notice_frozen_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (freeze.deletionAt > 0) {
                            stringResource(Res.string.notice_frozen_text, freeze.deletionAt.asDate())
                        } else {
                            stringResource(Res.string.notice_frozen_text_no_date)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
            val link = freeze.appealLink
            if (link != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { onAppeal(link) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(Res.string.notice_frozen_appeal))
                    }
                }
            } else {
                // Bez tlačítka by spodní okraj karty (8 dp, počítaný s tlačítky) byl moc těsný.
                Box(Modifier.size(8.dp))
            }
        }
    }
}

private val CARD_CORNER = 20.dp
