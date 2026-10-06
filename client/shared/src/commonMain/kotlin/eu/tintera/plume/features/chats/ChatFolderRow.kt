package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.chat_folders_add_folder
import eu.tintera.plume.resources.chat_folders_all
import eu.tintera.plume.resources.check_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Složky Telegramu pod pilulkou hledání: „Všechny chaty · Práce 3 · Rodina…". Tentýž prvek jako
 * druh chatu ve filtrech hledání — connected button group, vybrané je vždycky právě jedno.
 *
 * - **Jen ťuknutím, ne tahem do strany.** Vodorovný tah po seznamu patří archivaci.
 * - **Je to JEDINÉ zúžení seznamu chatů.** Dřív byly navíc filtry v plovoucím toolbaru dole
 *   (Nepřečtené, Lidé, Skupiny, Kanály) a dělaly totéž co doporučené složky, jen lokálně —
 *   dvě úrovně nad sebou mátly. Teď je na konci řádku „+" ([onAdd]) s doporučenými složkami.
 * - **„+" stojí mimo skupinu tlačítek.** Není to volba, ale akce; ve spojené skupině by se tvářil
 *   jako další složka, kterou jde vybrat.
 * - **Číslo je počet nepřečtených neztlumených chatů**, ne zpráv (viz [FolderTab]).
 * - **Název složky může nést vlastní emoji** — kreslí se jako v náhledu seznamu chatů.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatFolderRow(
    tabs: List<FolderTab>,
    onSelect: (Int?) -> Unit,
    /** „+" na konci řádku; bez něj (`null`) se nekreslí. */
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            /*
             * Vybraná složka musí být vidět: po přidání doporučené složky z „+" se vybere ta
             * nová, a ta stojí na konci řádku — za okrajem, kde by výběr nebyl poznat.
             */
            val bringIntoView = remember { BringIntoViewRequester() }
            LaunchedEffect(tab.selected) { if (tab.selected) bringIntoView.bringIntoView() }
            ToggleButton(
                checked = tab.selected,
                onCheckedChange = { if (!tab.selected) onSelect(tab.id) },
                shapes = when {
                    // Bez složek je v řádku jen celý seznam: sám, ne půlka spojené skupiny.
                    tabs.size == 1 -> ToggleButtonDefaults.shapes()
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = BUTTON_PADDING),
                modifier = Modifier.bringIntoViewRequester(bringIntoView).semantics { selected = tab.selected },
            ) {
                AnimatedVisibility(visible = tab.selected) {
                    Icon(
                        painterResource(Res.drawable.check_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                    )
                }
                FolderTitle(tab)
                if (tab.unread > 0) {
                    Text(
                        text = tab.unread.grouped(),
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (onAdd != null) {
            OutlinedIconButton(
                onClick = onAdd,
                modifier = Modifier.padding(start = ADD_SPACING),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(Res.drawable.add_24dp), contentDescription = stringResource(Res.string.chat_folders_add_folder))
            }
        }
    }
}

@Composable
private fun FolderTitle(tab: FolderTab) {
    if (tab.id == null) {
        Text(stringResource(Res.string.chat_folders_all), maxLines = 1)
        return
    }
    val (text, emojiIds) = remember(tab.title, tab.titleEntities) {
        var ids: Set<Long> = emptySet()
        val built = buildAnnotatedString { ids = appendWithCustomEmoji(tab.title, tab.titleEntities) }
        built to ids
    }
    Text(text, maxLines = 1, inlineContent = customEmojiInlineContent(emojiIds))
}

/** Užší odsazení, jako u filtrů hledání: s výchozím by se na telefon vešly dvě složky. */
private val BUTTON_PADDING = 12.dp

/** „+" je akce, ne další složka — odsazený víc než spára spojené skupiny. */
private val ADD_SPACING = 6.dp
