package eu.tintera.plume.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chevron_right_24dp
import eu.tintera.plume.resources.common_back
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Položka [ExpressiveMenu]. [destructive] = nevratná akce, kreslí se v chybové barvě.
 *
 * [trailing] je na konci řádku (odznak s počtem) — je to STAV toho, kam položka vede, ne další
 * akce; klikání nese celý řádek.
 *
 * [supporting] je drobný text pod popiskem — třeba rychlejší cesta k téže akci („podržte šipku
 * zpět"), kterou se uživatel dozví pokaždé, když položku použije.
 *
 * [submenu] = řádek otevře podnabídku s těmito skupinami MÍSTO [onClick]. Skládá se přes
 * [submenuAction], která podnabídku kvůli jediné položce nevyrobí.
 */
class MenuAction(
    val label: String,
    val icon: DrawableResource? = null,
    val destructive: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
    val supporting: String? = null,
    /** Vlastní obsah místo [icon] (fotka účtu v menu účtu). */
    val leading: (@Composable () -> Unit)? = null,
    val submenu: List<List<MenuAction>> = emptyList(),
    val onClick: () -> Unit,
)

/**
 * Řádek s podnabídkou ([MenuAction.submenu]) — nebo nic, když v ní nic není, a rovnou ta
 * položka, když je jediná: podnabídka a zpět kvůli jedné položce jsou dva kroky navíc a nahoře
 * zabere tentýž řádek (stejné pravidlo jako „Další" nad zprávou). Prázdné skupiny zahodí.
 */
fun submenuAction(label: String, icon: DrawableResource?, groups: List<List<MenuAction>>): MenuAction? {
    val shown = groups.filter { it.isNotEmpty() }
    return when (shown.sumOf { it.size }) {
        0 -> null
        1 -> shown.single().single()
        else -> MenuAction(label, icon, submenu = shown, onClick = {})
    }
}

/**
 * Nabídka v podobě M3 Expressive: položky ve skupinách (`DropdownMenuGroup`) se společným
 * zaoblením krajních položek, mezi skupinami mezera.
 *
 * **Skupiny nesou význam**: co k sobě patří, je v jedné, a nevratná akce (smazat, opustit) má
 * skupinu vlastní — oddělená a v chybové barvě se nesplete s častými akcemi nad ní. Stejný
 * jazyk jako bloky v seznamu chatů: krajní položka nese velké zaoblení.
 *
 * Prázdné skupiny se zahazují, takže volající může skupinu poskládat z podmíněných položek
 * a nemusí hlídat, jestli v ní něco zbylo. Nabídka zavře sebe sama před akcí ([onDismiss]).
 *
 * **Podnabídky** ([MenuAction.submenu], jedna úroveň) se chovají jako „Další" nad zprávou
 * (`docs/poznatky/konverzace-akce.md`): na telefonu vymění obsah na místě, první řádek je šipka
 * zpět a systémové zpět vede o úroveň výš, ne pryč; na desktopu ([submenusCascade]) se otevřou
 * do strany po najetí myší nebo kliknutím. Nová nabídka začíná vždycky nahoře.
 *
 * Okno nabídky si nebere klávesnici ([MenuPopupProperties]): nabídka nad vstupním polem ji
 * nechá otevřenou a konverzace pod ní neposkočí. Obsah roluje ([menuScroll]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<List<MenuAction>>,
    popupPositionProvider: DropdownMenuPopupPositionProvider =
        MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below),
    properties: PopupProperties = MenuPopupProperties,
) {
    val shown = groups.filter { it.isNotEmpty() }
    // Otevřená podnabídka (podle popisku řádku). Klíč `expanded`: nová nabídka začíná nahoře.
    var openSubmenu by remember(expanded) { mutableStateOf<String?>(null) }
    // Úroveň, ze které se nabídka zavírá: dojede v ní a během zavírání neskočí zpátky nahoru.
    var closingFrom by remember { mutableStateOf<String?>(null) }
    val host = MenuHost(
        expanded = expanded,
        openSubmenu = openSubmenu,
        onOpenSubmenu = { openSubmenu = it },
        onAction = { action ->
            closingFrom = openSubmenu
            onDismiss()
            action.onClick()
        },
    )
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = {
            closingFrom = openSubmenu
            onDismiss()
        },
        modifier = Modifier.menuScroll(),
        popupPositionProvider = popupPositionProvider,
        properties = properties,
    ) {
        // Zpět se registruje u okna nabídky (popup má vlastní dispečer zpět) a přednost má
        // před jeho zavřením: z podnabídky vede o úroveň výš.
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = expanded && openSubmenu != null && !submenusCascade,
            onBackCompleted = { openSubmenu = null },
        )
        val submenus = if (submenusCascade) emptyList() else shown.flatten().filter { it.submenu.isNotEmpty() }
        if (submenus.isEmpty()) {
            MenuColors { MenuGroups(shown, host) }
        } else {
            LeveledMenu(shown, submenus, if (expanded) openSubmenu else closingFrom, host, popupPositionProvider) {
                closingFrom = openSubmenu
                onDismiss()
            }
        }
    }
}

/**
 * Nabídka s podnabídkami na telefonu: úrovně se vyměňují na místě ([level] = popisek otevřené
 * podnabídky, `null` = hlavní úroveň) v rezervě velké jako největší z nich.
 */
@Composable
private fun LeveledMenu(
    shown: List<List<MenuAction>>,
    submenus: List<MenuAction>,
    level: String?,
    host: MenuHost,
    popupPositionProvider: DropdownMenuPopupPositionProvider,
    onTapOutside: () -> Unit,
) {
    val submenu = level?.let { label -> submenus.firstOrNull { it.label == label } }
    val motion = MaterialTheme.motionScheme
    // Strana, ke které se obsah v rezervě drží: ta u kotvy (⋮ vpravo nahoře → vpravo nahoře).
    // Pozicovač ji zná až po prvním rozmístění, čte se proto až při umístění, ne v kompozici.
    val alignment = {
        val origin = popupPositionProvider.transformOrigin
        BiasAbsoluteAlignment(
            horizontalBias = if (origin.pivotFractionX >= 0.5f) 1f else -1f,
            verticalBias = if (origin.pivotFractionY >= 0.5f) 1f else -1f,
        )
    }
    LargestLevelSize(
        alignment = alignment,
        onTapOutside = onTapOutside,
        levels = {
            MenuGroups(shown, host)
            submenus.forEach { MenuGroups(it.submenu, host, back = it.label) }
        },
    ) {
        AnimatedContent(
            targetState = submenu,
            contentKey = { it?.label },
            transitionSpec = {
                // Do podnabídky se jde doprava (jako do hloubky), zpět doleva. Velikost se
                // neanimuje: rezerva je pro obě úrovně stejná (viz LargestLevelSize).
                //
                // Příchozí úroveň (kreslí se navrch) naskočí RYCHLE a odchozí pod ní mizí
                // pomaleji a na místě — obráceně, než je u přechodů zvykem. Obě skupiny jsou
                // neprůhledné plochy a se stejně rychlým prolnutím by byly v půli obě napůl
                // průhledné: skrz nabídku prosvitly bubliny konverzace (až ~24 % pozadí, tmavé
                // bubliny pod světlou nabídkou vypadaly jako probliknutí). Takhle je to nejvýš ~7 %.
                // Odchozí se ani neposouvá: posunutá odkryla pruh pozadí na kraji okna.
                val direction = if (targetState != null) 1 else -1
                (
                    slideInHorizontally(motion.fastSpatialSpec()) { direction * it / 4 } +
                        fadeIn(motion.fastEffectsSpec())
                    ) togetherWith fadeOut(motion.defaultEffectsSpec()) using SizeTransform(clip = false)
            },
            label = "menu-level",
        ) { sub ->
            MenuColors {
                Box(Modifier.alignWithin(alignment)) {
                    if (sub == null) {
                        MenuGroups(shown, host)
                    } else {
                        MenuGroups(sub.submenu, host, back = sub.label)
                    }
                }
            }
        }
    }
}

/**
 * Nabídka s podnabídkami má po celou dobu velikost NEJVĚTŠÍ ze svých úrovní, ne té, která je
 * vidět. Okno nabídky (`Popup` na Androidu) je `WRAP_CONTENT`: se změnou obsahu se mění velikost
 * okna a jeho poloha se dopočítá až o snímek později. Při přepnutí úrovně, kdy se šířka
 * animovala, tak okno měnilo velikost v každém snímku — obsah byl chvíli oříznutý, posunutý vůči
 * ⋮, a při zvětšení okna probleskl černý pruh na jeho okraji (viděno po snímcích ze záznamu
 * obrazovky emulátoru). Tentýž důvod, proč nabídka nad zprávou drží výšku vyšší z obou úrovní.
 *
 * [levels] se složí jen kvůli změření a neumístí se (nekreslí se, nechytají dotyky, čtečka je
 * nevidí). Viditelná úroveň ([content]) dostane rezervu celou a drží se v ní strany [alignment].
 * Ťuknutí do prázdného zbytku rezervy zavře nabídku jako ťuknutí mimo ni ([onTapOutside]).
 */
@Composable
private fun LargestLevelSize(
    alignment: () -> Alignment,
    onTapOutside: () -> Unit,
    levels: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val tapOutside by rememberUpdatedState(onTapOutside)
    Layout(
        contents = listOf(content, levels),
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { tapOutside() } },
        measurePolicy = LargestLevelPolicy,
    )
}

private val LargestLevelPolicy = object : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val (content, levels) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val sizes = levels.map { it.measure(loose) }
        val width = constraints.constrainWidth(sizes.maxOf { it.width })
        val height = constraints.constrainHeight(sizes.maxOf { it.height })
        val placeable = content.single().measure(Constraints.fixed(width, height))
        return layout(width, height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        measurables[1].maxOf { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        measurables[1].maxOf { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int) =
        measurables[1].maxOf { it.minIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int) =
        measurables[1].maxOf { it.maxIntrinsicHeight(width) }
}

/** Obsah ve své přirozené velikosti, umístěný v celé ploše podle [alignment] (čteno při umístění). */
private fun Modifier.alignWithin(alignment: () -> Alignment): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.constrainWidth(placeable.width)
    val height = constraints.constrainHeight(placeable.height)
    layout(width, height) {
        placeable.place(alignment().align(IntSize(placeable.width, placeable.height), IntSize(width, height), layoutDirection))
    }
}

/** Stav nabídky, který potřebují řádky: otevřená podnabídka a co dělá ťuknutí na položku. */
private class MenuHost(
    val expanded: Boolean,
    val openSubmenu: String?,
    val onOpenSubmenu: (String?) -> Unit,
    val onAction: (MenuAction) -> Unit,
)

/**
 * Skupiny pod sebou. [back] = jsme v podnabídce na telefonu, první řádek je šipka zpět s jejím
 * názvem. [interactionSource] sdílí podnabídka na desktopu s řádkem, který ji otevřel: najetí
 * myší na ni se počítá jako najetí na řádek, takže cesta myší k podnabídce ji nezavře.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuGroups(
    groups: List<List<MenuAction>>,
    host: MenuHost,
    back: String? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    Column {
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = menuGroupShape(groupIndex, groups.size), interactionSource = interactionSource) {
                val backRow = back != null && groupIndex == 0
                val count = group.size + if (backRow) 1 else 0
                if (backRow) {
                    DropdownMenuItem(
                        onClick = { host.onOpenSubmenu(null) },
                        text = { Text(back, style = MaterialTheme.typography.titleSmall) },
                        shape = MenuDefaults.itemShape(0, count).shape,
                        leadingIcon = {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        },
                    )
                }
                group.forEachIndexed { i, action ->
                    val index = i + if (backRow) 1 else 0
                    key(action.label) {
                        if (action.submenu.isEmpty()) {
                            MenuItem(action, index, count, host)
                        } else {
                            SubmenuItem(action, index, count, host)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuItem(action: MenuAction, index: Int, count: Int, host: MenuHost) {
    val colors = if (action.destructive) {
        MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        )
    } else {
        MenuDefaults.itemColors()
    }
    DropdownMenuItem(
        onClick = { host.onAction(action) },
        text = { Text(action.label) },
        shape = MenuDefaults.itemShape(index, count).shape,
        leadingIcon = action.leading ?: action.icon?.let { icon -> { Icon(painterResource(icon), contentDescription = null) } },
        trailingContent = action.trailing,
        supportingText = action.supporting?.let { text -> { Text(text) } },
        colors = colors,
    )
}

/**
 * Řádek, který otevře podnabídku: šipka na konci říká, že ťuknutí vede hlouběji, ne k akci.
 * Na desktopu nese i podnabídku samu — vnořený `DropdownMenuPopup` ukotvený vedle řádku
 * (`MenuAnchorPosition.End`, vzor `MenuWithCascadingMenusSample` z material3), bez fokusu, jinak
 * by najetí myší na hlavní nabídku přestalo chodit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmenuItem(action: MenuAction, index: Int, count: Int, host: MenuHost) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val open = host.openSubmenu == action.label
    Box {
        DropdownMenuItem(
            onClick = { host.onOpenSubmenu(if (open) null else action.label) },
            text = { Text(action.label) },
            shape = MenuDefaults.itemShape(index, count).shape,
            leadingIcon = action.leading ?: action.icon?.let { icon -> { Icon(painterResource(icon), contentDescription = null) } },
            trailingContent = {
                Icon(
                    painterResource(Res.drawable.chevron_right_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(MenuDefaults.TrailingIconSize),
                )
            },
            supportingText = action.supporting?.let { text -> { Text(text) } },
            interactionSource = interaction,
        )
        if (submenusCascade) {
            DropdownMenuPopup(
                expanded = host.expanded && (open || hovered),
                onDismissRequest = { if (open) host.onOpenSubmenu(null) },
                modifier = Modifier.menuScroll(),
                popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.End),
                properties = PopupProperties(focusable = false),
            ) {
                MenuColors {
                    MenuGroups(action.submenu, host, interactionSource = interaction)
                }
            }
        }
    }
}

/**
 * Obsah nabídky roluje, když se nevejde do okna. `DropdownMenuPopup` z material3 Expressive to
 * na rozdíl od starého `DropdownMenu` nedělá sám (1.12: `DropdownMenuPopupContent` je holý
 * `Column`): okno nabídky je nejvýš tak vysoké jako obrazovka a co přeteče, je pod okrajem bez
 * cesty k němu. Telefon naležato má kolem 360 dp, takže z menu účtu zmizelo Nastavení — poslední
 * položka, a jinudy se k němu nejde.
 *
 * Patří do `modifier` každého `DropdownMenuPopup`; hlídá to `verifyMenuScroll`.
 */
@Composable
fun Modifier.menuScroll(): Modifier = verticalScroll(rememberRestoringScrollState())
    .padding(vertical = MenuShadowRoom)

/**
 * Místo pro stín skupin nahoře a dole UVNITŘ rolovaného obsahu. `verticalScroll` ořezává obsah ve
 * směru rolování (do strany ne), takže stín horní skupiny nahoře a spodní skupiny dole byl
 * useknutý rovnou hranou — po stranách stín byl, nahoře a dole ne. Nejvíc to bylo vidět na
 * desktopu. Skupina má stín úrovně 2 (`MenuDefaults.ShadowElevation`, 3 dp) a ten pod ní sahá
 * o pár bodů; nabídka je tím o tolik dál od kotvy, což nevadí.
 */
private val MenuShadowRoom = 6.dp

/**
 * Tvar skupiny nabídky: [MenuDefaults.groupShape] BEZ přechodu do „neaktivního" tvaru.
 *
 * M3 Expressive skupině, nad kterou myš byla a odjela, mění tvar na `inactiveShape` (všechny rohy
 * malé, `SegmentedMenuTokens.InactiveContainerShape`); skupina, nad kterou myš nikdy nebyla,
 * zůstává v tvaru podle pořadí (krajní rohy velké). Na desktopu se tak spodní skupina při každém
 * přejetí myší zaoblila a po odjetí zhranatěla, zatímco horní (myš do ní z kotvy často
 * nevjede) zůstávala kulatá — vypadalo to jako chyba. Na dotykovém displeji hover není a tvar se
 * nemění nikdy; tady platí totéž i pro myš.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun menuGroupShape(index: Int, count: Int): MenuGroupShapes =
    MenuDefaults.groupShape(index, count).let { it.copy(inactiveShape = it.shape) }

/**
 * Barvy nabídky: plocha skupin i položek je `surfaceContainerHighest`, ne výchozí
 * `surfaceContainerLow` z M3 Expressive (`StandardMenuTokens`).
 *
 * `surfaceContainerLow` je pod stránkou (`surfaceContainer`, `asListPage`) i pod řádky na ní
 * (`surfaceBright`), takže nabídka v tmavém motivu vypadala jako díra: nad seznamem chatů #121318
 * na řádcích #2A2C34 (API 37). Vrstvení projektu dává nabídce nad plochou `surfaceContainerHighest`
 * (CLAUDE.md, „Vrstvení"). Přemapovává se role, ne barvy po komponentách: skupinu i každou
 * položku kreslí M3 z téhož tokenu a položek jsou desítky.
 *
 * Kdo kreslí `DropdownMenuPopup` sám, obalí jeho obsah taky.
 */
@Composable
fun MenuColors(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val menu = remember(scheme) { scheme.copy(surfaceContainerLow = scheme.surfaceContainerHighest) }
    MaterialExpressiveTheme(colorScheme = menu, content = content)
}
