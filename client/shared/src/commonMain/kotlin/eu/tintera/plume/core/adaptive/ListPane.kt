package eu.tintera.plume.core.adaptive

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Kotvy, na které jde táhlem posunout hranici mezi seznamem chatů a konverzací. Pořadí odpovídá
 * [listPaneAnchors] a ukládá se jménem (`AppSettings.listPaneAnchor`).
 */
enum class ListPaneAnchor {
    /** Úzký sloupec jen s avatary a odznaky. */
    Collapsed,

    /** Výchozí šířka panelu (360 dp, na XL 412 dp). */
    Default,

    /** Polovina plochy. */
    Half,
}

/**
 * Šířka sbaleného seznamu: avatar s odznakem a okraji. Stejně široký jako navigační rail
 * v M3 Expressive, takže sloupec vypadá jako známý prvek, ne jako useknutý seznam.
 */
val COLLAPSED_LIST_WIDTH = 96.dp

/**
 * Pod touhle šířkou seznam chatů kreslí jen avatary. Řádek s názvem a náhledem by byl
 * nečitelný už mnohem dřív, než seznam dojede na sbalenou kotvu — a během tahu přes táhlo
 * se tudy prochází, takže se seznam přepne uprostřed tahu, ne až po puštění.
 */
val COLLAPSED_LIST_MAX_WIDTH = 200.dp

/**
 * Šířka seznamu ve třech panelech (seznam | konverzace | profil). Táhlo tam knihovna nekreslí
 * (umí jen dva panely), takže se zvolená kotva přenáší jako preferovaná šířka seznamu — jinak by
 * seznam po otevření profilu skočil zpátky na výchozí šířku.
 *
 * Sbalený zůstane sbalený. Širší seznam se zúží tak, aby vedle profilu (výchozí šířka) zbyla
 * konverzaci aspoň [MIN_CONVERSATION_WIDTH]: polovina plochy by ji jinak zmáčkla na pár slov.
 *
 * Mezi panely scaffold mezeru nedělá; [gap] jsou mezery, které si dělají karty (`PaneCard`):
 * konverzace od seznamu a profil od konverzace i od kraje okna.
 */
fun listWidthBesideProfile(anchor: ListPaneAnchor, total: Dp, defaultWidth: Dp, gap: Dp): Dp =
    listWidth(anchor, total, defaultWidth, reserveBesideProfile(defaultWidth, gap))

/**
 * Libovolná šířka seznamu ve třech panelech (za tahu táhlem) omezená jako [listWidthBesideProfile]:
 * nejužší sbalený sloupec, nejširší tak, aby vedle profilu na výchozí šířce zbylo konverzaci
 * [MIN_CONVERSATION_WIDTH]. Seznam má přednost: širší profil se pak zúží on ([profileWidthBeside]).
 */
fun clampListBesideProfile(wanted: Dp, total: Dp, defaultWidth: Dp, gap: Dp): Dp =
    clampListWidth(wanted, total, reserveBesideProfile(defaultWidth, gap))

/** Kotva, jejíž šířka ve třech panelech je nejblíž [width] — kam seznam dosedne po puštění táhla. */
fun nearestListAnchorBesideProfile(width: Dp, total: Dp, defaultWidth: Dp, gap: Dp): ListPaneAnchor =
    nearestListAnchor(width, total, defaultWidth, reserveBesideProfile(defaultWidth, gap))

/**
 * Šířka seznamu na kotvě [anchor], když vedle něj musí zůstat [reserve]: konverzace (a profil
 * vedle ní) i s okraji karet. Sbalený zůstane sbalený, širší se zúží na místo, které zbývá.
 *
 * Obecná podoba [listWidthBesideProfile] pro scénu Chatů (`PaneScene`), která má vlastní táhlo
 * seznamu i ve dvou panelech: tam se rezervuje jen nejužší konverzace s okraji.
 */
fun listWidth(anchor: ListPaneAnchor, total: Dp, defaultWidth: Dp, reserve: Dp): Dp {
    val wanted = when (anchor) {
        ListPaneAnchor.Collapsed -> return COLLAPSED_LIST_WIDTH
        ListPaneAnchor.Default -> defaultWidth
        ListPaneAnchor.Half -> total / 2
    }
    return clampListWidth(wanted, total, reserve)
}

/** Libovolná šířka seznamu (za tahu) omezená jako [listWidth]. */
fun clampListWidth(wanted: Dp, total: Dp, reserve: Dp): Dp =
    wanted.coerceAtMost(total - reserve).coerceAtLeast(COLLAPSED_LIST_WIDTH)

/** Kotva, jejíž šířka podle [listWidth] je nejblíž [width]. */
fun nearestListAnchor(width: Dp, total: Dp, defaultWidth: Dp, reserve: Dp): ListPaneAnchor =
    ListPaneAnchor.entries.minBy { abs((listWidth(it, total, defaultWidth, reserve) - width).value) }

/** Co musí zůstat vedle seznamu ve třech panelech: profil na výchozí šířce, nejužší konverzace a mezery [gap]. */
fun reserveBesideProfile(defaultWidth: Dp, gap: Dp): Dp = defaultWidth + MIN_CONVERSATION_WIDTH + gap

/** Co musí zůstat vedle seznamu ve dvou panelech: nejužší konverzace s okraji karty od seznamu a od kraje okna. */
val RESERVE_BESIDE_LIST: Dp = MIN_CONVERSATION_WIDTH + PANE_SPACING * 2

/**
 * Rozbalení sbaleného seznamu chatů na výchozí šířku — hledání ve sbaleném sloupci potřebuje
 * místo pro pilulku a výsledky. `null` tam, kde seznam vedle konverzace není.
 */
val LocalExpandListPane = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Tlačítko a zkratka, které seznam chatů vedle konverzace sbalí na sloupec s avatary a zase
 * rozbalí (`ListPaneToggleButton`, Ctrl+Shift+S / ⌃⌘S). Táhlo samo se najít nedá a z klávesnice
 * ani čtečkou s ním pohnout nejde. Směr určuje zvolená kotva, ne šířka: za tahu táhlem by se
 * měnil pod rukou.
 */
@Stable
class ListPaneToggle(val toggle: () -> Unit)

/** Přepínač sbalení seznamu chatů; `null` tam, kde hlavní seznam chatů vedle konverzace není. */
val LocalListPaneToggle = compositionLocalOf<ListPaneToggle?> { null }
