package eu.tintera.plume.features.chats

import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.chat_folders_read_all_main
import eu.tintera.plume.resources.chat_folders_read_all
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.RootTooltipBox
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.chat_folders_add_folder
import eu.tintera.plume.resources.chat_folders_all
import eu.tintera.plume.resources.chat_folders_all_short
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Složky Telegramu v plovoucí liště dole, vedle FAB: „Všechny chaty · Práce 3 · Rodina…".
 * Nahradila řádek přepínacích tlačítek pod pilulkou hledání.
 *
 * - **Popisky, když se vejdou; jinak jen ikony a popisek nese vybraná.** Rozhoduje změřená šířka
 *   ([FolderItems]), ne počet složek: tři dlouhé názvy se na telefon nevejdou, pět krátkých na
 *   desktop ano. Ikona složky bez vlastní volby je ta, kterou by zvolil Telegram podle obsahu.
 * - **Co se nevejde ani jako ikony, posouvá se do strany** a vybraná složka se vždycky ukáže.
 * - **Jen ťuknutím, ne tahem po seznamu.** Vodorovný tah po seznamu patří archivaci.
 * - **„+" je poslední položka** ([onAdd]) — doporučené složky, nová složka, správa.
 * - **Číslo je počet nepřečtených neztlumených chatů**, ne zpráv (viz [FolderTab]). U ikony bez
 *   popisku je v odznaku.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatFolderToolbar(
    tabs: List<FolderTab>,
    onSelect: (Int?) -> Unit,
    onAdd: () -> Unit,
    /** Podržení složky → „Označit vše jako přečtené"; `null` = hlavní seznam. */
    onReadAll: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        // Lišta leží nad kartami chatů; bez stínu (výchozí pro samotnou lištu) by se s nimi slévala.
        expandedShadowElevation = FloatingToolbarDefaults.ContainerExpandedElevationWithFab,
        modifier = modifier,
    ) {
        // Toolbar dává obsah do `Row` bez `verticalAlignment` (viz ChatSelectionToolbar).
        FolderItems(tabs, onSelect, onAdd, onReadAll, Modifier.align(Alignment.CenterVertically))
    }
}

/**
 * Položky lišty. Napřed se nanečisto změří verze se všemi popisky; když se vejde, kreslí se ona,
 * jinak kompaktní. Měření jde přes [SubcomposeLayout], protože o popiscích se rozhoduje dřív, než
 * se skutečné položky složí — a s nimi i animace popisku při změně výběru.
 */
@Composable
private fun FolderItems(
    tabs: List<FolderTab>,
    onSelect: (Int?) -> Unit,
    onAdd: () -> Unit,
    onReadAll: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    SubcomposeLayout(modifier) { constraints ->
        val probe = subcompose(PROBE) { FolderRow(tabs, labels = true, onSelect = {}, onAdd = {}, onReadAll = {}) }
            .map { it.measure(Constraints()) }
        val labels = probe.sumOf { it.width } <= constraints.maxWidth
        val content = subcompose(CONTENT) {
            FolderRow(tabs, labels = labels, onSelect = onSelect, onAdd = onAdd, onReadAll = onReadAll, modifier = Modifier.horizontalScroll(scroll))
        }.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = content.maxOf { it.width }
        val height = content.maxOf { it.height }
        layout(width, height) { content.forEach { it.placeRelative(0, (height - it.height) / 2) } }
    }
}

@Composable
private fun FolderRow(
    tabs: List<FolderTab>,
    labels: Boolean,
    onSelect: (Int?) -> Unit,
    onAdd: () -> Unit,
    onReadAll: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        tabs.forEach { tab ->
            FolderItem(
                tab,
                label = labels || tab.selected,
                onClick = { if (!tab.selected) onSelect(tab.id) },
                onReadAll = { onReadAll(tab.id) },
            )
        }
        val addLabel = stringResource(Res.string.chat_folders_add_folder)
        /*
         * Stejná plocha jako složka, ne `IconButton`: ten hlásí zarovnávací čáry minimální dotykové
         * plochy a toolbar podle nich vyvažuje odsazení — jenže v posouvaném řádku se čára počítá
         * od polohy „+" (stovky pixelů) a lišta pak narostla přes půl obrazovky.
         */
        FolderTooltip(addLabel, enabled = true) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ITEM_HEIGHT)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = addLabel },
            ) {
                Icon(painterResource(Res.drawable.add_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Jedna složka: ikona, a s [label] i název a počet. Vybraná má plochu `secondaryContainer` — výběr
 * je navigační stav, stejná role jako vybraná položka navigace v M3. Popisek vyjíždí a zajíždí
 * vodorovně, takže se lišta při změně výběru plynule přeskládá.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderItem(tab: FolderTab, label: Boolean, onClick: () -> Unit, onReadAll: () -> Unit) {
    val title = tab.plainTitle()
    var menuOpen by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        targetValue = if (tab.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "folder-item-container",
    )
    val content by animateColorAsState(
        targetValue = if (tab.selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "folder-item-content",
    )
    /*
     * Vybraná složka musí být vidět: po přidání doporučené složky z „+" se vybere ta nová, a ta
     * stojí na konci lišty — za okrajem, kde by výběr nebyl poznat.
     */
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(tab.selected) { if (tab.selected) bringIntoView.bringIntoView() }
    /*
     * Podržení otevře nabídku složky. Bublina s názvem, kterou ikona bez popisku dřív na podržení
     * ukazovala, tu proto není — pustily by se obě naráz. Název nese položka nabídky a čtečka.
     */
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .bringIntoViewRequester(bringIntoView)
                .height(ITEM_HEIGHT)
                .clip(CircleShape)
                .background(container)
                .combinedClickable(
                    role = Role.Tab,
                    onClick = onClick,
                    // Odezvu na podržení dělá `combinedClickable` sám; ruční by ji zdvojila.
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = ITEM_PADDING)
                .clearAndSetSemantics {
                    contentDescription = title
                    selected = tab.selected
                    role = Role.Tab
                    onClick(action = { onClick(); true })
                    onLongClick(action = { menuOpen = true; true })
                },
        ) {
            BadgedBox(
                badge = {
                    // Bez popisku je číslo v odznaku, s popiskem za názvem.
                    if (!label && tab.unread > 0) Badge { Text(tab.unread.grouped(), style = MaterialTheme.typography.labelSmall.tabularNumbers()) }
                },
            ) {
                Icon(painterResource(tab.icon()), contentDescription = null, tint = content)
            }
            AnimatedVisibility(
                visible = label,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderTitle(tab, content, Modifier.padding(start = LABEL_SPACING))
                    if (tab.unread > 0) {
                        RollingNumber(
                            value = tab.unread,
                            format = { it.grouped() },
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = content,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
        ExpressiveMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Above),
            groups = listOf(
                listOf(
                    MenuAction(
                        if (tab.id == null) {
                            stringResource(Res.string.chat_folders_read_all_main)
                        } else {
                            stringResource(Res.string.chat_folders_read_all, title)
                        },
                        Res.drawable.done_all_24dp,
                    ) {
                        menuOpen = false
                        onReadAll()
                    },
                ),
            ),
        )
    }
}

/**
 * Bublina s názvem. Vypnutá se neodpojuje z kompozice ([enabled] jen vypne gesta): kdyby se
 * obal přidával a odebíral, položka by se při změně výběru složila znovu a popisek by naskočil
 * bez animace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTooltip(label: String, enabled: Boolean, content: @Composable () -> Unit) {
    RootTooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        enableUserInput = enabled,
        content = content,
    )
}

@Composable
private fun FolderTitle(tab: FolderTab, color: Color, modifier: Modifier = Modifier) {
    if (tab.id == null) {
        // Popisek krátce, na celé „Všechny chaty" by v kompaktní liště padla skoro polovina místa.
        // Čtečka obrazovky a bublina dostávají celý název (`plainTitle`).
        Text(stringResource(Res.string.chat_folders_all_short), color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = modifier)
        return
    }
    val (text, emojiIds) = remember(tab.title, tab.titleEntities) {
        var ids: Set<Long> = emptySet()
        val built = buildAnnotatedString { ids = appendWithCustomEmoji(tab.title, tab.titleEntities) }
        built to ids
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        inlineContent = customEmojiInlineContent(emojiIds),
        modifier = modifier,
    )
}

@Composable
private fun FolderTab.plainTitle(): String = if (id == null) stringResource(Res.string.chat_folders_all) else title

/** „Všechny chaty" mají ikonu, kterou jim dává Telegram („All"). */
private fun FolderTab.icon() = folderIcon(if (id == null) "All" else iconName)

private const val PROBE = "probe"
private const val CONTENT = "content"

/** Jako icon button: 48 dp na výšku, s 12 dp po stranách je samotná ikona taky 48 dp široká. */
private val ITEM_HEIGHT = 48.dp
private val ITEM_PADDING = 12.dp
private val ITEM_SPACING = 2.dp
private val LABEL_SPACING = 8.dp
