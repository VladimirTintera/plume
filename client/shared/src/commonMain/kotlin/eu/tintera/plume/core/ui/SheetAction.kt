package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Řádek nabídky v bottom sheetu — akce nad chatem v seznamu i nad členem skupiny.
 *
 * Sdílený kus, ne kopie v každé nabídce: dvě samostatné verze by se rozešly hned při prvním
 * ladění odsazení a nabídky by se pak lišily podle toho, odkud se otevřely.
 */
@Composable
fun SheetActionRow(
    /** `null` = varianta položky nad ní; místo ikony zůstane odsazení, aby popisky lícovaly. */
    icon: DrawableResource?,
    label: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * Bez ikony nechat místo, aby popisek lícoval s položkami nad ním (varianty položky). Seznam,
     * ve kterém ikonu nemá žádná položka, místo držet nemá — text by byl odsazený bez důvodu.
     */
    reserveIconSpace: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            } else if (reserveIconSpace) {
                Spacer(Modifier.size(20.dp))
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/**
 * Tvar položky v nabídce: velké zaoblení nahoře u první a dole u poslední, malé mezi nimi —
 * tentýž jazyk jako skupiny v seznamu chatů a bloky v profilu.
 */
fun sheetActionShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) SHEET_GROUP_CORNER else SHEET_INNER_CORNER,
    topEnd = if (index == 0) SHEET_GROUP_CORNER else SHEET_INNER_CORNER,
    bottomStart = if (index == count - 1) SHEET_GROUP_CORNER else SHEET_INNER_CORNER,
    bottomEnd = if (index == count - 1) SHEET_GROUP_CORNER else SHEET_INNER_CORNER,
)

private val SHEET_GROUP_CORNER = 20.dp
private val SHEET_INNER_CORNER = 4.dp
