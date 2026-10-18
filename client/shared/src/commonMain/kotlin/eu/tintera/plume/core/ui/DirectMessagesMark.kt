package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.direct_topics_subtitle
import eu.tintera.plume.resources.mail_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Obálka před názvem chatu přímých zpráv kanálu. Chat nese název kanálu, takže by bez ní
 * v seznamu vypadal jako tentýž kanál podruhé. Stejné místo a velikost jako zámek tajného chatu.
 */
@Composable
fun DirectMessagesMark(
    tint: Color = LocalContentColor.current,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        painterResource(Res.drawable.mail_24dp),
        contentDescription = stringResource(Res.string.direct_topics_subtitle),
        tint = tint,
        modifier = modifier.size(size),
    )
}
