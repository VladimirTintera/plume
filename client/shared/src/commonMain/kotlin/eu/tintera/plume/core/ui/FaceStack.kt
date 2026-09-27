package eu.tintera.plume.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.tintera.plume.core.telegram.SenderInfo

/**
 * Hromádka tváří přes sebe — kdo naposledy hlasoval v anketě nebo komentoval příspěvek. Nejnovější
 * (první) leží navrchu a každou obtáčí lem v barvě plochy [ringColor], aby se od sousední
 * oddělila. Čtečce obrazovky nic neříká: jména jsou tam, kam hromádka vede („Kdo hlasoval",
 * vlákno komentářů); tady jde jen o to, že se něco děje a kdo v tom je.
 */
@Composable
fun FaceStack(
    faces: List<SenderInfo>,
    ringColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = FACE_SIZE,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(-size * FACE_OVERLAP),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { },
    ) {
        faces.forEachIndexed { index, face ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .zIndex((faces.size - index).toFloat())
                    .size(size + FACE_RING * 2)
                    .background(ringColor, CircleShape),
            ) {
                Avatar(chatId = face.id, title = face.title, avatarFileId = face.avatarFileId, size = size)
            }
        }
    }
}

private val FACE_SIZE = 20.dp
private val FACE_RING = 1.5.dp

/** Jak velký kus tváře zakryje ta nad ní. */
private const val FACE_OVERLAP = 0.3f
