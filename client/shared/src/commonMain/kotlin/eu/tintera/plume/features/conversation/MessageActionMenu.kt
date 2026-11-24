package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.menuGroupShape
import eu.tintera.plume.core.ui.menuScroll
import eu.tintera.plume.core.ui.MenuColors
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.features.conversation.items.Delivery
import eu.tintera.plume.features.conversation.items.MenuSlot
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chevron_right_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.conversation_delivery_failed
import eu.tintera.plume.resources.conversation_delivery_read
import eu.tintera.plume.resources.conversation_delivery_sending
import eu.tintera.plume.resources.conversation_delivery_sent
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.menu_info
import eu.tintera.plume.resources.menu_more
import eu.tintera.plume.resources.menu_sent_at
import eu.tintera.plume.resources.menu_sent_at_delivery
import eu.tintera.plume.resources.more_horiz_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Ukázaná položka nabídky nad zprávou: jak vypadá, kam patří a co udělá. */
internal data class MenuItem(
    val icon: DrawableResource,
    val label: String,
    val destructive: Boolean,
    val slot: MenuSlot,
    val onClick: () -> Unit,
)

/** Řádek nabídky, jak se kreslí: položka, nebo jeden z řádků, které nabídka přidává sama. */
private sealed interface MenuRow {
    data class Item(val item: MenuItem) : MenuRow

    /** Čas a doručení; s položkou Informace se na něj dá ťuknout. */
    data object Header : MenuRow

    /** Druhá úroveň („Další"). */
    data object More : MenuRow

    /** Zpět z „Další" na první úroveň (jen na telefonu, kaskáda ho nepotřebuje). */
    data object Back : MenuRow
}

/**
 * Nabídka akcí pod zprávou: skupiny M3 Expressive (`DropdownMenuGroup`) ve dvou úrovních.
 *
 * Nahoře je hlavička s časem a ikonou Informací (ťuknutím Informace), hlavní akce, akce ankety a nálezy v textu,
 * řádek „Další" a nevratné akce ve vlastní skupině. Zbytek je pod „Další" ([MenuLevels]).
 *
 * **Telefon: „Další" vymění obsah na místě**, se šipkou zpět. Vedle nabídky není místo,
 * kaskáda by se položila přes ni. Nabídka dostane výšku VYŠŠÍ z obou úrovní ([height]),
 * takže se výměnou úrovně pod prstem nepohne ani zpráva nad ní; obsah se drží té strany, kde je
 * zpráva ([alignBottom] = nabídka je nad zprávou), a roste od ní pryč.
 *
 * **Desktop: „Další" se otevře do strany** po najetí myší nebo kliknutím — kaskádová nabídka
 * z material3 (vzor `MenuWithCascadingMenusSample`: vnořený `DropdownMenuPopup`
 * s `MenuAnchorPosition.End`). Najetí se počítá na řádku i na podnabídce (sdílený
 * `interactionSource`), takže cesta myší k podnabídce ji nezavře.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActionMenu(
    levels: MenuLevels<MenuItem>,
    timestamp: Int,
    delivery: Delivery?,
    /** Je otevřená druhá úroveň (na telefonu místo první, na desktopu vedle ní)? */
    showMore: Boolean,
    onShowMore: (Boolean) -> Unit,
    cascade: Boolean,
    /** Nabídka je otevřená (ne doznívající po zavření) — jen pak se otevírá kaskáda. */
    open: Boolean,
    height: Dp,
    alignBottom: Boolean,
) {
    val header = headerText(timestamp, delivery)
    val moreInteraction = remember { MutableInteractionSource() }
    val moreHovered by moreInteraction.collectIsHoveredAsState()

    @Composable
    fun Entry(row: MenuRow, index: Int, count: Int) {
        val shape = MenuDefaults.itemShape(index, count).shape
        when (row) {
            is MenuRow.Item -> {
                val colors = if (row.item.destructive) {
                    MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    MenuDefaults.itemColors()
                }
                DropdownMenuItem(
                    onClick = row.item.onClick,
                    // Hodnota MÍSTO popisku („RR123456785CZ" u zásilky): výška nabídky se počítá
                    // na jeden řádek na položku, zalomený popisek by konec nabídky usekl.
                    text = { Text(row.item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = shape,
                    leadingIcon = { MenuIcon(row.item.icon) },
                    colors = colors,
                    contentPadding = ITEM_PADDING,
                    modifier = Modifier.height(MENU_ITEM_HEIGHT),
                )
            }
            MenuRow.Header -> {
                val info = levels.header
                DropdownMenuItem(
                    onClick = { info?.onClick?.invoke() },
                    enabled = info != null,
                    text = {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    shape = shape,
                    /*
                     * Ikona Informací VPŘEDU, ne šipka vzadu: říká, kam řádek vede, a dlouhé datum
                     * („9. října 2025, 8:53 · Přečteno") ji nevytlačí — text v řádku M3 nemá váhu
                     * a koncovou ikonu za dlouhým textem srazí na nulovou šířku. Čtečce obrazovky
                     * říká kam.
                     */
                    leadingIcon = {
                        Icon(
                            painterResource(Res.drawable.info_24dp),
                            contentDescription = info?.let { stringResource(Res.string.menu_info) },
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                    // Hlavička čte jako popisek, ne jako akce — ani zašedlá, když Informace nejsou.
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = ITEM_PADDING,
                    modifier = Modifier.height(MENU_HEADER_HEIGHT),
                )
            }
            MenuRow.More -> Box {
                DropdownMenuItem(
                    onClick = { onShowMore(!showMore) },
                    text = { Text(stringResource(Res.string.menu_more)) },
                    shape = shape,
                    leadingIcon = { MenuIcon(Res.drawable.more_horiz_24dp) },
                    trailingContent = {
                        Icon(
                            painterResource(Res.drawable.chevron_right_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.TrailingIconSize),
                        )
                    },
                    contentPadding = ITEM_PADDING,
                    interactionSource = moreInteraction,
                    modifier = Modifier.height(MENU_ITEM_HEIGHT),
                )
                if (cascade) {
                    DropdownMenuPopup(
                        modifier = Modifier.menuScroll(),
                        expanded = open && (showMore || moreHovered),
                        onDismissRequest = { onShowMore(false) },
                        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.End),
                        // Jako ve vzoru z material3: okno podnabídky fokus nebere, jinak by najetí
                        // myší na hlavní nabídku přestalo chodit.
                        properties = PopupProperties(focusable = false),
                    ) {
                        Groups(levels.more.map { group -> group.map(MenuRow::Item) }, moreInteraction) { row, i, n -> Entry(row, i, n) }
                    }
                }
            }
            MenuRow.Back -> DropdownMenuItem(
                onClick = { onShowMore(false) },
                text = { Text(stringResource(Res.string.menu_more), style = MaterialTheme.typography.titleSmall) },
                shape = shape,
                leadingIcon = {
                    Icon(
                        painterResource(Res.drawable.arrow_back_24dp),
                        contentDescription = stringResource(Res.string.common_back),
                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                    )
                },
                contentPadding = ITEM_PADDING,
                modifier = Modifier.height(MENU_ITEM_HEIGHT),
            )
        }
    }

    val topRows = topRows(levels)
    val moreRows = moreRows(levels)
    Box(
        Modifier.width(MENU_WIDTH).height(height),
        contentAlignment = if (alignBottom) Alignment.BottomStart else Alignment.TopStart,
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = showMore && !cascade && moreRows.isNotEmpty(),
            contentAlignment = if (alignBottom) Alignment.BottomStart else Alignment.TopStart,
            transitionSpec = {
                // Do „Další" se jde doprava (jako do hloubky), zpět doleva.
                val direction = if (targetState) 1 else -1
                (
                    slideInHorizontally(motion.fastSpatialSpec()) { direction * it / 4 } +
                        fadeIn(motion.fastEffectsSpec())
                    ) togetherWith (
                    slideOutHorizontally(motion.fastSpatialSpec()) { -direction * it / 4 } +
                        fadeOut(motion.fastEffectsSpec())
                    ) using SizeTransform(clip = false) { _, _ -> motion.fastSpatialSpec() }
            },
            label = "menu-level",
        ) { more ->
            Groups(
                if (more) moreRows else topRows,
                interactionSource = null,
                // V těsném okně má nabídka strop výšky a posouvá se (viz `placeMenuMessage`).
                modifier = Modifier.heightIn(max = height).verticalScroll(rememberRestoringScrollState()),
            ) { row, i, n -> Entry(row, i, n) }
        }
    }
}

/** Skupiny pod sebou; tvary krajních skupin a položek podle M3 (`groupShape`, `itemShape`). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Groups(
    groups: List<List<MenuRow>>,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    row: @Composable (MenuRow, Int, Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MENU_GROUP_GAP)) {
        MenuColors {
            groups.forEachIndexed { groupIndex, group ->
                DropdownMenuGroup(
                    shapes = menuGroupShape(groupIndex, groups.size),
                    contentPadding = PaddingValues(vertical = MENU_GROUP_PADDING),
                    interactionSource = interactionSource,
                    modifier = Modifier.width(MENU_WIDTH),
                ) {
                    group.forEachIndexed { index, item -> row(item, index, group.size) }
                }
            }
        }
    }
}

@Composable
private fun MenuIcon(icon: DrawableResource) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize))
}

/** Skupiny první úrovně: hlavička v první, „Další" a nevratné akce každá ve vlastní. */
private fun topRows(levels: MenuLevels<MenuItem>): List<List<MenuRow>> = buildList {
    val groups = levels.top.map { group -> group.map(MenuRow::Item) }
    add(listOf(MenuRow.Header) + groups.firstOrNull().orEmpty())
    addAll(groups.drop(1))
    if (levels.more.isNotEmpty()) add(listOf(MenuRow.More))
    if (levels.destructive.isNotEmpty()) add(levels.destructive.map(MenuRow::Item))
}

/** Skupiny „Další" na telefonu: řádek zpět v první z nich. */
private fun moreRows(levels: MenuLevels<MenuItem>): List<List<MenuRow>> =
    levels.more.mapIndexed { index, group ->
        (if (index == 0) listOf<MenuRow>(MenuRow.Back) else emptyList()) + group.map(MenuRow::Item)
    }

/**
 * Kdy zpráva přišla a jak dopadla.
 *
 * V bublině čas není a tahem doleva se k němu čtečka obrazovky nedostane, takže tohle je jediná
 * cesta, která funguje pro každého. Odznak doručení je pod bublinou jen u některých zpráv (viz
 * `readBadgeRows`) a odkrýt ho ťuknutím čtečka neumí — tady ho tedy má vždycky.
 */
@Composable
private fun headerText(timestamp: Int, delivery: Delivery?): String {
    val deliveryLabel = when (delivery) {
        Delivery.Sending -> stringResource(Res.string.conversation_delivery_sending)
        Delivery.Sent -> stringResource(Res.string.conversation_delivery_sent)
        Delivery.Read -> stringResource(Res.string.conversation_delivery_read)
        Delivery.Failed -> stringResource(Res.string.conversation_delivery_failed)
        null -> null
    }
    return if (deliveryLabel == null) {
        stringResource(Res.string.menu_sent_at, timestamp.asDayLabel(), timestamp.asTimeOfDay())
    } else {
        stringResource(Res.string.menu_sent_at_delivery, timestamp.asDayLabel(), timestamp.asTimeOfDay(), deliveryLabel)
    }
}

/** Vodorovné odsazení obsahu položky (`DropdownMenuItemHorizontalPadding`); svisle nic — výška je pevná. */
private val ITEM_PADDING = PaddingValues(horizontal = 12.dp)

internal val MENU_WIDTH = 220.dp
