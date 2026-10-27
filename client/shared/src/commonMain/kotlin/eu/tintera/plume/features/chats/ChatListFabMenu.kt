package eu.tintera.plume.features.chats

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.chats_create
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.new_chat_new_channel
import eu.tintera.plume.resources.new_chat_new_group
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.chats_new_story
import eu.tintera.plume.resources.photo_camera_24dp
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_new
import eu.tintera.plume.resources.edit_24dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Plocha pod otevřeným FAB menu: ťuknutí kamkoli vedle ho zavře a nepropadne dál (otevřelo by
 * chat nebo hledání). Obsah pod položkami ztlumí, jinak by se pilulky menu slévaly s řádky.
 * Kreslí se dvakrát — přes seznam a přes hlavičku, kterou Scaffold kreslí nad obsahem.
 */
@Composable
internal fun FabMenuScrim(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    AnimatedVisibility(visible = visible, enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()), modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = FAB_MENU_SCRIM_ALPHA))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                .clearAndSetSemantics {},
        )
    }
}

/**
 * „Vytvořit" — FAB menu M3 Expressive: přepínací FAB (tužka → ✕, zaoblený čtverec → kruh)
 * a nad ním Nový kanál, Nová skupina a Nová zpráva. Nová zpráva je u palce, nejblíž FAB.
 *
 * Dřív to byl rozšířený FAB „Nová zpráva", který se při scrollu sbalil na ikonu; skupina
 * a kanál byly jen řádky na obrazovce Nové zprávy (jsou tam dál, pro odkazy a hledání).
 * Popisek nesou položky, přepínač je jen ikona — tak to M3 u FAB menu chce.
 *
 * Položka menu nejdřív menu zavře, teprve pak naviguje: po návratu do seznamu má být zavřené.
 *
 * Se složkami je přepínač ve slotu lišty ([toolbar] dostane FAB a postaví kolem něj
 * `ChatFolderToolbar`) a „tlačítkem" menu je celá lišta, takže položky vyjedou nad ní.
 */
@Composable
internal fun NewChatFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    /** Nový příběh; `null` = položka není (archiv, platforma bez příběhů). */
    onNewStory: (() -> Unit)? = null,
    toolbar: (@Composable (fab: @Composable () -> Unit) -> Unit)? = null,
) {
    val createLabel = stringResource(Res.string.chats_create)
    val closeLabel = stringResource(Res.string.common_close)
    val closeAction = CustomAccessibilityAction(closeLabel) { onExpandedChange(false); true }
    // Příběh nahoře, nejdál od palce: je nejvzácnější. Nová zpráva zůstává u tlačítka.
    val items = listOfNotNull(
        onNewStory?.let { Triple(Res.drawable.photo_camera_24dp, Res.string.chats_new_story, it) },
        Triple(Res.drawable.campaign_24dp, Res.string.new_chat_new_channel, onNewChannel),
        Triple(Res.drawable.group_24dp, Res.string.new_chat_new_group, onNewGroup),
        Triple(Res.drawable.edit_24dp, Res.string.chats_new, onNewChat),
    )
    val fab = @Composable { containerSize: (Float) -> Dp ->
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = onExpandedChange,
            containerSize = containerSize,
            modifier = Modifier.semantics {
                // Přepínač je v pořadí čtečky PŘED položkami nad ním, jako v ukázce M3.
                traversalIndex = -1f
                contentDescription = if (expanded) closeLabel else createLabel
            },
        ) {
            val close by remember { derivedStateOf { checkedProgress > 0.5f } }
            Icon(
                painterResource(if (close) Res.drawable.close_24dp else Res.drawable.edit_24dp),
                contentDescription = null,
                modifier = Modifier.toggleFabIcon({ checkedProgress }, ToggleFloatingActionButtonDefaults.iconColor()),
            )
        }
    }
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            if (toolbar == null) fab(ToggleFloatingActionButtonDefaults.containerSize()) else toolbar { ToolbarSlotFab(fab) }
        },
    ) {
        items.forEachIndexed { index, (icon, label, onClick) ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    onClick()
                },
                icon = { Icon(painterResource(icon), contentDescription = null) },
                text = { Text(stringResource(label)) },
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    // Přepínač je ve čtečce před položkami; z poslední se menu zavře akcí.
                    if (index == items.lastIndex) customActions = listOf(closeAction)
                },
            )
        }
    }
}

/**
 * Barva a velikost ikony přepínacího FAB podle průběhu otevření, náhrada `animateIcon` z M3.
 *
 * `animateIcon` nahraje ikonu do grafické vrstvy v `drawWithCache` a dál kreslí jen tu nahranou
 * vrstvu. Compose UI 1.12 ale vrstvy z `drawWithCache` při odpojení uzlu uvolní, a mezipaměť
 * přitom nechá platnou (`CacheDrawModifierNodeImpl.onDetach` zneplatňuje jen `onReset`). Po
 * rozložení Foldu NavDisplay přesune seznam do jiné scény, uzel se odpojí a znovu připojí
 * a kreslí uvolněnou, prázdnou vrstvu: FAB bez ikony, dokud menu někdo neotevře (barva se
 * čte v mezipaměti, takže ji změna průběhu zneplatní). Vrstvu z `graphicsLayer` vlastní
 * uzel a po připojení ji nahraje znovu. Barva i velikost se čtou při kreslení a měření,
 * takže se během přechodu nic nerekomponuje.
 */
private fun Modifier.toggleFabIcon(
    checkedProgress: () -> Float,
    color: (Float) -> Color,
    size: (Float) -> Dp = ToggleFloatingActionButtonDefaults.iconSize(),
): Modifier = this
    .layout { measurable, _ ->
        val sizePx = size(checkedProgress()).roundToPx()
        val placeable = measurable.measure(Constraints.fixed(sizePx, sizePx))
        layout(sizePx, sizePx) { placeable.place(0, 0) }
    }
    .graphicsLayer { colorFilter = ColorFilter.tint(color(checkedProgress())) }

/**
 * Přepínací FAB ve slotu lišty složek. Velikost slotu určuje lišta (56 dp rozbalená, 80 dp
 * sbalená) a FAB ji musí vyplnit; přepínač má ale velikost vlastní, z níž se při otevření menu
 * morfuje do kruhu „Zavřít". Výchozí velikost proto začíná na velikosti slotu: zavřený FAB
 * slot vyplní, otevřený se zmenší na zavírací kruh.
 *
 * Slot se čte z omezení při měření a do lambdy jde obyčejnou proměnnou, ne stavem: přepínač
 * velikost čte v měření a lišta mění slot právě přeměřením, takže změna dojde i bez stavu.
 */
@Composable
private fun ToolbarSlotFab(fab: @Composable (containerSize: (Float) -> Dp) -> Unit) {
    val slot = remember { floatArrayOf(TOOLBAR_FAB_EXPANDED_SIZE.value) }
    val closeSize = ToggleFloatingActionButtonDefaults.containerSize()(1f)
    Box(
        Modifier.layout { measurable, constraints ->
            slot[0] = constraints.maxWidth.toDp().value
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
        /*
         * Pevně na celý slot, jako `fillMaxSize` u FAB z ukázek M3. Lišta i `Box` dávají dětem
         * minimum 0, přepínač by si pak vzal svých 56 dp a v nafouknutém slotu by se jen
         * přilepil do rohu (ověřeno na emulátoru).
         */
        propagateMinConstraints = true,
    ) {
        fab { progress -> lerp(slot[0].dp, closeSize, progress) }
    }
}

/** Ztlumení seznamu pod otevřeným FAB menu. */
private const val FAB_MENU_SCRIM_ALPHA = 0.8f

/** FAB v rozbalené liště složek (standardní) a ve sbalené (střední) — hodnoty `FloatingToolbar`. */
private val TOOLBAR_FAB_EXPANDED_SIZE = 56.dp

internal val TOOLBAR_FAB_COLLAPSED_SIZE = 80.dp
