package eu.tintera.plume.features.devices

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.UnconfirmedDevice
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.devices_24dp
import eu.tintera.plume.resources.devices_unconfirmed_no
import eu.tintera.plume.resources.devices_unconfirmed_text
import eu.tintera.plume.resources.devices_unconfirmed_title
import eu.tintera.plume.resources.devices_unconfirmed_yes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * „Byli jste to vy?" u nového přihlášení — nahoře v seznamu chatů a v sekci Zařízení.
 *
 * Stejný tvar jako karta s aktualizací, ale v `tertiaryContainer`: nejde o novinku aplikace,
 * ale o účet, a dvě karty nad sebou se musí rozlišit. Chybová role by tu lhala — nové přihlášení
 * je skoro vždycky vlastní zařízení; karta se ptá, nestraší.
 *
 * Odmítnutí odhlašuje bez dalšího potvrzení: je to přesně ta akce, o kterou karta žádá, a kdo
 * se omylem odhlásí z vlastního zařízení, přihlásí se znovu.
 */
@Composable
fun UnconfirmedLoginCard(
    device: UnconfirmedDevice,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onTerminate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(CARD_CORNER),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.devices_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(Res.string.devices_unconfirmed_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            Res.string.devices_unconfirmed_text,
                            device.deviceModel,
                            device.location,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                // Tlačítka v terciární roli karty; výchozí `primary` by v ní působilo jako cizí prvek.
                TextButton(
                    onClick = onTerminate,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.devices_unconfirmed_no))
                }
                Button(
                    onClick = onConfirm,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.devices_unconfirmed_yes))
                }
            }
        }
    }
}

private val CARD_CORNER = 20.dp
