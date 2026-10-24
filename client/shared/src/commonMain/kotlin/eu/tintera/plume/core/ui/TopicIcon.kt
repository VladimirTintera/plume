package eu.tintera.plume.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.TopicIcon
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tag_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Ikona tématu: vlastní emoji, když ho téma má, jinak bublina v barvě tématu s počátečním
 * písmenem. Obecné téma má `#` — tak ho znají lidé z oficiálních klientů.
 *
 * **Tvar je bublina (jedno nároží stažené), ne kruh ani sušenka.** Tvary avatarů v aplikaci nesou
 * druh chatu (kruh člověk, sušenka skupina, čtverec kanál) a téma není ani jedno z toho.
 *
 * Barvu posílá server (paleta šesti barev), takže je to jediné místo, kde barva nejde z tématu
 * aplikace. Písmeno se proto barví podle jejího jasu, jinak by na žluté bílé nebylo vidět.
 */
@Composable
fun ForumTopicIcon(icon: TopicIcon, name: String, isGeneral: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val customEmojiId = icon.customEmojiId
    when {
        isGeneral -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(topicShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(
                painterResource(Res.drawable.tag_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.55f),
            )
        }
        customEmojiId != null -> CustomEmojiIcon(customEmojiId, size = size, modifier = modifier)
        else -> {
            val background = Color(0xFF000000.toInt() or icon.color)
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.size(size).clip(topicShape).background(background),
            ) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase().orEmpty(),
                    color = if (background.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
        }
    }
}

private val topicShape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 18)
