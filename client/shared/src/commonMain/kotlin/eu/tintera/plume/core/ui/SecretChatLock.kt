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
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.secret_chat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zámek před jménem tajného chatu — v seznamu i v hlavičce. Protějšek s tajným chatem má obvykle
 * i obyčejný chat se stejným jménem a fotkou; bez zámku by je nešlo rozeznat.
 */
@Composable
fun SecretChatLock(
    tint: Color = LocalContentColor.current,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        painterResource(Res.drawable.lock_24dp),
        contentDescription = stringResource(Res.string.secret_chat),
        tint = tint,
        modifier = modifier.size(size),
    )
}
