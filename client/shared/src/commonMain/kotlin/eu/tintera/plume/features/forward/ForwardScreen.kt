package eu.tintera.plume.features.forward

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSelection
import eu.tintera.plume.core.ui.PickerError
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.forward_hide_captions
import eu.tintera.plume.resources.forward_hide_sender
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.forward_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kam přeposlat: hledání a seznam chatů ([ChatPicker], společný se sdílením i novou konverzací).
 *
 * Obyčejná obrazovka, ne bottom sheet — seznam chatů je dlouhý a listuje se v něm; sheet by
 * navíc při hledání zápasil s klávesnicí. Po odeslání se otevře cílový chat, takže je hned vidět,
 * že zpráva dorazila.
 */
@Composable
fun ForwardScreen(
    viewModel: ForwardViewModel,
    onBack: () -> Unit,
    onForwarded: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.forward_title),
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.forward_search),
        ),
        loading = state.loading,
        // Jeden blok bez nadpisu — cíle se nečlení, je to prostě seznam chatů.
        sections = listOf(PickerSection(key = "chats", chats = state.chats)),
        // Dokud odesílání běží, další ťuknutí by poslalo zprávu dvakrát.
        enabled = !state.sending,
        onPick = viewModel::toggle,
        selection = PickerSelection(
            // Přeposílá se do víc chatů naráz, takže ťuknutí cíl VYBERE a odesílá se až tlačítkem.
            selected = state.selected,
            onConfirm = { viewModel.send(onForwarded) },
        ),
        savedMessagesChatId = state.savedMessagesChatId,
        onBack = onBack,
        error = state.error?.let { PickerError(it, viewModel::consumeError) },
        header = {
            ForwardOptionsRow(
                capabilities = state.capabilities,
                options = state.options,
                enabled = !state.sending,
                onHideSender = viewModel::setHideSender,
                onHideCaptions = viewModel::setHideCaptions,
            )
        },
        modifier = modifier,
    )
}

/**
 * Jak přeposlat: „Skrýt odesílatele" (kopie bez „Přeposláno od …") a s ní „Skrýt popisky".
 *
 * Dvě NEZÁVISLÁ přepínací tlačítka, ne connected group — ta je pro výběr z jedné sady (druh
 * chatu v hledání), tady jsou to dvě volby, z nichž druhá platí jen s první. Proto se druhá
 * ukáže, až je první zapnutá, a jen když zprávy nějaký popisek nesou.
 *
 * Co server nedovolí (`canBeCopied`), se nenabízí vůbec — stejně jako položky nabídky nad zprávou.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ForwardOptionsRow(
    capabilities: ForwardCapabilities,
    options: ForwardOptions,
    enabled: Boolean,
    onHideSender: (Boolean) -> Unit,
    onHideCaptions: (Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = capabilities.canHideSender,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Vodorovné odsazení dává seznam (volby jsou jeho první položka); mezeru pod sebou
            // nese tenhle Row, takže bez voleb po ní nezbude prázdné místo.
            modifier = Modifier.padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
        ) {
            OptionToggle(
                checked = options.hideSender,
                onCheckedChange = onHideSender,
                enabled = enabled,
                label = stringResource(Res.string.forward_hide_sender),
            )
            AnimatedVisibility(
                visible = options.hideSender && capabilities.hasCaptions,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                OptionToggle(
                    checked = options.hideCaptions,
                    onCheckedChange = onHideCaptions,
                    enabled = enabled,
                    label = stringResource(Res.string.forward_hide_captions),
                )
            }
        }
    }
}

/** Přepínací tlačítko s fajfkou, když je zapnuté — jako filtry hledání zpráv. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, label: String) {
    ToggleButton(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) {
        AnimatedVisibility(visible = checked) {
            Icon(
                painterResource(Res.drawable.check_24dp),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
        }
        Text(label)
    }
}
