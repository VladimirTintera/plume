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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * Hlavička při výběru víc chatů: ✕ a počet vybraných. Nahrazuje pilulku hledání — hledat
 * a vybírat zároveň nejde, a hlavička má říct jen to, v jakém režimu uživatel je. Akce jsou
 * dole v liště, v dosahu palce, jako u výběru zpráv v konverzaci.
 */
@Composable
internal fun ChatSelectionTopBar(count: Int, onClose: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(Res.plurals.chats_selection_count, count, count),
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.chats_selection_close),
                )
            }
        },
    )
}

/**
 * Akce nad vybranými chaty místo FAB menu — archivovat, ztlumit, přečíst. Každá je přepínač
 * podle stavu všech vybraných ([chatSelectionActions]), takže ikona i popis říkají, co se
 * STANE. Tatáž plovoucí pilulka (`vibrantFloatingToolbarColors`) jako výběr zpráv.
 *
 * **Archivace zšedne, nezmizí**, když je ve výběru Uložené zprávy — zmizelá akce by neřekla, že
 * ji zrovna tenhle výběr nedovoluje (totéž pravidlo jako u výběru zpráv).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatSelectionToolbar(
    actions: ChatSelectionActions?,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Při odjezdu kreslí lišta poslední akce — v tu chvíli je výběr už pryč.
    val last = remember { arrayOfNulls<ChatSelectionActions>(1) }
    if (actions != null) last[0] = actions
    AnimatedVisibility(
        visible = actions != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.8f),
        modifier = modifier,
    ) {
        val shown = last[0] ?: return@AnimatedVisibility
        // Ikony se musí vystředit samy: obsah dostane toolbar ve vnořeném `Row` bez
        // `verticalAlignment` (viz docs/poznatky/konverzace-pohyb.md, „Hledání v konverzaci").
        // Odstup uvnitř animace: skrytá lišta nesmí nechat 24 dp prázdna nad FAB.
        HorizontalFloatingToolbar(
            modifier = Modifier.padding(bottom = TOOLBAR_CLEARANCE),
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            SelectionAction(
                icon = if (shown.archive) Res.drawable.archive_24dp else Res.drawable.unarchive_24dp,
                label = if (shown.archive) Res.string.chats_action_archive else Res.string.chats_action_unarchive,
                enabled = shown.canArchive,
                onClick = onArchive,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            SelectionAction(
                icon = if (shown.mute) Res.drawable.notifications_off_24dp else Res.drawable.notifications_24dp,
                label = if (shown.mute) Res.string.chats_action_mute else Res.string.chats_action_unmute,
                enabled = true,
                onClick = onMute,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            SelectionAction(
                icon = if (shown.read) Res.drawable.done_all_24dp else Res.drawable.mark_chat_unread_24dp,
                label = if (shown.read) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread,
                enabled = true,
                onClick = onRead,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** Zašednutí se prolíná, aby lišta při změně výběru neproblikla. */
@Composable
private fun SelectionAction(
    icon: DrawableResource,
    label: StringResource,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_ALPHA,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chat-selection-action-alpha",
    )
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.alpha(alpha))
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
