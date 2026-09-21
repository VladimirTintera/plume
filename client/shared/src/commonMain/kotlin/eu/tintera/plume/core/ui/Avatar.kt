package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bookmark_filled_24dp
import org.jetbrains.compose.resources.painterResource
import kotlin.math.absoluteValue

/**
 * Avatar chatu.
 *
 * Když chat obrázek nemá (velká část soukromých chatů a skupin), vykreslí se iniciály na barevném
 * kruhu. Barva se **odvozuje z id chatu**, ne náhodně — musí být stabilní mezi spuštěními, jinak
 * by uživatel přišel o to, že chaty poznává „podle barvy".
 */
@Composable
fun Avatar(
    chatId: Long,
    title: String,
    avatarFileId: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    /**
     * Uložené zprávy: záložka místo fotky a jména vlastního účtu, jako ve všech klientech.
     * Výslovně od volajícího — viz [LocalSavedMessagesChatId].
     */
    isSavedMessages: Boolean = false,
    /**
     * Tvar podle druhu chatu ([avatarShape]). Výchozí kruh je člověk — i odesílatel ve skupině,
     * reakce a zhlédnutí, kde jde vždycky o člověka.
     */
    shape: Shape = CircleShape,
) {

    if (isSavedMessages) {
        Surface(
            modifier = modifier.size(size),
            shape = shape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(Res.drawable.bookmark_filled_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
        return
    }

    if (avatarFileId != null) {
        RemoteImage(
            fileId = avatarFileId,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
        )
        return
    }

    val palette = avatarPalette()
    val (background, foreground) = palette[chatId.hashCode().absoluteValue % palette.size]

    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = background,
        contentColor = foreground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title.initials(),
                // Písmo se škáluje s velikostí avataru, aby stejná komponenta fungovala
                // v seznamu i v hlavičce konverzace.
                fontSize = (size.value * 0.38f).sp,
                textAlign = TextAlign.Center,
                // Iniciály jsou značka, ne text ke čtení — nesou je výrazné písmo nadpisů.
                // Výška řádku ze škály (32 sp) by byla vyšší než malý avatar; přirozená výška
                // písma se škáluje s `fontSize`.
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = TextUnit.Unspecified),
            )
        }
    }
}

/**
 * Tvar avataru podle druhu chatu: člověk kruh, skupina čtyřlaločná „sušenka", kanál zaoblený
 * čtverec. Druh se tak pozná dřív, než se dočte název — stejně jako v Google Messages.
 *
 * **Statický tvar nese DRUH, morf nese STAV** ([ShapeMorphs]). Avatary se proto nehýbou a
 * nesahají po tvarech, které už něco znamenají (šesti- a dvanáctistranná sušenka, SoftBurst).
 * Tvar musí být stejný všude, kam avatar letí sdíleným prvkem (seznam → hlavička konverzace →
 * profil); jinak by uprostřed letu přeskočil.
 */
@Composable
fun ChatKind.avatarShape(): Shape = when (this) {
    ChatKind.Private -> CircleShape
    ChatKind.Group -> MaterialShapes.Cookie4Sided.toShape()
    ChatKind.Channel -> MaterialShapes.Square.toShape()
}

/**
 * Až dvě iniciály. Bere se první písmeno prvních dvou slov — u „GFN Catalog - Updates" tedy „GC",
 * ne „GC-U". Emoji a interpunkce na začátku názvu se přeskakují, jinak by avatar ukázal pomlčku.
 */
private fun String.initials(): String {
    val words = trim()
        .split(' ', '\t', '\n')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }

    return when {
        words.isEmpty() -> "?"
        else -> words.take(2).joinToString("").uppercase()
    }
}

/**
 * Paleta pro avatary bez obrázku, jako dvojice pozadí + popředí.
 *
 * Bere se z aktuálního barevného schématu, takže respektuje Material You i tmavý režim — natvrdo
 * zadané barvy by v dynamickém tématu vyčnívaly. Dvojice musí zůstat spárované; `onPrimaryContainer`
 * na `tertiaryContainer` je kontrastní nesmysl, který M3 negarantuje.
 */
@Composable
private fun avatarPalette(): List<Pair<Color, Color>> = with(MaterialTheme.colorScheme) {
    listOf(
        primaryContainer to onPrimaryContainer,
        secondaryContainer to onSecondaryContainer,
        tertiaryContainer to onTertiaryContainer,
        errorContainer to onErrorContainer,
    )
}
