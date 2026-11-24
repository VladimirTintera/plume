package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.ChatFolderTab
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.resources.chat_action_folders
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.chats_action_pin
import eu.tintera.plume.resources.chats_action_unpin
import eu.tintera.plume.resources.chats_selection_all
import eu.tintera.plume.resources.chats_selection_delete
import eu.tintera.plume.resources.chats_selection_delete_groups
import eu.tintera.plume.resources.chats_selection_delete_title
import eu.tintera.plume.resources.chats_selection_leave_title
import eu.tintera.plume.resources.chats_selection_delete_mixed
import eu.tintera.plume.resources.chats_selection_delete_private
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.keep_off_24dp
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.select_check_box_24dp
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.chats_action_unmute
import eu.tintera.plume.resources.chats_selection_close
import eu.tintera.plume.resources.chats_selection_count
import eu.tintera.plume.resources.check_circle_filled_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.radio_button_unchecked_24dp
import eu.tintera.plume.resources.unarchive_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsSpace

/**
 * Hlavička při výběru víc chatů: ✕, počet vybraných a „Vybrat vše". Nahrazuje pilulku hledání —
 * hledat a vybírat zároveň nejde, a hlavička má říct jen to, v jakém režimu uživatel je. Akce
 * jsou dole v liště, v dosahu palce, jako u výběru zpráv v konverzaci.
 *
 * „Vybrat vše" zšedne, když už vybrané všechno je ([allSelected]); vybírá jen načtené chaty
 * (`withAll`). Obě tlačítka mají bublinu s popisem ([WithTooltip]) — ikona sama neřekne všechno.
 */
@Composable
internal fun ChatSelectionTopBar(count: Int, allSelected: Boolean, onSelectAll: () -> Unit, onClose: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(Res.plurals.chats_selection_count, count, count),
                style = MaterialTheme.typography.titleLargeEmphasized.tabularNumbers(),
            )
        },
        navigationIcon = {
            WindowControlsSpace {
                val label = stringResource(Res.string.chats_selection_close)
                WithTooltip(label = label, position = TooltipAnchorPosition.Below) {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = label)
                    }
                }
            }
        },
        actions = {
            val label = stringResource(Res.string.chats_selection_all)
            WithTooltip(label = label, position = TooltipAnchorPosition.Below) {
                IconButton(onClick = onSelectAll, enabled = !allSelected, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.select_check_box_24dp), contentDescription = label)
                }
            }
        },
    )
}

/**
 * Co lišta výběru volá. Jedna instance na ViewModel (`ChatsViewModel.selectionCallbacks`), aby
 * každá další akce nepřidávala parametr na třech místech, kde lišta stojí (seznam, archiv,
 * sbalený sloupec), a aby se lišta kvůli novým lambdám zbytečně neskládala.
 */
internal data class ChatSelectionCallbacks(
    val onArchive: () -> Unit,
    val onPin: () -> Unit,
    val onMute: () -> Unit,
    val onRead: () -> Unit,
    val onAddToFolder: (folderId: Int) -> Unit,
    val onDelete: () -> Unit,
)

/**
 * Akce nad vybranými chaty místo FAB menu: archivovat, připnout, ztlumit, přečíst, přidat do
 * složky, smazat / opustit. Přepínače se řídí stavem všech vybraných ([chatSelectionActions]),
 * takže ikona i popis říkají, co se STANE. Tatáž plovoucí pilulka (`vibrantFloatingToolbarColors`)
 * jako výběr zpráv.
 *
 * - **Každé tlačítko má bublinu s popisem** ([WithTooltip], na dotyku po podržení, myší po
 *   najetí): šest ikon vedle sebe se poznat dá, ale jistotu dá až text.
 * - **Archivace a mazání zšednou, nezmizí**, když je ve výběru Uložené zprávy — zmizelá akce by
 *   neřekla, že ji zrovna tenhle výběr nedovoluje (totéž pravidlo jako u výběru zpráv).
 * - **Smazání je jediná nevratná akce** — chce potvrzení ([ConfirmDialog], destruktivní) s větou
 *   podle toho, co ve výběru je (soukromé chaty se smažou, ze skupin se odejde).
 * - **„Do složky" je nabídka složek**; jen kde nějaké jsou.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatSelectionToolbar(
    actions: ChatSelectionActions?,
    callbacks: ChatSelectionCallbacks,
    modifier: Modifier = Modifier,
    /** Počet vybraných do potvrzení smazání. */
    count: Int = 0,
    /**
     * Svislá lišta pro sbalený seznam (`CollapsedChatList`): vodorovná se do sloupce 96 dp
     * nevejde. Tytéž akce i barvy, jen `VerticalFloatingToolbar`.
     */
    vertical: Boolean = false,
) {
    // Při odjezdu kreslí lišta poslední akce — v tu chvíli je výběr už pryč.
    val last = remember { arrayOfNulls<ChatSelectionActions>(1) }
    if (actions != null) last[0] = actions
    var confirmDelete by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = actions != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.8f),
        modifier = modifier,
    ) {
        val shown = last[0] ?: return@AnimatedVisibility
        // Bubliny u svislé lišty vedle ní, u vodorovné nad ní — pod prstem by je zakryl.
        val tooltip = if (vertical) TooltipAnchorPosition.Right else TooltipAnchorPosition.Above
        // Ikony se musí vystředit samy: obsah dostane toolbar ve vnořeném `Row` bez
        // `verticalAlignment` (viz docs/poznatky/konverzace-pohyb.md, „Hledání v konverzaci").
        // Odstup uvnitř animace: skrytá lišta nesmí nechat 24 dp prázdna nad FAB.
        if (vertical) {
            VerticalFloatingToolbar(
                modifier = Modifier.padding(bottom = VERTICAL_TOOLBAR_CLEARANCE),
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                SelectionActions(shown, callbacks, tooltip, { confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            HorizontalFloatingToolbar(
                modifier = Modifier.padding(bottom = TOOLBAR_CLEARANCE),
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                SelectionActions(shown, callbacks, tooltip, { confirmDelete = true }, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
    val pending = last[0]
    if (confirmDelete && actions != null && pending != null) {
        DeleteSelectedDialog(
            count = count,
            actions = pending,
            onConfirm = {
                confirmDelete = false
                callbacks.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Akce lišty — tytéž ve vodorovné i svislé liště; [itemModifier] je vystředí. */
@Composable
private fun SelectionActions(
    shown: ChatSelectionActions,
    callbacks: ChatSelectionCallbacks,
    tooltip: TooltipAnchorPosition,
    onDelete: () -> Unit,
    itemModifier: Modifier,
) {
    SelectionAction(
        icon = if (shown.archive) Res.drawable.archive_24dp else Res.drawable.unarchive_24dp,
        label = if (shown.archive) Res.string.chats_action_archive else Res.string.chats_action_unarchive,
        enabled = shown.canArchive,
        tooltip = tooltip,
        onClick = callbacks.onArchive,
        modifier = itemModifier,
    )
    SelectionAction(
        icon = if (shown.pin) Res.drawable.push_pin_24dp else Res.drawable.keep_off_24dp,
        label = if (shown.pin) Res.string.chats_action_pin else Res.string.chats_action_unpin,
        enabled = true,
        tooltip = tooltip,
        onClick = callbacks.onPin,
        modifier = itemModifier,
    )
    SelectionAction(
        icon = if (shown.mute) Res.drawable.notifications_off_24dp else Res.drawable.notifications_24dp,
        label = if (shown.mute) Res.string.chats_action_mute else Res.string.chats_action_unmute,
        enabled = true,
        tooltip = tooltip,
        onClick = callbacks.onMute,
        modifier = itemModifier,
    )
    SelectionAction(
        icon = if (shown.read) Res.drawable.done_all_24dp else Res.drawable.mark_chat_unread_24dp,
        label = if (shown.read) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread,
        enabled = true,
        tooltip = tooltip,
        onClick = callbacks.onRead,
        modifier = itemModifier,
    )
    if (shown.folders.isNotEmpty()) {
        FolderAction(shown.folders, tooltip, callbacks.onAddToFolder, itemModifier)
    }
    SelectionAction(
        icon = if (shown.leavesGroups && !shown.deletesPrivate) Res.drawable.logout_24dp else Res.drawable.delete_24dp,
        label = when {
            shown.leavesGroups && !shown.deletesPrivate -> Res.string.chats_action_leave
            else -> Res.string.chats_selection_delete
        },
        enabled = shown.canDelete,
        tooltip = tooltip,
        onClick = onDelete,
        modifier = itemModifier,
    )
}

/** „Přidat do složky": tlačítko s nabídkou složek; ťuknutí na složku do ní přidá celý výběr. */
@Composable
private fun FolderAction(
    folders: List<ChatFolderTab>,
    tooltip: TooltipAnchorPosition,
    onAddToFolder: (Int) -> Unit,
    modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SelectionAction(
            icon = Res.drawable.folder_24dp,
            label = Res.string.chat_action_folders,
            enabled = true,
            tooltip = tooltip,
            onClick = { open = true },
        )
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                folders.map { folder ->
                    MenuAction(label = folder.title, icon = folderIcon(folder.iconName)) {
                        open = false
                        onAddToFolder(folder.id)
                    }
                },
            ),
            // Lišta je u spodku obrazovky; nabídka pod ní by se nevešla.
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Above),
        )
    }
}

/**
 * Potvrzení hromadného smazání. Věta říká, co se stane s čím: soukromé chaty se smažou jen
 * u nás (protějšku zůstanou), ze skupin a kanálů se odejde — tatáž operace jako „Smazat" /
 * „Opustit" u jednoho chatu (`deleteOrLeave`).
 */
@Composable
private fun DeleteSelectedDialog(
    count: Int,
    actions: ChatSelectionActions,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val body = when {
        actions.deletesPrivate && actions.leavesGroups -> Res.string.chats_selection_delete_mixed
        actions.leavesGroups -> Res.string.chats_selection_delete_groups
        else -> Res.string.chats_selection_delete_private
    }
    // Jen skupiny a kanály: celé se to jmenuje „Opustit", ne „Smazat" — jako u jednoho chatu.
    val leaveOnly = actions.leavesGroups && !actions.deletesPrivate
    ConfirmDialog(
        title = pluralStringResource(
            if (leaveOnly) Res.plurals.chats_selection_leave_title else Res.plurals.chats_selection_delete_title,
            count,
            count,
        ),
        text = stringResource(body),
        confirmLabel = stringResource(if (leaveOnly) Res.string.chats_action_leave else Res.string.chats_selection_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = if (leaveOnly) Res.drawable.logout_24dp else Res.drawable.delete_24dp,
        destructive = true,
    )
}

/** Zašednutí se prolíná, aby lišta při změně výběru neproblikla. */
@Composable
private fun SelectionAction(
    icon: DrawableResource,
    label: StringResource,
    enabled: Boolean,
    tooltip: TooltipAnchorPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_ALPHA,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chat-selection-action-alpha",
    )
    val text = stringResource(label)
    // Bublina i u zšedlé akce: právě tam se uživatel ptá, co tlačítko dělá.
    WithTooltip(label = text, position = tooltip, modifier = modifier) {
        IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription = text, modifier = Modifier.alpha(alpha))
        }
    }
}

/**
 * Značka výběru v rohu avataru: prázdné kolečko, nebo plné s fajfkou. Kolečko je u KAŽDÉHO
 * chatu, který jde vybrat — samotná barva vybraného řádku by u jediného vybraného chatu
 * neřekla, že jdou vybírat i další. Lem v barvě řádku ji od avataru odliší.
 *
 * Fajfka se měří proti ploše řádku (`accentOn`): na vybraném `secondaryContainer` může být
 * v paletě se zvýšeným kontrastem `primary` k nerozeznání.
 */
@Composable
internal fun SelectionMark(selected: Boolean, rim: Color, contentColor: Color, modifier: Modifier = Modifier) {
    val accent = accentOn(rim, MaterialTheme.colorScheme.primary, contentColor)
    Box(
        modifier
            .size(MARK_SIZE + MARK_RIM * 2)
            .background(rim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (selected) Res.drawable.check_circle_filled_24dp else Res.drawable.radio_button_unchecked_24dp),
            // Stav čte čtečka z řádku (`selected`), značka je jen dekorace.
            contentDescription = null,
            tint = if (selected) accent else contentColor.copy(alpha = UNSELECTED_ALPHA),
            modifier = Modifier.size(MARK_SIZE),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
private const val UNSELECTED_ALPHA = 0.7f
private val MARK_SIZE: Dp = 20.dp
private val MARK_RIM: Dp = 2.dp

/** Odstup lišty od spodku, stejný jako u lišty výběru zpráv v konverzaci. */
private val TOOLBAR_CLEARANCE = 24.dp

/** Svislá lišta ve sbaleném sloupci stojí níž — nad ní je seznam avatarů, ne FAB. */
private val VERTICAL_TOOLBAR_CLEARANCE = 16.dp
