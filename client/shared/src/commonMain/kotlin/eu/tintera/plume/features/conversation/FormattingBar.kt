package eu.tintera.plume.features.conversation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.TextFormat
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.code_24dp
import eu.tintera.plume.resources.format_bold
import eu.tintera.plume.resources.format_bold_24dp
import eu.tintera.plume.resources.format_clear
import eu.tintera.plume.resources.format_clear_24dp
import eu.tintera.plume.resources.format_code
import eu.tintera.plume.resources.format_italic
import eu.tintera.plume.resources.format_italic_24dp
import eu.tintera.plume.resources.format_quote
import eu.tintera.plume.resources.format_quote_24dp
import eu.tintera.plume.resources.format_spoiler
import eu.tintera.plume.resources.format_strikethrough
import eu.tintera.plume.resources.format_strikethrough_24dp
import eu.tintera.plume.resources.format_underline
import eu.tintera.plume.resources.format_underlined_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Lišta formátování nad polem, dokud je v něm označený text: tučné, kurzíva, podtržení,
 * přeškrtnutí, spoiler, kód, citace a „bez formátování".
 *
 * **Lišta, ne položky v nabídce výběru.** Ikony u vlastních položek nabídka výběru na telefonu
 * nezobrazí: Android kreslí lištu výběru systémovým `ActionMode` a `leadingIcon` z naší položky
 * zahodí (kreslí ho jen rozbalovací nabídka pro myš), na iOS ji kreslí `UIEditMenu`. Lišta navíc
 * ukáže, co už na výběru je — zapnuté tlačítko —, což textová položka neumí.
 *
 * Tlačítka jsou přepínače jako filtry v seznamu chatů (`toggleableShapes`, zapnuté se zakulatí
 * do čtverce), popisek je v tooltipu po podržení a v `contentDescription`. Na úzkém displeji se
 * řada posouvá do strany: tlačítka mají minimální dotykovou plochu 48 dp a osm se jich vedle
 * pole na složeném telefonu nevejde.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FormattingBar(composer: ComposerDraft, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FORMAT_BUTTONS.forEach { (format, icon, label) ->
            val applied = composer.isApplied(format)
            Labelled(stringResource(label)) { text ->
                IconToggleButton(
                    checked = applied,
                    onCheckedChange = {
                        composer.toggle(format)
                        haptics.toggle(!applied)
                        onChanged()
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    // Plná `primary`, ne `secondaryContainer` jako u filtrů seznamu chatů: karta pole
                    // je přitónovaná do `primaryContainer` a světlý kontejner na ní zanikal.
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(painterResource(icon), contentDescription = text)
                }
            }
        }
        // „Bez formátování" je akce, ne stav — obyčejné tlačítko, zašedlé, když není co sundat.
        Labelled(stringResource(Res.string.format_clear)) { text ->
            IconButton(
                onClick = {
                    composer.clearSelection()
                    haptics.tick()
                    onChanged()
                },
                enabled = composer.selectionFormatted,
            ) {
                Icon(painterResource(Res.drawable.format_clear_24dp), contentDescription = text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Labelled(label: String, content: @Composable (String) -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content(label)
    }
}

private val FORMAT_BUTTONS: List<Triple<TextFormat, DrawableResource, StringResource>> = listOf(
    Triple(TextFormat.Bold, Res.drawable.format_bold_24dp, Res.string.format_bold),
    Triple(TextFormat.Italic, Res.drawable.format_italic_24dp, Res.string.format_italic),
    Triple(TextFormat.Underline, Res.drawable.format_underlined_24dp, Res.string.format_underline),
    Triple(TextFormat.Strikethrough, Res.drawable.format_strikethrough_24dp, Res.string.format_strikethrough),
    Triple(TextFormat.Spoiler, Res.drawable.visibility_off_24dp, Res.string.format_spoiler),
    Triple(TextFormat.Code, Res.drawable.code_24dp, Res.string.format_code),
    Triple(TextFormat.Quote, Res.drawable.format_quote_24dp, Res.string.format_quote),
)
