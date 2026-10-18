package eu.tintera.plume.features.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send_options
import eu.tintera.plume.resources.expand_more_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * „Odeslat" v hlavičce editoru ankety a seznamu úkolů jako **split button** (M3 Expressive):
 * vlevo odeslání, vpravo šipka s nabídkou (bez zvuku, naplánovat — [onMenuOpenChange] otevře
 * nabídku, kterou kreslí volající vedle).
 *
 * Dřív se nabídka otevírala dlouhým stiskem obyčejného tlačítka, jako u odeslání zprávy. U pole
 * zprávy to uživatelé znají a aplikace k tomu ukáže nápovědu; tady nic nenapovídalo, že tlačítko
 * má schovanou nabídku. Šipka ji ukáže a otočí se, když je otevřená. Bez nabídky
 * ([menuAvailable] = false, třeba uložení upraveného seznamu) je to obyčejné tlačítko.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SendSplitButton(
    label: String,
    enabled: Boolean,
    menuAvailable: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!menuAvailable) {
        Button(onClick = onSend, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = modifier) { Text(label) }
        return
    }
    val rotation by animateFloatAsState(
        targetValue = if (menuOpen) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "send-options-chevron",
    )
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(onClick = onSend, enabled = enabled) { Text(label) }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(checked = menuOpen, onCheckedChange = onMenuOpenChange, enabled = enabled) {
                Icon(
                    painterResource(Res.drawable.expand_more_24dp),
                    contentDescription = stringResource(Res.string.conversation_send_options),
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = rotation },
                )
            }
        },
        modifier = modifier,
    )
}
