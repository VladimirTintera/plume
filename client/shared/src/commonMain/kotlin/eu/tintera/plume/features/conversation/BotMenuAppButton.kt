package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.apps_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Tlačítko nabídky bota s mini aplikací, u pole na místě tlačítka příkazů (tak to mají
 * i oficiální klienti; příkazy dál napovídá „/").
 *
 * **V prázdném poli je to plná pilulka s textem od bota, při psaní jen ikona.** Text píše bot
 * a bývá dlouhý („Otevřít obchod"); vedle sponky a odesílacího tlačítka by na úzkém telefonu
 * pilulka ukousla poli místo zrovna ve chvíli, kdy do něj uživatel píše. Sbalená vypadá jako
 * ostatní ikony v kartě — dvě plné kruhové plochy vedle sebe (s odesláním) by se praly.
 */
@Composable
internal fun BotMenuAppButton(
    text: String,
    compact: Boolean,
    /** Barva ikon v kartě pole; sbalené tlačítko ji přebírá. */
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(if (compact) Color.Transparent else colors.primary, effects, "menu-app-container")
    val content by animateColorAsState(if (compact) iconColor else colors.onPrimary, effects, "menu-app-content")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        // Sbalené nese text od bota jako popis; rozbalené ho čtečce dá samo (text uvnitř).
        modifier = if (compact) modifier.semantics { contentDescription = text } else modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = BUTTON_HEIGHT).padding(horizontal = PADDING),
        ) {
            Icon(painterResource(Res.drawable.apps_24dp), contentDescription = null, modifier = Modifier.size(24.dp))
            AnimatedVisibility(
                visible = !compact,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Start),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.Start),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = MAX_TEXT_WIDTH).padding(end = 4.dp),
                    )
                }
            }
        }
    }
}

/** 40 dp jako ikonová tlačítka v kartě; dotykovou plochu dorovná `Surface` na 48 dp. */
private val BUTTON_HEIGHT = 40.dp

/** Sbalené je ikona na středu 40dp kruhu: (40 − 24) / 2. */
private val PADDING = 8.dp

/** Text od bota se zkracuje; delší pilulka by poli nechala málo místa i v prázdném stavu. */
private val MAX_TEXT_WIDTH = 120.dp
