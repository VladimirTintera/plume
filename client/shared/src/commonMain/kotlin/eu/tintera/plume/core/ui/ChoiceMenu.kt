package eu.tintera.plume.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.expand_more_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * **Řádek s výběrem jedné hodnoty z nabídky** (tah v seznamu chatů, automatické stahování,
 * uchovávání médií, časovač zámku…): název, pod ním platná hodnota, šipka a po ťuknutí
 * [ChoiceMenu] ukotvená u řádku.
 *
 * Dřív to byl `AlertDialog` s kolečky: krátká volba otevřela okno přes celou obrazovku, kolečka
 * pod sebou byla vzor M2 a vybrat šlo jen přes další ťuknutí do středu obrazovky. Nabídka
 * zůstane u řádku, vybraná hodnota je zaškrtnutá a ťuknutí ji rovnou zvolí — týž vzor jako
 * období ve filtrech hledání.
 *
 * Řádek je průhledný (`ListItem` v `GroupedColumn`), tvar a podklad mu dává blok kolem.
 */
@Composable
fun <T> ChoiceRow(
    headline: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    /** Věta nad volbami, co volba dělá — dřív text dialogu. */
    note: String? = null,
    /** Druhý řádek u volby (co přesně se u úrovně stáhne). */
    optionSupporting: (@Composable (T) -> String)? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "choice-arrow",
    )
    Box(modifier.highlightAnchor(headline)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
            headlineContent = { Text(headline) },
            supportingContent = { Text(label(selected)) },
            trailingContent = {
                Icon(
                    painterResource(Res.drawable.expand_more_24dp),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            },
            modifier = Modifier.clickable(enabled = enabled, role = Role.DropdownList) { open = true },
        )
        ChoiceMenu(
            expanded = open,
            onDismiss = { open = false },
            options = options,
            selected = selected,
            label = label,
            onSelect = { option ->
                open = false
                onSelect(option)
            },
            note = note,
            optionSupporting = optionSupporting,
        )
    }
}

/**
 * Nabídka s výběrem jedné hodnoty: nahoře volitelná věta ([note]), pod ní skupina voleb,
 * vybraná zaškrtnutá. Ukotvuje se k rodiči — patří do `Box` s tím, co ji otevřelo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    note: String? = null,
    optionSupporting: (@Composable (T) -> String)? = null,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below),
        properties = MenuPopupProperties,
    ) {
        MenuColors {
            val groups = if (note != null) 2 else 1
            // Obě skupiny stejně široké: věta nad volbami užší než volby vypadala jako zbytek.
            Column(Modifier.width(IntrinsicSize.Max)) {
                if (note != null) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, groups), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Širší věta by nabídku roztáhla přes obrazovku.
                            modifier = Modifier.widthIn(max = NOTE_MAX_WIDTH).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                }
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(groups - 1, groups), modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            checked = option == selected,
                            onCheckedChange = { onSelect(option) },
                            text = { Text(label(option)) },
                            supportingText = optionSupporting?.let { supporting -> { Text(supporting(option)) } },
                            shapes = MenuDefaults.itemShape(index, options.size),
                            checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

private val NOTE_MAX_WIDTH = 280.dp
