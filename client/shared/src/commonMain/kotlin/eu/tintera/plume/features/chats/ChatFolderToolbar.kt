package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.resources.chat_folder_delete
import eu.tintera.plume.resources.chat_folder_delete_body
import eu.tintera.plume.resources.chat_folder_delete_title
import eu.tintera.plume.resources.chat_folder_edit_title
import eu.tintera.plume.resources.chat_folders_manage
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.tune_24dp
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
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import eu.tintera.plume.core.ui.wheelScrollsHorizontally
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
 * Složky Telegramu v plovoucí liště dole, s FAB na konci: „Všechny chaty · Práce 3 · Rodina…".
 * Nahradila řádek přepínacích tlačítek pod pilulkou hledání.
 *
 * - **Lišta M3 Expressive s FAB** (`HorizontalFloatingToolbar(expanded, floatingActionButton)`):
 *   sbalená ([expanded] `false`) zajede do FAB a FAB naroste z 56 na 80 dp, rozbalená vyjede
 *   a FAB se zmenší zpátky. Animaci, stín i velikost FAB řídí lišta; [floatingActionButton] musí
 *   vyplnit slot, který dostane.
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
    expanded: Boolean,
    onSelect: (Int?) -> Unit,
    onAdd: () -> Unit,
    /** Co nabídne podržení složky ([FolderTabActions]). */
    actions: FolderTabActions,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Lišta se ptá obsahu na šířku, ale její řádek se posouvá do strany, takže obsah žádnou
     * horní mez nedostane. Kolik místa zbývá vedle FAB, se proto měří tady, nad lištou.
     */
    BoxWithConstraints(modifier) {
        val padding = FloatingToolbarDefaults.ContentPadding
        val layoutDirection = LocalLayoutDirection.current
        val contentWidth = maxWidth - TOOLBAR_FAB_SIZE - TOOLBAR_TO_FAB_GAP -
            padding.calculateStartPadding(layoutDirection) - padding.calculateEndPadding(layoutDirection)
        HorizontalFloatingToolbar(
            expanded = expanded,
            floatingActionButton = floatingActionButton,
            // Výchozí `surfaceContainer` je barva stránky: pod koncem seznamu by lišta zmizela.
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            FolderItems(tabs, expanded, contentWidth, onSelect, onAdd, actions)
        }
    }
}

/**
 * Položky lišty. Složené jsou DVĚ řady — se všemi popisky a kompaktní — a při měření se vybere
 * ta, která se vejde do [maxWidth]; druhá se změří, ale nepostaví (nekreslí se, nedostane dotyk).
 *
 * Dřív to rozhodoval `SubcomposeLayout` (verze s popisky nanečisto, pak skutečná řada). Lišta
 * s FAB se ale obsahu ptá na intrinsickou šířku — z ní počítá, jak daleko zajede — a
 * `SubcomposeLayout` intrinsické míry neumí, aplikace by spadla. Dvě složené řady je umí.
 * Která je vidět, se dozví i kompozice (`labels`), kvůli čtečce, fokusu a dojetí na vybranou.
 *
 * **Co se nevejde, posouvá se VLASTNÍM posunem**, ne tím, který má řádek lišty v M3: k tomu se
 * zvenku nedostaneme a kolečko myši na něj převést nejde (viz `wheelScrollsHorizontally`).
 * Položky jsou proto oříznuté na [maxWidth] a řádek lišty nikdy nepřeteče.
 */
@Composable
private fun FolderItems(
    tabs: List<FolderTab>,
    expanded: Boolean,
    maxWidth: Dp,
    onSelect: (Int?) -> Unit,
    onAdd: () -> Unit,
    actions: FolderTabActions,
) {
    var labels by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    Layout(
        contents = listOf(
            { FolderRow(tabs, labels = true, placed = labels, expanded = expanded, onSelect, onAdd, actions) },
            { FolderRow(tabs, labels = false, placed = !labels, expanded = expanded, onSelect, onAdd, actions) },
        ),
        modifier = Modifier
            .widthIn(max = maxWidth)
            .horizontalScroll(scroll)
            // Až za posunem: kolečko se tak zpracuje dřív než v něm a posun ho už nedostane.
            .wheelScrollsHorizontally(scroll),
        measurePolicy = remember(maxWidth) { FolderItemsMeasurePolicy(maxWidth) { labels = it } },
    )
}

/** Vybírá mezi řadou s popisky (první obsah) a kompaktní (druhý); viz [FolderItems]. */
private class FolderItemsMeasurePolicy(
    private val maxWidth: Dp,
    private val onLabels: (Boolean) -> Unit,
) : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val full = measurables[0].map { it.measure(Constraints()) }
        val labels = full.sumOf { it.width } <= maxWidth.roundToPx()
        onLabels(labels)
        val shown = if (labels) full else measurables[1].map { it.measure(constraints.copy(minWidth = 0)) }
        val width = shown.maxOf { it.width }
        val height = shown.maxOf { it.height }
        return layout(width, height) { shown.forEach { it.placeRelative(0, (height - it.height) / 2) } }
    }

    /** Šířka ukazované řady, nanejvýš [maxWidth] — víc lišta nedostane, zbytek se posouvá. */
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val limit = maxWidth.roundToPx()
        val full = measurables[0].sumOf { it.maxIntrinsicWidth(height) }
        return if (full <= limit) full else minOf(measurables[1].sumOf { it.maxIntrinsicWidth(height) }, limit)
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        maxIntrinsicWidth(measurables, height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        measurables.flatten().maxOf { it.maxIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        measurables.flatten().maxOf { it.minIntrinsicHeight(width) }
}

/**
 * Jedna řada položek. [placed] říká, jestli je to řada, kterou lišta ukazuje: ta druhá se jen
 * měří, takže nesmí do čtečky, nedostane fokus a nedojíždí na vybranou složku. Ve sbalené liště
 * ([expanded] `false`) fokus nedostane ani ukazovaná, jako v ukázce M3.
 */
@Composable
private fun FolderRow(
    tabs: List<FolderTab>,
    labels: Boolean,
    placed: Boolean,
    expanded: Boolean,
    onSelect: (Int?) -> Unit,
    onAdd: () -> Unit,
    actions: FolderTabActions,
) {
    val focusable = placed && expanded
    Row(
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (placed) Modifier else Modifier.clearAndSetSemantics {},
    ) {
        tabs.forEach { tab ->
            FolderItem(
                tab,
                label = labels || tab.selected,
                placed = placed,
                focusable = focusable,
                onClick = { if (!tab.selected) onSelect(tab.id) },
                actions = actions,
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
                    .focusProperties { canFocus = focusable }
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
private fun FolderItem(tab: FolderTab, label: Boolean, placed: Boolean, focusable: Boolean, onClick: () -> Unit, actions: FolderTabActions) {
    val title = tab.plainTitle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
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
     * stojí na konci lišty — za okrajem, kde by výběr nebyl poznat. Jen v ukazované řadě: ta
     * druhá se nestaví a posunula by lištu jinam.
     */
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(tab.selected, placed) { if (tab.selected && placed) bringIntoView.bringIntoView() }
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
                .focusProperties { canFocus = focusable }
                .contextClickable(
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
            groups = folderMenuGroups(
                tab = tab,
                title = title,
                actions = actions,
                close = { menuOpen = false },
                onDelete = { confirmDelete = true },
            ),
        )
    }
    val id = tab.id
    if (confirmDelete && id != null) {
        ConfirmDialog(
            title = stringResource(Res.string.chat_folder_delete_title),
            text = stringResource(Res.string.chat_folder_delete_body),
            confirmLabel = stringResource(Res.string.chat_folder_delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete(id)
            },
            onDismiss = { confirmDelete = false },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }
}

/**
 * Co nabídne podržení složky v liště. Jedna instance na ViewModel (`ChatsViewModel`), aby nová
 * akce nepřidávala parametr přes všechny úrovně lišty (lišta → položky → řada → položka).
 */
internal data class FolderTabActions(
    /** „Označit vše jako přečtené"; `null` = hlavní seznam. */
    val onReadAll: (Int?) -> Unit,
    /** Editor složky v Nastavení — i s přidáním chatů. */
    val onEdit: (Int) -> Unit,
    /** Správa složek v Nastavení: pořadí, doporučené, sdílení. */
    val onManage: () -> Unit,
    val onDelete: (Int) -> Unit,
)

/**
 * Nabídka nad záložkou. Vlastní složka: přečíst vše, upravit a spravovat, a ve vlastní skupině
 * (nevratné, viz `ExpressiveMenu`) smazat — sdílenou ne, ta chce rozhodnout i o odchodu z chatů
 * a to umí jen Správa složek. „Všechny chaty": přečíst vše a spravovat složky.
 */
@Composable
private fun folderMenuGroups(
    tab: FolderTab,
    title: String,
    actions: FolderTabActions,
    close: () -> Unit,
    onDelete: () -> Unit,
): List<List<MenuAction>> {
    val id = tab.id
    val readAll = MenuAction(
        if (id == null) stringResource(Res.string.chat_folders_read_all_main) else stringResource(Res.string.chat_folders_read_all, title),
        Res.drawable.done_all_24dp,
    ) {
        close()
        actions.onReadAll(id)
    }
    val manage = MenuAction(stringResource(Res.string.chat_folders_manage), Res.drawable.tune_24dp) {
        close()
        actions.onManage()
    }
    if (id == null) return listOf(listOf(readAll), listOf(manage))
    val edit = MenuAction(stringResource(Res.string.chat_folder_edit_title), Res.drawable.edit_24dp) {
        close()
        actions.onEdit(id)
    }
    val delete = MenuAction(stringResource(Res.string.chat_folder_delete), Res.drawable.delete_24dp, destructive = true) {
        close()
        onDelete()
    }
    return listOfNotNull(listOf(readAll), listOf(edit, manage), listOf(delete).takeUnless { tab.shareable })
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
    // Vybraná složka zvýrazněným řezem (M3 Expressive): výběr nese i písmo, ne jen plocha.
    val style = if (tab.selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge
    if (tab.id == null) {
        // Popisek krátce, na celé „Všechny chaty" by v kompaktní liště padla skoro polovina místa.
        // Čtečka obrazovky a bublina dostávají celý název (`plainTitle`).
        Text(stringResource(Res.string.chat_folders_all_short), color = color, style = style, maxLines = 1, modifier = modifier)
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
        style = style,
        maxLines = 1,
        inlineContent = customEmojiInlineContent(emojiIds),
        modifier = modifier,
    )
}

@Composable
private fun FolderTab.plainTitle(): String = if (id == null) stringResource(Res.string.chat_folders_all) else title

/** „Všechny chaty" mají ikonu, kterou jim dává Telegram („All"). */
private fun FolderTab.icon() = folderIcon(if (id == null) "All" else iconName)

/** Velikost FAB v rozbalené liště a mezera k němu — `FloatingToolbarDefaults` je mají interní. */
private val TOOLBAR_FAB_SIZE = 56.dp
private val TOOLBAR_TO_FAB_GAP = 8.dp


/** Jako icon button: 48 dp na výšku, s 12 dp po stranách je samotná ikona taky 48 dp široká. */
private val ITEM_HEIGHT = 48.dp
private val ITEM_PADDING = 12.dp
private val ITEM_SPACING = 2.dp
private val LABEL_SPACING = 8.dp
